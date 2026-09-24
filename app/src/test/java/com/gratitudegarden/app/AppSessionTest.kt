package com.gratitudegarden.app

import com.gratitudegarden.app.data.AppSession
import com.gratitudegarden.app.data.appSessionFor
import com.gratitudegarden.app.data.jwtSubject
import com.gratitudegarden.app.data.userId
import io.github.jan.supabase.auth.status.RefreshFailureCause
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.IOException
import java.util.Base64
import kotlin.time.ExperimentalTime

/**
 * [appSessionFor] — whether the app shows the garden, the login form or a splash.
 *
 * The case that matters is offline with an older token: supabase-kt reports
 * `Initializing` or `RefreshFailure` and no user, and the app must still open the
 * signed-in user's cached garden rather than the login form.
 */
@OptIn(ExperimentalTime::class) // UserInfo / UserSession take kotlin.time.Instant
class AppSessionTest {

    @Test
    fun authenticatedIsSignedInAsItsUser() {
        val status = SessionStatus.Authenticated(session(user = UserInfo(aud = "authenticated", id = "u1")))
        assertEquals(AppSession.SignedIn("u1"), appSessionFor(status, storedUserId = null))
    }

    @Test
    fun notAuthenticatedIsSignedOutEvenWithAStaleStoredUser() {
        assertEquals(AppSession.SignedOut, appSessionFor(SessionStatus.NotAuthenticated(isSignOut = true), "u1"))
    }

    @Test
    fun initializingWithAStoredSessionOpensTheGarden() {
        assertEquals(AppSession.SignedIn("u1"), appSessionFor(SessionStatus.Initializing, "u1"))
    }

    @Test
    fun initializingWithNothingStoredIsASplashNotTheLoginForm() {
        assertEquals(AppSession.Loading, appSessionFor(SessionStatus.Initializing, null))
    }

    @Test
    fun refreshFailureKeepsTheStoredUserSignedIn() {
        @Suppress("DEPRECATION")
        val status = SessionStatus.RefreshFailure(RefreshFailureCause.NetworkError(IOException("offline")))
        assertEquals(AppSession.SignedIn("u1"), appSessionFor(status, "u1"))
        assertEquals(AppSession.SignedOut, appSessionFor(status, null))
    }

    @Test
    fun aSessionWithoutAStoredUserFallsBackToTheTokenSubject() {
        assertEquals("u2", session(user = null, accessToken = jwt("""{"sub":"u2","role":"authenticated"}""")).userId())
    }

    @Test
    fun jwtSubjectToleratesJunk() {
        assertNull(jwtSubject("not a jwt"))
        assertNull(jwtSubject(jwt("""{"role":"anon"}""")))
    }

    private fun session(user: UserInfo?, accessToken: String = "token") =
        UserSession(accessToken = accessToken, refreshToken = "r", expiresIn = 3600, tokenType = "bearer", user = user)

    /** An unsigned JWT with [payload]; base64url without padding, as real tokens are. */
    private fun jwt(payload: String): String {
        val enc = Base64.getUrlEncoder().withoutPadding()
        return enc.encodeToString("""{"alg":"none"}""".toByteArray()) + "." +
            enc.encodeToString(payload.toByteArray()) + ".sig"
    }
}
