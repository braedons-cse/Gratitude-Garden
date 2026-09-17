package com.gratitudegarden.app

import com.gratitudegarden.app.notifications.ReminderNotifications
import com.gratitudegarden.app.notifications.ReminderNotifications.pickMessageIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Non-UI unit test #1 — [ReminderNotifications.pickMessageIndex].
 *
 * Verifies the daily-reminder message picker's contract: it never repeats the
 * previously shown message, always returns a valid index, and (given enough draws)
 * can reach every *other* message. These are invariants of the *behaviour*, not of
 * any particular implementation, so the same assertions hold before and after the
 * allocation-free optimization (see docs/unit-test-optimizations.md).
 */
class PickMessageIndexTest {

    private val indices = ReminderNotifications.MESSAGES.indices

    // Non-UI unit test #1 (primary): the reminder never shows the same variant twice
    // in a row, and every returned value is a real message index.
    @Test
    fun neverRepeatsAndStaysInRange() {
        val rng = Random(1234)
        for (last in indices) {
            repeat(2_000) {
                val next = pickMessageIndex(last, rng)
                assertNotEquals("must not repeat the previous message", last, next)
                assertTrue("index $next out of range $indices", next in indices)
            }
        }
    }

    // Every message except `last` is reachable — the picker isn't accidentally
    // pinned to a subset.
    @Test
    fun reachesEveryOtherMessage() {
        val rng = Random(999)
        val last = 2
        val seen = buildSet { repeat(5_000) { add(pickMessageIndex(last, rng)) } }
        assertEquals(indices.toSet() - last, seen)
    }

    // A `lastIndex` of -1 ("nothing shown yet") means every message is fair game.
    @Test
    fun minusOneAllowsEveryMessage() {
        val rng = Random(7)
        val seen = buildSet { repeat(5_000) { add(pickMessageIndex(-1, rng)) } }
        assertEquals(indices.toSet(), seen)
    }
}
