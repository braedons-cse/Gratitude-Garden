package com.cse5236.gratitudegarden.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

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

/**
 * Thin client over the Supabase backend. Business logic (coins, streaks,
 * provisioning) lives in Postgres RPCs — this just authenticates, calls them,
 * and reads RLS-scoped rows.
 */
class GardenRepository(private val client: SupabaseClient) {

    val sessionStatus: StateFlow<SessionStatus> get() = client.auth.sessionStatus

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
    }

    suspend fun editEntry(id: String, newText: String) {
        client.postgrest.rpc(
            "edit_gratitude_entry",
            buildJsonObject {
                put("p_entry_id", id)
                put("p_new_text", newText)
            },
        )
    }

    suspend fun deleteEntry(id: String) {
        client.postgrest.rpc(
            "delete_gratitude_entry",
            buildJsonObject { put("p_entry_id", id) },
        )
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
    }

    suspend fun waterPlant(plantId: String, cost: Int = 10) {
        client.postgrest.rpc(
            "water_plant",
            buildJsonObject {
                put("p_plant_id", plantId)
                put("p_water_cost", cost)
            },
        )
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
