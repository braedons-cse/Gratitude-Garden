package com.gratitudegarden.app.data

import android.util.Log
import androidx.room.withTransaction
import com.gratitudegarden.app.data.local.EntryEntity
import com.gratitudegarden.app.data.local.GardenDatabase
import com.gratitudegarden.app.data.local.OutboxOp
import com.gratitudegarden.app.data.local.OutboxType
import com.gratitudegarden.app.data.local.SettingsEntity
import com.gratitudegarden.app.data.local.SyncCursorEntity
import com.gratitudegarden.app.data.local.SyncState
import com.gratitudegarden.app.data.local.epochMicros
import com.gratitudegarden.app.data.local.toEntity
import com.gratitudegarden.app.data.local.toRow
import com.gratitudegarden.app.util.LogTags
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

// ── Row DTOs (partial — only the columns the UI needs) ───────────────
@Serializable
data class GratitudeEntry(
    val id: String,
    @SerialName("entry_text") val entryText: String,
    @SerialName("input_method") val inputMethod: String,
    /** Null while the entry waits to sync: the server decides the reward. */
    @SerialName("coins_awarded") val coinsAwarded: Int? = null,
    /** Null while the entry waits to sync, like [coinsAwarded]. */
    @SerialName("xp_awarded") val xpAwarded: Int? = null,
    @SerialName("entry_date") val entryDate: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
    /** Read only by the entry sync, as its cursor; not stored locally. */
    @SerialName("updated_at") val updatedAt: String? = null,
    /** Local only: whether the server has this version yet. */
    @Transient val syncState: SyncState = SyncState.SYNCED,
    /** Local only: the server's reason, when it refused this entry ([SyncState.FAILED]). */
    @Transient val syncError: String? = null,
)

/** What became of a submitted entry. In every case it is in the journal. */
sealed interface SubmitResult {
    /** The server has it. [coins] and [xp] are null only if its reply couldn't be read. */
    data class Planted(val coins: Int?, val xp: Int? = null) : SubmitResult

    /** Saved on the device; it goes to the server once it can. */
    data object Saved : SubmitResult

    /** The server turned it down, for [reason]; the entry is kept, marked failed. */
    data class Refused(val reason: String) : SubmitResult
}

@Serializable
data class Garden(
    val id: String,
    val name: String,
    @SerialName("grid_rows") val gridRows: Int = 6,
    @SerialName("grid_cols") val gridCols: Int = 5,
    /** Only ever written by set_active_backdrop, which checks ownership. */
    @SerialName("active_backdrop_item_id") val activeBackdropItemId: String? = null,
)

@Serializable
data class GardenPlantRow(
    val id: String,
    @SerialName("item_id") val itemId: String,
    @SerialName("grid_x") val gridX: Int,
    @SerialName("grid_y") val gridY: Int,
    @SerialName("growth_stage") val growthStage: String,
    val health: String,
)

@Serializable
data class UserStatsRow(
    @SerialName("total_entries") val totalEntries: Int = 0,
    @SerialName("current_streak") val currentStreak: Int = 0,
    @SerialName("longest_streak") val longestStreak: Int = 0,
    @SerialName("last_entry_date") val lastEntryDate: String? = null,
    /** Freezes banked. Read through [freezesOn]: this month's free one may not be banked yet. */
    @SerialName("streak_freezes") val streakFreezes: Int = 0,
    /** First day (`yyyy-MM-01`) of the last month whose free freeze was banked; null = never. */
    @SerialName("freeze_grant_month") val freezeGrantMonth: String? = null,
)

/** Most freezes anyone can hold; the check on `user_stats.streak_freezes`. */
const val MAX_STREAK_FREEZES = 2

/** What a freeze costs. Only labels the button: `buy_streak_freeze` charges its own price. */
const val STREAK_FREEZE_PRICE = 50

/** The streak to show right now. */
data class StreakStatus(
    val days: Int = 0,
    /**
     * Days have been missed since the last entry, and the freezes on hand cover them: the
     * next entry spends them and the run carries on. Until then the streak is on ice.
     */
    val heldByFreeze: Boolean = false,
    /** Freezes available, this month's free one included. */
    val freezes: Int = 0,
)

/**
 * Freezes available on [today], this month's free one included. Mirrors `freezes_on()` in
 * migration 20260924200000, which grants the monthly one lazily: change both or neither.
 */
fun UserStatsRow.freezesOn(today: LocalDate): Int {
    val granted = freezeGrantMonth?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val unbanked = if (granted == null || granted < today.withDayOfMonth(1)) 1 else 0
    return minOf(MAX_STREAK_FREEZES, streakFreezes + unbanked)
}

/**
 * The streak as of [today].
 *
 * The stored [UserStatsRow.currentStreak] is only rewritten when an entry is submitted, so
 * between submits it goes stale: a run that ended days ago keeps its old value until the
 * next entry resets it. The run is alive while the last entry was today or yesterday. After
 * that it survives only if the freezes on hand cover every missed day, because the next
 * entry will spend them (`submit_gratitude_entry` bridges a gap only when it can bridge all
 * of it); otherwise it's broken and shows 0.
 *
 * The clock is injected so this is testable without the machine's date; [streakNow]
 * supplies the real one.
 */
fun UserStatsRow.streakOn(today: LocalDate): StreakStatus {
    val freezes = freezesOn(today)
    val last = lastEntryDate
        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?: return StreakStatus(freezes = freezes)
    // One day ahead is legitimate: an entry is dated in the zone it was written in, so after
    // flying west the last entry can sit on what is still tomorrow here. Further out isn't.
    if (last > today.plusDays(1)) return StreakStatus(freezes = freezes)
    if (last >= today.minusDays(1)) return StreakStatus(currentStreak, heldByFreeze = false, freezes)
    // Frozen days only ever sit before a written one, so none lie after [last].
    val missed = ChronoUnit.DAYS.between(last, today) - 1
    return if (missed <= freezes) StreakStatus(currentStreak, heldByFreeze = true, freezes)
    else StreakStatus(freezes = freezes)
}

