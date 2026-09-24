package com.gratitudegarden.app.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
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
)

fun UserStatsRow.toEntity(userId: String) = StatsEntity(
    userId = userId,
    totalEntries = totalEntries,
    currentStreak = currentStreak,
    longestStreak = longestStreak,
    lastEntryDate = lastEntryDate,
)

fun StatsEntity.toRow() = UserStatsRow(
    totalEntries = totalEntries,
    currentStreak = currentStreak,
    longestStreak = longestStreak,
    lastEntryDate = lastEntryDate,
)

@Dao
interface StatsDao {
    @Query("SELECT * FROM user_stats WHERE userId = :userId")
    fun observe(userId: String): Flow<StatsEntity?>

    @Upsert
    suspend fun upsert(stats: StatsEntity)
}
