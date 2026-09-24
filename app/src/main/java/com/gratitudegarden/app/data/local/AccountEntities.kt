package com.gratitudegarden.app.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert
import com.gratitudegarden.app.data.ProfileRow
import com.gratitudegarden.app.data.WalletRow
import kotlinx.coroutines.flow.Flow

// The signed-in user's one-row tables besides stats: profile, wallet and settings. Each
// is replaced wholesale on refresh and keyed by user id, like StatsEntity.

@Entity(tableName = "profile")
data class ProfileEntity(
    @PrimaryKey val userId: String,
    val displayName: String,
    val level: Int,
    val xp: Int,
    val isAdmin: Boolean,
)

fun ProfileRow.toEntity(userId: String) = ProfileEntity(userId, displayName, level, xp, isAdmin)

fun ProfileEntity.toRow() = ProfileRow(displayName, level, xp, isAdmin)

@Entity(tableName = "wallet")
data class WalletEntity(
    @PrimaryKey val userId: String,
    val balance: Int,
)

fun WalletRow.toEntity(userId: String) = WalletEntity(userId, balance)

fun WalletEntity.toRow() = WalletRow(balance)

/** Only the columns of `user_settings` the app reads. */
@Entity(tableName = "user_settings")
data class SettingsEntity(
    @PrimaryKey val userId: String,
    val notifPromptSeen: Boolean,
    /**
     * Entries per day that earn coins, as `submit_gratitude_entry` reads it (already
     * clamped). Mirrored so an entry written offline is refused here rather than at sync.
     */
    val dailyEntryCap: Int = DEFAULT_DAILY_CAP,
) {
    companion object {
        const val DEFAULT_DAILY_CAP = 10

        /** The server's `least(coalesce(daily_entry_cap, 10), 50)`. */
        fun clampCap(cap: Int?): Int = minOf(cap ?: DEFAULT_DAILY_CAP, 50)
    }
}

@Dao
interface AccountDao {
    @Query("SELECT * FROM profile WHERE userId = :userId")
    fun observeProfile(userId: String): Flow<ProfileEntity?>

    @Upsert
    suspend fun upsertProfile(profile: ProfileEntity)

    @Query("SELECT * FROM wallet WHERE userId = :userId")
    fun observeWallet(userId: String): Flow<WalletEntity?>

    @Upsert
    suspend fun upsertWallet(wallet: WalletEntity)

    @Query("SELECT * FROM user_settings WHERE userId = :userId")
    fun observeSettings(userId: String): Flow<SettingsEntity?>

    @Query("SELECT * FROM user_settings WHERE userId = :userId")
    suspend fun getSettings(userId: String): SettingsEntity?

    @Upsert
    suspend fun upsertSettings(settings: SettingsEntity)

    @Query("UPDATE user_settings SET notifPromptSeen = 1 WHERE userId = :userId")
    suspend fun markNotifPromptSeen(userId: String)
}