/**
 * The day an entry written at [writtenAt] counts for: its local date in [zone]. Mirrors
 * `(v_written at time zone v_tz)::date` in `submit_gratitude_entry`, which trusts the write
 * time sent with a queued entry, so one written offline before midnight keeps its day.
 */
fun entryDay(writtenAt: Instant, zone: ZoneId): LocalDate = writtenAt.atZone(zone).toLocalDate()

val UserStatsRow.streakNow: StreakStatus
    get() = streakOn(LocalDate.now())

@Serializable
data class WalletRow(val balance: Int = 0)

@Serializable
data class ProfileRow(
    @SerialName("display_name") val displayName: String = "",
    val level: Int = 1,
    val xp: Int = 0,
    @SerialName("is_admin") val isAdmin: Boolean = false,
)

@Serializable
data class Item(
    val id: String,
    val slug: String,
    val category: String,
    val name: String,
    val rarity: String = "common",
    @SerialName("price_coins") val priceCoins: Int = 0,
    @SerialName("level_required") val levelRequired: Int = 1,
    @SerialName("is_purchasable") val isPurchasable: Boolean = true,
    @SerialName("is_starter") val isStarter: Boolean = false,
)

@Serializable
private data class InventoryRow(@SerialName("item_id") val itemId: String)

@Serializable
private data class FrozenDayRow(val day: String)

@Serializable
private data class SettingsRow(
    @SerialName("notif_prompt_seen") val notifPromptSeen: Boolean = false,
    @SerialName("daily_entry_cap") val dailyEntryCap: Int? = null,
)

