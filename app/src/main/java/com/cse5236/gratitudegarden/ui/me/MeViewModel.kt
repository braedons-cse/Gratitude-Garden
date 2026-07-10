package com.cse5236.gratitudegarden.ui.me

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cse5236.gratitudegarden.data.GardenRepository
import com.cse5236.gratitudegarden.ui.repo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MeUiState(
    val loading: Boolean = true,
    val name: String = "",
    val level: Int = 1,
    val coins: Int = 0,
    val streak: Int = 0,
    val totalEntries: Int = 0,
    val isAdmin: Boolean = false,
    val deleting: Boolean = false,
    val deleteError: String? = null,
)

class MeViewModel(private val repo: GardenRepository) : ViewModel() {

    private val _ui = MutableStateFlow(MeUiState())
    val ui: StateFlow<MeUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val profile = repo.profile()
                val wallet = repo.wallet()
                val stats = repo.stats()
                _ui.update {
                    it.copy(
                        loading = false,
                        name = profile?.displayName ?: "Gardener",
                        level = profile?.level ?: 1,
                        coins = wallet?.balance ?: 0,
                        streak = stats?.currentStreak ?: 0,
                        totalEntries = stats?.totalEntries ?: 0,
                        isAdmin = profile?.isAdmin ?: false,
                    )
                }
            } catch (_: Exception) {
                _ui.update { it.copy(loading = false) }
            }
        }
    }

    /**
     * Permanently delete the signed-in account after the user has confirmed
     * twice and entered [password]. On success the Supabase session is cleared,
     * so [GratitudeGardenApp] auto-navigates back to the login screen — no
     * explicit navigation needed here.
     */
    fun deleteAccount(password: String) {
        if (_ui.value.deleting) return
        _ui.update { it.copy(deleting = true, deleteError = null) }
        viewModelScope.launch {
            try {
                repo.deleteOwnAccount(password)
                // Session gone → the app swaps to the auth flow.
            } catch (e: Exception) {
                _ui.update { it.copy(deleting = false, deleteError = friendlyDeleteError(e)) }
            }
        }
    }

    fun clearDeleteError() {
        if (_ui.value.deleteError != null) _ui.update { it.copy(deleteError = null) }
    }

    private fun friendlyDeleteError(e: Exception): String {
        val m = e.message ?: return "Couldn't delete your account. Please try again."
        return when {
            "Invalid login credentials" in m || "invalid_credentials" in m ||
                "invalid_grant" in m -> "Incorrect password."
            else -> "Couldn't delete your account. Please try again."
        }
    }

    companion object {
        val Factory = viewModelFactory { initializer { MeViewModel(repo()) } }
    }
}
