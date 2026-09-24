package com.gratitudegarden.app

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gratitudegarden.app.data.local.GardenDatabase
import com.gratitudegarden.app.data.local.StatsEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Smoke test for the KSP-generated Room code: the database opens, and a row written
 * through the DAO comes back through the observing query.
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
}
