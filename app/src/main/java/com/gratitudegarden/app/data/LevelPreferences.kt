package com.gratitudegarden.app.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.levelDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "level_prefs")

/**
 * The highest level each user has already been congratulated on, on this device. Local on
 * purpose: `profiles` is overwritten by every refresh, and a level-up delivered in the
 * background by the outbox should still get its moment the next time the Garden opens.
 */
class LevelPreferences(private val context: Context) {

    private fun key(userId: String) = intPreferencesKey("celebrated_level_$userId")

    /** Null until the first level this device has seen for [userId] is recorded. */
    fun celebrated(userId: String): Flow<Int?> =
        context.levelDataStore.data.map { it[key(userId)] }

    suspend fun setCelebrated(userId: String, level: Int) {
        context.levelDataStore.edit { it[key(userId)] = level }
    }
}
