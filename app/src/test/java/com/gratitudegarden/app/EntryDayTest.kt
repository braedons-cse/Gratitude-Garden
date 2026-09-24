package com.gratitudegarden.app

import com.gratitudegarden.app.data.entryDay
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * [entryDay] — the day an entry counts for. It must match `submit_gratitude_entry`'s
 * `(v_written at time zone v_tz)::date`, or the "thoughts left today" chip counts a
 * different day than the cap does.
 */
class EntryDayTest {

    private val newYork = ZoneId.of("America/New_York")

    @Test
    fun theLocalDateOfTheWriteTime() {
        // 23:30 in New York on the 23rd is already the 24th in UTC.
        val lateEvening = Instant.parse("2026-09-24T03:30:00Z")
        assertEquals(LocalDate.of(2026, 9, 23), entryDay(lateEvening, newYork))
        assertEquals(LocalDate.of(2026, 9, 24), entryDay(lateEvening, ZoneId.of("UTC")))
    }

    // Written offline just before midnight, synced the next morning: the write time
    // decides, so the entry keeps the night it was written.
    @Test
    fun anEntryWrittenBeforeMidnightKeepsItsDay() {
        val beforeMidnight = Instant.parse("2026-09-24T03:59:00Z") // 23:59 EDT on the 23rd
        assertEquals(LocalDate.of(2026, 9, 23), entryDay(beforeMidnight, newYork))
    }
}
