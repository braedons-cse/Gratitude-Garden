package com.gratitudegarden.app

import com.gratitudegarden.app.data.local.epochMicros
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [epochMicros] — the ordering key for cached journal entries.
 *
 * Postgres trims trailing zeros from the fraction, so the server's `created_at` strings
 * vary in length, and entries written on the device will use `Z` rather than `+00:00`.
 * The key has to order all of those by instant, which comparing the strings does not.
 */
class EpochMicrosTest {

    @Test
    fun keepsFullMicrosecondPrecision() {
        assertEquals(1_000_001L, epochMicros("1970-01-01T00:00:01.000001+00:00"))
    }

    @Test
    fun trimmedFractionsParseToTheSameInstant() {
        assertEquals(
            epochMicros("2026-09-23T06:15:42.500000+00:00"),
            epochMicros("2026-09-23T06:15:42.5+00:00"),
        )
        assertEquals(
            epochMicros("2026-09-23T06:15:42.000000+00:00"),
            epochMicros("2026-09-23T06:15:42+00:00"),
        )
    }

    @Test
    fun zAndOffsetFormsOrderByInstantNotByText() {
        val server = "2026-09-23T06:15:42.1+00:00"
        val device = "2026-09-23T06:15:42Z" // earlier, but sorts later as text
        assertTrue(device > server)
        assertTrue(epochMicros(device) < epochMicros(server))
    }

    @Test
    fun nonUtcOffsetsNormalize() {
        assertEquals(
            epochMicros("2026-09-23T06:15:42+00:00"),
            epochMicros("2026-09-23T02:15:42-04:00"),
        )
    }
}
