package com.cse5236.gratitudegarden.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cse5236.gratitudegarden.data.GardenRepository
import com.cse5236.gratitudegarden.ui.repo
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(val loading: Boolean = false, val error: String? = null)

class AuthViewModel(private val repo: GardenRepository) : ViewModel() {

    val sessionStatus: StateFlow<SessionStatus> = repo.sessionStatus

    private val _ui = MutableStateFlow(AuthUiState())
    val ui: StateFlow<AuthUiState> = _ui.asStateFlow()

    fun clearError() {
        if (_ui.value.error != null) _ui.value = _ui.value.copy(error = null)
    }

    fun signIn(email: String, password: String) =
        launch("We couldn't log you in") { repo.signIn(email.trim(), password) }

    fun signUp(name: String, email: String, password: String) =
        launch("We couldn't create your garden") { repo.signUp(email.trim(), password, name.trim()) }

    fun signOut() = launch("Couldn't sign out") { repo.signOut() }

    private fun launch(fallback: String, block: suspend () -> Unit) {
        _ui.value = AuthUiState(loading = true)
        viewModelScope.launch {
            _ui.value = try {
                block()
                AuthUiState()
            } catch (e: Exception) {
                AuthUiState(error = e.message?.takeIf { it.isNotBlank() } ?: fallback)
            }
        }
    }

    companion object {
        val Factory = viewModelFactory { initializer { AuthViewModel(repo()) } }
    }
}
