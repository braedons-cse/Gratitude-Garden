package com.gratitudegarden.app

import com.gratitudegarden.app.util.passwordProblem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [passwordProblem] mirrors the Supabase Auth policy (min length 8, plus lowercase /
 * uppercase / digits / symbols). It is pure, so it tests without a device or a network.
 * See docs/unit-test-optimizations.md for why this lives in src/test rather than androidTest.
 */
class PasswordProblemTest {

    @Test
    fun `accepts a password meeting every rule`() {
        assertNull(passwordProblem("Gratitude1!"))
    }

    @Test
    fun `length is reported before character classes`() {
        // "Aa1!" satisfies all four classes but is too short -- the length message wins, so
        // the user is not told to add characters they already have.
        assertEquals("at least 8 characters", passwordProblem("Aa1!"))
        assertEquals("at least 8 characters", passwordProblem(""))
    }

    @Test
    fun `names the single missing character class`() {
        assertEquals("an uppercase letter", passwordProblem("gratitude1!"))
        assertEquals("a lowercase letter", passwordProblem("GRATITUDE1!"))
        assertEquals("a number", passwordProblem("Gratitude!"))
        assertEquals("a symbol", passwordProblem("Gratitude1"))
    }

    @Test
    fun `joins several missing classes into a readable phrase`() {
        assertEquals("a number and a symbol", passwordProblem("Gratitude"))
        assertEquals(
            "an uppercase letter, a number and a symbol",
            passwordProblem("gratitude"),
        )
    }

    @Test
    fun `whitespace does not count as a symbol`() {
        // GoTrue's symbol set is punctuation only. Counting a space here would pass the
        // client and then fail the server -- the exact round trip this function prevents.
        assertEquals("a symbol", passwordProblem("Gratitude 1"))
    }

    @Test
    fun `non-ascii punctuation counts as a symbol`() {
        // Defined as "not letter, not digit, not whitespace" so the client is never stricter
        // than the server.
        assertNull(passwordProblem("Gratitude1¡"))
    }
}