/**
 * The app's one source of data. Screens observe Room through the `observe*` flows; the
 * refreshes pull from Supabase and write into Room, so a failed refresh leaves the last
 * good copy on screen instead of empty defaults (docs/offline-first-plan.md).
 *
 * Business logic (coins, streaks, provisioning) lives in Postgres RPCs. A mutation calls
 * one, then refreshes the tables it touched; every screen observing them updates from Room.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class GardenRepository(
    private val client: SupabaseClient,
    private val db: GardenDatabase,
    private val connectivity: ConnectivityMonitor,
    private val outboxScheduler: OutboxScheduler = OutboxScheduler.None,
) {

    // Refreshes run here rather than in the caller's scope: one started by a screen that
    // then goes away still finishes, and sign-out can cancel all of them before it wipes
    // the tables, so none can write the previous user's rows back afterwards.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Long-lived watchers (the session, refresh triggers). Separate from [scope] because
    // sign-out cancels everything in [scope], and these must keep running across it.
    private val watchScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Whose data to show. Unlike the auth library's status, a stored session that can't be
     * refreshed right now (offline, token expired) still counts as signed in; see
     * [appSessionFor].
     */
    val session: StateFlow<AppSession> = client.auth.sessionStatus
        .mapLatest { status ->
            val retrying = status is SessionStatus.Initializing || status is SessionStatus.RefreshFailure
            appSessionFor(status, if (retrying) storedUserId() else null)
        }
        .stateIn(watchScope, SharingStarted.Eagerly, AppSession.Loading)

    private suspend fun storedUserId(): String? =
        client.auth.sessionManager.loadSessionOrNull()?.userId()

    /**
     * Whether requests go out with the user's own token. Without one (initializing, or the
     * refresh is failing) they'd carry only the anon key. RLS would refuse them today, but
     * the cache shouldn't depend on that: a refresh that came back empty would wipe it.
     */
    private fun hasToken(): Boolean = client.auth.sessionStatus.value is SessionStatus.Authenticated

    // One refresh of each group of tables at a time. Two overlapping fetches of the same
    // rows could finish out of order and leave the older copy in Room.
    private val accountLock = Mutex()
    private val gardenLock = Mutex()
    private val catalogLock = Mutex()
    private val entriesLock = Mutex()

    // One delivery of the journal outbox at a time, so ops go out in order.
    private val outboxLock = Mutex()

    // Local journal writes, and the delivery claiming the op it is about to send. Held
    // only around Room, never across a network call.
    private val localLock = Mutex()

    /**
     * The op being sent right now, or null. A local write never folds a change into it:
     * the request has already left with the old contents.
     */
    @Volatile private var inFlight: Long? = null

    // ── Auth ─────────────────────────────────────────────────────────
    // Signing in or up starts from an empty database: whatever is left belongs to a
    // session that already ended, possibly someone else's.
    suspend fun signIn(email: String, password: String) {
        clearLocal()
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun signUp(email: String, password: String, displayName: String) {
        clearLocal()
        client.auth.signUpWith(Email) {
            this.email = email
            this.password = password
            data = buildJsonObject { put("display_name", displayName) }
        }
    }

    suspend fun signOut() {
        client.auth.signOut()
        clearLocal()
    }

    /**
     * Permanently delete the signed-in user's own account.
     *
     * [password] is re-verified by signing in again (a wrong password throws
     * here, before anything is deleted). The `delete_current_user` RPC then
     * removes the row in auth.users server-side; the ON DELETE CASCADE foreign
     * keys wipe every owned row (profile, settings, stats, wallet, entries,
     * garden + plants, inventory). Finally the now-defunct local session and
     * the local copy of the data are cleared so the app returns to the auth screen.
     */
    suspend fun deleteOwnAccount(password: String) {
        val email = client.auth.currentUserOrNull()?.email
            ?: throw IllegalStateException("No signed-in account.")
        // Re-authenticate to confirm the password belongs to this account.
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
        client.postgrest.rpc("delete_current_user")
        // The session's user no longer exists; drop it locally regardless of the
        // server round-trip so sessionStatus flips to NotAuthenticated.
        runCatching { client.auth.signOut() }
        clearLocal()
    }

    /**
     * Drop everything stored on the device. It is one person's journal, possibly on a
     * shared phone. In-flight refreshes are cancelled first so none can write back into
     * the emptied tables.
     */
    private suspend fun clearLocal() {
        outboxScheduler.cancel()
        scope.coroutineContext.job.children.forEach { it.cancelAndJoin() }
        lastFullRefreshAt = null
        withContext(Dispatchers.IO) { db.clearAllTables() }
    }

    /**
     * Whether the device can reach the internet. The journal works either way; the garden's
     * economy (buy, plant, water, move, dig) is online-only, and its buttons say so.
     */
    val isOnline: StateFlow<Boolean> get() = connectivity.isOnline

    // ── Reads (Room) ─────────────────────────────────────────────────
    // Every read is scoped to the signed-in user, captured when the flow is created. The
    // screens' ViewModels live inside a per-session scope (SessionScope), so a flow never
    // outlives the user it was made for. The user comes from [session], not the auth
    // library, which reports no user at all while a token refresh is failing.
    private fun currentUid(): String? = (session.value as? AppSession.SignedIn)?.userId

    private inline fun <T> ofUser(signedOut: T, flow: (String) -> Flow<T>): Flow<T> =
        currentUid()?.let(flow) ?: flowOf(signedOut)

    fun observeProfile(): Flow<ProfileRow?> =
        ofUser(null) { uid -> db.accountDao().observeProfile(uid).map { it?.toRow() } }

    fun observeWallet(): Flow<WalletRow?> =
        ofUser(null) { uid -> db.accountDao().observeWallet(uid).map { it?.toRow() } }

    fun observeStats(): Flow<UserStatsRow?> =
        ofUser(null) { uid -> db.statsDao().observe(uid).map { it?.toRow() } }

    fun observeGarden(): Flow<Garden?> =
        ofUser(null) { uid -> db.gardenDao().observeGarden(uid).map { it?.toRow() } }

    fun observePlants(): Flow<List<GardenPlantRow>> =
        ofUser(emptyList()) { uid ->
            db.gardenDao().observeGarden(uid).flatMapLatest { garden ->
                if (garden == null) flowOf(emptyList())
                else db.gardenDao().observePlants(garden.id).map { rows -> rows.map { it.toRow() } }
            }
        }

    /** The whole shop catalog, cheapest first. Global, so it is the same for every user. */
    fun observeCatalog(): Flow<List<Item>> =
        db.itemDao().observeCatalog().map { rows -> rows.map { it.toRow() } }

    /** Ids of the items the user owns. */
    fun observeInventory(): Flow<Set<String>> =
        ofUser(emptySet()) { uid -> db.itemDao().observeInventory(uid).map { it.toSet() } }

    /** The newest [limit] live entries. The Journal pages by asking for a bigger [limit]. */
    fun observeEntries(limit: Int): Flow<List<GratitudeEntry>> =
        ofUser(emptyList()) { uid ->
            db.entryDao().observeNewest(uid, limit).map { rows -> rows.map { it.toRow() } }
        }

    /** The entry dates (`yyyy-MM-dd`) within the last [days] days, for the Journal's week strip. */
    fun observeRecentEntryDates(days: Int = 7): Flow<Set<String>> =
        ofUser(emptySet()) { uid ->
            val since = LocalDate.now().minusDays((days - 1).toLong()).toString()
            db.entryDao().observeDatesSince(uid, since).map { it.toSet() }
        }

    /** The days (`yyyy-MM-dd`) within the last [days] days that a streak freeze covered. */
    fun observeRecentFrozenDates(days: Int = 7): Flow<Set<String>> =
        ofUser(emptySet()) { uid ->
            val since = LocalDate.now().minusDays((days - 1).toLong()).toString()
            db.statsDao().observeFrozenDaysSince(uid, since).map { it.toSet() }
        }

    /**
     * How many entries the user has written today, **including soft-deleted ones**.
     *
     * This has to match `submit_gratitude_entry`'s cap check exactly, and that check
     * counts deleted entries too: `delete_gratitude_entry` stamps `deleted_at` without
     * refunding the coins, so a deleted entry has still been paid for. The entry sync
     * keeps deleted rows for exactly this reason.
     *
     * "Today" is [entryDay] of now: the day an entry written now counts for.
     */
    fun observeEntriesTodayCount(): Flow<Int> =
        ofUser(0) { uid ->
            val today = entryDay(Instant.now(), ZoneId.systemDefault()).toString()
            db.entryDao().observeCountOn(uid, today)
        }

    /** Entries per day that earn coins: the server's `daily_entry_cap`, clamped as it clamps it. */
    fun observeDailyCap(): Flow<Int> =
        ofUser(SettingsEntity.DEFAULT_DAILY_CAP) { uid ->
            db.accountDao().observeSettings(uid).map { it?.dailyEntryCap ?: SettingsEntity.DEFAULT_DAILY_CAP }
        }

    /**
     * Fires when anything the home-screen widget shows may have changed: who is signed in,
     * or a write to one of its tables. It carries no data. The reader re-reads, with "today"
     * as it is then, which the flows above can't do: they fix the day when they're created.
     */
    fun widgetChanges(): Flow<Unit> =
        session.flatMapLatest { s ->
            if (s is AppSession.Loading) emptyFlow()
            else db.invalidationTracker.createFlow(*WIDGET_TABLES, emitInitialState = true).map { }
        }

    /**
     * True once the enable-reminders prompt has been shown (accepted or declined). Read
     * from the local copy of `user_settings`; until that has been fetched, answers true,
     * so the prompt can't appear for someone who has already dismissed it.
     */
    suspend fun notifPromptSeen(): Boolean {
        val uid = currentUid() ?: return true // not signed in → never prompt
        return db.accountDao().observeSettings(uid).first()?.notifPromptSeen ?: true
    }

    // ── Refresh (Supabase → Room) ────────────────────────────────────
    // Remote reads MUST filter by the signed-in user id explicitly — do NOT rely on
    // RLS to return a single row. Admins have additive "read any row" policies
    // (for the admin dashboard), so an unfiltered select returns EVERY user's
    // row and firstOrNull() would grab an arbitrary account (e.g. someone
    // else's profile). Always scope reads to the current uid.

    private var fullRefresh: Deferred<Unit>? = null
    @Volatile private var lastFullRefreshAt: TimeMark? = null

    init {
        // A queue left from an earlier run gets a delivery scheduled. WorkManager keeps one
        // that was already scheduled across restarts; this covers a write that raced a
        // finishing worker, and an app that was force-stopped, which cancels its jobs.
        watchScope.launch {
            try {
                if (db.outboxDao().size() > 0) outboxScheduler.schedule()
            } catch (e: Exception) {
                Log.w(LogTags.APP_LOGIC, "Couldn't check the journal outbox", e)
            }
        }
        // Refresh on its own when the network comes back or the auth library gets a valid
        // token again, so a screen that loaded offline doesn't stay stale until the user
        // writes something or restarts the app. Both are needed: after a reconnect the
        // token may still be expired, and the refresh waits for the second trigger.
        watchScope.launch {
            merge(
                connectivity.isOnline.becameTrue(),
                client.auth.sessionStatus.map { it is SessionStatus.Authenticated }.becameTrue(),
            ).collect {
                try {
                    drainOutbox()
                    refreshAll()
                } catch (e: Exception) {
                    Log.w(LogTags.APP_LOGIC, "Automatic refresh failed", e)
                }
            }
        }
    }

    /**
     * Refresh every table from Supabase. Callers that overlap share one run, and unless
     * [force] is set, a run that finished within [FRESH_FOR] counts as current. That lets
     * each screen ask on creation without multiplying requests; pull-to-refresh forces.
     *
     * Throws if the refresh fails. Room keeps what it had, so the caller only has to
     * decide whether to say so.
     */
    suspend fun refreshAll(force: Boolean = false) {
        val run = synchronized(this) {
            fullRefresh?.takeIf { it.isActive }
                ?: if (!force && lastFullRefreshAt.isWithin(FRESH_FOR)) null
                else scope.async { refreshEverything() }.also { fullRefresh = it }
        }
        run?.await()
    }

    private fun TimeMark?.isWithin(d: Duration) = this != null && elapsedNow() < d

    private suspend fun refreshEverything() {
        val uid = currentUid() ?: return
        // Skipped rather than failed: the refresh below runs once the token is back.
        if (!hasToken()) return
        coroutineScope {
            launch { refreshAccount(uid) }
            launch { refreshGarden(uid) }
            launch { refreshCatalog(uid) }
            launch { syncEntries(uid) }
        }
        lastFullRefreshAt = TimeSource.Monotonic.markNow()
    }

    private suspend inline fun <reified T : Any> fetchOwnRow(table: String, uid: String, idColumn: String = "user_id"): T? =
        client.postgrest.from(table).select { filter { eq(idColumn, uid) } }.decodeList<T>().firstOrNull()

    /** Profile, wallet, stats and settings: one row each. */
    private suspend fun refreshAccount(uid: String) {
        accountLock.withLock {
            coroutineScope {
                // Fetched concurrently; every await happens before the transaction opens,
                // so no write lock is held across a network call.
                val profileQ = async { fetchOwnRow<ProfileRow>("profiles", uid, idColumn = "id") }
                val walletQ = async { fetchOwnRow<WalletRow>("coin_wallets", uid) }
                val statsQ = async { fetchOwnRow<UserStatsRow>("user_stats", uid) }
                val settingsQ = async { fetchOwnRow<SettingsRow>("user_settings", uid) }
                // At most two a month, so the whole history is small.
                val frozenQ = async {
                    client.postgrest.from("streak_frozen_days").select {
                        filter { eq("user_id", uid) }
                    }.decodeList<FrozenDayRow>()
                }
                val profile = profileQ.await()
                val wallet = walletQ.await()
                val stats = statsQ.await()
                val settings = settingsQ.await()
                val frozen = frozenQ.await()
                db.withTransaction {
                    profile?.let { db.accountDao().upsertProfile(it.toEntity(uid)) }
                    wallet?.let { db.accountDao().upsertWallet(it.toEntity(uid)) }
                    stats?.let { db.statsDao().upsert(it.toEntity(uid)) }
                    db.statsDao().replaceFrozenDays(uid, frozen.map { it.day })
                    settings?.let {
                        db.accountDao().upsertSettings(
                            SettingsEntity(uid, it.notifPromptSeen, SettingsEntity.clampCap(it.dailyEntryCap)),
                        )
                    }
                }
            }
        }
    }

    private suspend fun refreshGarden(uid: String) {
        gardenLock.withLock {
            val garden = fetchOwnRow<Garden>("gardens", uid)
            val plants = if (garden == null) emptyList() else {
                client.postgrest.from("garden_plants").select {
                    filter { eq("garden_id", garden.id) }
                }.decodeList<GardenPlantRow>()
            }
            db.gardenDao().replace(uid, garden?.toEntity(uid), plants.map { it.toEntity(garden!!.id) })
        }
    }

    // items is a global catalog (same rows for everyone), so no uid filter.
    private suspend fun refreshCatalog(uid: String) {
        catalogLock.withLock {
            coroutineScope {
                val items = async { client.postgrest.from("items").select().decodeList<Item>() }
                val owned = async {
                    client.postgrest.from("user_inventory").select {
                        filter { eq("user_id", uid) }
                    }.decodeList<InventoryRow>().map { it.itemId }
                }
                val catalog = items.await().map { it.toEntity() }
                val ownedIds = owned.await()
                db.withTransaction {
                    db.itemDao().replaceCatalog(catalog)
                    db.itemDao().replaceInventory(uid, ownedIds)
                }
            }
        }
    }

    /**
     * Bring the local journal up to date with a delta sync on `updated_at`.
     *
     * Every insert, edit and soft-delete sets `updated_at` (a BEFORE UPDATE trigger), so
     * reading rows past the stored cursor, deleted ones included, yields exactly what
     * changed. The first sync on a device reads the whole history this way, in batches.
     * The cursor is `(updated_at, id)`, not the timestamp alone, so a batch boundary can
     * fall inside a run of rows that share one `updated_at` (a bulk update) without
     * skipping or repeating any of them.
     *
     * Each batch and the cursor that follows it commit together, so an interrupted sync
     * resumes where it stopped.
     *
     * Known gap: `updated_at` is the writing transaction's start time, so a write that
     * commits after a later-stamped one was already read would be missed until it changes
     * again. It needs two devices writing to one account within the same instant.
     */
    private suspend fun syncEntries(uid: String) {
        entriesLock.withLock {
            val key = "entries:$uid"
            var cursor = db.syncCursorDao().get(key)
            while (true) {
                val after = cursor
                val batch = client.postgrest.from("gratitude_entries").select {
                    filter {
                        eq("user_id", uid)
                        if (after != null) {
                            or {
                                gt("updated_at", after.updatedAt)
                                and {
                                    eq("updated_at", after.updatedAt)
                                    gt("id", after.lastId)
                                }
                            }
                        }
                    }
                    order("updated_at", Order.ASCENDING)
                    order("id", Order.ASCENDING)
                    limit(SYNC_BATCH.toLong())
                }.decodeList<GratitudeEntry>()
                if (batch.isEmpty()) break

                val last = batch.last()
                val next = SyncCursorEntity(key, updatedAt = checkNotNull(last.updatedAt), lastId = last.id)
                db.withTransaction {
                    // An entry with a change still queued keeps its local version: the
                    // server's is older, and the outbox writes the answer back when it
                    // delivers. So does one the server refused, until the user retries or
                    // discards it; otherwise, say, an edit refused because the entry was
                    // deleted on another device would vanish, text and all, on the next
                    // refresh. Checked inside the transaction, so a local write can't slip
                    // in between the check and the upsert.
                    val keepLocal = db.outboxDao().pendingEntryIds().toSet() + db.entryDao().refusedIds(uid)
                    db.entryDao().upsertAll(batch.filter { it.id !in keepLocal }.map { it.toEntity(uid) })
                    db.syncCursorDao().put(next)
                }
                cursor = next
                if (batch.size < SYNC_BATCH) break
            }
        }
    }

    /**
     * After a write succeeds, pull the tables it changed. Failures are swallowed: the
     * write itself went through, and the next refresh catches up.
     */
    private suspend fun afterWrite(vararg refreshes: suspend (String) -> Unit) {
        val uid = currentUid() ?: return
        if (!hasToken()) return
        try {
            scope.async { coroutineScope { refreshes.forEach { r -> launch { r(uid) } } } }.await()
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive() // still let the caller's own cancellation through
        }
    }

    // ── Gratitude entries (local first, then the outbox) ─────────────
    // A journal write lands in Room at once and queues an op; drainOutbox() delivers the
    // queue in order, now if it can and otherwise from WorkManager once there's a network
    // (docs/offline-first-plan.md, phase 2). Nothing typed is lost to a failed request.

    /**
     * Write a gratitude entry. It is in the journal immediately, and delivered straight
     * away when the server can be reached within [wait] (a receiver has less time than a
     * screen). Past it the entry is delivered in the background.
     *
     * The coin reward is decided **server-side**: it varies with the streak and how
     * substantive the entry is, so nothing here names an amount. The anon key ships in the
     * APK, so anything the client names as a reward is really just a suggestion an
     * attacker can rewrite.
     *
     * The day's cap is checked here too, against the mirrored `daily_entry_cap`, so an
     * entry past it is refused now rather than written offline and refused at sync.
     */
    suspend fun submitEntry(text: String, voice: Boolean, wait: Duration = SUBMIT_WAIT): SubmitResult {
        val uid = currentUid() ?: throw IllegalStateException("Not signed in.")
        val id = UUID.randomUUID().toString()
        val now = Instant.now()
        val zone = ZoneId.systemDefault()
        val day = entryDay(now, zone).toString()
        val inputMethod = if (voice) "voice_to_text" else "text"
        val writtenAt = now.toString()
        localLock.withLock {
            db.withTransaction {
                val cap = SettingsEntity.clampCap(db.accountDao().getSettings(uid)?.dailyEntryCap)
                // Same wording as the server's error, so the screens map it the same way.
                if (db.entryDao().countOn(uid, day) >= cap) throw IllegalStateException("daily entry cap ($cap) reached")
                db.entryDao().upsert(
                    EntryEntity(
                        id = id,
                        userId = uid,
                        entryText = text,
                        inputMethod = inputMethod,
                        coinsAwarded = null,
                        xpAwarded = null,
                        entryDate = day,
                        createdAt = writtenAt,
                        createdAtMicros = epochMicros(writtenAt),
                        deletedAt = null,
                        syncState = SyncState.PENDING,
                    ),
                )
                db.outboxDao().insert(
                    OutboxOp(
                        userId = uid,
                        type = OutboxType.SUBMIT,
                        entryId = id,
                        text = text,
                        inputMethod = inputMethod,
                        timeZone = zone.id,
                        writtenAt = writtenAt,
                    ),
                )
            }
        }
        outboxScheduler.schedule()
        // Delivery runs in [scope], so running out of patience here only stops the waiting.
        withTimeoutOrNull(wait) { runCatching { drainOutbox() } }
        currentCoroutineContext().ensureActive()
        val entry = db.entryDao().get(id)
        return when (entry?.syncState) {
            SyncState.SYNCED -> SubmitResult.Planted(entry.coinsAwarded, entry.xpAwarded)
            SyncState.FAILED -> SubmitResult.Refused(entry.syncError.orEmpty())
            else -> SubmitResult.Saved
        }
    }

    /**
     * Change an entry's text. A change still waiting in the queue is rewritten rather than
     * followed by another, so the server sees one request with the final text. Editing an
     * entry the server refused sends it again with the new text ([retryEntry]).
     */
    suspend fun editEntry(id: String, newText: String) {
        val uid = currentUid() ?: return
        val refused = localLock.withLock {
            db.withTransaction {
                if (db.entryDao().get(id)?.syncState == SyncState.FAILED) {
                    db.entryDao().setText(id, newText, SyncState.FAILED)
                    return@withTransaction true
                }
                val waiting = db.outboxDao().opsFor(id).lastOrNull()?.takeIf { it.seq != inFlight }
                if (waiting != null && waiting.type != OutboxType.DELETE) {
                    db.outboxDao().setText(waiting.seq, newText)
                } else {
                    db.outboxDao().insert(OutboxOp(userId = uid, type = OutboxType.EDIT, entryId = id, text = newText))
                }
                db.entryDao().setText(id, newText, SyncState.PENDING)
                false
            }
        }
        if (refused) retryEntry(id) else deliverSoon()
    }

    /**
     * Delete an entry. One the server hasn't received yet is dropped with its queue and
     * never sent at all; otherwise it is hidden now and the delete is queued.
     */
    suspend fun deleteEntry(id: String) {
        val uid = currentUid() ?: return
        localLock.withLock {
            db.withTransaction {
                val waiting = db.outboxDao().opsFor(id).filter { it.seq != inFlight }
                if (waiting.any { it.type == OutboxType.SUBMIT }) {
                    db.outboxDao().deleteFor(id)
                    db.entryDao().hardDelete(id)
                } else {
                    // Queued edits of it are moot now.
                    waiting.filter { it.type == OutboxType.EDIT }.forEach { db.outboxDao().delete(it.seq) }
                    db.outboxDao().insert(OutboxOp(userId = uid, type = OutboxType.DELETE, entryId = id))
                    db.entryDao().markDeleted(id, Instant.now().toString(), SyncState.PENDING)
                }
            }
        }
        deliverSoon()
    }

    /**
     * Wait, up to [timeout], until the auth library has loaded the stored session and tried
     * its token. Background work in a fresh process calls this before delivering.
     */
    suspend fun awaitSessionSettled(timeout: Duration) {
        withTimeoutOrNull(timeout) { client.auth.sessionStatus.first { it !is SessionStatus.Initializing } }
    }

    /**
     * [awaitSessionSettled], then whose session it is. For a receiver in a fresh process:
     * without the wait an entry is still written (the user is known almost at once) but can't
     * be delivered, because the token isn't ready. Offline with an expired token the status
     * never settles, and the stored session answers after [timeout].
     */
    suspend fun awaitReady(timeout: Duration): AppSession {
        awaitSessionSettled(timeout)
        return withTimeoutOrNull(1.seconds) { session.first { it !is AppSession.Loading } }
            ?: AppSession.Loading
    }

    /**
     * Whether anything was written today; null when nobody is signed in. Counts deleted
     * entries, like the cap: the thought was still written. An entry from another device
     * shows up through the streak's last day once the stats have synced, before the entry has.
     */
    suspend fun wroteToday(): Boolean? {
        val uid = currentUid() ?: return null
        val today = entryDay(Instant.now(), ZoneId.systemDefault()).toString()
        if (db.entryDao().countOn(uid, today) > 0) return true
        val lastEntryDate = db.statsDao().observe(uid).first()?.lastEntryDate
        return lastEntryDate != null && lastEntryDate >= today
    }

    /**
     * Send a refused entry again, with its text as it is now. It goes as a submit followed by
     * an edit: if the server never received it, the submit creates it (under its original id
     * and write time); if it did, the submit is recognised and changes nothing, and the edit
     * applies the text. Either way no second reward, and no need to know which.
     *
     * If the day's cap refused it, it will be refused again until its day has passed the
     * 36-hour window, after which the server dates it now.
     */
    suspend fun retryEntry(id: String) {
        val uid = currentUid() ?: return
        localLock.withLock {
            db.withTransaction {
                val entry = db.entryDao().get(id)?.takeIf { it.syncState == SyncState.FAILED } ?: return@withTransaction
                db.outboxDao().insert(
                    OutboxOp(
                        userId = uid,
                        type = OutboxType.SUBMIT,
                        entryId = id,
                        text = entry.entryText,
                        inputMethod = entry.inputMethod,
                        timeZone = ZoneId.systemDefault().id,
                        writtenAt = Instant.from(OffsetDateTime.parse(entry.createdAt)).toString(),
                    ),
                )
                db.outboxDao().insert(OutboxOp(userId = uid, type = OutboxType.EDIT, entryId = id, text = entry.entryText))
                db.entryDao().setSyncState(id, SyncState.PENDING)
            }
        }
        deliverSoon()
    }

    /**
     * Give up on a refused change: put back the server's copy of the entry, or remove it if
     * the server never had it. Only the server can say which, so this needs a connection
     * and a token; without them it throws rather than guess (a read without a token comes
     * back empty, which would look like "never had it").
     */
    suspend fun discardEntry(id: String) {
        val uid = currentUid() ?: return
        if (!hasToken()) throw IOException("Can't reach the server to discard this change.")
        val serverCopy = client.postgrest.from("gratitude_entries").select {
            filter {
                eq("id", id)
                eq("user_id", uid)
            }
        }.decodeList<GratitudeEntry>().firstOrNull()
        localLock.withLock {
            db.withTransaction {
                // A retry may have been tapped meanwhile; then there's nothing to discard.
                if (db.entryDao().get(id)?.syncState != SyncState.FAILED) return@withTransaction
                if (serverCopy == null) db.entryDao().hardDelete(id) else db.entryDao().upsert(serverCopy.toEntity(uid))
            }
        }
    }

    /**
     * Keep the words of a refused entry as a brand-new one, for when the original can't be
     * saved (it was deleted on another device). The original is discarded first, so this
     * needs a connection, and the new entry goes through [submitEntry] like any other,
     * daily cap and reward included.
     */
    suspend fun saveAsNewEntry(id: String): SubmitResult? {
        val entry = db.entryDao().get(id)?.takeIf { it.syncState == SyncState.FAILED } ?: return null
        discardEntry(id)
        return submitEntry(entry.entryText, voice = entry.inputMethod == "voice_to_text")
    }

    /** How many entries have changes the server hasn't confirmed; sign-out warns about them. */
    fun observeUnsyncedCount(): Flow<Int> =
        ofUser(0) { uid -> db.outboxDao().observePendingEntries(uid) }

    /** Try now, and leave WorkManager to retry if now doesn't work out. */
    private fun deliverSoon() {
        outboxScheduler.schedule()
        scope.launch {
            try {
                drainOutbox()
            } catch (e: Exception) {
                currentCoroutineContext().ensureActive()
                Log.w(LogTags.APP_LOGIC, "Journal delivery failed", e)
            }
        }
    }

    /**
     * Send the queued journal changes, oldest first, until the queue is empty or the server
     * can't be reached. Returns false when something is left for a later retry.
     *
     * Every op is safe to repeat (see [OutboxOp]), so a request whose answer is lost just
     * stays queued and goes again. An op the server refuses outright, such as text it
     * rejects, is dropped along with the entry's later ops, and the entry is marked
     * [SyncState.FAILED] with its text kept; the ops behind it go on.
     *
     * Runs in [scope] whoever asks, so sign-out cancels it before wiping the tables and it
     * can't write the previous user's entries back.
     */
    suspend fun drainOutbox(): Boolean = scope.async { outboxLock.withLock { drainLocked() } }.await()

    private suspend fun drainLocked(): Boolean {
        val uid = currentUid() ?: return true // signed out: the queue went with the data
        if (!hasToken()) return db.outboxDao().size() == 0
        var delivered = false
        while (true) {
            val op = localLock.withLock { db.outboxDao().head(uid)?.also { inFlight = it.seq } } ?: break
            try {
                val row = send(op)
                // entriesLock: an entry sync that read the old row mid-flight finishes first,
                // so it can't write that row back over this answer.
                entriesLock.withLock {
                    localLock.withLock { db.withTransaction { recordDelivered(op, uid, row) } }
                }
                delivered = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isTransient(e)) {
                    db.outboxDao().recordAttempt(op.seq, e.javaClass.simpleName)
                    return false
                }
                val reason = (e as? RestException)?.error ?: e.message.orEmpty()
                Log.w(LogTags.APP_LOGIC, "Server refused a queued ${op.type}: $reason")
                localLock.withLock {
                    db.withTransaction {
                        db.outboxDao().deleteFor(op.entryId)
                        db.entryDao().setSyncState(op.entryId, SyncState.FAILED, reason)
                    }
                }
            } finally {
                inFlight = null
            }
        }
        if (delivered) afterWrite(::syncEntries, ::refreshAccount)
        return true
    }

    /** The server's copy of the entry, or null for a delete or a reply that didn't decode. */
    private suspend fun send(op: OutboxOp): GratitudeEntry? {
        // rpc() takes a JsonObject of the function's named args.
        val result = when (op.type) {
            OutboxType.SUBMIT -> client.postgrest.rpc(
                "submit_gratitude_entry",
                buildJsonObject {
                    put("p_id", op.entryId)
                    put("p_entry_text", op.text)
                    put("p_input_method", op.inputMethod)
                    // The day is the entry's local date where and when it was written.
                    put("p_time_zone", op.timeZone)
                    put("p_written_at", op.writtenAt)
                },
            )
            OutboxType.EDIT -> client.postgrest.rpc(
                "edit_gratitude_entry",
                buildJsonObject {
                    put("p_entry_id", op.entryId)
                    put("p_new_text", op.text)
                },
            )
            OutboxType.DELETE -> {
                client.postgrest.rpc("delete_gratitude_entry", buildJsonObject { put("p_entry_id", op.entryId) })
                return null
            }
        }
        return runCatching { result.decodeAs<GratitudeEntry>() }.getOrNull()
    }

    private suspend fun recordDelivered(op: OutboxOp, uid: String, row: GratitudeEntry?) {
        db.outboxDao().delete(op.seq)
        val moreQueued = db.outboxDao().opsFor(op.entryId).isNotEmpty()
        when {
            // Later changes are still on their way: take what only the server knows and
            // keep the local text.
            moreQueued -> if (row != null) {
                db.entryDao().setServerFields(row.id, row.coinsAwarded, row.xpAwarded, row.entryDate, row.createdAt, epochMicros(row.createdAt))
            }
            row != null -> db.entryDao().upsert(row.toEntity(uid))
            // A delete, or an answer we couldn't read: the next entry sync brings the
            // server's copy, which nothing now holds back.
            else -> db.entryDao().setSyncState(op.entryId, SyncState.SYNCED)
        }
    }

    // ── Notification prompt flag (Supabase-backed, per-user) ─────────
    // The reminder *preferences* (on/off + time) are stored locally on the
    // device; only this one-time "have we already asked?" flag lives in
    // user_settings so the prompt is shown exactly once per account, not per
    // install.
    //
    // The write goes through an RPC, not a plain UPDATE: user_settings has no
    // owner UPDATE policy at all (20260916210000 dropped it), because
    // daily_entry_cap lives in this table and submit_gratitude_entry reads it as
    // the daily earnings ceiling -- a client-writable cap is a coin printer.
    // mark_notif_prompt_seen() is a no-argument one-way latch, so there is
    // nothing here for a caller to name.

    suspend fun markNotifPromptSeen() {
        // The RPC derives the user from auth.uid() and raises if there is none;
        // the local guard just avoids a pointless round trip when signed out.
        val uid = currentUid() ?: return
        client.postgrest.rpc("mark_notif_prompt_seen")
        db.accountDao().markNotifPromptSeen(uid)
    }

    // ── Shop / inventory / garden interactions ───────────────────────
    suspend fun purchaseItem(itemId: String) {
        client.postgrest.rpc("purchase_item", buildJsonObject { put("p_item_id", itemId) })
        afterWrite(::refreshAccount, ::refreshCatalog)
    }

    /** Equip a backdrop the user owns. The server refuses one they don't. */
    suspend fun setActiveBackdrop(itemId: String) {
        client.postgrest.rpc("set_active_backdrop", buildJsonObject { put("p_item_id", itemId) })
        afterWrite(::refreshGarden)
    }

    /**
     * Buy one streak freeze. Not an item: freezes stack, and purchase_item returns what's
     * already owned. The server charges [STREAK_FREEZE_PRICE] and refuses past
     * [MAX_STREAK_FREEZES]; both balances come back through the refresh.
     */
    suspend fun buyStreakFreeze() {
        client.postgrest.rpc("buy_streak_freeze")
        afterWrite(::refreshAccount)
    }

    suspend fun placePlant(itemId: String, gridX: Int, gridY: Int) {
        client.postgrest.rpc(
            "place_plant",
            buildJsonObject {
                put("p_item_id", itemId)
                put("p_grid_x", gridX)
                put("p_grid_y", gridY)
            },
        )
        afterWrite(::refreshGarden)
    }

    /** Water a plant. The cost is decided and charged server-side. */
    suspend fun waterPlant(plantId: String) {
        client.postgrest.rpc(
            "water_plant",
            buildJsonObject { put("p_plant_id", plantId) },
        )
        afterWrite(::refreshGarden, ::refreshAccount)
    }

    /** Move an existing plant to a new cell (server rejects occupied/out-of-bounds). */
    suspend fun movePlant(plantId: String, gridX: Int, gridY: Int) {
        client.postgrest.rpc(
            "move_plant",
            buildJsonObject {
                put("p_plant_id", plantId)
                put("p_grid_x", gridX)
                put("p_grid_y", gridY)
            },
        )
        afterWrite(::refreshGarden)
    }

    /** Dig up (delete) one of the user's own plants. RLS scopes the delete to the owner. */
    suspend fun digUpPlant(plantId: String) {
        client.postgrest.from("garden_plants").delete {
            filter { eq("id", plantId) }
        }
        afterWrite(::refreshGarden)
    }

    /**
     * Plant an owned seed into the first free cell. Returns false if the garden is full.
     * Free cells come from the local copy; the server still rejects an occupied one.
     */
    suspend fun plantInFirstEmptyCell(itemId: String): Boolean {
        val garden = observeGarden().first() ?: return false
        val occupied = observePlants().first().map { it.gridX to it.gridY }.toSet()
        for (y in 0 until garden.gridRows) {
            for (x in 0 until garden.gridCols) {
                if ((x to y) !in occupied) {
                    placePlant(itemId, x, y)
                    return true
                }
            }
        }
        return false
    }

    private companion object {
        /** How long a full refresh counts as current for callers that don't force one. */
        val FRESH_FOR = 30.seconds

        /** Rows per request in the entry sync. PostgREST on Supabase caps a response at 1000. */
        const val SYNC_BATCH = 500

        /**
         * How long a submit waits to be delivered before reporting it saved instead. It is
         * delivered all the same; this only bounds the "Planting…" spinner.
         */
        /** What the home-screen widget reads: the streak, the day's count and cap, the garden. */
        private val WIDGET_TABLES = arrayOf(
            "user_stats", "gratitude_entries", "user_settings", "garden", "garden_plants", "items",
        )

        val SUBMIT_WAIT = 8.seconds

        /**
         * Worth retrying: an expired or not-yet-refreshed token, a timeout, rate limiting,
         * the server's own trouble. Any other refusal will be refused again.
         */
        fun isTransient(e: Throwable): Boolean = when (e) {
            is RestException -> e.statusCode in setOf(401, 403, 408, 429) || e.statusCode >= 500
            else -> true // no connection, timeouts, and anything unrecognised
        }
    }
}
