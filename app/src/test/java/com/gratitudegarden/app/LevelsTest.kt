package com.gratitudegarden.app

import com.gratitudegarden.app.data.LevelProgress
import com.gratitudegarden.app.data.levelForXp
import com.gratitudegarden.app.data.levelProgress
import com.gratitudegarden.app.data.xpForLevel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The XP curve: level n starts at 10 * (n² - 1) XP. [levelForXp] mirrors `level_for_xp()`
 * on the server, which sets `profiles.level`; the app uses its own copy only to draw the
 * progress bar, so the two have to agree at every threshold.
 */
class LevelsTest {

    @Test
    fun thresholdsFollowTheCurve() {
        assertEquals(listOf(0, 0, 30, 80, 150, 240, 350), (0..6).map(::xpForLevel))
    }

    @Test
    fun eachLevelStartsExactlyAtItsThreshold() {
        // The same pairs the migration's dry run checked against level_for_xp().
        val expected = mapOf(
            0 to 1, 29 to 1, 30 to 2, 79 to 2, 80 to 3, 149 to 3,
            150 to 4, 239 to 4, 240 to 5, 349 to 5, 350 to 6, 100_000 to 100,
        )
        expected.forEach { (xp, level) -> assertEquals("xp $xp", level, levelForXp(xp)) }
    }

    @Test
    fun negativeXpIsLevelOne() {
        assertEquals(1, levelForXp(-5))
    }

    @Test
    fun theLevelBracketsTheXpEverywhere() {
        for (xp in 0..5_000) {
            val level = levelForXp(xp)
            assertTrue("xp $xp", xpForLevel(level) <= xp && xp < xpForLevel(level + 1))
        }
    }

    @Test
    fun theLargestXpDoesNotOverflow() {
        val level = levelForXp(Int.MAX_VALUE)
        assertTrue(xpForLevel(level) <= Int.MAX_VALUE)
        assertEquals(Int.MAX_VALUE, xpForLevel(level + 1))
    }

    @Test
    fun progressIsMeasuredWithinTheLevel() {
        assertEquals(LevelProgress(level = 1, xpIntoLevel = 0, xpForNext = 30), levelProgress(0))
        assertEquals(LevelProgress(level = 2, xpIntoLevel = 20, xpForNext = 50), levelProgress(50))
        assertEquals(0.4f, levelProgress(50).fraction, 0.0001f)
        assertEquals(LevelProgress(level = 5, xpIntoLevel = 0, xpForNext = 110), levelProgress(240))
    }
}
