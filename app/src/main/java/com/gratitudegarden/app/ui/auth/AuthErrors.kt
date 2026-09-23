package com.gratitudegarden.app.ui.auth

import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.exceptions.HttpRequestException
import java.io.IOException

/**
 * What the login and signup screens show when auth fails. Never the exception's own message:
 * supabase-kt folds the request URL and headers into it, which is noise for a user and
 * nothing a store reviewer should see on a login form.
 */
fun authErrorMessage(e: Throwable, fallback: String): String = authErrorMessage(
    code = (e as? AuthRestException)?.errorCode,
    offline = e is HttpRequestException || e is IOException, // Ktor timeouts are IOExceptions
    fallback = fallback,
)

/** The mapping itself, split out so it can be tested without building an HTTP response. */
internal fun authErrorMessage(code: AuthErrorCode?, offline: Boolean, fallback: String): String = when {
    offline -> "We can't reach the garden right now. Check your connection and try again."
    code == AuthErrorCode.InvalidCredentials -> "That email and password don't match."
    code == AuthErrorCode.EmailExists || code == AuthErrorCode.UserAlreadyExists ->
        "There's already a garden with that email. Try logging in instead."
    code == AuthErrorCode.WeakPassword -> "That password is too easy to guess. Try a longer one."
    code == AuthErrorCode.EmailAddressInvalid || code == AuthErrorCode.ValidationFailed ->
        "That email address doesn't look right."
    code == AuthErrorCode.EmailNotConfirmed -> "Check your inbox and confirm your email first."
    code == AuthErrorCode.OverRequestRateLimit || code == AuthErrorCode.OverEmailSendRateLimit ->
        "Too many tries. Wait a minute, then try again."
    code == AuthErrorCode.SignupDisabled -> "New gardens can't be created right now."
    code == AuthErrorCode.UserBanned -> "This account has been suspended."
    else -> "$fallback. Please try again."
}
