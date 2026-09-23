package com.gratitudegarden.app

import com.gratitudegarden.app.ui.auth.authErrorMessage
import io.github.jan.supabase.auth.exception.AuthErrorCode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.IOException

/**
 * [authErrorMessage] — what the auth screens show on failure.
 *
 * Previously the exception's own message was shown, and supabase-kt folds the request URL
 * and headers into it, so a mistyped password put an HTTP dump under the login form.
 */
class AuthErrorMessageTest {

    private val fallback = "We couldn't log you in"

    @Test
    fun knownAuthCodesGetPlainLanguage() {
        assertEquals(
            "That email and password don't match.",
            authErrorMessage(AuthErrorCode.InvalidCredentials, offline = false, fallback),
        )
        assertEquals(
            "There's already a garden with that email. Try logging in instead.",
            authErrorMessage(AuthErrorCode.UserAlreadyExists, offline = false, fallback),
        )
    }

    @Test
    fun networkFailureWinsOverAnyCode() {
        val msg = authErrorMessage(AuthErrorCode.InvalidCredentials, offline = true, fallback)
        assertEquals("We can't reach the garden right now. Check your connection and try again.", msg)
    }

    @Test
    fun unmappedFailuresFallBackWithoutLeakingTheRawMessage() {
        val raw = IllegalStateException("invalid_grant\nURL: https://example.supabase.co/auth/v1/token\nHeaders: {...}")
        val msg = authErrorMessage(raw, fallback)
        assertEquals("We couldn't log you in. Please try again.", msg)
        assertFalse(msg.contains("URL"))
    }

    @Test
    fun ioExceptionsCountAsOffline() {
        assertEquals(
            "We can't reach the garden right now. Check your connection and try again.",
            authErrorMessage(IOException("timeout"), fallback),
        )
    }
}
