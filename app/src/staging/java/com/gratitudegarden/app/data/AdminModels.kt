package com.gratitudegarden.app.data

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Generic admin-CRUD metadata.
 *
 * Rather than hand-write a form for every one of the nine Supabase tables, the
 * admin dashboard is data-driven: each table is described by an [AdminTableSpec]
 * and rows flow through as plain [JsonObject]s. The UI renders a typed editor per
 * field; writes are turned back into a typed [JsonObject] by [buildPayload].
 */

enum class AdminFieldType { TEXT, INT, BOOL, ENUM, DATE, TIMESTAMP, ITEM_REF }

data class AdminField(
    val column: String,
    val label: String,
    val type: AdminFieldType,
    val nullable: Boolean = false,
    val enumValues: List<String> = emptyList(),
)

/** How a table's rows are located/owned relative to the selected user. */
enum class AdminScope {
    BY_ID,        // profiles: one row whose `id` == selected user
    BY_USER_ID,   // user-owned tables filtered/created on `user_id`
    BY_GARDEN,    // garden_plants: filtered/created on the user's garden `garden_id`
    GLOBAL,       // items: not user-scoped
}

data class AdminTableSpec(
    val table: String,
    val title: String,
    val scope: AdminScope,
    val pk: List<String>,
    /** True when the primary key is DB-generated and must be omitted on insert. */
    val generatedId: Boolean,
    /** True when at most one row exists per user (profile/settings/stats/wallet/garden). */
    val single: Boolean,
    /** Column to label a row by in the list UI. */
    val displayColumn: String,
    val fields: List<AdminField>,
) {
    /**
     * Build a Postgrest write body from the editor's raw string values.
     *
     * - blank + nullable  -> explicit JSON null (clears the column)
     * - blank + required  -> omitted (keeps existing value on update / uses DB default on insert)
     * - [extra] (owner columns like user_id / garden_id) is merged in last.
     */
    fun buildPayload(values: Map<String, String?>, extra: Map<String, JsonElement> = emptyMap()): JsonObject {
        val map = LinkedHashMap<String, JsonElement>()
        for (f in fields) {
            val raw = values[f.column]?.trim()
            if (raw.isNullOrEmpty()) {
                if (f.nullable) map[f.column] = JsonNull
                continue
            }
            map[f.column] = when (f.type) {
                AdminFieldType.INT -> JsonPrimitive(raw.toIntOrNull() ?: 0)
                AdminFieldType.BOOL -> JsonPrimitive(raw.toBooleanStrictOrNull() ?: false)
                else -> JsonPrimitive(raw)
            }
        }
        extra.forEach { (k, v) -> map[k] = v }
        return JsonObject(map)
    }
}

/** Read one column out of a row as a display/edit string (JSON null -> null). */
fun JsonObject.cell(column: String): String? {
    val e = this[column] ?: return null
    if (e is JsonNull) return null
    return (e as? JsonPrimitive)?.content ?: e.toString()
}

// ── Known enum value sets (from the live Supabase schema) ────────────────────
private val ITEM_CATEGORY = listOf("seed", "decor", "backdrop")
private val ITEM_RARITY = listOf("common", "uncommon", "rare", "epic", "legendary")
private val INPUT_METHOD = listOf("text", "voice_to_text")
private val GROWTH_STAGE = listOf("seedling", "sapling", "mature")
private val PLANT_HEALTH = listOf("healthy", "thirsty", "wilting")
private val THEME = listOf("light", "dark", "system")

