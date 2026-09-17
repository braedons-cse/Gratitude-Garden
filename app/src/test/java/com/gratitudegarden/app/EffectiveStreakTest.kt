package com.gratitudegarden.app

import com.gratitudegarden.app.data.UserStatsRow
import com.gratitudegarden.app.data.effectiveStreakOn
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * Non-UI unit test #2 — [effectiveStreakOn].
 *
 * The stored `current_streak` is only rewritten when an entry is submitted, so
 * between submits it can be stale. [effectiveStreakOn] decides whether that stored
 * run is still alive: it survives only while the last entry was today or yesterday,
 * otherwise the effective streak reads 0.
 *
 * These tests are possible *because* of the optimization — the clock is injected via
 * `today` instead of being read from `LocalDate.now(UTC)` inside the function, so the
 * outcome is deterministic and doesn't depend on the day the suite happens to run.
 */
class EffectiveStreakTest {

    private val today = LocalDate.of(2026, 7, 24)

    private fun stats(lastEntryDate: String?, streak: Int) = UserStatsRow(
        totalEntries = 42,
        currentStreak = streak,
        longestStreak = streak,
        lastEntryDate = lastEntryDate,
    )

    // Non-UI unit test #2 (primary): a run stays alive when logged today or
    // yesterday, and is considered broken (0) once a full day is missed.
    @Test
    fun streakSurvivesTodayAndYesterdayButBreaksAfterAMissedDay() {
        assertEquals("last entry today keeps the run", 7, stats("2026-07-24", 7).effectiveStreakOn(today))
        assertEquals("last entry yesterday keeps the run", 7, stats("2026-07-23", 7).effectiveStreakOn(today))
        assertEquals("two days stale breaks the run", 0, stats("2026-07-22", 7).effectiveStreakOn(today))
    }

    // No entry ever logged → nothing to keep alive.
    @Test
    fun noLastEntryDateIsZero() {
        assertEquals(0, stats(null, 9).effectiveStreakOn(today))
    }

    // A value that can't be parsed as a date is treated as "no live run" rather than
    // crashing (runCatching swallows the parse failure).
    @Test
    fun malformedLastEntryDateIsZero() {
        assertEquals(0, stats("not-a-date", 9).effectiveStreakOn(today))
    }

    // A future-dated entry (clock skew / different timezone write) is not "today or
    // yesterday", so it does not resurrect a streak.
    @Test
    fun futureEntryDateDoesNotCountAsAlive() {
        assertEquals(0, stats("2026-07-25", 5).effectiveStreakOn(today))
    }
}
