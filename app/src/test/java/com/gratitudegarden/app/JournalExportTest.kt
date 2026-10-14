package com.gratitudegarden.app

import com.gratitudegarden.app.data.EXPORT_FORMAT
import com.gratitudegarden.app.data.ExportSummary
import com.gratitudegarden.app.data.JournalExport
import com.gratitudegarden.app.data.exportPhotoName
import com.gratitudegarden.app.data.exportedEntry
import com.gratitudegarden.app.data.journalJson
import com.gratitudegarden.app.data.journalMarkdown
import com.gratitudegarden.app.data.local.EntryEntity
import com.gratitudegarden.app.data.local.SyncState
import com.gratitudegarden.app.data.markdownBody
import com.gratitudegarden.app.ui.me.exportMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/**
 * The journal export (roadmap 2.2): journal.json keeps everything a reader needs, journal.md
 * reads as the Journal does, and the words come out exactly as written.
 */
class JournalExportTest {

    private val newYork = ZoneId.of("America/New_York")

    private fun entry(
        id: String = "3f2a9c41-0000-4000-8000-000000000001",
        text: String = "the sun",
        day: String = "2026-10-12",
        createdAt: String = "2026-10-13T00:30:00+00:00",
        mood: Int? = null,
        photo: String? = null,
        state: SyncState = SyncState.SYNCED,
    ) = EntryEntity(
        id = id, userId = "u1", entryText = text, inputMethod = "text", coinsAwarded = 5, xpAwarded = 10,
        entryDate = day, createdAt = createdAt, createdAtMicros = 0, deletedAt = null,
        syncState = state, photoPath = photo, mood = mood,
    )

    private fun export(vararg entries: EntryEntity, photosIncluded: Boolean = true) = JournalExport(
        format = EXPORT_FORMAT,
        exportedAt = "2026-10-13T12:00:00-04:00",
        entries = entries.map { exportedEntry(it, photosIncluded) },
    )

    @Test
    fun aPhotoIsNamedByItsDayAndEntry() {
        assertEquals("photos/2026-10-12-3f2a9c41.jpg", exportPhotoName(entry(photo = "u1/e/p.jpg")))
    }

    @Test
    fun theJsonKeepsTheWordsTheMoodAndThePhoto() {
        val json = Json.parseToJsonElement(
            journalJson(export(entry(text = "rain\n• tea", mood = 4, photo = "u1/e/p.jpg"))),
        ).jsonObject
        assertEquals(EXPORT_FORMAT, json["format"]!!.jsonPrimitive.int)
        val e = json["entries"]!!.jsonArray.single().jsonObject
        assertEquals("rain\n• tea", e["text"]!!.jsonPrimitive.content)
        assertEquals("good", e["mood"]!!.jsonPrimitive.content)
        assertEquals("2026-10-12", e["date"]!!.jsonPrimitive.content)
        assertEquals("2026-10-13T00:30:00+00:00", e["written_at"]!!.jsonPrimitive.content)
        assertEquals("photos/2026-10-12-3f2a9c41.jpg", e["photo"]!!.jsonPrimitive.content)
        assertEquals("synced", e["status"]!!.jsonPrimitive.content)
        assertFalse("only said when it's true", "photo_missing" in e)
    }

    @Test
    fun noMoodAndNoPhotoAreLeftOut() {
        val e = Json.parseToJsonElement(journalJson(export(entry()))).jsonObject["entries"]!!
            .jsonArray.single().jsonObject
        assertFalse("mood" in e)
        assertFalse("photo" in e)
    }

    @Test
    fun aPhotoThatCouldNotBeFetchedIsMarkedMissing() {
        val e = exportedEntry(entry(photo = "u1/e/p.jpg"), photoIncluded = false)
        assertNull(e.photo)
        assertTrue(e.photoMissing)
        assertTrue("couldn't be downloaded" in journalMarkdown(export(entry(photo = "u1/e/p.jpg"), photosIncluded = false), newYork))
    }

    @Test
    fun waitingAndRefusedEntriesSaySo() {
        assertEquals("waiting", exportedEntry(entry(state = SyncState.PENDING), true).status)
        assertEquals("refused", exportedEntry(entry(state = SyncState.FAILED), true).status)
    }

    @Test
    fun theMarkdownHasADayHeadingAndTheTimeWhereTheUserIs() {
        val md = journalMarkdown(
            export(
                entry(id = "a", text = "the sun", mood = 5, photo = "u1/a/p.jpg"),
                entry(id = "b", text = "soup", createdAt = "2026-10-13T01:00:00+00:00", state = SyncState.PENDING),
                entry(id = "c", text = "a walk", day = "2026-10-13", createdAt = "2026-10-13T14:00:00+00:00"),
            ),
            newYork,
        )
        assertTrue(md.startsWith("# Gratitude Garden journal\n\nExported Tuesday, October 13, 2026. 3 thoughts.\n"))
        assertTrue(md.contains("\n## Monday, October 12, 2026\n\n### 8:30 PM · feeling great\n\nthe sun\n\n![Photo](photos/2026-10-12-a.jpg)\n"))
        assertTrue(md.contains("\n### 9:00 PM · waiting to sync\n\nsoup\n"))
        assertTrue(md.contains("\n## Tuesday, October 13, 2026\n\n### 10:00 AM\n\na walk\n"))
        assertEquals("one heading per day", 2, Regex("^## ", RegexOption.MULTILINE).findAll(md).count())
    }

    @Test
    fun linesStayLinesAndBulletsBecomeAList() {
        assertEquals("one  \ntwo", markdownBody("one\ntwo"))
        assertEquals("one\n\ntwo", markdownBody("one\n\ntwo"))
        assertEquals("today:\n\n- tea\n- rain\n\nthat's all", markdownBody("today:\n• tea\n- rain\nthat's all"))
        assertEquals("- tea\n\nafter a gap", markdownBody("• tea\n\nafter a gap"))
    }

    @Test
    fun wordsThatLookLikeMarkdownStayWords() {
        assertEquals(
            "\\# 1 thing  \n\\> quoted  \n1\\. first  \n\\---",
            markdownBody("# 1 thing\n> quoted\n1. first\n---"),
        )
        assertEquals("- \\#hashtag", markdownBody("• #hashtag"))
        assertEquals("2026 was good", markdownBody("2026 was good"))
    }

    @Test
    fun theMessageCountsWhatWasSaved() {
        assertEquals("Saved 1 thought.", exportMessage(ExportSummary(1, 0, 0)))
        assertEquals("Saved 12 thoughts and 1 photo.", exportMessage(ExportSummary(12, 1, 0)))
        assertEquals(
            "Saved 12 thoughts and 3 photos. 2 photos couldn't be downloaded. Connect and export again to include them.",
            exportMessage(ExportSummary(12, 3, 2)),
        )
        assertEquals("Couldn't save your journal. Please try again.", exportMessage(null))
    }
}
