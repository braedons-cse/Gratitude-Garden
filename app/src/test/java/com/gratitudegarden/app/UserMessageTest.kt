package com.gratitudegarden.app

import com.gratitudegarden.app.ui.OFFLINE_MESSAGE
import com.gratitudegarden.app.ui.userMessage
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException

/**
 * [userMessage] — the sentence every screen shows when a call fails. The raw exception text
 * (which carries the request URL and headers) must never come through.
 */
class UserMessageTest {

    private val known: (String) -> String? = { m ->
        if ("insufficient coins" in m) "Not enough coins yet." else null
    }

    @Test
    fun intentionalServerErrorsKeepTheirFriendlyCopy() {
        val e = IllegalStateException("insufficient coins (need 10, have 2)\nURL: https://x/rest/v1/rpc/water_plant")
        assertEquals("Not enough coins yet.", userMessage(e, "Couldn't water your plant", known))
    }

    @Test
    fun unrecognisedErrorsGetTheFallbackNotTheRawText() {
        val e = IllegalStateException("PGRST301\nURL: https://x/rest/v1/rpc/water_plant\nHeaders: {...}")
        assertEquals("Couldn't water your plant", userMessage(e, "Couldn't water your plant", known))
    }

    @Test
    fun networkFailuresSayOffline() {
        assertEquals(OFFLINE_MESSAGE, userMessage(IOException("timeout"), "Couldn't load your garden", known))
    }
}
