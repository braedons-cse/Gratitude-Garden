package com.gratitudegarden.app.ui.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gratitudegarden.app.data.ADMIN_ITEMS_TABLE
import com.gratitudegarden.app.data.ADMIN_USER_TABLES
import com.gratitudegarden.app.data.AdminRepository
import com.gratitudegarden.app.data.AdminScope
import com.gratitudegarden.app.data.AdminTableSpec
import com.gratitudegarden.app.data.cell
import com.gratitudegarden.app.ui.gardenApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

data class AdminUser(val id: String, val displayName: String, val isAdmin: Boolean)

enum class AdminMode { USERS, CATALOG }

data class AdminUiState(
    val loading: Boolean = true,
    val error: String? = null,
    val message: String? = null,
    val busy: Boolean = false,
    val mode: AdminMode = AdminMode.USERS,
    val users: List<AdminUser> = emptyList(),
    val selectedUserId: String? = null,
    /** table name -> rows for the selected user. */
    val userTables: Map<String, List<JsonObject>> = emptyMap(),
    val items: List<JsonObject> = emptyList(),
    /** item id -> label, used to drive ITEM_REF pickers. */
    val itemOptions: List<Pair<String, String>> = emptyList(),
    /** the selected user's garden id (needed to scope garden_plants). */
    val gardenId: String? = null,
)

class AdminViewModel(private val repo: AdminRepository) : ViewModel() {

    private val _ui = MutableStateFlow(AdminUiState())
    val ui: StateFlow<AdminUiState> = _ui.asStateFlow()

    init { refreshAll() }

    fun refreshAll() {
        viewModelScope.launch {
            try {
                val users = repo.users().map {
                    AdminUser(
                        id = it.cell("id").orEmpty(),
                        displayName = it.cell("display_name").orEmpty().ifBlank { "(no name)" },
                        isAdmin = it.cell("is_admin").toBoolean(),
                    )
                }.sortedBy { it.displayName.lowercase() }
                _ui.update { it.copy(loading = false, users = users, error = null) }
                loadItems()
                _ui.value.selectedUserId?.let { loadUserTables(it) }
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = friendly(e)) }
            }
        }
    }

    fun setMode(mode: AdminMode) = _ui.update { it.copy(mode = mode) }

    fun selectUser(userId: String) {
        _ui.update { it.copy(selectedUserId = userId, userTables = emptyMap(), gardenId = null) }
        viewModelScope.launch { loadUserTables(userId) }
    }

    private suspend fun loadItems() {
        val items = repo.rows("items").sortedBy { it.cell("name")?.lowercase() }
        val options = items.map { (it.cell("id").orEmpty()) to (it.cell("name").orEmpty().ifBlank { it.cell("slug").orEmpty() }) }
        _ui.update { it.copy(items = items, itemOptions = options) }
    }

    private suspend fun loadUserTables(userId: String) {
        try {
            val map = LinkedHashMap<String, List<JsonObject>>()
            var gardenId: String? = null
            for (spec in ADMIN_USER_TABLES) {
                map[spec.table] = when (spec.scope) {
                    AdminScope.BY_ID -> repo.rows(spec.table, "id", userId)
                    AdminScope.BY_USER_ID -> {
                        val rows = repo.rows(spec.table, "user_id", userId)
                        if (spec.table == "gardens") gardenId = rows.firstOrNull()?.cell("id")
                        rows
                    }
                    AdminScope.BY_GARDEN ->
                        gardenId?.let { repo.rows(spec.table, "garden_id", it) } ?: emptyList()
                    AdminScope.GLOBAL -> emptyList()
                }
            }
            _ui.update { it.copy(userTables = map, gardenId = gardenId, error = null) }
        } catch (e: Exception) {
            _ui.update { it.copy(error = friendly(e)) }
        }
    }

    // ── CRUD actions ─────────────────────────────────────────────────────────

    fun create(spec: AdminTableSpec, values: Map<String, String?>) = mutate(spec) {
        repo.insert(spec.table, spec.buildPayload(values, ownerExtra(spec)))
        "Created in ${spec.title}"
    }

    fun save(spec: AdminTableSpec, row: JsonObject, values: Map<String, String?>) = mutate(spec) {
        repo.update(spec.table, pkOf(spec, row), spec.buildPayload(values))
        "Saved ${spec.title}"
    }

    fun delete(spec: AdminTableSpec, row: JsonObject) = mutate(spec) {
        repo.delete(spec.table, pkOf(spec, row))
        "Deleted from ${spec.title}"
    }

    /** Fully delete a user's account (auth row + all owned data via cascade). */
    fun deleteUserAccount(user: AdminUser) {
        if (_ui.value.busy) return
        _ui.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                repo.deleteUserAccount(user.id)
                _ui.update {
                    it.copy(
                        busy = false,
                        selectedUserId = null,
                        userTables = emptyMap(),
                        gardenId = null,
                        message = "Deleted account: ${user.displayName}",
                    )
                }
                refreshAll()
            } catch (e: Exception) {
                _ui.update { it.copy(busy = false, message = friendly(e)) }
            }
        }
    }

    /** Owner columns the editor doesn't expose, injected on insert. */
    private fun ownerExtra(spec: AdminTableSpec): Map<String, JsonElement> {
        val sel = _ui.value.selectedUserId
        return when (spec.scope) {
            AdminScope.BY_ID -> mapOf("id" to JsonPrimitive(sel))
            AdminScope.BY_USER_ID -> mapOf("user_id" to JsonPrimitive(sel))
            AdminScope.BY_GARDEN -> mapOf("garden_id" to JsonPrimitive(_ui.value.gardenId))
            AdminScope.GLOBAL -> emptyMap()
        }
    }

    private fun pkOf(spec: AdminTableSpec, row: JsonObject): Map<String, String> =
        spec.pk.associateWith { row.cell(it).orEmpty() }

    private fun mutate(spec: AdminTableSpec, block: suspend () -> String) {
        if (_ui.value.busy) return
        _ui.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                val msg = block()
                // Reload just what changed.
                if (spec.scope == AdminScope.GLOBAL) loadItems()
                _ui.value.selectedUserId?.let { loadUserTables(it) }
                if (spec.table == "profiles") refreshAll()
                _ui.update { it.copy(busy = false, message = msg) }
            } catch (e: Exception) {
                _ui.update { it.copy(busy = false, message = friendly(e)) }
            }
        }
    }

    fun consumeMessage() {
        if (_ui.value.message != null) _ui.update { it.copy(message = null) }
    }

    private fun friendly(e: Exception): String {
        val m = e.message ?: return "Something went wrong"
        return when {
            "duplicate key" in m || "already exists" in m -> "That row already exists."
            "violates foreign key" in m -> "Referenced row doesn't exist (check the item/garden id)."
            "violates check constraint" in m -> "A value failed a database check constraint."
            "permission denied" in m || "row-level security" in m ->
                "Blocked by RLS — is this account an admin?"
            else -> m.take(160)
        }
    }

    companion object {
        val Factory = viewModelFactory { initializer { AdminViewModel(AdminRepository(gardenApp().container.supabase)) } }
    }
}
