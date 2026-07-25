package com.cse5236.gratitudegarden.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.LocalDate
import java.time.ZoneOffset

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
 * real UTC date. Behaviour is unchanged — the property below reads `today` exactly
 * as before. See docs/unit-test-optimizations.md.
 */
fun UserStatsRow.effectiveStreakOn(today: LocalDate): Int {
    val last = lastEntryDate
        ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        ?: return 0
    return if (last == today || last == today.minusDays(1)) currentStreak else 0
}

val UserStatsRow.effectiveStreak: Int
    get() = effectiveStreakOn(LocalDate.now(ZoneOffset.UTC))

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
private data class EntryDateRow(@SerialName("entry_date") val entryDate: String)

@Serializable
private data class NotifPromptRow(
    @SerialName("notif_prompt_seen") val notifPromptSeen: Boolean = false,
)

/**
 * Thin client over the Supabase backend. Business logic (coins, streaks,
 * provisioning) lives in Postgres RPCs — this just authenticates, calls them,
 * and reads RLS-scoped rows.
 */
class GardenRepository(private val client: SupabaseClient) {

    val sessionStatus: StateFlow<SessionStatus> get() = client.auth.sessionStatus

    // ── Cross-screen change signal ───────────────────────────────────
    // The home pager keeps every tab's ViewModel alive, so a mutation on one
    // screen would otherwise leave the others showing stale data until the app
    // is recreated. Each mutation emits here; every ViewModel collects it and
    // reloads, so plant/buy/water/entry changes appear everywhere immediately.
    private val _changes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val changes: SharedFlow<Unit> = _changes.asSharedFlow()
    private fun notifyChanged() { _changes.tryEmit(Unit) }

