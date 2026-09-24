package com.gratitudegarden.app.data

import androidx.room.withTransaction
import com.gratitudegarden.app.data.local.GardenDatabase
import com.gratitudegarden.app.data.local.SettingsEntity
import com.gratitudegarden.app.data.local.SyncCursorEntity
import com.gratitudegarden.app.data.local.toEntity
import com.gratitudegarden.app.data.local.toRow
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
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
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate
import java.time.ZoneId
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
    @SerialName("coins_awarded") val coinsAwarded: Int,
    @SerialName("entry_date") val entryDate: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null,
    /** Read only by the entry sync, as its cursor; not stored locally. */
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class Garden(
    val id: String,
    val name: String,
    @SerialName("grid_rows") val gridRows: Int = 6,
    @SerialName("grid_cols") val gridCols: Int = 5,
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
)

/**
 * The streak to actually show the user right now.
 *
 * The stored [currentStreak] is only rewritten when an entry is submitted, so
 * between submits it goes stale: a run that ended days ago keeps reporting its
 * old value until the next entry resets it. A run is only still alive while the
 * last entry was today or yesterday (logging today after logging yesterday
 * continues it) — once a full calendar day is missed the run is broken and the
 * effective streak is 0, even though the stored column hasn't been rewritten yet.
 *
 * Dates are compared in UTC to match how `entry_date` / `last_entry_date` are
 * recorded server-side (`now() at time zone 'UTC'`).
 */
/**
 * Pure core of [effectiveStreak]: the still-alive streak *as of [today]*.
 *
 * Optimization: the clock is injected rather than read inside the function, so this
 * is deterministic and unit-testable in isolation (no dependency on the machine's
 * current date). [effectiveStreak] is the thin convenience wrapper that supplies the
 * real local date. Behaviour is unchanged — the property below reads `today` exactly
 * as before. See docs/unit-test-optimizations.md.
 */
fun UserStatsRow.effectiveStreakOn(today: LocalDate): Int {
    val last = lastEntryDate
        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?: return 0
    // One day ahead is legitimate: the server never dates an entry before the previous one,
    // so after a zone change (or the move off UTC days) the last entry can sit on tomorrow.
    return if (last >= today.minusDays(1) && last <= today.plusDays(1)) currentStreak else 0
}

/**
 * The day the server will date the next entry: the local date, but never earlier than the
 * previous entry. Mirrors `greatest((now() at time zone tz)::date, last_entry_date)` in
 * `submit_gratitude_entry`, which keeps a zone change from reopening a finished day.
 */
fun entryDay(localToday: LocalDate, lastEntryDate: String?): LocalDate {
    val last = lastEntryDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return localToday
    return maxOf(localToday, last)
}

