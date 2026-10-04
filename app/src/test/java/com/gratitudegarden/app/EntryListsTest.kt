package com.gratitudegarden.app

import com.gratitudegarden.app.util.EntryLine
import com.gratitudegarden.app.util.ListEdit
import com.gratitudegarden.app.util.continueList
import com.gratitudegarden.app.util.entryLines
import com.gratitudegarden.app.util.toggleBullet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Lists in an entry: how they're read back, and what Enter and the list button do. */
class EntryListsTest {

    /** [old] with a newline typed at [at], as the text field reports it. */
    private fun enter(old: String, at: Int = old.length): ListEdit? =
        continueList(old, old.substring(0, at) + "\n" + old.substring(at), at + 1)

    @Test
    fun linesWithABulletAreItems() {
        assertEquals(
            listOf(
                EntryLine.Plain("Today:"),
                EntryLine.Item("tea"),
                EntryLine.Item("rain"),
                EntryLine.Item("a call"),
                EntryLine.Plain(""),
                EntryLine.Plain("-no space, not an item"),
            ),
            entryLines("Today:\n• tea\n- rain\n* a call\n\n-no space, not an item"),
        )
    }

    @Test
    fun enterAfterAnItemStartsTheNext() {
        assertEquals(ListEdit("• tea\n• ", 8), enter("• tea"))
    }

    @Test
    fun enterKeepsAHandTypedBullet() {
        assertEquals(ListEdit("x\n- rain\n- ", 11), enter("x\n- rain"))
    }

    @Test
    fun enterMidItemSplitsItIntoTwo() {
        assertEquals(ListEdit("• tea\n• cake", 8), enter("• teacake", at = 5))
    }

    @Test
    fun enterOnAnEmptyItemEndsTheList() {
        assertEquals(ListEdit("• tea\n", 6), enter("• tea\n• "))
    }

    @Test
    fun enterOnAPlainLineIsLeftAlone() {
        assertNull(enter("tea"))
        assertNull(enter(""))
    }

    @Test
    fun anythingButOneNewlineIsLeftAlone() {
        assertNull(continueList("• tea", "• teas", 6))
        assertNull(continueList("• tea", "• tea\n\n", 7))
        // A paste that ends in a newline isn't a key press.
        assertNull(continueList("• tea", "• tea and cake\n", 15))
    }

    @Test
    fun theListButtonMakesAnItemAndTakesItAway() {
        val made = toggleBullet("tea\nrain", 6)
        assertEquals(ListEdit("tea\n• rain", 8), made)
        assertEquals(ListEdit("tea\nrain", 6), toggleBullet(made.text, made.cursor))
    }

    @Test
    fun theListButtonWorksOnAnEmptyField() {
        assertEquals(ListEdit("• ", 2), toggleBullet("", 0))
    }

    @Test
    fun takingABulletAwayNeverPutsTheCursorOnTheLineBefore() {
        assertEquals(ListEdit("tea\nrain", 4), toggleBullet("tea\n• rain", 5))
    }
}