    // ── Auth ─────────────────────────────────────────────────────────
    suspend fun signIn(email: String, password: String) {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun signUp(email: String, password: String, displayName: String) {
        client.auth.signUpWith(Email) {
            this.email = email
            this.password = password
            data = buildJsonObject { put("display_name", displayName) }
        }
    }

    suspend fun signOut() = client.auth.signOut()

    /**
     * Permanently delete the signed-in user's own account.
     *
     * [password] is re-verified by signing in again (a wrong password throws
     * here, before anything is deleted). The `delete_current_user` RPC then
     * removes the row in auth.users server-side; the ON DELETE CASCADE foreign
     * keys wipe every owned row (profile, settings, stats, wallet, entries,
     * garden + plants, inventory). Finally the now-defunct local session is
     * cleared so the app returns to the auth screen.
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
    }

    // ── Gratitude entries (RPCs) ─────────────────────────────────────
    // rpc() takes a JsonObject of the function's named args.
    suspend fun submitEntry(text: String, voice: Boolean) {
        client.postgrest.rpc(
            "submit_gratitude_entry",
            buildJsonObject {
                put("p_entry_text", text)
                put("p_input_method", if (voice) "voice_to_text" else "text")
                put("p_coin_reward", 5)
            },
        )
        notifyChanged()
    }

    suspend fun editEntry(id: String, newText: String) {
        client.postgrest.rpc(
            "edit_gratitude_entry",
            buildJsonObject {
                put("p_entry_id", id)
                put("p_new_text", newText)
            },
        )
        notifyChanged()
    }

    suspend fun deleteEntry(id: String) {
        client.postgrest.rpc(
            "delete_gratitude_entry",
            buildJsonObject { put("p_entry_id", id) },
        )
        notifyChanged()
    }

    // ── Reads ────────────────────────────────────────────────────────
    // These MUST filter by the signed-in user id explicitly — do NOT rely on
    // RLS to return a single row. Admins have additive "read any row" policies
    // (for the admin dashboard), so an unfiltered select returns EVERY user's
    // row and firstOrNull() would grab an arbitrary account (e.g. someone
    // else's profile). Always scope reads to the current uid.
    private fun currentUid(): String? = client.auth.currentUserOrNull()?.id

    suspend fun entries(): List<GratitudeEntry> {
        val uid = currentUid() ?: return emptyList()
        return client.postgrest.from("gratitude_entries").select {
            filter { eq("user_id", uid) }
        }.decodeList<GratitudeEntry>()
            .filter { it.deletedAt == null }
            .sortedByDescending { it.createdAt }
    }

    /** How many journal entries to fetch per page (see [entriesPage]). */
    val entriesPageSize: Int get() = 20

    /**
     * One page of the signed-in user's gratitude entries, newest first.
     *
     * Optimization vs [entries]: the newest-first ordering and the soft-delete filter
     * run server-side, and only [limit] rows are fetched — so the Journal no longer
     * downloads, deserializes, sorts, and retains the user's *entire* history just to
     * fill one screen. Pass [createdBefore] (the `createdAt` of the oldest row you
     * already hold) to fetch the next page. This is keyset pagination: it stays
     * correct even as new entries are inserted between page loads.
     */
    suspend fun entriesPage(
        limit: Int = entriesPageSize,
        createdBefore: String? = null,
    ): List<GratitudeEntry> {
        val uid = currentUid() ?: return emptyList()
        return client.postgrest.from("gratitude_entries").select {
            filter {
                eq("user_id", uid)
                filter("deleted_at", FilterOperator.IS, null)
                if (createdBefore != null) lt("created_at", createdBefore)
            }
            order("created_at", Order.DESCENDING)
            limit(limit.toLong())
        }.decodeList<GratitudeEntry>()
    }

    /**
     * The set of entry dates (`yyyy-MM-dd`) within the last [days] days — exactly what
     * the Journal's week-strip needs, fetched as a narrow, bounded query instead of
     * scanning the whole history. Compared in UTC to match how `entry_date` is stored.
     */
    suspend fun recentEntryDates(days: Int = 7): Set<String> {
        val uid = currentUid() ?: return emptySet()
        val since = LocalDate.now(ZoneOffset.UTC).minusDays((days - 1).toLong()).toString()
        return client.postgrest.from("gratitude_entries")
            .select(Columns.list("entry_date")) {
                filter {
                    eq("user_id", uid)
                    filter("deleted_at", FilterOperator.IS, null)
                    gte("entry_date", since)
                }
            }.decodeList<EntryDateRow>().map { it.entryDate }.toSet()
    }

    suspend fun garden(): Garden? {
        val uid = currentUid() ?: return null
        return client.postgrest.from("gardens").select {
            filter { eq("user_id", uid) }
        }.decodeList<Garden>().firstOrNull()
    }

    suspend fun plants(): List<GardenPlantRow> {
        val gardenId = garden()?.id ?: return emptyList()
        return client.postgrest.from("garden_plants").select {
            filter { eq("garden_id", gardenId) }
        }.decodeList<GardenPlantRow>()
    }

    suspend fun stats(): UserStatsRow? {
        val uid = currentUid() ?: return null
        return client.postgrest.from("user_stats").select {
            filter { eq("user_id", uid) }
        }.decodeList<UserStatsRow>().firstOrNull()
    }

    suspend fun wallet(): WalletRow? {
        val uid = currentUid() ?: return null
        return client.postgrest.from("coin_wallets").select {
            filter { eq("user_id", uid) }
        }.decodeList<WalletRow>().firstOrNull()
    }

    suspend fun profile(): ProfileRow? {
        val uid = currentUid() ?: return null
        return client.postgrest.from("profiles").select {
            filter { eq("id", uid) }
        }.decodeList<ProfileRow>().firstOrNull()
    }

    // ── Notification prompt flag (Supabase-backed, per-user) ─────────
    // The reminder *preferences* (on/off + time) are stored locally on the
    // device; only this one-time "have we already asked?" flag lives in
    // user_settings so the prompt is shown exactly once per account, not per
    // install. handle_new_user() guarantees the row exists, so a plain UPDATE
    // is enough (there is no INSERT policy on user_settings).

    /** True once the enable-reminders prompt has been shown (accepted or declined). */
    suspend fun notifPromptSeen(): Boolean {
        val uid = currentUid() ?: return true // not signed in → never prompt
        return client.postgrest.from("user_settings").select {
            filter { eq("user_id", uid) }
        }.decodeList<NotifPromptRow>().firstOrNull()?.notifPromptSeen ?: false
    }

    suspend fun markNotifPromptSeen() {
        val uid = currentUid() ?: return
        client.postgrest.from("user_settings").update(
            buildJsonObject { put("notif_prompt_seen", true) },
        ) {
            filter { eq("user_id", uid) }
        }
    }

    // ── Shop / inventory / garden interactions ───────────────────────
    // items is a global catalog (same rows for everyone), so no uid filter.
    suspend fun items(category: String): List<Item> =
        client.postgrest.from("items").select().decodeList<Item>()
            .filter { it.category == category }
            .sortedBy { it.priceCoins }

    /** item_id -> slug for everything, so the garden can render plants by type. */
    suspend fun itemSlugs(): Map<String, String> =
        client.postgrest.from("items").select().decodeList<Item>()
            .associate { it.id to it.slug }

    suspend fun inventory(): Set<String> {
        val uid = currentUid() ?: return emptySet()
        return client.postgrest.from("user_inventory").select {
            filter { eq("user_id", uid) }
        }.decodeList<InventoryRow>().map { it.itemId }.toSet()
    }

    suspend fun purchaseItem(itemId: String) {
        client.postgrest.rpc("purchase_item", buildJsonObject { put("p_item_id", itemId) })
        notifyChanged()
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
        notifyChanged()
    }

    suspend fun waterPlant(plantId: String, cost: Int = 10) {
        client.postgrest.rpc(
            "water_plant",
            buildJsonObject {
                put("p_plant_id", plantId)
                put("p_water_cost", cost)
            },
        )
        notifyChanged()
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
        notifyChanged()
    }

    /** Dig up (delete) one of the user's own plants. RLS scopes the delete to the owner. */
    suspend fun digUpPlant(plantId: String) {
        client.postgrest.from("garden_plants").delete {
            filter { eq("id", plantId) }
        }
        notifyChanged()
    }

    /** The seeds the user owns, as catalog items — for the "plant a seed here" picker. */
    suspend fun ownedSeeds(): List<Item> {
        val owned = inventory()
        return items("seed").filter { it.id in owned }
    }

    /** Plant an owned seed into the first free cell. Returns false if the garden is full. */
    suspend fun plantInFirstEmptyCell(itemId: String): Boolean {
        val garden = garden() ?: return false
        val occupied = plants().map { it.gridX to it.gridY }.toSet()
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
}
