package com.gratitudegarden.app.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.gratitudegarden.app.data.GratitudeEntry
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit

/**
 * Where a journal entry stands with the server. Entries written on the device start
 * [PENDING] and become [SYNCED] once the outbox delivers them; one the server refuses
 * (the text, the cap) is [FAILED] and keeps its text.
 */
enum class SyncState { SYNCED, PENDING, FAILED }

/**
 * A journal entry, soft-deleted ones included: the daily cap counts deleted entries
 * (they were paid for), so the local count has to see them too.
 */
@Entity(
    tableName = "gratitude_entries",
    indices = [Index("userId", "createdAtMicros"), Index("userId", "entryDate")],
)
data class EntryEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val entryText: String,
    val inputMethod: String,
    /** Null until the server has answered: it decides the reward. */
    val coinsAwarded: Int?,
    /** Null until the server has answered, like [coinsAwarded]. */
    val xpAwarded: Int?,
    /**
     * Local day the entry counts for, `yyyy-MM-dd`. For an entry still [SyncState.PENDING],
     * the day predicted on the device ([com.gratitudegarden.app.data.entryDay]); the
     * server's answer replaces it.
     */
    val entryDate: String,
    /** When the entry was written, ISO-8601 with an offset. The server keeps the same instant. */
    val createdAt: String,
    /**
     * [createdAt] as microseconds since the epoch, for ordering. Sorting the strings only
     * works while every one is in the server's `+00:00` form, which stops being true once
     * entries are also written on the device.
     */
    val createdAtMicros: Long,
    val deletedAt: String?,
    val syncState: SyncState = SyncState.SYNCED,
    /** Why the server refused it, when [syncState] is [SyncState.FAILED]. */
    val syncError: String? = null,
    /**
     * The entry's photo, `{user}/{entry}/{photo}.jpg` ([com.gratitudegarden.app.data.photoPathFor]):
     * its name in the bucket and, under the device's photo folder, where the local copy is.
     * Null when there's none.
     */
    val photoPath: String? = null,
    /** 1–5, rough to great ([com.gratitudegarden.app.model.Mood]); null when none was given. */
    val mood: Int? = null,
)

fun GratitudeEntry.toEntity(userId: String) = EntryEntity(
    id = id,
    userId = userId,
    entryText = entryText,
    inputMethod = inputMethod,
    coinsAwarded = coinsAwarded,
    xpAwarded = xpAwarded,
    entryDate = entryDate,
    createdAt = createdAt,
    createdAtMicros = epochMicros(createdAt),
    deletedAt = deletedAt,
    photoPath = photoPath,
    mood = mood,
)

fun EntryEntity.toRow() =
    GratitudeEntry(
        id, entryText, inputMethod, coinsAwarded, xpAwarded, entryDate, createdAt, deletedAt,
        syncState = syncState, syncError = syncError, photoPath = photoPath, mood = mood,
    )

/** Microseconds since the epoch for an ISO-8601 timestamp with an offset. */
internal fun epochMicros(timestamp: String): Long =
    ChronoUnit.MICROS.between(Instant.EPOCH, OffsetDateTime.parse(timestamp).toInstant())

@Dao
interface EntryDao {
    /** The newest [limit] live entries; the Journal pages by growing [limit]. */
    @Query(
        "SELECT * FROM gratitude_entries WHERE userId = :userId AND deletedAt IS NULL " +
            "ORDER BY createdAtMicros DESC LIMIT :limit"
    )
    fun observeNewest(userId: String, limit: Int): Flow<List<EntryEntity>>

    /** Live entries dated [from] to [to] (`yyyy-MM-dd`, both included), oldest first. */
    @Query(
        "SELECT * FROM gratitude_entries WHERE userId = :userId AND deletedAt IS NULL " +
            "AND entryDate BETWEEN :from AND :to ORDER BY createdAtMicros"
    )
    fun observeBetween(userId: String, from: String, to: String): Flow<List<EntryEntity>>

    /** Distinct days with a live entry on or after [since] (`yyyy-MM-dd`), for the week strip. */
    @Query(
        "SELECT DISTINCT entryDate FROM gratitude_entries " +
            "WHERE userId = :userId AND deletedAt IS NULL AND entryDate >= :since"
    )
    fun observeDatesSince(userId: String, since: String): Flow<List<String>>

    /** Entries dated [day], deleted ones included, to match the server's daily cap. */
    @Query("SELECT COUNT(*) FROM gratitude_entries WHERE userId = :userId AND entryDate = :day")
    fun observeCountOn(userId: String, day: String): Flow<Int>

    @Upsert
    suspend fun upsertAll(entries: List<EntryEntity>)

    @Upsert
    suspend fun upsert(entry: EntryEntity)

    @Query("SELECT * FROM gratitude_entries WHERE id = :id")
    suspend fun get(id: String): EntryEntity?

    @Query("SELECT COUNT(*) FROM gratitude_entries WHERE userId = :userId AND entryDate = :day")
    suspend fun countOn(userId: String, day: String): Int

    /** Entries the server refused, which keep their local version until the user decides. */
    @Query("SELECT id FROM gratitude_entries WHERE userId = :userId AND syncState = 'FAILED'")
    suspend fun refusedIds(userId: String): List<String>

    /** What an edit changes: the text and the mood, together. */
    @Query("UPDATE gratitude_entries SET entryText = :text, mood = :mood, syncState = :state WHERE id = :id")
    suspend fun setContent(id: String, text: String, mood: Int?, state: SyncState)

    /** Deleted, and without its photo, as `delete_gratitude_entry` leaves it. */
    @Query("UPDATE gratitude_entries SET deletedAt = :deletedAt, photoPath = NULL, syncState = :state WHERE id = :id")
    suspend fun markDeleted(id: String, deletedAt: String, state: SyncState)

    @Query("UPDATE gratitude_entries SET photoPath = :path, syncState = :state WHERE id = :id")
    suspend fun setPhoto(id: String, path: String?, state: SyncState)

    /** Every photo an entry of the user's refers to; the files the device must keep. */
    @Query("SELECT photoPath FROM gratitude_entries WHERE userId = :userId AND photoPath IS NOT NULL")
    suspend fun photoPaths(userId: String): List<String>

    @Query("UPDATE gratitude_entries SET syncState = :state, syncError = :error WHERE id = :id")
    suspend fun setSyncState(id: String, state: SyncState, error: String? = null)

    /**
     * What the server decided about an entry that still has local changes queued behind
     * the one it just answered: the rewards, the day, the time. The text stays local.
     */
    @Query(
        "UPDATE gratitude_entries SET coinsAwarded = :coins, xpAwarded = :xp, entryDate = :day, " +
            "createdAt = :createdAt, createdAtMicros = :createdAtMicros WHERE id = :id"
    )
    suspend fun setServerFields(id: String, coins: Int?, xp: Int?, day: String, createdAt: String, createdAtMicros: Long)

    /** An entry that never reached the server, deleted before it could. */
    @Query("DELETE FROM gratitude_entries WHERE id = :id")
    suspend fun hardDelete(id: String)
}
