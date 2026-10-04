package com.gratitudegarden.app.util

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import java.security.MessageDigest
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Checks a new password against the passwords known from data breaches (Have I Been Pwned),
 * the check Supabase only offers on its Pro plan (roadmap 0.5).
 *
 * The password never leaves the device. Only the first 5 hex characters of its SHA-1 hash
 * are sent; the service answers with every breached hash sharing that prefix (several hundred,
 * padded with fake rows so the response size says nothing), and the match happens here.
 *
 * This runs on the client, and that is enough for this check alone: it protects users from
 * reusing a leaked password, so someone who gets past it only harms themselves. Everything
 * else that matters is enforced by the server. For the same reason it **fails open**: no
 * network, a slow answer or an error skips it rather than blocking sign-up.
 */
class PwnedPasswords(private val client: HttpClient) {

    /** How many breaches include [password]; null when the check couldn't run. */
    suspend fun timesSeen(password: String): Int? = withTimeoutOrNull(5.seconds) {
        val (prefix, suffix) = hashParts(password)
        try {
            val response = client.get("https://api.pwnedpasswords.com/range/$prefix") {
                header("Add-Padding", "true")
            }
            if (response.status.isSuccess()) breachCount(response.bodyAsText(), suffix) else null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
    }
}

/** Thrown by sign-up when the chosen password appears in a known breach. */
class BreachedPasswordException : Exception("password found in a known breach")

/** The upper-case SHA-1 of [password], split into the 5 characters sent and the 35 kept. */
internal fun hashParts(password: String): Pair<String, String> {
    val hex = MessageDigest.getInstance("SHA-1")
        .digest(password.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02X".format(it) }
    return hex.take(5) to hex.drop(5)
}

/**
 * Reads a range response, one `SUFFIX:COUNT` per line, and returns the count for [suffix],
 * or 0 when it isn't there. Padding rows carry a count of 0, so they never match as breaches.
 */
internal fun breachCount(body: String, suffix: String): Int =
    body.lineSequence()
        .map { it.trim().split(':') }
        .firstOrNull { it.size == 2 && it[0].equals(suffix, ignoreCase = true) }
        ?.get(1)?.toIntOrNull()
        ?: 0
