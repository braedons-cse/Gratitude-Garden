package com.gratitudegarden.app

import com.gratitudegarden.app.data.Garden
import com.gratitudegarden.app.data.GardenPlantRow
import com.gratitudegarden.app.data.UserStatsRow
import com.gratitudegarden.app.widget.WidgetMidnight
import com.gratitudegarden.app.widget.WidgetPlant
import com.gratitudegarden.app.widget.widgetStateFrom
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * [widgetStateFrom] — the home-screen widget reads the same rows as the Garden and must say
 * the same things — and [WidgetMidnight.nextMidnightMillis], when it next redraws on its own.
 */
class WidgetStateTest {

    private val today = LocalDate.of(2026, 7, 24)

    /** This month's free freeze already banked: [freezes] is all there is. */
    private fun stats(lastEntryDate: String, streak: Int, freezes: Int = 0) = UserStatsRow(
        currentStreak = streak,
        lastEntryDate = lastEntryDate,
        streakFreezes = freezes,
        freezeGrantMonth = "2026-07-01",
    )

    private val garden = Garden(id = "g1", name = "My Garden", gridRows = 6, gridCols = 5, activeBackdropItemId = "b1")
    private val slugs = mapOf("b1" to "backdrop.cherry_grove", "s1" to "seed.sunset_tulip")

    private fun plant(x: Int, y: Int, stage: String) =
        GardenPlantRow(id = "p$x$y", itemId = "s1", gridX = x, gridY = y, growthStage = stage, health = "healthy")

    private fun state(
        stats: UserStatsRow? = stats("2026-07-24", streak = 4),
        plants: List<GardenPlantRow> = emptyList(),
        usedToday: Int = 0,
        dailyCap: Int = 10,
    ) = widgetStateFrom(stats, today, garden, plants, slugs, usedToday, dailyCap)

    @Test
    fun aLiveStreakShowsItsDaysWithTheFlame() {
        val s = state()
        assertEquals(4, s.streakDays)
        assertFalse(s.streakHeld)
    }

    @Test
    fun aStreakFreezesCoverIsHeldNotBroken() {
        val s = state(stats = stats("2026-07-22", streak = 9, freezes = 1))
        assertEquals(9, s.streakDays)
        assertTrue(s.streakHeld)
    }

    @Test
    fun aBrokenStreakReadsZero() {
        assertEquals(0, state(stats = stats("2026-07-20", streak = 9)).streakDays)
    }

    @Test
    fun noStatsYetIsNoStreak() {
        assertEquals(0, state(stats = null).streakDays)
    }

    @Test
    fun thoughtsLeftCountsDownAndStopsAtZero() {
        assertEquals(7, state(usedToday = 3).thoughtsLeft)
        assertEquals(0, state(usedToday = 12).thoughtsLeft)
    }

    @Test
    fun theBackdropAndPlantsCarryTheirSlugs() {
        val s = state(plants = listOf(plant(2, 3, "sapling"), plant(0, 1, "seedling")))
        assertEquals("backdrop.cherry_grove", s.backdropSlug)
        // Row by row, whatever order they came in: the same garden is the same state.
        assertEquals(
            listOf(WidgetPlant(0, 1, "seed.sunset_tulip", 1), WidgetPlant(2, 3, "seed.sunset_tulip", 2)),
            s.plants,
        )
    }

    @Test
    fun noGardenYetFallsBackToTheDefaultPlot() {
        val s = widgetStateFrom(null, today, null, emptyList(), emptyMap(), 0, 10)
        assertEquals(6, s.gridRows)
        assertEquals(5, s.gridCols)
        assertNull(s.backdropSlug)
    }

    @Test
    fun theWidgetWakesAMinuteIntoTheNextLocalDay() {
        val zone = ZoneId.of("America/New_York")
        val now = ZonedDateTime.of(2026, 9, 28, 23, 57, 0, 0, zone)
        assertEquals(
            ZonedDateTime.of(2026, 9, 29, 0, 1, 0, 0, zone).toInstant(),
            Instant.ofEpochMilli(WidgetMidnight.nextMidnightMillis(now)),
        )
    }

    // Where a DST change skips midnight (Santiago's spring-forward, 00:00 → 01:00), the
    // next day starts at 01:00, and that's when "today" changes.
    @Test
    fun whereMidnightDoesNotExistItWakesAtTheDaysStart() {
        val zone = ZoneId.of("America/Santiago")
        val now = ZonedDateTime.of(2026, 9, 5, 22, 0, 0, 0, zone)
        assertEquals(
            ZonedDateTime.of(2026, 9, 6, 1, 1, 0, 0, zone).toInstant(),
            Instant.ofEpochMilli(WidgetMidnight.nextMidnightMillis(now)),
        )
    }
}
