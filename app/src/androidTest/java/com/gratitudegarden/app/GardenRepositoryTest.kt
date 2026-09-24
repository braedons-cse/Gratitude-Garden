package com.gratitudegarden.app

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gratitudegarden.app.data.AppSession
import com.gratitudegarden.app.data.ConnectivityMonitor
import com.gratitudegarden.app.data.GardenRepository
import com.gratitudegarden.app.data.local.GardenDatabase
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.auth.MemorySessionManager
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.serializer.KotlinXSerializer
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import kotlin.time.ExperimentalTime

/**
 * The real [GardenRepository] against a fake Supabase ([FakeSupabase]) and an in-memory
 * Room: what reaches the local copy, what doesn't, and which requests go out.
 */
@RunWith(AndroidJUnit4::class)
class GardenRepositoryTest {
    // A stuck refresh or sync should fail its test, not hang the whole run.
    @get:Rule
    val timeout: Timeout = Timeout.seconds(30)

    private lateinit var fake: FakeSupabase
    private lateinit var db: GardenDatabase
    private lateinit var client: SupabaseClient
    private lateinit var repo: GardenRepository

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        fake = FakeSupabase()
        db = Room.inMemoryDatabaseBuilder(context, GardenDatabase::class.java).build()
        client = createSupabaseClient("https://fake.supabase.co", "anon-key") {
            httpEngine = fake.engine
            // As in AppContainer: partial DTOs.
            defaultSerializer = KotlinXSerializer(Json { ignoreUnknownKeys = true; coerceInputValues = true })
            install(Auth) {
                autoLoadFromStorage = false
                autoSaveToStorage = false
                alwaysAutoRefresh = false
                sessionManager = MemorySessionManager()
            }
            install(Postgrest)
        }
        repo = GardenRepository(client, db, ConnectivityMonitor(context))
    }

    @After
    fun tearDown() = db.close()

    /** Sign in as the fake's user and let the first refresh finish; by default, forget its requests. */
    @OptIn(ExperimentalTime::class)
    private suspend fun signIn(forgetRequests: Boolean = true) {
        // No awaitInitialization(): with autoLoadFromStorage off, supabase-kt stays at
        // Initializing until a session is imported, so it would wait forever.
        val user = UserInfo(aud = "authenticated", id = fake.userId)
        client.auth.importSession(
            UserSession(accessToken = "token", refreshToken = "r", expiresIn = 3600, tokenType = "bearer", user = user),
            autoRefresh = false,
        )
        withTimeout(5_000) { repo.session.first { it is AppSession.SignedIn } }
        // Joins the refresh that regaining a token starts on its own, if it's running.
        repo.refreshAll()
        if (forgetRequests) fake.requests.clear()
    }

    @Test
    fun refreshFillsTheLocalCopyForEveryScreen() = runBlocking {
        fake.addEntries(3)
        signIn()

        assertEquals("Tester", repo.observeProfile().first()?.displayName)
        assertEquals(12, repo.observeWallet().first()?.balance)
        assertEquals(3, repo.observeStats().first()?.totalEntries)
        assertEquals("My Garden", repo.observeGarden().first()?.name)
        assertEquals(listOf("p1", "p2"), repo.observePlants().first().map { it.id }.sorted())
        assertEquals(listOf("i1", "i2"), repo.observeCatalog().first().map { it.id })
        assertEquals(setOf("i1"), repo.observeInventory().first())
        assertEquals(listOf("e00002", "e00001", "e00000"), repo.observeEntries(10).first().map { it.id })
        assertEquals(3, repo.observeEntriesTodayCount().first())
    }

    @Test
    fun aFailedRefreshLeavesTheLocalCopyAlone() = runBlocking {
        signIn()
        fake.balance = 99
        fake.gardenName = null
        fake.plantIds.clear()
        fake.offline = true

        try {
            repo.refreshAll(force = true)
            fail("refresh should fail offline")
        } catch (_: Exception) {
        }

        // Stale beats empty: nothing on screen changes.
        assertEquals(12, repo.observeWallet().first()?.balance)
        assertEquals("My Garden", repo.observeGarden().first()?.name)
        assertEquals(2, repo.observePlants().first().size)
    }

    @Test
    fun entrySyncPagesThroughTiedTimestampsWithoutGapsOrRepeats() = runBlocking {
        fake.addEntries(1203)
        // Eleven rows share one updated_at across the first batch boundary (500), as after a
        // bulk update. A timestamp-only cursor would skip e00500..e00505 or repeat e00495..e00499.
        val tie = fake.entries[495].updatedAt
        fake.entries.subList(495, 506).forEach { it.updatedAt = tie }
        signIn()

        val ids = repo.observeEntries(5_000).first().map { it.id }
        assertEquals(1203, ids.size)
        assertEquals(1203, ids.toSet().size)
    }

    @Test
    fun firstSyncOfAHistoryTakesOneRequestPerBatch() = runBlocking {
        fake.addEntries(1203)
        signIn(forgetRequests = false)

        val syncs = fake.requestsTo("gratitude_entries")
        assertEquals("500 + 500 + 203", 3, syncs.size)
        assertTrue("the first has no cursor", !syncs[0].contains("or="))
        assertTrue("the rest resume from one", syncs.drop(1).all { it.contains("or=") })
    }

    @Test
    fun deltaSyncPicksUpEditsFromElsewhere() = runBlocking {
        fake.addEntries(3)
        signIn()
        fake.edit("e00001", "edited on the tablet")

        repo.refreshAll(force = true)

        assertEquals("edited on the tablet", repo.observeEntries(10).first().first { it.id == "e00001" }.entryText)
    }

    @Test
    fun deletingAnEntryHidesItButItStillCountsTowardToday() = runBlocking {
        fake.addEntries(3)
        signIn()

        repo.deleteEntry("e00002")

        assertEquals(listOf("e00001", "e00000"), repo.observeEntries(10).first().map { it.id })
        // The daily cap counts deleted entries: they were paid for.
        assertEquals(3, repo.observeEntriesTodayCount().first())
        // The write pulled only what changed, via the cursor.
        assertTrue(fake.requestsTo("gratitude_entries").single().contains("or="))
    }

    @Test
    fun aPlantDugUpElsewhereDisappears() = runBlocking {
        signIn()
        fake.plantIds.remove("p2")

        repo.refreshAll(force = true)

        assertEquals(listOf("p1"), repo.observePlants().first().map { it.id })
    }

    @Test
    fun overlappingRefreshesShareOneRun() = runBlocking {
        signIn()
        fake.latencyMs = 300

        coroutineScope { repeat(3) { launch { repo.refreshAll(force = true) } } }

        for (table in listOf("profiles", "coin_wallets", "user_stats", "gardens", "items", "gratitude_entries")) {
            assertEquals("requests to $table", 1, fake.requestsTo(table).size)
        }
    }

    @Test
    fun aRecentRefreshIsReusedUnlessForced() = runBlocking {
        signIn()

        repo.refreshAll()
        assertEquals(0, fake.requests.size)

        repo.refreshAll(force = true)
        assertTrue(fake.requests.isNotEmpty())
    }

    @Test
    fun signingOutEmptiesTheDatabase() = runBlocking {
        fake.addEntries(3)
        signIn()

        repo.signOut()

        assertEquals(AppSession.SignedOut, withTimeout(5_000) { repo.session.first { it !is AppSession.SignedIn } })
        assertEquals(0, db.entryDao().observeNewest(fake.userId, 100).first().size)
        assertNull(db.gardenDao().observeGarden(fake.userId).first())
        assertNull(db.accountDao().observeWallet(fake.userId).first())
        assertEquals(0, db.itemDao().observeCatalog().first().size)
    }
}
