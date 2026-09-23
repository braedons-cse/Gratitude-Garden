package com.gratitudegarden.app

import com.gratitudegarden.app.data.entryDay
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * [entryDay] — the day the server will date the next entry. It must match
 * `submit_gratitude_entry`'s `greatest(local date, last_entry_date)`, or the "thoughts
 * left today" chip counts a different day than the cap does.
 */
class EntryDayTest {

    private val today = LocalDate.of(2026, 9, 23)

    @Test
    fun normallyTheLocalDate() {
        assertEquals(today, entryDay(today, "2026-09-22"))
        assertEquals(today, entryDay(today, "2026-09-23"))
        assertEquals(today, entryDay(today, null))
    }

    // After a zone change east and back, the last entry sits on tomorrow and the server
    // keeps dating entries there; counting the local date would count the wrong day.
    @Test
    fun neverBeforeTheLastEntry() {
        assertEquals(LocalDate.of(2026, 9, 24), entryDay(today, "2026-09-24"))
    }

    @Test
    fun unparseableLastDateFallsBackToLocal() {
        assertEquals(today, entryDay(today, "garbage"))
    }
}
