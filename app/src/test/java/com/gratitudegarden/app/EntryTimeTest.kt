package com.gratitudegarden.app

import com.gratitudegarden.app.ui.screens.timeOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * [timeOf] — the journal's per-entry time label.
 *
 * `created_at` comes back from Postgres in UTC. The original implementation called
 * `OffsetDateTime.toLocalTime()`, which keeps the UTC offset, so an entry written at
 * 2:15 AM in New York was labelled 6:15 AM. The label must be in the device's zone.
 * Assertions check the digits only, since the AM/PM marker follows the JVM locale.
 */
class EntryTimeTest {

    private val createdAt = "2026-09-23T06:15:42.123456+00:00"

    @Test
    fun utcTimestampIsShownInTheDeviceZone() {
        assertTrue(timeOf(createdAt, ZoneId.of("America/New_York")).startsWith("2:15"))
        assertTrue(timeOf(createdAt, ZoneId.of("Asia/Tokyo")).startsWith("3:15"))
        assertTrue(timeOf(createdAt, ZoneId.of("UTC")).startsWith("6:15"))
    }

    @Test
    fun unparseableTimestampFallsBackToEmpty() {
        assertEquals("", timeOf("not a timestamp", ZoneId.of("UTC")))
    }
}
