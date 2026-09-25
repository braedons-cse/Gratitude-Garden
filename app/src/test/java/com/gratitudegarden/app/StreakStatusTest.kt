package com.gratitudegarden.app

import com.gratitudegarden.app.data.StreakStatus
import com.gratitudegarden.app.data.UserStatsRow
import com.gratitudegarden.app.data.freezesOn
import com.gratitudegarden.app.data.streakOn
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * Non-UI unit test #2 — [streakOn] and [freezesOn].
 *
 * The stored `current_streak` is only rewritten when an entry is submitted, so between
 * submits it can be stale. [streakOn] decides whether that stored run is still alive: it
 * survives while the last entry was today or yesterday, or while the streak freezes on hand
 * cover every day missed since (the next entry spends them). Otherwise it reads 0.
 *
 * These tests are possible *because* the clock is injected via `today` instead of being
 * read inside the function, so the outcome doesn't depend on the day the suite runs.
 */
class StreakStatusTest {

    private val today = LocalDate.of(2026, 7, 24)

    /** By default this month's free freeze is already banked and spent: no freezes. */
    private fun stats(
        lastEntryDate: String?,
        streak: Int,
        freezes: Int = 0,
        grantMonth: String? = "2026-07-01",
    ) = UserStatsRow(
        totalEntries = 42,
        currentStreak = streak,
        longestStreak = streak,
        lastEntryDate = lastEntryDate,
        streakFreezes = freezes,
        freezeGrantMonth = grantMonth,
    )

    // Non-UI unit test #2 (primary): a run stays alive when logged today or
    // yesterday, and is considered broken (0) once a full day is missed.
    @Test
    fun streakSurvivesTodayAndYesterdayButBreaksAfterAMissedDay() {
        assertEquals("last entry today keeps the run", 7, stats("2026-07-24", 7).streakOn(today).days)
        assertEquals("last entry yesterday keeps the run", 7, stats("2026-07-23", 7).streakOn(today).days)
        assertEquals("two days stale breaks the run", 0, stats("2026-07-22", 7).streakOn(today).days)
    }

    // No entry ever logged → nothing to keep alive.
    @Test
    fun noLastEntryDateIsZero() {
        assertEquals(0, stats(null, 9).streakOn(today).days)
    }

    // A value that can't be parsed as a date is treated as "no live run" rather than
    // crashing (runCatching swallows the parse failure).
    @Test
    fun malformedLastEntryDateIsZero() {
        assertEquals(0, stats("not-a-date", 9).streakOn(today).days)
    }

    // One day ahead is legitimate: an entry is dated in the zone it was written in, so
    // after flying west the last entry can sit on tomorrow's local date.
    @Test
    fun entryDatedTomorrowStillCountsAsAlive() {
        assertEquals(5, stats("2026-07-25", 5).streakOn(today).days)
    }

    // Anything further out isn't a zone effect, so it doesn't resurrect a streak.
    @Test
    fun entryDatedTwoDaysAheadDoesNotCountAsAlive() {
        assertEquals(0, stats("2026-07-26", 5).streakOn(today).days)
    }

    // ── Freezes ──────────────────────────────────────────────────────

    @Test
    fun oneMissedDayIsHeldByOneFreeze() {
        assertEquals(StreakStatus(7, heldByFreeze = true, freezes = 1), stats("2026-07-22", 7, freezes = 1).streakOn(today))
    }

    @Test
    fun twoMissedDaysNeedTwoFreezes() {
        assertEquals("one isn't enough: the server won't half-bridge", 0, stats("2026-07-21", 7, freezes = 1).streakOn(today).days)
        assertEquals(StreakStatus(7, heldByFreeze = true, freezes = 2), stats("2026-07-21", 7, freezes = 2).streakOn(today))
    }

    @Test
    fun threeMissedDaysAreMoreThanAnyoneCanHold() {
        assertEquals(0, stats("2026-07-20", 7, freezes = 2).streakOn(today).days)
    }

    @Test
    fun anUnbrokenRunIsNotHeld() {
        assertEquals(StreakStatus(7, heldByFreeze = false, freezes = 2), stats("2026-07-23", 7, freezes = 2).streakOn(today))
    }

    @Test
    fun aNewMonthBringsAFreeFreeze() {
        assertEquals("never granted", 1, stats(null, 0, grantMonth = null).freezesOn(today))
        assertEquals("last month's banked, this month's not yet", 2, stats(null, 0, freezes = 1, grantMonth = "2026-06-01").freezesOn(today))
        assertEquals("this month's already banked", 1, stats(null, 0, freezes = 1, grantMonth = "2026-07-01").freezesOn(today))
    }

    @Test
    fun theFreeFreezeNeverTakesTheCountPastTheLimit() {
        assertEquals(2, stats(null, 0, freezes = 2, grantMonth = "2026-06-01").freezesOn(today))
    }

    @Test
    fun thisMonthsFreeFreezeCanHoldAStreak() {
        // Nothing banked, but July's free one hasn't been claimed yet.
        assertEquals(StreakStatus(4, heldByFreeze = true, freezes = 1), stats("2026-07-22", 4, grantMonth = "2026-06-01").streakOn(today))
    }
}
