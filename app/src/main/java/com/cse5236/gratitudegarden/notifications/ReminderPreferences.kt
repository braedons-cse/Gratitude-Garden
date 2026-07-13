package com.cse5236.gratitudegarden.notifications

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/** On-device reminder settings (Option 3: local-only, not synced to Supabase). */
data class ReminderSettings(
    val enabled: Boolean = false,
    val hour: Int = DEFAULT_HOUR,
    val minute: Int = DEFAULT_MINUTE,
) {
    companion object {
        // 8 PM — an end-of-day nudge to log gratitude before the streak lapses.
        const val DEFAULT_HOUR = 20
        const val DEFAULT_MINUTE = 0
    }
}

private val Context.reminderDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "reminder_prefs")

/**
 * Preferences-DataStore wrapper for the daily reminder. Deliberately device-local:
 * these settings never leave the phone. (The one-time "enable reminders?" prompt
 * flag, by contrast, lives in Supabase `user_settings.notif_prompt_seen`.)
 */
class ReminderPreferences(private val context: Context) {

    private val keyEnabled = booleanPreferencesKey("reminder_enabled")
    private val keyHour = intPreferencesKey("reminder_hour")
    private val keyMinute = intPreferencesKey("reminder_minute")

    val settings: Flow<ReminderSettings> = context.reminderDataStore.data.map { p ->
        ReminderSettings(
            enabled = p[keyEnabled] ?: false,
            hour = p[keyHour] ?: ReminderSettings.DEFAULT_HOUR,
            minute = p[keyMinute] ?: ReminderSettings.DEFAULT_MINUTE,
        )
    }

    /** One-shot read (used by the worker and by callers that just need the value now). */
    suspend fun current(): ReminderSettings = settings.first()

    suspend fun setEnabled(enabled: Boolean) {
        context.reminderDataStore.edit { it[keyEnabled] = enabled }
    }

    suspend fun setTime(hour: Int, minute: Int) {
        context.reminderDataStore.edit {
            it[keyHour] = hour
            it[keyMinute] = minute
        }
    }
}
