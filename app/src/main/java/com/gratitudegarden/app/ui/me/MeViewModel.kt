package com.gratitudegarden.app.ui.me

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gratitudegarden.app.data.GardenRepository
import com.gratitudegarden.app.data.effectiveStreak
import com.gratitudegarden.app.notifications.ReminderNotifications
import com.gratitudegarden.app.notifications.ReminderPreferences
import com.gratitudegarden.app.notifications.ReminderScheduler
import com.gratitudegarden.app.ui.gardenApp
import com.gratitudegarden.app.ui.repo
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
    // Whether the OS grants exact-alarm access. When false, reminders still fire
    // (inexactly), but we surface a hint offering to enable precise timing.
    val exactAlarmPermitted: Boolean = true,
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
        // Reconcile the alarm on launch (NOT on every data change — see below).
        reconcileReminder()
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
                    streak = stats?.effectiveStreak ?: 0,
                    totalEntries = stats?.totalEntries ?: 0,
                    isAdmin = profile?.isAdmin ?: false,
                    reminderEnabled = reminder.enabled,
                    reminderHour = reminder.hour,
                    reminderMinute = reminder.minute,
                    osNotificationsEnabled = ReminderNotifications.enabledAtOsLevel(appContext),
                    micGranted = micGranted(),
                    exactAlarmPermitted = ReminderScheduler.canScheduleExact(appContext),
                )
            }
        } catch (_: Exception) {
            _ui.update { it.copy(loading = false) }
        }
    }

    /**
     * Reconcile the daily-reminder alarm with current OS state. Called on launch and
     * on resume — deliberately NOT from [load], which also runs on every cross-screen
     * data change and would needlessly churn the alarm.
     *
     * - Notifications revoked → turn the reminder off (reflects reality).
     * - Otherwise (re)arm the alarm. This is idempotent and also handles the two ways
     *   the OS drops our alarm out from under us: exact-alarm access being granted
     *   (upgrade inexact→exact) or revoked (Android cancels the exact alarm, so we
     *   must re-arm — inexactly).
     */
    private fun reconcileReminder() {
        viewModelScope.launch {
            val reminder = reminderPrefs.current()
            if (!reminder.enabled) return@launch
            if (!ReminderNotifications.enabledAtOsLevel(appContext)) {
                setReminderEnabled(false)
            } else {
                ReminderScheduler.schedule(appContext, reminder.hour, reminder.minute)
            }
        }
    }

    private fun micGranted(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Re-read the microphone + notification + exact-alarm permission state — call on
     * resume and after a permission request. [reconcileReminder] then turns the
     * reminder off if notifications were revoked, or re-arms the alarm (picking up a
     * newly granted/revoked exact-alarm access) so the reminder keeps firing.
     */
    fun refreshPermissions() {
        _ui.update {
            it.copy(
                osNotificationsEnabled = ReminderNotifications.enabledAtOsLevel(appContext),
                micGranted = micGranted(),
                exactAlarmPermitted = ReminderScheduler.canScheduleExact(appContext),
            )
        }
        reconcileReminder()
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
