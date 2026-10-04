package com.gratitudegarden.app.util

// Lists in an entry (roadmap 1.4). The text stays plain, so a list is just lines that start
// with a bullet: nothing changes on the server, and an entry reads the same anywhere it's
// shown as text. Pure, so the editing rules are unit-tested.

/** What the list button and Enter put at the start of a line. */
const val BULLET = "• "

// Typed by hand they count too.
private val BULLET_PREFIXES = listOf(BULLET, "- ", "* ")

/** The bullet [line] starts with, or null when it isn't a list item. */
internal fun bulletOf(line: String): String? = BULLET_PREFIXES.firstOrNull { line.startsWith(it) }

/** One line of an entry as it's shown. */
sealed interface EntryLine {
    data class Item(val text: String) : EntryLine
    data class Plain(val text: String) : EntryLine
}

internal fun entryLines(text: String): List<EntryLine> = text.lines().map { line ->
    val bullet = bulletOf(line)
    if (bullet != null) EntryLine.Item(line.removePrefix(bullet)) else EntryLine.Plain(line)
}

/** Text and where the cursor goes after a list edit. */
data class ListEdit(val text: String, val cursor: Int)

/**
 * Keeps a list going when Enter is pressed. [old] became [new] with the cursor at [cursor].
 * If that was one newline typed at the end of a list item, the new line gets the same
 * bullet; on an empty item it ends the list instead, taking that bullet away. Null when the
 * edit was anything else and stands as typed.
 */
internal fun continueList(old: String, new: String, cursor: Int): ListEdit? {
    if (new.length != old.length + 1 || cursor < 1 || cursor > new.length) return null
    if (new[cursor - 1] != '\n' || new.removeRange(cursor - 1, cursor) != old) return null
    val lineStart = old.lastIndexOf('\n', cursor - 2) + 1
    val line = old.substring(lineStart, cursor - 1)
    val bullet = bulletOf(line) ?: return null
    return if (line == bullet) {
        // Enter on an empty item: drop its bullet and the newline, leaving a plain blank line.
        ListEdit(old.removeRange(lineStart, cursor - 1), lineStart)
    } else {
        ListEdit(new.substring(0, cursor) + bullet + new.substring(cursor), cursor + bullet.length)
    }
}

/** The list button: makes the line at [cursor] an item, or a plain line again if it was one. */
internal fun toggleBullet(text: String, cursor: Int): ListEdit {
    val at = cursor.coerceIn(0, text.length)
    val lineStart = text.lastIndexOf('\n', at - 1) + 1
    val bullet = bulletOf(text.substring(lineStart))
    return if (bullet != null) {
        ListEdit(text.removeRange(lineStart, lineStart + bullet.length), maxOf(lineStart, at - bullet.length))
    } else {
        ListEdit(text.substring(0, lineStart) + BULLET + text.substring(lineStart), at + BULLET.length)
    }
}
