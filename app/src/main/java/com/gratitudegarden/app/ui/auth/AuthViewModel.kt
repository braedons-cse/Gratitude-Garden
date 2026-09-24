package com.gratitudegarden.app.ui.auth

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gratitudegarden.app.data.AppSession
import com.gratitudegarden.app.data.GardenRepository
import com.gratitudegarden.app.ui.repo
import com.gratitudegarden.app.util.LogTags
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AuthUiState(val loading: Boolean = false, val error: String? = null)

class AuthViewModel(private val repo: GardenRepository) : ViewModel() {

    val session: StateFlow<AppSession> = repo.session

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
                Log.w(LogTags.APP_LOGIC, fallback, e) // the full request detail stays in logcat
                AuthUiState(error = authErrorMessage(e, fallback))
            }
        }
    }

    companion object {
        val Factory = viewModelFactory { initializer { AuthViewModel(repo()) } }
    }
}
