package com.gratitudegarden.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.milestoneDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "milestone_prefs")

/**
 * The highest streak milestone each user's current run has been celebrated for, on this
 * device. Local for the same reason as [LevelPreferences]: an entry the outbox delivered in
 * the background should still get its moment the next time the Garden opens.
 */
class MilestonePreferences(private val context: Context) {

    private fun key(userId: String) = intPreferencesKey("celebrated_streak_$userId")

    /** Null until this device has seen [userId]'s streak. */
    fun celebrated(userId: String): Flow<Int?> =
        context.milestoneDataStore.data.map { it[key(userId)] }

    suspend fun setCelebrated(userId: String, milestone: Int) {
        context.milestoneDataStore.edit { it[key(userId)] = milestone }
    }
}
