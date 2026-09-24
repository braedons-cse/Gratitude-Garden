package com.gratitudegarden.app.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import com.gratitudegarden.app.data.Item
import kotlinx.coroutines.flow.Flow

/** The shop catalog. Global: the same rows for every user, so no user id. */
@Entity(tableName = "items")
data class ItemEntity(
    @PrimaryKey val id: String,
    val slug: String,
    val category: String,
    val name: String,
    val rarity: String,
    val priceCoins: Int,
    val levelRequired: Int,
    val isPurchasable: Boolean,
    val isStarter: Boolean,
)

fun Item.toEntity() =
    ItemEntity(id, slug, category, name, rarity, priceCoins, levelRequired, isPurchasable, isStarter)

fun ItemEntity.toRow() =
    Item(id, slug, category, name, rarity, priceCoins, levelRequired, isPurchasable, isStarter)

/** One owned item. Only ownership is mirrored; the item itself lives in [ItemEntity]. */
@Entity(tableName = "user_inventory", primaryKeys = ["userId", "itemId"])
data class InventoryEntity(
    val userId: String,
    val itemId: String,
)

@Dao
interface ItemDao {
    @Query("SELECT * FROM items ORDER BY priceCoins")
    fun observeCatalog(): Flow<List<ItemEntity>>

    @Transaction
    suspend fun replaceCatalog(items: List<ItemEntity>) {
        deleteCatalog()
        insertItems(items)
    }

    @Query("DELETE FROM items")
    suspend fun deleteCatalog()

    @Insert
    suspend fun insertItems(items: List<ItemEntity>)

    @Query("SELECT itemId FROM user_inventory WHERE userId = :userId")
    fun observeInventory(userId: String): Flow<List<String>>

    @Transaction
    suspend fun replaceInventory(userId: String, itemIds: Collection<String>) {
        deleteInventoryOf(userId)
        insertInventory(itemIds.map { InventoryEntity(userId, it) })
    }

    @Query("DELETE FROM user_inventory WHERE userId = :userId")
    suspend fun deleteInventoryOf(userId: String)

    @Insert
    suspend fun insertInventory(rows: List<InventoryEntity>)
}
