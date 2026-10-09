package com.gratitudegarden.app.data

import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserSession
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Base64

/**
 * Whose data the app is showing, if anyone's. Differs from the auth library's
 * [SessionStatus] in one way that matters offline: a stored session whose token can't be
 * refreshed right now still counts as signed in.
 */
sealed interface AppSession {
    /** The stored session hasn't been read yet: show a splash, not the login form. */
    data object Loading : AppSession

    data object SignedOut : AppSession

    data class SignedIn(val userId: String) : AppSession
}

/**
 * Map the auth library's status to an [AppSession]. [storedUserId] is the user of the
 * session saved on the device, consulted only while the library is initializing or
 * retrying a refresh.
 *
 * supabase-kt reports `Initializing` for as long as it fails to refresh a token that is
 * near expiry, and `RefreshFailure` once the token has expired, retrying either way. Both
 * happen on every offline launch with an older token. Treating them as signed out would
 * put a user who is merely offline on the login form, with a journal they can't open. A
 * refresh token the server actually rejects makes the library clear the session, which
 * arrives here as `NotAuthenticated`; so does an account the repository finds deleted.
 * Every `SignedOut` erases the device's copy (`GardenRepository`'s `init`).
 */
fun appSessionFor(status: SessionStatus, storedUserId: String?): AppSession = when (status) {
    is SessionStatus.Authenticated -> status.session.userId()?.let { AppSession.SignedIn(it) } ?: AppSession.SignedOut
    is SessionStatus.NotAuthenticated -> AppSession.SignedOut
    SessionStatus.Initializing -> storedUserId?.let { AppSession.SignedIn(it) } ?: AppSession.Loading
    is SessionStatus.RefreshFailure -> storedUserId?.let { AppSession.SignedIn(it) } ?: AppSession.SignedOut
}

/** The session's user id: from the stored user when present, else the token's `sub` claim. */
fun UserSession.userId(): String? = user?.id ?: jwtSubject(accessToken)

/** The `sub` claim of a JWT, without verifying it. Only used to recognise our own stored session. */
internal fun jwtSubject(jwt: String): String? = runCatching {
    val payload = jwt.split('.')[1]
    val json = Base64.getUrlDecoder().decode(payload).decodeToString()
    Json.parseToJsonElement(json).jsonObject["sub"]?.jsonPrimitive?.content
}.getOrNull()
