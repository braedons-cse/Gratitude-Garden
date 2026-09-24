package com.gratitudegarden.app

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gratitudegarden.app.data.GratitudeEntry
import com.gratitudegarden.app.data.local.EntryEntity
import com.gratitudegarden.app.data.local.GardenDatabase
import com.gratitudegarden.app.data.local.GardenEntity
import com.gratitudegarden.app.data.local.PlantEntity
import com.gratitudegarden.app.data.local.StatsEntity
import com.gratitudegarden.app.data.local.toEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The KSP-generated Room code against a real SQLite: the queries the screens will observe,
 * and the transactional replaces a refresh will use.
 */
@RunWith(AndroidJUnit4::class)
class GardenDatabaseTest {
    private lateinit var db: GardenDatabase

    @Before
    fun open() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, GardenDatabase::class.java).build()
    }

    @After
    fun close() = db.close()

    @Test
    fun upsertReplacesTheRowAndObserveSeesIt() = runBlocking {
        val dao = db.statsDao()
        assertNull(dao.observe("u1").first())

        dao.upsert(StatsEntity("u1", totalEntries = 3, currentStreak = 2, longestStreak = 5, lastEntryDate = "2026-09-23"))
        dao.upsert(StatsEntity("u1", totalEntries = 4, currentStreak = 3, longestStreak = 5, lastEntryDate = "2026-09-24"))

        assertEquals(4, dao.observe("u1").first()?.totalEntries)
        assertNull(dao.observe("someone-else").first())
    }

    @Test
    fun gardenReplaceDropsPlantsThatAreGoneAndLeavesOtherUsersAlone() = runBlocking {
        val dao = db.gardenDao()
        val mine = GardenEntity("g1", "u1", "My Garden", 6, 5)
        val theirs = GardenEntity("g2", "u2", "Theirs", 6, 5)
        dao.replace("u1", mine, listOf(plant("p1", "g1"), plant("p2", "g1")))
        dao.replace("u2", theirs, listOf(plant("p3", "g2")))

        // p2 was dug up on another device.
        dao.replace("u1", mine, listOf(plant("p1", "g1")))

        assertEquals(listOf("p1"), dao.observePlants("g1").first().map { it.id })
        assertEquals(listOf("p3"), dao.observePlants("g2").first().map { it.id })
    }

    @Test
    fun inventoryReplaceIsPerUser() = runBlocking {
        val dao = db.itemDao()
        dao.replaceInventory("u1", listOf("a", "b"))
        dao.replaceInventory("u2", listOf("c"))
        dao.replaceInventory("u1", listOf("b"))

        assertEquals(listOf("b"), dao.observeInventory("u1").first())
        assertEquals(listOf("c"), dao.observeInventory("u2").first())
    }

    @Test
    fun newestOrdersByInstantAndHidesDeleted() = runBlocking {
        val dao = db.entryDao()
        dao.upsertAll(
            listOf(
                entry("old", "2026-09-22T10:00:00+00:00", "2026-09-22"),
                entry("new", "2026-09-23T10:00:00.5+00:00", "2026-09-23"),
                entry("mid", "2026-09-23T10:00:00.123456+00:00", "2026-09-23"),
                entry("gone", "2026-09-23T11:00:00+00:00", "2026-09-23", deletedAt = "2026-09-23T12:00:00+00:00"),
            )
        )

        assertEquals(listOf("new", "mid", "old"), dao.observeNewest("u1", 10).first().map { it.id })
        assertEquals(listOf("new", "mid"), dao.observeNewest("u1", 2).first().map { it.id })
        assertEquals(setOf("2026-09-23"), dao.observeDatesSince("u1", "2026-09-23").first().toSet())
        // The cap counts the deleted entry too: it was paid for.
        assertEquals(3, dao.observeCountOn("u1", "2026-09-23").first())
    }

    private fun plant(id: String, gardenId: String) =
        PlantEntity(id, gardenId, itemId = "seed", gridX = 0, gridY = 0, growthStage = "seedling", health = "healthy")

    private fun entry(id: String, createdAt: String, day: String, deletedAt: String? = null): EntryEntity =
        GratitudeEntry(id, "thanks", "text", coinsAwarded = 5, entryDate = day, createdAt = createdAt, deletedAt = deletedAt)
            .toEntity("u1")
}
