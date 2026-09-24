package com.gratitudegarden.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * On-device copy of the user's data. Screens read from here; a refresh from Supabase
 * writes into it (see docs/offline-first-plan.md).
 *
 * Every schema version is exported to `app/schemas`. Before the first release, version 1
 * may still be rewritten in place; after it, every change needs a version bump and a
 * migration, and the shipped JSON files must never be edited.
 */
@Database(
    entities = [StatsEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class GardenDatabase : RoomDatabase() {
    abstract fun statsDao(): StatsDao

    companion object {
        const val FILE_NAME = "garden.db"

        fun create(context: Context): GardenDatabase =
            Room.databaseBuilder(context, GardenDatabase::class.java, FILE_NAME).build()
    }
}