val UserStatsRow.effectiveStreak: Int
    get() = effectiveStreakOn(LocalDate.now())

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
private data class NotifPromptRow(
    @SerialName("notif_prompt_seen") val notifPromptSeen: Boolean = false,
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
) {

    val sessionStatus: StateFlow<SessionStatus> get() = client.auth.sessionStatus

    // Refreshes run here rather than in the caller's scope: one started by a screen that
    // then goes away still finishes, and sign-out can cancel all of them before it wipes
    // the tables, so none can write the previous user's rows back afterwards.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // One refresh of each group of tables at a time. Two overlapping fetches of the same
    // rows could finish out of order and leave the older copy in Room.
    private val accountLock = Mutex()
    private val gardenLock = Mutex()
    private val catalogLock = Mutex()
    private val entriesLock = Mutex()

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
        scope.coroutineContext.job.children.forEach { it.cancelAndJoin() }
        lastFullRefreshAt = null
        withContext(Dispatchers.IO) { db.clearAllTables() }
    }

    // ── Reads (Room) ─────────────────────────────────────────────────
    // Every read is scoped to the signed-in user, captured when the flow is created. The
    // screens' ViewModels live inside a per-session scope (SessionScope), so a flow never
    // outlives the user it was made for.
    private fun currentUid(): String? = client.auth.currentUserOrNull()?.id

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

    /**
     * How many entries the user has written today, **including soft-deleted ones**.
     *
     * This has to match `submit_gratitude_entry`'s cap check exactly, and that check
     * counts deleted entries too: `delete_gratitude_entry` stamps `deleted_at` without
     * refunding the coins, so a deleted entry has still been paid for. The entry sync
     * keeps deleted rows for exactly this reason.
     *
     * "Today" is [entryDay], the same day `submit_gratitude_entry` will date the next
     * entry, so it moves with the stats' `lastEntryDate`.
     */
    fun observeEntriesTodayCount(): Flow<Int> =
        ofUser(0) { uid ->
            observeStats().flatMapLatest { stats ->
                val today = entryDay(LocalDate.now(), stats?.lastEntryDate).toString()
                db.entryDao().observeCountOn(uid, today)
            }
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
                val settingsQ = async { fetchOwnRow<NotifPromptRow>("user_settings", uid) }
                val profile = profileQ.await()
                val wallet = walletQ.await()
                val stats = statsQ.await()
                val settings = settingsQ.await()
                db.withTransaction {
                    profile?.let { db.accountDao().upsertProfile(it.toEntity(uid)) }
                    wallet?.let { db.accountDao().upsertWallet(it.toEntity(uid)) }
                    stats?.let { db.statsDao().upsert(it.toEntity(uid)) }
                    settings?.let { db.accountDao().upsertSettings(SettingsEntity(uid, it.notifPromptSeen)) }
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
                    db.entryDao().upsertAll(batch.map { it.toEntity(uid) })
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
        try {
            scope.async { coroutineScope { refreshes.forEach { r -> launch { r(uid) } } } }.await()
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive() // still let the caller's own cancellation through
        }
    }

    // ── Gratitude entries (RPCs) ─────────────────────────────────────
    // rpc() takes a JsonObject of the function's named args.
    /**
     * Plant a gratitude entry.
     *
     * The coin reward is decided **server-side** — it varies with the user's
     * streak and how substantive the entry is — so it is never sent from here.
     * The anon key ships in the APK, so anything the client names as a reward is
     * really just a suggestion an attacker can rewrite.
     *
     * Returns the coins actually awarded, or null if the reply couldn't be
     * decoded. The entry saves either way, and the refreshed wallet shows the
     * new balance regardless, so a null only costs the "+N" in the
     * confirmation toast.
     */
    suspend fun submitEntry(text: String, voice: Boolean): Int? {
        val result = client.postgrest.rpc(
            "submit_gratitude_entry",
            buildJsonObject {
                put("p_entry_text", text)
                put("p_input_method", if (voice) "voice_to_text" else "text")
                // The server dates the entry in this zone (local day, not UTC day).
                put("p_time_zone", ZoneId.systemDefault().id)
            },
        )
        afterWrite(::syncEntries, ::refreshAccount)
        return runCatching { result.decodeAs<GratitudeEntry>().coinsAwarded }.getOrNull()
    }

    suspend fun editEntry(id: String, newText: String) {
        client.postgrest.rpc(
            "edit_gratitude_entry",
            buildJsonObject {
                put("p_entry_id", id)
                put("p_new_text", newText)
            },
        )
        afterWrite(::syncEntries)
    }

    suspend fun deleteEntry(id: String) {
        client.postgrest.rpc(
            "delete_gratitude_entry",
            buildJsonObject { put("p_entry_id", id) },
        )
        afterWrite(::syncEntries, ::refreshAccount)
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
        db.accountDao().upsertSettings(SettingsEntity(uid, notifPromptSeen = true))
    }

    // ── Shop / inventory / garden interactions ───────────────────────
    suspend fun purchaseItem(itemId: String) {
        client.postgrest.rpc("purchase_item", buildJsonObject { put("p_item_id", itemId) })
        afterWrite(::refreshAccount, ::refreshCatalog)
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
    }
}
