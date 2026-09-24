package com.gratitudegarden.app.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

/**
 * How far a delta sync has read: the `(updated_at, id)` of the last row it stored. The next
 * sync asks only for rows after it. [key] names the table and user, e.g. `entries:<uid>`.
 */
@Entity(tableName = "sync_cursor")
data class SyncCursorEntity(
    @PrimaryKey val key: String,
    /** The server's `updated_at`, verbatim, so the comparison happens in Postgres at full precision. */
    val updatedAt: String,
    val lastId: String,
)

@Dao
interface SyncCursorDao {
    @Query("SELECT * FROM sync_cursor WHERE `key` = :key")
    suspend fun get(key: String): SyncCursorEntity?

    @Upsert
    suspend fun put(cursor: SyncCursorEntity)
}
