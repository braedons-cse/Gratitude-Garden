package com.gratitudegarden.app

import com.gratitudegarden.app.util.breachCount
import com.gratitudegarden.app.util.hashParts
import org.junit.Assert.assertEquals
import org.junit.Test

/** The pure halves of the breached-password check: what is sent, and how the answer is read. */
class PwnedPasswordsTest {

    @Test
    fun `sends the first 5 characters of the SHA-1 and keeps the other 35`() {
        // SHA-1("password") = 5BAA61E4C9B93F3F0682250B6CF8331B7EE68FD8
        assertEquals("5BAA6" to "1E4C9B93F3F0682250B6CF8331B7EE68FD8", hashParts("password"))
    }

    @Test
    fun `hashes the password as UTF-8`() {
        // SHA-1 of "é" in UTF-8 (C3 A9); a platform-default charset would give another hash.
        assertEquals("BF15B" to "E717AC1B080B4F1C456692825891FF5073D", hashParts("é"))
    }

    @Test
    fun `finds the count for a matching suffix`() {
        val body = "0018A45C4D1DEF81644B54AB7F969B88D65:1\r\n" +
            "1E4C9B93F3F0682250B6CF8331B7EE68FD8:52256179\r\n" +
            "011053FD0102E94D6AE2F8B83D76FAF94F6:2\r\n"
        assertEquals(52256179, breachCount(body, "1E4C9B93F3F0682250B6CF8331B7EE68FD8"))
    }

    @Test
    fun `ignores case`() {
        assertEquals(3, breachCount("1e4c9b93f3f0682250b6cf8331b7ee68fd8:3", "1E4C9B93F3F0682250B6CF8331B7EE68FD8"))
    }

    @Test
    fun `a padding row is not a breach`() {
        assertEquals(0, breachCount("1E4C9B93F3F0682250B6CF8331B7EE68FD8:0\r\n", "1E4C9B93F3F0682250B6CF8331B7EE68FD8"))
    }

    @Test
    fun `a suffix that isn't listed has no breaches`() {
        assertEquals(0, breachCount("0018A45C4D1DEF81644B54AB7F969B88D65:1\r\n", "1E4C9B93F3F0682250B6CF8331B7EE68FD8"))
        assertEquals(0, breachCount("", "1E4C9B93F3F0682250B6CF8331B7EE68FD8"))
    }
}
