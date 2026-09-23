package com.gratitudegarden.app.util

/** Minimum password length. Must match the Supabase Auth setting, which is the real gate. */
internal const val PASSWORD_MIN_LENGTH = 8

/**
 * Mirrors the Supabase Auth password policy so the signup form can tell the user what is
 * wrong *while they type*, instead of letting them submit and surfacing a raw GoTrue error
 * message after a failed round trip.
 *
 * The server is the authority, not this function -- it is enabled in the Supabase dashboard
 * under Authentication -> Providers -> Email (minimum length 8, plus lowercase / uppercase /
 * digits / symbols required). This is UX, not a security boundary: bypassing it only earns
 * the server rejection it was trying to explain. Keep the two in sync; if they ever drift,
 * the server error still renders, so the failure mode is today's behaviour rather than a
 * silently accepted weak password.
 *
 * "Symbol" is deliberately defined as *not a letter, not a digit, not whitespace* rather than
 * as an explicit punctuation list. GoTrue matches against a fixed ASCII set, and an explicit
 * list here risks being narrower than the server's (rejecting a password the server would
 * accept). Whitespace is excluded because it is not in GoTrue's symbol set, so counting a
 * space as a symbol would pass the client and then fail the server -- the exact round trip
 * this exists to prevent.
 *
 * @return null when the password satisfies every rule, otherwise a short phrase naming what
 *   is still missing, ready to drop into a sentence.
 */
internal fun passwordProblem(password: String): String? {
    if (password.length < PASSWORD_MIN_LENGTH) {
        return "at least $PASSWORD_MIN_LENGTH characters"
    }

    val missing = buildList {
        if (password.none { it.isLowerCase() }) add("a lowercase letter")
        if (password.none { it.isUpperCase() }) add("an uppercase letter")
        if (password.none { it.isDigit() }) add("a number")
        if (password.none { !it.isLetterOrDigit() && !it.isWhitespace() }) add("a symbol")
    }

    return when (missing.size) {
        0 -> null
        1 -> missing[0]
        else -> missing.dropLast(1).joinToString(", ") + " and " + missing.last()
    }
}
