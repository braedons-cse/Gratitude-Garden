package com.gratitudegarden.app

import com.gratitudegarden.app.data.reconnections
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [reconnections] — when the repository refreshes because the network came back.
 *
 * Starting online must not count (the screens refresh on creation anyway), and the
 * repeated `true`s a network callback sends as capabilities change must not refresh again.
 */
class ReconnectionsTest {

    private fun count(vararg states: Boolean) = runBlocking { flowOf(*states.toTypedArray()).reconnections().toList().size }

    @Test
    fun startingOnlineIsNotAReconnection() {
        assertEquals(0, count(true))
        assertEquals(0, count(true, true, true))
    }

    @Test
    fun offlineThenOnlineCountsOnce() {
        assertEquals(1, count(false, true))
        assertEquals(1, count(true, false, true))
    }

    @Test
    fun repeatedOnlineUpdatesDoNotRefreshAgain() {
        assertEquals(1, count(false, true, true, true))
    }

    @Test
    fun everyDropAndReturnCounts() {
        assertEquals(2, count(true, false, true, false, false, true))
    }

    @Test
    fun stayingOfflineNeverFires() {
        assertEquals(0, count(false, false))
    }
}
