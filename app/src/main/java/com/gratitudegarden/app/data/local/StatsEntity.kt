package com.gratitudegarden.app.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.gratitudegarden.app.data.UserStatsRow
import kotlinx.coroutines.flow.Flow

/**
 * Local copy of the signed-in user's `user_stats` row, replaced wholesale on each refresh.
 *
 * Keyed by user id even though Room only ever holds one account: if a sign-out wipe is
 * ever missed, the next user reads nothing rather than someone else's streak.
 */
@Entity(tableName = "user_stats")
data class StatsEntity(
    @PrimaryKey val userId: String,
    val totalEntries: Int,
    val currentStreak: Int,
    val longestStreak: Int,
    /** ISO `yyyy-MM-dd`, exactly as the server sends it. */
    val lastEntryDate: String?,
    val streakFreezes: Int = 0,
    /** ISO `yyyy-MM-dd`, the first of a month. */
    val freezeGrantMonth: String? = null,
)

fun UserStatsRow.toEntity(userId: String) = StatsEntity(
    userId = userId,
    totalEntries = totalEntries,
    currentStreak = currentStreak,
    longestStreak = longestStreak,
    lastEntryDate = lastEntryDate,
    streakFreezes = streakFreezes,
    freezeGrantMonth = freezeGrantMonth,
)

fun StatsEntity.toRow() = UserStatsRow(
    totalEntries = totalEntries,
    currentStreak = currentStreak,
    longestStreak = longestStreak,
    lastEntryDate = lastEntryDate,
    streakFreezes = streakFreezes,
    freezeGrantMonth = freezeGrantMonth,
)

/** A day a streak freeze covered (`streak_frozen_days`). */
@Entity(tableName = "streak_frozen_days", primaryKeys = ["userId", "day"])
data class FrozenDayEntity(
    val userId: String,
    /** ISO `yyyy-MM-dd`. */
    val day: String,
)

@Dao
interface StatsDao {
    @Query("SELECT * FROM user_stats WHERE userId = :userId")
    fun observe(userId: String): Flow<StatsEntity?>

    @Upsert
    suspend fun upsert(stats: StatsEntity)

    @Query("SELECT day FROM streak_frozen_days WHERE userId = :userId AND day >= :since")
    fun observeFrozenDaysSince(userId: String, since: String): Flow<List<String>>

    @Transaction
    suspend fun replaceFrozenDays(userId: String, days: Collection<String>) {
        deleteFrozenDaysOf(userId)
        insertFrozenDays(days.map { FrozenDayEntity(userId, it) })
    }

    @Query("DELETE FROM streak_frozen_days WHERE userId = :userId")
    suspend fun deleteFrozenDaysOf(userId: String)

    @Insert
    suspend fun insertFrozenDays(rows: List<FrozenDayEntity>)
}
