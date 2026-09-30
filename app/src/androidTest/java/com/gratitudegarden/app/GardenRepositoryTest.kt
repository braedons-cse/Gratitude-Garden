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
import io.github.jan.supabase.storage.Storage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
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
    private lateinit var photoDir: File
    private lateinit var stagingDir: File

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
            install(Storage)
        }
        val scratch = File(context.cacheDir, "repo-test-${UUID.randomUUID()}")
        photoDir = File(scratch, "photos")
        stagingDir = File(scratch, "staging").apply { mkdirs() }
        repo = GardenRepository(client, db, ConnectivityMonitor(context), photoDir)
    }

    @After
    fun tearDown() {
        db.close()
        photoDir.parentFile?.deleteRecursively()
    }

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
        // Cheapest first: the free starter backdrop leads.
        assertEquals(listOf("i3", "i1", "i2", "i4"), repo.observeCatalog().first().map { it.id })
        assertEquals(setOf("i1", "i3"), repo.observeInventory().first())
        assertEquals("i3", repo.observeGarden().first()?.activeBackdropItemId)
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
    fun refreshBringsTheActiveBackdrop() = runBlocking {
        signIn()
        assertEquals("i3", repo.observeGarden().first()?.activeBackdropItemId)

        // Changed on the server (another device, or the admin dashboard).
        fake.activeBackdropId = "i4"
        repo.refreshAll(force = true)
        assertEquals("i4", repo.observeGarden().first()?.activeBackdropItemId)
    }

    @Test
    fun equippingABackdropCallsTheServerOnceAndShowsIt() = runBlocking {
        fake.ownedItemIds += "i4"
        signIn()

        repo.setActiveBackdrop("i4")

        assertEquals(1, fake.rpcCalls("set_active_backdrop").size)
        assertEquals("i4", repo.observeGarden().first()?.activeBackdropItemId)
    }

    @Test
    fun equippingAnUnownedBackdropChangesNothing() = runBlocking {
        signIn()

        try {
            repo.setActiveBackdrop("i4")
            fail("the server refuses a backdrop the user doesn't own")
        } catch (_: Exception) {
        }

        assertEquals("i3", fake.activeBackdropId)
        assertEquals("i3", repo.observeGarden().first()?.activeBackdropItemId)
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

    // ── Entry photos (roadmap 1.4) ─────────────────────────────────────

    /** A photo as the sheet hands it over: a file in the staging folder. The bytes needn't be a real JPEG. */
    private fun staged(content: String = "photo-${UUID.randomUUID()}"): File =
        File(stagingDir, "${UUID.randomUUID()}.jpg").apply { writeText(content) }

    private fun local(path: String) = File(photoDir, path)

    /** Wait for the background delivery attempts to have tried [n] uploads and let go of the queue. */
    private suspend fun settleOfflineUploads(n: Int) {
        withTimeout(5_000) { while (fake.photoUploads().size < n) delay(10) }
        repo.drainOutbox()
    }

    @Test
    fun anEntryWithAPhotoIsSavedThenItsPhotoUploadedAndSet() = runBlocking {
        signIn()
        val photo = staged("sunset")

        assertEquals(SubmitResult.Planted(5, xp = 10), repo.submitEntry("the sunset", voice = false, photo = photo))

        val entry = journal().single()
        val path = checkNotNull(entry.photoPath)
        assertTrue(path, path.startsWith("${fake.userId}/${entry.id}/"))
        assertEquals(SyncState.SYNCED, entry.syncState)
        assertEquals(path, fake.entries.single().photoPath)
        assertEquals(setOf(path), fake.storedPhotos.keys)
        assertEquals("sunset", fake.storedPhotos[path]!!.decodeToString())
        // The entry first, so a slow upload never holds up the reward; then the file, then the row.
        val order = fake.requests.map {
            when {
                "rpc/submit_gratitude_entry" in it -> "submit"
                it.startsWith("POST /storage/v1/object/") -> "upload"
                "rpc/set_entry_photo" in it -> "set"
                else -> null
            }
        }.filterNotNull()
        assertEquals(listOf("submit", "upload", "set"), order)
        assertFalse("the staged file was handed over", photo.exists())
        assertEquals("sunset", local(path).readText())
    }

    @Test
    fun aPhotoWrittenOfflineIsKeptAndGoesUpOnReconnect() = runBlocking {
        signIn()
        fake.offline = true

        assertEquals(SubmitResult.Saved, repo.submitEntry("a quiet morning", voice = false, photo = staged("dawn")))

        val path = checkNotNull(journal().single().photoPath)
        assertEquals("dawn", local(path).readText())
        assertTrue(fake.storedPhotos.isEmpty())

        fake.offline = false
        assertTrue(repo.drainOutbox())

        assertEquals(SyncState.SYNCED, journal().single().syncState)
        assertEquals(path, fake.entries.single().photoPath)
        assertEquals("dawn", fake.storedPhotos[path]!!.decodeToString())
    }

    @Test
    fun lostAnswersNeverPayTwiceNorLeaveAStrayFile() = runBlocking {
        signIn()
        fake.loseEachFirstAnswer = true

        repo.submitEntry("worth keeping", voice = false, photo = staged())
        for (attempt in 1..10) if (repo.drainOutbox() && repo.observeUnsyncedCount().first() == 0) break

        val entry = journal().single()
        assertEquals(SyncState.SYNCED, entry.syncState)
        assertEquals(1, fake.paidSubmits)
        assertEquals(setOf(entry.photoPath), fake.storedPhotos.keys)
        assertEquals(entry.photoPath, fake.entries.single().photoPath)
    }

    @Test
    fun replacingAPhotoTwiceOfflineSendsOnlyTheLastOne() = runBlocking {
        signIn()
        repo.submitEntry("the garden", voice = false, photo = staged("first"))
        val id = journal().single().id
        val first = checkNotNull(journal().single().photoPath)
        fake.requests.clear()
        fake.offline = true

        repo.setEntryPhoto(id, staged("second"))
        val second = checkNotNull(journal().single().photoPath)
        // Each change starts a delivery, which fails offline. An op on the wire can't take a
        // later change (the request has left), so let the attempt finish first.
        settleOfflineUploads(1)
        repo.setEntryPhoto(id, staged("third"))
        val third = checkNotNull(journal().single().photoPath)
        settleOfflineUploads(2)
        assertEquals(SyncState.PENDING, journal().single().syncState)
        fake.offline = false
        fake.requests.clear()

        assertTrue(repo.drainOutbox())

        assertEquals(1, fake.photoUploads().size)
        assertEquals(1, fake.rpcCalls("set_entry_photo").size)
        assertEquals("the replaced photo is gone from the server", setOf(third), fake.storedPhotos.keys)
        assertEquals(third, fake.entries.single().photoPath)
        assertEquals(SyncState.SYNCED, journal().single().syncState)
        assertFalse("nothing refers to it any more", local(first).exists())
        assertFalse(local(second).exists())
        assertEquals("third", local(third).readText())
    }

    @Test
    fun removingAPhotoClearsItEverywhere() = runBlocking {
        signIn()
        repo.submitEntry("a letter from home", voice = false, photo = staged())
        val entry = journal().single()
        val path = checkNotNull(entry.photoPath)

        repo.setEntryPhoto(entry.id, null)
        assertTrue(repo.drainOutbox())

        assertNull(journal().single().photoPath)
        assertNull(fake.entries.single().photoPath)
        assertTrue(fake.storedPhotos.isEmpty())
        assertFalse(local(path).exists())
    }

    @Test
    fun deletingAnEntryDeletesItsPhoto() = runBlocking {
        signIn()
        repo.submitEntry("the old oak", voice = false, photo = staged())
        val path = checkNotNull(journal().single().photoPath)

        repo.deleteEntry(journal().single().id)
        assertTrue(repo.drainOutbox())

        assertTrue(fake.storedPhotos.isEmpty())
        assertNotNull(fake.entries.single().deletedAt)
        assertNull(fake.entries.single().photoPath)
        assertFalse(local(path).exists())
    }

    @Test
    fun deletingAQueuedEntryNeverUploadsItsPhoto() = runBlocking {
        signIn()
        fake.offline = true
        repo.submitEntry("never mind", voice = false, photo = staged())
        val path = checkNotNull(journal().single().photoPath)
        repo.deleteEntry(journal().single().id)
        fake.offline = false
        fake.requests.clear()

        assertTrue(repo.drainOutbox())

        assertTrue(fake.requests.isEmpty())
        assertTrue(fake.storedPhotos.isEmpty())
        assertFalse(local(path).exists())
    }

    @Test
    fun aPhotoFromAnotherDeviceIsFetchedOnceAndLetGoWhenReplaced() = runBlocking {
        fake.addEntries(1)
        val id = fake.entries.single().id
        val first = "${fake.userId}/$id/p1.jpg"
        fake.setPhotoElsewhere(id, first, byteArrayOf(7, 7, 7))
        signIn()
        assertEquals(first, journal().single().photoPath)
        assertFalse("fetched only when it's shown", local(first).exists())

        val file = checkNotNull(repo.photoFile(first))
        assertArrayEquals(byteArrayOf(7, 7, 7), file.readBytes())
        fake.requests.clear()
        assertEquals(file, repo.photoFile(first))
        assertTrue("the second look is local", fake.requests.isEmpty())

        val second = "${fake.userId}/$id/p2.jpg"
        fake.setPhotoElsewhere(id, second)
        repo.refreshAll(force = true)

        assertEquals(second, journal().single().photoPath)
        assertFalse("the replaced photo's file is let go", local(first).exists())
    }

    @Test
    fun anOfflinePhotoThatWasNeverFetchedIsNull() = runBlocking {
        fake.addEntries(1)
        val id = fake.entries.single().id
        val path = "${fake.userId}/$id/p1.jpg"
        fake.setPhotoElsewhere(id, path)
        signIn()
        fake.offline = true

        assertNull(repo.photoFile(path))
    }

    @Test
    fun aPathOutsideTheUsersFolderIsNeverFetched() = runBlocking {
        signIn()

        assertNull(repo.photoFile("../../databases/garden.db"))
        assertNull(repo.photoFile("someone-else/e1/p1.jpg"))
        assertTrue(fake.requests.isEmpty())
    }

    @Test
    fun signingOutEmptiesThePhotoFolder() = runBlocking {
        signIn()
        repo.submitEntry("my own", voice = false, photo = staged())
        assertTrue(photoDir.walk().any { it.isFile })

        repo.signOut()

        assertFalse(photoDir.exists())
    }

    @Test
    fun aRefusedEntryIsSentAgainWithItsPhoto() = runBlocking {
        signIn()
        fake.refuseText = "nope"
        repo.submitEntry("nope, not this", voice = false, photo = staged("kept"))
        val entry = journal().single()
        assertEquals(SyncState.FAILED, entry.syncState)
        assertTrue("a refused entry keeps its photo", local(checkNotNull(entry.photoPath)).exists())
        fake.refuseText = null

        repo.retryEntry(entry.id)
        assertTrue(repo.drainOutbox())

        assertEquals(SyncState.SYNCED, journal().single().syncState)
        assertEquals(entry.photoPath, fake.entries.single().photoPath)
        assertEquals("kept", fake.storedPhotos[entry.photoPath]!!.decodeToString())
    }

    @Test
    fun aRetriedEntryWhosePhotoCameFromElsewhereNeedsNoUpload() = runBlocking {
        fake.addEntries(1)
        val id = fake.entries.single().id
        val path = "${fake.userId}/$id/p1.jpg"
        fake.setPhotoElsewhere(id, path)
        signIn()
        fake.refuseText = "nope"
        repo.editEntry(id, "nope, not this", mood = null)
        repo.drainOutbox()
        assertEquals(SyncState.FAILED, journal().single().syncState)
        assertFalse("never shown here, so never downloaded", local(path).exists())
        fake.refuseText = "never matches"
        fake.requests.clear()

        repo.retryEntry(id)
        assertTrue(repo.drainOutbox())

        assertEquals(SyncState.SYNCED, journal().single().syncState)
        assertTrue("the server already has it", fake.photoUploads().isEmpty())
        assertEquals(path, fake.entries.single().photoPath)
        assertTrue(path in fake.storedPhotos)
    }

    @Test
    fun moreFilesThanOneAnswerHoldsAreAllRemoved() = runBlocking {
        signIn()
        repo.submitEntry("a busy folder", voice = false, photo = staged())
        val id = journal().single().id
        // Uploads whose answers were lost, piled up past one PostgREST page.
        repeat(1200) { fake.storedPhotos["${fake.userId}/$id/stray-%04d.jpg".format(it)] = byteArrayOf(1) }
        fake.requests.clear()

        repo.deleteEntry(id)
        assertTrue(repo.drainOutbox())

        assertTrue(fake.storedPhotos.isEmpty())
        assertTrue(fake.rpcCalls("own_photo_objects").size >= 2)
    }

    @Test
    fun aPhotoTheServerRefusesIsNotLeftInStorage() = runBlocking {
        signIn()
        repo.submitEntry("deleted on the tablet", voice = false)
        val id = journal().single().id
        fake.requests.clear()
        fake.offline = true
        repo.setEntryPhoto(id, staged())
        val path = checkNotNull(journal().single().photoPath)
        settleOfflineUploads(1)
        fake.deleteElsewhere(id)
        fake.offline = false
        fake.requests.clear()

        repo.drainOutbox()

        assertEquals(SyncState.FAILED, journal().single().syncState)
        assertEquals("uploaded, then refused", 1, fake.photoUploads().size)
        assertFalse("and not left behind", path in fake.storedPhotos)
    }

    @Test
    fun savingAsNewKeepsAPhotoThatWasNeverShownHere() = runBlocking {
        fake.addEntries(1)
        val id = fake.entries.single().id
        fake.setPhotoElsewhere(id, "${fake.userId}/$id/p1.jpg", byteArrayOf(9, 9, 9))
        signIn()
        fake.refuseText = "nope"
        repo.editEntry(id, "nope, but keep the words", mood = null)
        repo.drainOutbox()
        fake.refuseText = "never matches"

        repo.saveAsNewEntry(id)

        val copy = journal().single { it.id != id }
        assertEquals("nope, but keep the words", copy.entryText)
        val path = checkNotNull(copy.photoPath)
        assertTrue(path.startsWith("${fake.userId}/${copy.id}/"))
        assertArrayEquals(byteArrayOf(9, 9, 9), fake.storedPhotos[path])
    }

    @Test
    fun editingTextWhileAPhotoWaitsKeepsBoth() = runBlocking {
        signIn()
        repo.submitEntry("first words", voice = false)
        val id = journal().single().id
        fake.offline = true
        repo.setEntryPhoto(id, staged())
        repo.editEntry(id, "better words", mood = null)
        fake.offline = false

        assertTrue(repo.drainOutbox())

        assertEquals("better words", fake.entries.single().text)
        assertNotNull(fake.entries.single().photoPath)
    }

    // ── Moods: sent with the text ──────────────────────────────────────

    @Test
    fun aMoodGoesWithTheEntryAndEarnsNothingExtra() = runBlocking {
        signIn()

        assertEquals(SubmitResult.Planted(5, xp = 10), repo.submitEntry("a warm bath", voice = false, mood = 4))
        repo.submitEntry("no words for it", voice = false)

        assertEquals(4, fake.entries.first { it.text == "a warm bath" }.mood)
        assertNull(fake.entries.first { it.text == "no words for it" }.mood)
        assertEquals(listOf(null, 4), journal().map { it.mood })
    }

    @Test
    fun changingTheMoodOfAQueuedEntrySendsOneSubmitWithTheFinalMood() = runBlocking {
        signIn()
        fake.offline = true
        repo.submitEntry("a long day", voice = false, mood = 2)
        val id = journal().single().id
        repo.editEntry(id, "a long day, but a good dinner", mood = 4)
        assertEquals(4, journal().single().mood)
        fake.offline = false
        fake.requests.clear()

        repo.drainOutbox()

        assertEquals(1, fake.rpcCalls("submit_gratitude_entry").size)
        assertEquals(0, fake.rpcCalls("edit_gratitude_entry").size)
        assertEquals(4, fake.entries.single().mood)
        assertEquals("a long day, but a good dinner", fake.entries.single().text)
    }

    @Test
    fun anEditCanTakeTheMoodOff() = runBlocking {
        signIn()
        repo.submitEntry("hard to say", voice = false, mood = 3)
        val id = journal().single().id

        repo.editEntry(id, "hard to say", mood = null)
        repo.drainOutbox()

        assertNull(fake.entries.single().mood)
        assertNull(journal().single().mood)
        assertEquals(SyncState.SYNCED, journal().single().syncState)
    }

    @Test
    fun retryingARefusedEntryKeepsItsMood() = runBlocking {
        signIn()
        fake.refuseText = "rejected"
        repo.submitEntry("rejected at first", voice = false, mood = 2)
        val id = journal().single().id
        assertEquals(SyncState.FAILED, journal().single().syncState)
        fake.refuseText = null

        repo.retryEntry(id)
        repo.drainOutbox()

        assertEquals(2, fake.entries.single().mood)
        assertEquals(SyncState.SYNCED, journal().single().syncState)
    }

    @Test
    fun savingAsNewKeepsTheMood() = runBlocking {
        fake.addEntries(1)
        signIn()
        fake.rpcsFail = true
        repo.editEntry("e00000", "written on the plane", mood = 5)
        fake.deleteElsewhere("e00000")
        fake.rpcsFail = false
        repo.drainOutbox()

        repo.saveAsNewEntry("e00000")

        val kept = journal().single()
        assertEquals(5, kept.mood)
        assertEquals(5, fake.entries.single { it.id == kept.id }.mood)
    }

    @Test
    fun aMoodChangedElsewhereArrivesWithASync() = runBlocking {
        fake.addEntries(1)
        signIn()
        assertNull(journal().single().mood)

        fake.setMoodElsewhere("e00000", 4)
        repo.refreshAll(force = true)

        assertEquals(4, journal().single().mood)
    }

    @Test
    fun aSyncDuringAQueuedMoodChangeKeepsTheLocalMood() = runBlocking {
        fake.addEntries(1)
        signIn()
        fake.rpcsFail = true
        repo.editEntry("e00000", "thanks #0", mood = 2)
        fake.setMoodElsewhere("e00000", 5)
        repo.refreshAll(force = true)

        assertEquals(2, journal().single().mood)

        fake.rpcsFail = false
        repo.drainOutbox()
        assertEquals(2, fake.entries.single().mood)
        assertEquals(2, journal().single().mood)
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

        assertEquals(SubmitResult.Planted(5, xp = 10), repo.submitEntry("a good friend", voice = false))
        assertEquals(SyncState.SYNCED, journal().single().syncState)
    }

    @Test
    fun theDaysFirstEntryPaysTheMostXpAndTheLevelFollows() = runBlocking {
        fake.xp = 25
        signIn()
        assertEquals(1, repo.observeProfile().first()?.level)

        assertEquals(SubmitResult.Planted(5, xp = 10), repo.submitEntry("first today", voice = false))
        assertEquals(SubmitResult.Planted(5, xp = 2), repo.submitEntry("and another", voice = false))

        assertEquals(listOf(2, 10), journal().map { it.xpAwarded })
        val profile = repo.observeProfile().first()
        assertEquals("the refresh after delivery brought the XP", 37, profile?.xp)
        assertEquals("30 XP is level 2", 2, profile?.level)
    }

    @Test
    fun editingAQueuedEntrySendsOneSubmitWithTheFinalText() = runBlocking {
        signIn()
        fake.offline = true
        repo.submitEntry("first draft", voice = false)
        val id = journal().single().id
        repo.editEntry(id, "second draft", mood = null)
        repo.editEntry(id, "final words", mood = null)
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
        assertEquals("nor given XP again", 10, fake.xp)
        assertEquals(SyncState.SYNCED, journal().single().syncState)
        assertEquals(10, journal().single().xpAwarded)
    }

    @Test
    fun replayingTheWholeQueueTwiceChangesNothing() = runBlocking {
        fake.addEntries(2)
        signIn()
        fake.offline = true
        repo.submitEntry("new one", voice = false)
        repo.editEntry("e00000", "edited offline", mood = null)
        repo.deleteEntry("e00001")
        fake.offline = false
        fake.requests.clear()
        // Each op is carried out, its answer lost, and sent again: the queue goes out twice.
        fake.loseEachFirstAnswer = true
        while (!repo.drainOutbox()) Unit

        assertEquals(2, fake.rpcCalls("submit_gratitude_entry").size)
        assertEquals(2, fake.rpcCalls("edit_gratitude_entry").size)
        // A delete is two calls, the delete and the listing of the entry's photos. Losing the
        // listing's first answer sends the whole op again, the delete included.
        assertEquals(3, fake.rpcCalls("delete_gratitude_entry").size)
        assertEquals(2, fake.rpcCalls("own_photo_objects").size)
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
        repo.editEntry("e00000", "written on the plane", mood = null)
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

    // ── The reminder and the inline reply, from outside the app ───────

    @Test
    fun nothingWrittenTodayMeansTheReminderPosts() = runBlocking {
        fake.lastEntryDate = LocalDate.now().minusDays(1).toString()
        signIn()

        assertEquals(false, repo.wroteToday())
    }

    @Test
    fun aDeletedEntryStillCountsAsWrittenToday() = runBlocking {
        fake.lastEntryDate = LocalDate.now().minusDays(1).toString()
        signIn()
        repo.submitEntry("written, then deleted", voice = false)
        repo.deleteEntry(journal().single().id)

        assertEquals(true, repo.wroteToday())
    }

    @Test
    fun anotherDevicesEntryCountsOnceItsStatsHaveSynced() = runBlocking {
        // The fake's stats say today; no entry has reached this device.
        signIn()
        assertEquals(0, db.entryDao().countOn(fake.userId, LocalDate.now().toString()))

        assertEquals(true, repo.wroteToday())
    }

    @Test
    fun signedOutNobodyHasWritten() = runBlocking {
        assertNull(repo.wroteToday())
    }

    @Test
    fun awaitReadyAnswersWithTheSessionOnceTheTokenIsTried() = runBlocking {
        signIn()

        assertEquals(AppSession.SignedIn(fake.userId), repo.awaitReady(1.seconds))
    }

    @Test
    fun anEntryWrittenTellsTheWidget() = runBlocking {
        signIn()
        val changes = Channel<Unit>(Channel.UNLIMITED)
        val watching = launch { repo.widgetChanges().collect { changes.send(it) } }
        withTimeout(5_000) { changes.receive() } // the initial state

        fake.offline = true
        repo.submitEntry("told the widget", voice = false)

        withTimeout(5_000) { changes.receive() }
        watching.cancel()
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

        repo.editEntry(id, "a better draft", mood = null)
        repo.drainOutbox()

        assertEquals("a better draft", fake.entries.single().text)
        assertEquals(SyncState.SYNCED, journal().single().syncState)
    }

    @Test
    fun retryingARefusedEditOfASyncedEntryAppliesIt() = runBlocking {
        fake.addEntries(1)
        signIn()
        fake.refuseText = "rejected"
        repo.editEntry("e00000", "rejected words", mood = null)
        repo.drainOutbox()
        assertEquals(SyncState.FAILED, journal().single().syncState)
        fake.refuseText = null

        repo.editEntry("e00000", "kinder words", mood = null)
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
        repo.editEntry("e00000", "rejected words", mood = null)
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
        repo.editEntry("e00000", "written on the plane", mood = null)
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
