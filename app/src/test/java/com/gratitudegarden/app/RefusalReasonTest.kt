package com.gratitudegarden.app

import com.gratitudegarden.app.ui.screens.refusalReason
import org.junit.Assert.assertEquals
import org.junit.Test

/** [refusalReason] — what the Journal says about an entry the server refused. */
class RefusalReasonTest {

    @Test
    fun theCapSaysToTryTomorrow() {
        assertEquals(
            "That day already had all its thoughts. Try again tomorrow, or discard this one.",
            refusalReason("daily entry cap (10) reached"),
        )
    }

    @Test
    fun aMissingEntryWasDeletedElsewhere() {
        assertEquals("This thought was deleted on another device.", refusalReason("entry not found"))
    }

    // The server's own words are never shown: they aren't written for people.
    @Test
    fun anythingElseGetsTheGeneralLine() {
        assertEquals("The garden didn't accept this thought.", refusalReason("entry id already used"))
        assertEquals("The garden didn't accept this thought.", refusalReason(null))
    }
}
