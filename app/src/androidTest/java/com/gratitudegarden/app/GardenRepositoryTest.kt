package com.gratitudegarden.app

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gratitudegarden.app.data.AppSession
import com.gratitudegarden.app.data.ConnectivityMonitor
import com.gratitudegarden.app.data.GardenRepository
import com.gratitudegarden.app.data.SubmitResult
import com.gratitudegarden.app.data.local.GardenDatabase
import com.gratitudegarden.app.data.local.SyncState
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
import java.time.LocalDate
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
    fun refreshBringsTheFreezesAndTheFrozenDays() = runBlocking {
        val yesterday = LocalDate.now().minusDays(1).toString()
        val longAgo = LocalDate.now().minusDays(30).toString()
        fake.freezes = 1
        fake.frozenDays += listOf(yesterday, longAgo)
        signIn()

        assertEquals(1, repo.observeStats().first()?.streakFreezes)
        // The week strip only asks for the last seven days.
        assertEquals(setOf(yesterday), repo.observeRecentFrozenDates().first())

        // A frozen day refunded on the server (a late entry filled it) goes locally too.
        fake.frozenDays.remove(yesterday)
        repo.refreshAll(force = true)
        assertEquals(emptySet<String>(), repo.observeRecentFrozenDates().first())
    }

    @Test
    fun buyingAFreezeCallsTheServerOnceAndShowsBothBalances() = runBlocking {
        fake.balance = 60
        signIn()

        repo.buyStreakFreeze()

        assertEquals(1, fake.rpcCalls("buy_streak_freeze").size)
        assertEquals(10, repo.observeWallet().first()?.balance)
        assertEquals(1, repo.observeStats().first()?.streakFreezes)
    }

    @Test
    fun aRefusedFreezePurchaseChangesNothing() = runBlocking {
        fake.balance = 60
        fake.freezes = 2
        signIn()

        try {
            repo.buyStreakFreeze()
            fail("the server refuses a third freeze")
        } catch (_: Exception) {
        }

        assertEquals(60, repo.observeWallet().first()?.balance)
        assertEquals(2, repo.observeStats().first()?.streakFreezes)
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
        repo.drainOutbox()

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

    // ── The outbox: journal writes that work offline ───────────────────

    private suspend fun journal() = repo.observeEntries(100).first()

    @Test
    fun anEntryWrittenOfflineIsInTheJournalAtOnce() = runBlocking {
        signIn()
        fake.offline = true

        val result = repo.submitEntry("the smell of rain", voice = false)

        assertEquals(SubmitResult.Saved, result)
        val entry = journal().single()
        assertEquals("the smell of rain", entry.entryText)
        assertEquals(SyncState.PENDING, entry.syncState)
        assertNull("the server decides the reward", entry.coinsAwarded)
        assertEquals(1, repo.observeEntriesTodayCount().first())
        assertEquals(1, repo.observeUnsyncedCount().first())
    }

    @Test
    fun reconnectingDeliversWhatWasWrittenOffline() = runBlocking {
        signIn()
        fake.offline = true
        repo.submitEntry("the smell of rain", voice = false)
        fake.offline = false
        fake.requests.clear()

        assertTrue(repo.drainOutbox())

        val entry = journal().single()
        assertEquals(SyncState.SYNCED, entry.syncState)
        assertEquals(5, entry.coinsAwarded)
        assertEquals(entry.id, fake.entries.single().id)
        assertEquals(1, fake.rpcCalls("submit_gratitude_entry").size)
        assertEquals(0, repo.observeUnsyncedCount().first())
        assertEquals("the reward reached the wallet", 17, repo.observeWallet().first()?.balance)
    }

    @Test
    fun onlineAnEntryIsPlantedStraightAway() = runBlocking {
        signIn()

        assertEquals(SubmitResult.Planted(5), repo.submitEntry("a good friend", voice = false))
        assertEquals(SyncState.SYNCED, journal().single().syncState)
    }

    @Test
    fun editingAQueuedEntrySendsOneSubmitWithTheFinalText() = runBlocking {
        signIn()
        fake.offline = true
        repo.submitEntry("first draft", voice = false)
        val id = journal().single().id
        repo.editEntry(id, "second draft")
        repo.editEntry(id, "final words")
        assertEquals("final words", journal().single().entryText)
        fake.offline = false
        fake.requests.clear()

        repo.drainOutbox()

        assertEquals(1, fake.rpcCalls("submit_gratitude_entry").size)
        assertEquals(0, fake.rpcCalls("edit_gratitude_entry").size)
        assertEquals("final words", fake.entries.single().text)
    }

    @Test
    fun deletingAQueuedEntrySendsNothing() = runBlocking {
        signIn()
        fake.offline = true
        repo.submitEntry("never mind", voice = false)
        repo.deleteEntry(journal().single().id)
        fake.offline = false
        fake.requests.clear()

        repo.drainOutbox()

        assertEquals(0, fake.rpcCalls().size)
        assertTrue(fake.entries.isEmpty())
        assertTrue(journal().isEmpty())
        assertEquals("the server never saw it, so neither does the cap", 0, repo.observeEntriesTodayCount().first())
    }

    @Test
    fun aLostResponseIsReplayedAndPaidOnce() = runBlocking {
        signIn()
        fake.dropResponses = true

        // The server saves and pays; the answer never arrives.
        assertEquals(SubmitResult.Saved, repo.submitEntry("worth it twice?", voice = false))
        assertEquals(1, fake.paidSubmits)
        fake.dropResponses = false

        assertTrue(repo.drainOutbox())

        assertEquals(1, fake.entries.size)
        assertEquals("the replay was recognised, not paid again", 1, fake.paidSubmits)
        assertEquals(SyncState.SYNCED, journal().single().syncState)
    }

    @Test
    fun replayingTheWholeQueueTwiceChangesNothing() = runBlocking {
        fake.addEntries(2)
        signIn()
        fake.offline = true
        repo.submitEntry("new one", voice = false)
        repo.editEntry("e00000", "edited offline")
        repo.deleteEntry("e00001")
        fake.offline = false
        fake.requests.clear()
        // Each op is carried out, its answer lost, and sent again: the queue goes out twice.
        fake.loseEachFirstAnswer = true
        while (!repo.drainOutbox()) Unit

        assertEquals(2, fake.rpcCalls("submit_gratitude_entry").size)
        assertEquals(2, fake.rpcCalls("edit_gratitude_entry").size)
        assertEquals(2, fake.rpcCalls("delete_gratitude_entry").size)
        assertEquals(1, fake.paidSubmits)
        assertEquals(3, fake.entries.size)
        assertEquals("edited offline", fake.entries.first { it.id == "e00000" }.text)
        assertTrue(fake.entries.first { it.id == "e00001" }.deletedAt != null)
        assertEquals(listOf(SyncState.SYNCED, SyncState.SYNCED), journal().map { it.syncState })
        assertEquals(0, repo.observeUnsyncedCount().first())
    }

    @Test
    fun aSyncDuringAQueuedEditKeepsTheLocalText() = runBlocking {
        fake.addEntries(1)
        signIn()
        fake.rpcsFail = true
        repo.editEntry("e00000", "written on the plane")
        // Meanwhile the server's copy changes and a refresh reads it.
        fake.edit("e00000", "older, from the tablet")
        repo.refreshAll(force = true)

        assertEquals("written on the plane", journal().single().entryText)

        fake.rpcsFail = false
        repo.drainOutbox()
        assertEquals("written on the plane", fake.entries.single().text)
        assertEquals("written on the plane", journal().single().entryText)
        assertEquals(SyncState.SYNCED, journal().single().syncState)
    }

    @Test
    fun aRefusedEntryIsKeptAndTheQueueMovesOn() = runBlocking {
        signIn()
        fake.offline = true
        fake.refuseText = "rejected"
        repo.submitEntry("this gets rejected", voice = false)
        repo.submitEntry("this one is fine", voice = false)
        fake.offline = false

        assertTrue(repo.drainOutbox())

        val refused = journal().first { it.entryText == "this gets rejected" }
        assertEquals(SyncState.FAILED, refused.syncState)
        assertEquals("entry text required", db.entryDao().get(refused.id)?.syncError)
        assertEquals(SyncState.SYNCED, journal().first { it.entryText == "this one is fine" }.syncState)
        assertEquals(0, repo.observeUnsyncedCount().first())
    }

    @Test
    fun theDailyCapIsCheckedOnTheDevice() = runBlocking {
        fake.dailyCap = 2
        signIn()
        fake.offline = true
        repo.submitEntry("one", voice = false)
        repo.submitEntry("two", voice = false)

        try {
            repo.submitEntry("three", voice = false)
            fail("the third entry is over the cap")
        } catch (e: IllegalStateException) {
            assertEquals("daily entry cap (2) reached", e.message)
        }
        assertEquals(2, journal().size)
    }

    @Test
    fun signingOutDropsTheQueue() = runBlocking {
        signIn()
        fake.rpcsFail = true
        repo.submitEntry("not yet synced", voice = false)
        assertEquals(1, repo.observeUnsyncedCount().first())

        repo.signOut()

        assertEquals(0, db.outboxDao().size())
    }

    // ── Refused entries: retry and discard ────────────────────────────

    /** Write [text] while the server refuses it, and return its id. */
    private suspend fun refusedEntry(text: String): String {
        fake.refuseText = text
        repo.submitEntry(text, voice = false)
        fake.refuseText = null
        return journal().single { it.entryText == text }.id.also {
            assertEquals(SyncState.FAILED, journal().single { e -> e.id == it }.syncState)
        }
    }

    @Test
    fun retryingARefusedEntryDeliversItOnce() = runBlocking {
        signIn()
        val id = refusedEntry("refused the first time")

        repo.retryEntry(id)
        repo.drainOutbox()

        assertEquals(SyncState.SYNCED, journal().single().syncState)
        assertEquals(id, fake.entries.single().id)
        assertEquals(1, fake.paidSubmits)
    }

    @Test
    fun editingARefusedEntrySendsTheNewText() = runBlocking {
        signIn()
        val id = refusedEntry("refused draft")

        repo.editEntry(id, "a better draft")
        repo.drainOutbox()

        assertEquals("a better draft", fake.entries.single().text)
        assertEquals(SyncState.SYNCED, journal().single().syncState)
    }

    @Test
    fun retryingARefusedEditOfASyncedEntryAppliesIt() = runBlocking {
        fake.addEntries(1)
        signIn()
        fake.refuseText = "rejected"
        repo.editEntry("e00000", "rejected words")
        repo.drainOutbox()
        assertEquals(SyncState.FAILED, journal().single().syncState)
        fake.refuseText = null

        repo.editEntry("e00000", "kinder words")
        repo.drainOutbox()

        // The retry's submit was recognised (no new row, no payment), then the edit applied.
        assertEquals(1, fake.entries.size)
        assertEquals(0, fake.paidSubmits)
        assertEquals("kinder words", fake.entries.single().text)
        assertEquals(SyncState.SYNCED, journal().single().syncState)
    }

    @Test
    fun discardingARefusedNewEntryRemovesIt() = runBlocking {
        signIn()
        val id = refusedEntry("never reached the server")

        repo.discardEntry(id)

        assertTrue(journal().isEmpty())
        assertTrue("the server never had it", fake.entries.isEmpty())
    }

    @Test
    fun discardingARefusedEditPutsTheServerCopyBack() = runBlocking {
        fake.addEntries(1)
        signIn()
        fake.refuseText = "rejected"
        repo.editEntry("e00000", "rejected words")
        repo.drainOutbox()
        fake.refuseText = null

        repo.discardEntry("e00000")

        val entry = journal().single()
        assertEquals("thanks #0", entry.entryText)
        assertEquals(SyncState.SYNCED, entry.syncState)
    }

    @Test
    fun discardingOfflineChangesNothing() = runBlocking {
        signIn()
        val id = refusedEntry("keep me for now")
        fake.offline = true

        try {
            repo.discardEntry(id)
            fail("discarding needs the server")
        } catch (_: Exception) {
        }

        assertEquals(SyncState.FAILED, journal().single().syncState)
    }

    @Test
    fun anEditOfAnEntryDeletedElsewhereIsKeptUntilTheUserDecides() = runBlocking {
        fake.addEntries(1)
        signIn()
        fake.rpcsFail = true
        repo.editEntry("e00000", "written on the plane")
        fake.deleteElsewhere("e00000")
        fake.rpcsFail = false

        repo.drainOutbox()
        repo.refreshAll(force = true)

        // Refused, and the refresh that brought the server's deleted copy didn't replace it.
        val refused = journal().single()
        assertEquals("written on the plane", refused.entryText)
        assertEquals(SyncState.FAILED, refused.syncState)
        assertEquals("entry not found", refused.syncError)

        repo.saveAsNewEntry("e00000")

        val kept = journal().single()
        assertEquals("written on the plane", kept.entryText)
        assertEquals(SyncState.SYNCED, kept.syncState)
        assertTrue("a new entry, not the deleted one", kept.id != "e00000")
        assertEquals(2, fake.entries.size)
    }
}
