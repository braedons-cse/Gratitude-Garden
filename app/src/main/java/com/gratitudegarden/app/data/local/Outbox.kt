package com.gratitudegarden.app.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

enum class OutboxType { SUBMIT, EDIT, DELETE, PHOTO }

/**
 * A journal change made on the device and not yet confirmed by the server, replayed in
 * [seq] order. Every op is safe to send twice: the server recognises a submit by its
 * client-generated id, an edit sets the same text again, and a delete of a deleted entry
 * succeeds. A photo is uploaded under a name of its own with upsert, set again to the same
 * name, and whatever else is in the entry's folder removed, so a second run finds nothing
 * left to change.
 *
 * A submit carries what the server needs to date it as written: the instant and the zone
 * at that moment, not at sync time.
 */
@Entity(tableName = "outbox", indices = [Index("userId"), Index("entryId")])
data class OutboxOp(
    @PrimaryKey(autoGenerate = true) val seq: Long = 0,
    val userId: String,
    val type: OutboxType,
    val entryId: String,
    /** The entry text, for [OutboxType.SUBMIT] and [OutboxType.EDIT]. */
    val text: String? = null,
    val inputMethod: String? = null,
    val timeZone: String? = null,
    /** ISO-8601 instant the entry was written, for [OutboxType.SUBMIT]. */
    val writtenAt: String? = null,
    /** For [OutboxType.PHOTO]: the photo to set, uploaded first; null removes the entry's photo. */
    val photoPath: String? = null,
    val attempts: Int = 0,
    val lastError: String? = null,
)

@Dao
interface OutboxDao {
    @Query("SELECT * FROM outbox WHERE userId = :userId ORDER BY seq LIMIT 1")
    suspend fun head(userId: String): OutboxOp?

    @Query("SELECT * FROM outbox WHERE entryId = :entryId ORDER BY seq")
    suspend fun opsFor(entryId: String): List<OutboxOp>

    /** Entries with a change still queued. A sync from the server must not overwrite them. */
    @Query("SELECT DISTINCT entryId FROM outbox")
    suspend fun pendingEntryIds(): List<String>

    /** How many of the user's entries have changes the server hasn't confirmed. */
    @Query("SELECT COUNT(DISTINCT entryId) FROM outbox WHERE userId = :userId")
    fun observePendingEntries(userId: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM outbox")
    suspend fun size(): Int

    @Insert
    suspend fun insert(op: OutboxOp): Long

    @Query("UPDATE outbox SET text = :text WHERE seq = :seq")
    suspend fun setText(seq: Long, text: String)

    @Query("UPDATE outbox SET photoPath = :path WHERE seq = :seq")
    suspend fun setPhotoPath(seq: Long, path: String?)

    /** Photos still to be uploaded. Their files must stay until the upload is done. */
    @Query("SELECT photoPath FROM outbox WHERE userId = :userId AND photoPath IS NOT NULL")
    suspend fun photoPaths(userId: String): List<String>

    @Query("UPDATE outbox SET attempts = attempts + 1, lastError = :error WHERE seq = :seq")
    suspend fun recordAttempt(seq: Long, error: String?)

    @Query("DELETE FROM outbox WHERE seq = :seq")
    suspend fun delete(seq: Long)

    @Query("DELETE FROM outbox WHERE entryId = :entryId")
    suspend fun deleteFor(entryId: String)
}
