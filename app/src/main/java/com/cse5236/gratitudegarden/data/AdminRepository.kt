package com.cse5236.gratitudegarden.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.JsonObject

/**
 * Admin-only data access. Every call still goes through the anon key + the
 * signed-in user's JWT — cross-user reads/writes succeed only because the
 * `*_admin_all` RLS policies allow them when `public.is_current_user_admin()`
 * is true. No service-role key ever touches the app.
 *
 * Rows are read and written as raw [JsonObject]s so a single set of generic
 * methods can serve all nine tables (see [AdminTableSpec]).
 */
class AdminRepository(private val client: SupabaseClient) {

    private val pg get() = client.postgrest

    /** Server-side admin check (drives the Me-screen entry point and dashboard gate). */
    suspend fun isCurrentUserAdmin(): Boolean =
        pg.rpc("is_current_user_admin").decodeAs<Boolean>()

    /** All profiles (admin-visible via RLS) — the dashboard's user list. */
    suspend fun users(): List<JsonObject> =
        pg.from("profiles").select().decodeList<JsonObject>()

    /** Read rows from [table], optionally filtered by a single equality. */
    suspend fun rows(
        table: String,
        filterColumn: String? = null,
        filterValue: String? = null,
    ): List<JsonObject> =
        pg.from(table).select {
            if (filterColumn != null && filterValue != null) {
                filter { eq(filterColumn, filterValue) }
            }
        }.decodeList<JsonObject>()

    suspend fun insert(table: String, values: JsonObject) {
        pg.from(table).insert(values)
    }

    suspend fun update(table: String, pk: Map<String, String>, values: JsonObject) {
        pg.from(table).update(values) {
            filter { pk.forEach { (k, v) -> eq(k, v) } }
        }
    }

    suspend fun delete(table: String, pk: Map<String, String>) {
        pg.from(table).delete {
            filter { pk.forEach { (k, v) -> eq(k, v) } }
        }
    }
}
