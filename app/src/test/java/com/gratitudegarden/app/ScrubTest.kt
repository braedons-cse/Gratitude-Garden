package com.gratitudegarden.app

import com.gratitudegarden.app.util.scrub
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [scrub] — what every crash report goes through before it leaves the device. The inputs are
 * shaped like the messages supabase-kt and Postgres actually produce.
 */
class ScrubTest {

    private val uid = "3f2b8c1e-9a4d-4e7f-b0c2-5d6e7f8a9b0c"
    private val jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIzZjJiIn0.c2lnbmF0dXJl"

    @Test
    fun restExceptionLosesHeadersAndIdsButKeepsTheError() {
        val message = """
            PGRST116
            JSON object requested, multiple (or no) rows returned
            URL: https://abc.supabase.co/rest/v1/profiles?id=eq.$uid&select=*
            Headers: [Authorization=[Bearer $jwt], apikey=[$jwt]]
            Http Method: GET
        """.trimIndent()
        val out = scrub(message)!!
        assertTrue(out.startsWith("PGRST116\nJSON object requested"))
        assertTrue("https://abc.supabase.co/rest/v1/profiles?[query]" in out)
        assertTrue("Http Method: GET" in out)
        assertFalse("Headers" in out)
        assertFalse(uid in out)
        assertFalse("eyJ" in out)
    }

    @Test
    fun aRejectedRowIsNotQuoted() {
        val message = "new row for relation \"gratitude_entries\" violates check constraint\n" +
            "Failing row contains ($uid, My sister called\nand we laughed for an hour, 2026-10-01).\n" +
            "URL: https://abc.supabase.co/rest/v1/rpc/edit_gratitude_entry"
        val out = scrub(message)!!
        assertTrue("Failing row contains [row]" in out)
        assertTrue("violates check constraint" in out)
        assertTrue("rpc/edit_gratitude_entry" in out)
        assertFalse("sister" in out)
        assertFalse("laughed" in out)
    }

    @Test
    fun keyValuesAreNotQuoted() {
        val out = scrub("duplicate key value violates unique constraint\nKey (email)=(someone@example.com) already exists.")!!
        assertEquals("duplicate key value violates unique constraint\nKey (email)=[value]", out)
    }

    @Test
    fun photoPathsAndBareTokensAreRedacted() {
        val out = scrub("HTTP request to https://abc.supabase.co/storage/v1/object/entry-photos/$uid/$uid/$uid.jpg (PUT) failed with message: Bearer abc.def")!!
        assertEquals(
            "HTTP request to https://abc.supabase.co/storage/v1/object/entry-photos/[id]/[id]/[id].jpg (PUT) failed with message: Bearer [token]",
            out,
        )
    }

    @Test
    fun emailsAreRedacted() {
        assertEquals("no account for [email]", scrub("no account for someone.else+gg@example.co.uk"))
    }

    @Test
    fun plainMessagesPassThrough() {
        assertEquals("Index 3 out of bounds for length 3", scrub("Index 3 out of bounds for length 3"))
        assertNull(scrub(null))
    }
}
