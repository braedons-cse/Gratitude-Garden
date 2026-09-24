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
    val coinsAwarded: Int,
    /** Local day the server dated the entry, `yyyy-MM-dd`. */
    val entryDate: String,
    /**
     * The server's timestamp verbatim. Kept for display, and because keyset paging sends
     * it back as the cursor, which has to match to the microsecond.
     */
    val createdAt: String,
    /**
     * [createdAt] as microseconds since the epoch, for ordering. Sorting the strings only
     * works while every one is in the server's `+00:00` form, which stops being true once
     * entries are also written on the device.
     */
    val createdAtMicros: Long,
    val deletedAt: String?,
)

fun GratitudeEntry.toEntity(userId: String) = EntryEntity(
    id = id,
    userId = userId,
    entryText = entryText,
    inputMethod = inputMethod,
    coinsAwarded = coinsAwarded,
    entryDate = entryDate,
    createdAt = createdAt,
    createdAtMicros = epochMicros(createdAt),
    deletedAt = deletedAt,
)

fun EntryEntity.toRow() =
    GratitudeEntry(id, entryText, inputMethod, coinsAwarded, entryDate, createdAt, deletedAt)

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
}