/** The eight user-scoped tables, in dashboard display order. */
val ADMIN_USER_TABLES: List<AdminTableSpec> = listOf(
    AdminTableSpec(
        table = "profiles", title = "Profile", scope = AdminScope.BY_ID,
        pk = listOf("id"), generatedId = false, single = true, displayColumn = "display_name",
        fields = listOf(
            AdminField("display_name", "Display name", AdminFieldType.TEXT),
            AdminField("avatar_key", "Avatar key", AdminFieldType.TEXT, nullable = true),
            AdminField("level", "Level", AdminFieldType.INT),
            AdminField("xp", "XP", AdminFieldType.INT),
            AdminField("is_admin", "Admin", AdminFieldType.BOOL),
        ),
    ),
    AdminTableSpec(
        table = "user_settings", title = "Settings", scope = AdminScope.BY_USER_ID,
        pk = listOf("user_id"), generatedId = false, single = true, displayColumn = "theme",
        fields = listOf(
            AdminField("reminder_enabled", "Reminder enabled", AdminFieldType.BOOL),
            AdminField("reminder_time", "Reminder time (HH:MM)", AdminFieldType.TEXT),
            AdminField("time_zone", "Time zone", AdminFieldType.TEXT),
            AdminField("sounds_haptics_enabled", "Sounds / haptics", AdminFieldType.BOOL),
            AdminField("theme", "Theme", AdminFieldType.ENUM, enumValues = THEME),
            AdminField("daily_entry_cap", "Daily entry cap", AdminFieldType.INT),
        ),
    ),
    AdminTableSpec(
        table = "user_stats", title = "Stats", scope = AdminScope.BY_USER_ID,
        pk = listOf("user_id"), generatedId = false, single = true, displayColumn = "total_entries",
        fields = listOf(
            AdminField("total_entries", "Total entries", AdminFieldType.INT),
            AdminField("total_coins_earned", "Total coins earned", AdminFieldType.INT),
            AdminField("plants_grown", "Plants grown", AdminFieldType.INT),
            AdminField("current_streak", "Current streak", AdminFieldType.INT),
            AdminField("longest_streak", "Longest streak", AdminFieldType.INT),
            AdminField("last_entry_date", "Last entry date (YYYY-MM-DD)", AdminFieldType.DATE, nullable = true),
        ),
    ),
    AdminTableSpec(
        table = "coin_wallets", title = "Coin wallet", scope = AdminScope.BY_USER_ID,
        pk = listOf("user_id"), generatedId = false, single = true, displayColumn = "balance",
        fields = listOf(
            AdminField("balance", "Balance", AdminFieldType.INT),
        ),
    ),
    AdminTableSpec(
        table = "gratitude_entries", title = "Gratitude entries", scope = AdminScope.BY_USER_ID,
        pk = listOf("id"), generatedId = true, single = false, displayColumn = "entry_text",
        fields = listOf(
            AdminField("entry_text", "Entry text", AdminFieldType.TEXT),
            AdminField("input_method", "Input method", AdminFieldType.ENUM, enumValues = INPUT_METHOD),
            AdminField("mood", "Mood (1–5)", AdminFieldType.INT, nullable = true),
            AdminField("coins_awarded", "Coins awarded", AdminFieldType.INT),
            AdminField("entry_date", "Entry date (YYYY-MM-DD)", AdminFieldType.DATE),
            AdminField("deleted_at", "Deleted at (soft delete)", AdminFieldType.TIMESTAMP, nullable = true),
        ),
    ),
    AdminTableSpec(
        table = "gardens", title = "Garden", scope = AdminScope.BY_USER_ID,
        pk = listOf("id"), generatedId = true, single = true, displayColumn = "name",
        fields = listOf(
            AdminField("name", "Name", AdminFieldType.TEXT),
            AdminField("grid_rows", "Grid rows", AdminFieldType.INT),
            AdminField("grid_cols", "Grid cols", AdminFieldType.INT),
            AdminField("active_backdrop_item_id", "Active backdrop", AdminFieldType.ITEM_REF, nullable = true),
            AdminField("last_viewed_at", "Last viewed at", AdminFieldType.TIMESTAMP, nullable = true),
        ),
    ),
    AdminTableSpec(
        table = "user_inventory", title = "Inventory", scope = AdminScope.BY_USER_ID,
        pk = listOf("user_id", "item_id"), generatedId = false, single = false, displayColumn = "item_id",
        fields = listOf(
            AdminField("item_id", "Item", AdminFieldType.ITEM_REF),
            AdminField("acquired_at", "Acquired at", AdminFieldType.TIMESTAMP),
        ),
    ),
    AdminTableSpec(
        table = "garden_plants", title = "Garden plants", scope = AdminScope.BY_GARDEN,
        pk = listOf("id"), generatedId = true, single = false, displayColumn = "item_id",
        fields = listOf(
            AdminField("item_id", "Item", AdminFieldType.ITEM_REF),
            AdminField("grid_x", "Grid X", AdminFieldType.INT),
            AdminField("grid_y", "Grid Y", AdminFieldType.INT),
            AdminField("growth_stage", "Growth stage", AdminFieldType.ENUM, enumValues = GROWTH_STAGE),
            AdminField("health", "Health", AdminFieldType.ENUM, enumValues = PLANT_HEALTH),
            AdminField("planted_at", "Planted at", AdminFieldType.TIMESTAMP),
            AdminField("last_watered_at", "Last watered at", AdminFieldType.TIMESTAMP, nullable = true),
        ),
    ),
)

/** The global catalog table (its own dashboard tab). */
val ADMIN_ITEMS_TABLE = AdminTableSpec(
    table = "items", title = "Items catalog", scope = AdminScope.GLOBAL,
    pk = listOf("id"), generatedId = true, single = false, displayColumn = "name",
    fields = listOf(
        AdminField("slug", "Slug", AdminFieldType.TEXT),
        AdminField("name", "Name", AdminFieldType.TEXT),
        AdminField("category", "Category", AdminFieldType.ENUM, enumValues = ITEM_CATEGORY),
        AdminField("rarity", "Rarity", AdminFieldType.ENUM, enumValues = ITEM_RARITY),
        AdminField("description", "Description", AdminFieldType.TEXT, nullable = true),
        AdminField("price_coins", "Price (coins)", AdminFieldType.INT),
        AdminField("level_required", "Level required", AdminFieldType.INT),
        AdminField("asset_key", "Asset key", AdminFieldType.TEXT, nullable = true),
        AdminField("is_purchasable", "Purchasable", AdminFieldType.BOOL),
        AdminField("is_starter", "Starter", AdminFieldType.BOOL),
        AdminField("available_from", "Available from", AdminFieldType.TIMESTAMP, nullable = true),
        AdminField("available_until", "Available until", AdminFieldType.TIMESTAMP, nullable = true),
    ),
)
