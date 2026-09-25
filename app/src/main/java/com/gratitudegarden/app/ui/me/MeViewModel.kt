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
import com.gratitudegarden.app.data.LevelProgress
import com.gratitudegarden.app.data.StreakStatus
import com.gratitudegarden.app.data.levelProgress
import com.gratitudegarden.app.data.streakNow
import com.gratitudegarden.app.notifications.ReminderNotifications
import com.gratitudegarden.app.notifications.ReminderPreferences
import com.gratitudegarden.app.notifications.ReminderScheduler
import com.gratitudegarden.app.ui.gardenApp
import com.gratitudegarden.app.ui.repo
import com.gratitudegarden.app.ui.showIn
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MeUiState(
    val loading: Boolean = true,
    val name: String = "",
    /** Level and XP, both read off the XP so the number and the bar agree. */
    val progress: LevelProgress = levelProgress(0),
    val coins: Int = 0,
    val streak: Int = 0,
    /** Days were missed and freezes cover them; the next entry spends them. */
    val streakHeld: Boolean = false,
    /** Streak freezes available, this month's free one included. */
    val freezes: Int = 0,
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
    /** Entries with changes the server hasn't confirmed; logging out would lose them. */
    val unsynced: Int = 0,
    val syncing: Boolean = false,
    /** Set when a "Sync now" couldn't reach the server. */
    val syncFailed: Boolean = false,
)

class MeViewModel(
    private val repo: GardenRepository,
    private val appContext: Context,
) : ViewModel() {

    private val _ui = MutableStateFlow(MeUiState())
    val ui: StateFlow<MeUiState> = _ui.asStateFlow()

    private val reminderPrefs = ReminderPreferences(appContext)

    init {
        showIn(_ui, repo.observeProfile()) {
            copy(
                name = it?.displayName ?: "Gardener",
                progress = levelProgress(it?.xp ?: 0),
                isAdmin = it?.isAdmin ?: false,
            )
        }
        showIn(_ui, repo.observeWallet()) { copy(coins = it?.balance ?: 0) }
        showIn(_ui, repo.observeStats()) {
            val s = it?.streakNow ?: StreakStatus()
            copy(
                streak = s.days,
                streakHeld = s.heldByFreeze,
                freezes = s.freezes,
                totalEntries = it?.totalEntries ?: 0,
            )
        }
        showIn(_ui, repo.observeUnsyncedCount()) { copy(unsynced = it) }
        viewModelScope.launch { loadDeviceSettings() }
        // Reconcile the alarm on launch (NOT on every data change — see below).
        reconcileReminder()
        // Failures are silent here: the Garden tab already reports them, and the
        // numbers on this screen stay at their last synced values.
        viewModelScope.launch { runCatching { repo.refreshAll() } }
    }

    /** The settings that live on this device rather than in Supabase. */
    private suspend fun loadDeviceSettings() {
        val reminder = reminderPrefs.current()
        _ui.update {
            it.copy(
                loading = false,
                reminderEnabled = reminder.enabled,
                reminderHour = reminder.hour,
                reminderMinute = reminder.minute,
                osNotificationsEnabled = ReminderNotifications.enabledAtOsLevel(appContext),
                micGranted = micGranted(),
            )
        }
    }

    /**
     * Reconcile the daily-reminder alarm with current OS state. Called on launch and
     * on resume — deliberately NOT on data changes, which would needlessly churn the
     * alarm.
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
     * reminder off if notifications were revoked, or re-arms the alarm so the reminder
     * keeps firing.
     */
    fun refreshPermissions() {
        _ui.update {
            it.copy(
                osNotificationsEnabled = ReminderNotifications.enabledAtOsLevel(appContext),
                micGranted = micGranted(),
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

    /** Deliver the queued journal changes now, from the log-out warning. */
    fun syncNow() {
        if (_ui.value.syncing) return
        _ui.update { it.copy(syncing = true, syncFailed = false) }
        viewModelScope.launch {
            val delivered = runCatching { repo.drainOutbox() }.getOrDefault(false)
            _ui.update { it.copy(syncing = false, syncFailed = !delivered) }
        }
    }

    fun clearSyncFailed() {
        if (_ui.value.syncFailed) _ui.update { it.copy(syncFailed = false) }
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
