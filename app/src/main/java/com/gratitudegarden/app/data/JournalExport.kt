package com.gratitudegarden.app.data

import com.gratitudegarden.app.data.local.EntryEntity
import com.gratitudegarden.app.data.local.SyncState
import com.gratitudegarden.app.model.Mood
import com.gratitudegarden.app.util.EntryLine
import com.gratitudegarden.app.util.entryLines
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Export (roadmap 2.2): the whole journal as one ZIP the user saves where they like, holding
// journal.md to read, journal.json to keep or import elsewhere, and photos/. Built from the
// device's copy, so it works offline; only photos never fetched here need the network. Pure,
// so the formats are unit-tested; GardenRepository.exportJournal writes the ZIP.

const val EXPORT_MARKDOWN = "journal.md"
const val EXPORT_JSON = "journal.json"
private const val EXPORT_PHOTOS = "photos"

/** Bumped if journal.json ever changes shape, so a reader can tell the versions apart. */
internal const val EXPORT_FORMAT = 1

/** One entry as exported. [photo] is its file inside the ZIP. */
@Serializable
data class ExportedEntry(
    val id: String,
    /** The journal day, `yyyy-MM-dd`. */
    val date: String,
    /** ISO-8601 with an offset, as the server stamped it. */
    @SerialName("written_at") val writtenAt: String,
    val text: String,
    /** rough, low, okay, good or great; absent when none was given. */
    val mood: String? = null,
    @SerialName("input_method") val inputMethod: String,
    val photo: String? = null,
    /** The entry has a photo that couldn't be fetched for this export (offline). */
    @SerialName("photo_missing") val photoMissing: Boolean = false,
    /** synced, waiting (not on the server yet) or refused (the server said no). */
    val status: String,
)

@Serializable
data class JournalExport(
    val format: Int,
    @SerialName("exported_at") val exportedAt: String,
    val entries: List<ExportedEntry>,
)

/** What an export held, for the message after it. */
data class ExportSummary(val entries: Int, val photos: Int, val photosMissing: Int)

/** Where [entry]'s photo goes in the ZIP: by day, then the entry, so the folder sorts by date. */
internal fun exportPhotoName(entry: EntryEntity): String =
    "$EXPORT_PHOTOS/${entry.entryDate}-${entry.id.take(8)}.jpg"

/** [entry] as exported; [photoIncluded] says whether its photo's file made it into the ZIP. */
internal fun exportedEntry(entry: EntryEntity, photoIncluded: Boolean): ExportedEntry =
    ExportedEntry(
        id = entry.id,
        date = entry.entryDate,
        writtenAt = entry.createdAt,
        text = entry.entryText,
        mood = Mood.of(entry.mood)?.label,
        inputMethod = entry.inputMethod,
        photo = if (entry.photoPath != null && photoIncluded) exportPhotoName(entry) else null,
        photoMissing = entry.photoPath != null && !photoIncluded,
        status = when (entry.syncState) {
            SyncState.SYNCED -> "synced"
            SyncState.PENDING -> "waiting"
            SyncState.FAILED -> "refused"
        },
    )

private val exportJson = Json { prettyPrint = true }

internal fun journalJson(export: JournalExport): String = exportJson.encodeToString(JournalExport.serializer(), export)

private val dayFmt = DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.US)
private val timeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

/**
 * The journal to read: a heading per day, oldest first, and each entry's time, mood and
 * photo under it. Times are moved to [zone], as the Journal shows them.
 */
internal fun journalMarkdown(export: JournalExport, zone: ZoneId): String = buildString {
    append("# Gratitude Garden journal\n\n")
    val exported = OffsetDateTime.parse(export.exportedAt).atZoneSameInstant(zone)
    val count = export.entries.size
    append("Exported ${exported.format(dayFmt)}. ")
    append(if (count == 1) "1 thought." else "$count thoughts.")
    append("\n")
    for ((day, entries) in export.entries.groupBy { it.date }) {
        append("\n## ").append(dayHeading(day)).append("\n")
        for (entry in entries) {
            append("\n### ").append(entryHeading(entry, zone)).append("\n\n")
            append(markdownBody(entry.text)).append("\n")
            entry.photo?.let { append("\n![Photo](").append(it).append(")\n") }
            if (entry.photoMissing) {
                append("\n*This photo couldn't be downloaded for the export. Connect and export again to include it.*\n")
            }
        }
    }
}

private fun dayHeading(day: String): String =
    runCatching { LocalDate.parse(day).format(dayFmt) }.getOrDefault(day)

private fun entryHeading(entry: ExportedEntry, zone: ZoneId): String {
    val time = runCatching {
        OffsetDateTime.parse(entry.writtenAt).atZoneSameInstant(zone).format(timeFmt)
    }.getOrDefault("")
    return listOfNotNull(
        time.ifEmpty { null },
        entry.mood?.let { "feeling $it" },
        when (entry.status) {
            "waiting" -> "waiting to sync"
            "refused" -> "couldn't sync"
            else -> null
        },
    ).joinToString(" · ").ifEmpty { "Thought" }
}

/**
 * An entry's text as Markdown. Its own lines are kept (a hard break between plain lines),
 * its bullets become a Markdown list, and a line that would otherwise turn into a heading,
 * quote or list is escaped, so the words read exactly as written.
 */
internal fun markdownBody(text: String): String {
    val out = StringBuilder()
    var previous: EntryLine? = null
    for (line in entryLines(text.trim())) {
        when (line) {
            is EntryLine.Item -> {
                // A list needs a blank line before it, or it stays part of the paragraph.
                if (previous is EntryLine.Plain && previous.text.isNotBlank()) out.append("\n")
                if (previous != null) out.append("\n")
                out.append("- ").append(escapeLine(line.text))
            }
            is EntryLine.Plain -> {
                when (previous) {
                    null -> {}
                    is EntryLine.Item -> out.append(if (line.text.isBlank()) "\n" else "\n\n")
                    is EntryLine.Plain ->
                        out.append(if (line.text.isBlank() || previous.text.isBlank()) "\n" else "  \n")
                }
                out.append(escapeLine(line.text))
            }
        }
        previous = line
    }
    return out.toString()
}

// A line starting like these would become Markdown structure instead of words: a heading,
// quote, list, rule or table. A numbered line is escaped at its dot: `\1` isn't an escape.
private val NUMBERED_START = Regex("""^(\s*\d+)([.)]\s)""")
private val MARKUP_START = Regex("""^(\s*)([#>+=|*_-])""")

private fun escapeLine(line: String): String =
    line.replace(NUMBERED_START) { m -> m.groupValues[1] + "\\" + m.groupValues[2] }
        .replace(MARKUP_START) { m -> m.groupValues[1] + "\\" + m.groupValues[2] }
