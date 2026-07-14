package com.cse5236.gratitudegarden.ui.me

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.cse5236.gratitudegarden.data.GardenRepository
import com.cse5236.gratitudegarden.notifications.ReminderNotifications
import com.cse5236.gratitudegarden.notifications.ReminderPreferences
import com.cse5236.gratitudegarden.notifications.ReminderScheduler
import com.cse5236.gratitudegarden.ui.gardenApp
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
    // Reminder settings (stored locally on-device via ReminderPreferences).
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 20,
    val reminderMinute: Int = 0,
    // Whether the OS will actually let our notifications through.
    val osNotificationsEnabled: Boolean = true,
    // Whether the microphone runtime permission is currently granted.
    val micGranted: Boolean = false,
)

class MeViewModel(
    private val repo: GardenRepository,
    private val appContext: Context,
) : ViewModel() {

    private val _ui = MutableStateFlow(MeUiState())
    val ui: StateFlow<MeUiState> = _ui.asStateFlow()

    private val reminderPrefs = ReminderPreferences(appContext)

    init {
        viewModelScope.launch { load() }
        // Keep stats/coins fresh when they change on other screens.
        viewModelScope.launch { repo.changes.collect { load() } }
    }

    private suspend fun load() {
        try {
            val profile = repo.profile()
            val wallet = repo.wallet()
            val stats = repo.stats()
            val reminder = reminderPrefs.current()
            _ui.update {
                it.copy(
                    loading = false,
                    name = profile?.displayName ?: "Gardener",
                    level = profile?.level ?: 1,
                    coins = wallet?.balance ?: 0,
                    streak = stats?.currentStreak ?: 0,
                    totalEntries = stats?.totalEntries ?: 0,
                    isAdmin = profile?.isAdmin ?: false,
                    reminderEnabled = reminder.enabled,
                    reminderHour = reminder.hour,
                    reminderMinute = reminder.minute,
                    osNotificationsEnabled = ReminderNotifications.enabledAtOsLevel(appContext),
                    micGranted = micGranted(),
                )
            }
            // If notifications were revoked while a reminder was set, turn it off.
            if (reminder.enabled && !ReminderNotifications.enabledAtOsLevel(appContext)) {
                setReminderEnabled(false)
            }
        } catch (_: Exception) {
            _ui.update { it.copy(loading = false) }
        }
    }

    private fun micGranted(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Re-read the microphone + notification permission state — call on resume and
     * after a permission request. If notifications were turned off at the OS level
     * while the daily reminder was on, the reminder is switched off too so the toggle
     * reflects reality.
     */
    fun refreshPermissions() {
        val osNotif = ReminderNotifications.enabledAtOsLevel(appContext)
        val wasReminderOn = _ui.value.reminderEnabled
        _ui.update { it.copy(osNotificationsEnabled = osNotif, micGranted = micGranted()) }
        if (wasReminderOn && !osNotif) setReminderEnabled(false)
    }

    /** Toggle the daily reminder on/off. Enabling schedules; disabling cancels. */
    fun setReminderEnabled(enabled: Boolean) {
        _ui.update { it.copy(reminderEnabled = enabled) }
        viewModelScope.launch {
            reminderPrefs.setEnabled(enabled)
            if (enabled) {
                val s = reminderPrefs.current()
                ReminderScheduler.schedule(appContext, s.hour, s.minute)
            } else {
                ReminderScheduler.cancel(appContext)
            }
        }
    }

    /** Change the time-of-day; re-arms the schedule when reminders are enabled. */
    fun setReminderTime(hour: Int, minute: Int) {
        _ui.update { it.copy(reminderHour = hour, reminderMinute = minute) }
        viewModelScope.launch {
            reminderPrefs.setTime(hour, minute)
            if (_ui.value.reminderEnabled) {
                ReminderScheduler.schedule(appContext, hour, minute)
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
        val Factory = viewModelFactory { initializer { MeViewModel(repo(), gardenApp()) } }
    }
}
