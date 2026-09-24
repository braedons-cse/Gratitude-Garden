package com.gratitudegarden.app.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Transaction
import com.gratitudegarden.app.data.Garden
import com.gratitudegarden.app.data.GardenPlantRow
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "garden", indices = [Index("userId")])
data class GardenEntity(
    @PrimaryKey val id: String,
    val userId: String,
    val name: String,
    val gridRows: Int,
    val gridCols: Int,
)

fun Garden.toEntity(userId: String) = GardenEntity(id, userId, name, gridRows, gridCols)

fun GardenEntity.toRow() = Garden(id, name, gridRows, gridCols)

@Entity(tableName = "garden_plants", indices = [Index("gardenId")])
data class PlantEntity(
    @PrimaryKey val id: String,
    val gardenId: String,
    val itemId: String,
    val gridX: Int,
    val gridY: Int,
    val growthStage: String,
    val health: String,
)

fun GardenPlantRow.toEntity(gardenId: String) =
    PlantEntity(id, gardenId, itemId, gridX, gridY, growthStage, health)

fun PlantEntity.toRow() = GardenPlantRow(id, itemId, gridX, gridY, growthStage, health)

@Dao
interface GardenDao {
    @Query("SELECT * FROM garden WHERE userId = :userId LIMIT 1")
    fun observeGarden(userId: String): Flow<GardenEntity?>

    @Query("SELECT * FROM garden_plants WHERE gardenId = :gardenId")
    fun observePlants(gardenId: String): Flow<List<PlantEntity>>

    /**
     * Swap in a freshly fetched garden and its plants in one transaction, so an observer
     * never sees the new garden with the old plants, or a plant that was dug up elsewhere.
     */
    @Transaction
    suspend fun replace(userId: String, garden: GardenEntity?, plants: List<PlantEntity>) {
        deletePlantsOf(userId)
        deleteGardensOf(userId)
        if (garden != null) {
            insertGarden(garden)
            insertPlants(plants)
        }
    }

    @Query("DELETE FROM garden_plants WHERE gardenId IN (SELECT id FROM garden WHERE userId = :userId)")
    suspend fun deletePlantsOf(userId: String)

    @Query("DELETE FROM garden WHERE userId = :userId")
    suspend fun deleteGardensOf(userId: String)

    @Insert
    suspend fun insertGarden(garden: GardenEntity)

    @Insert
    suspend fun insertPlants(plants: List<PlantEntity>)
}
