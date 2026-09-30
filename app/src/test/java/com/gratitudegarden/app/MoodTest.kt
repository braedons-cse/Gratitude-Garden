package com.gratitudegarden.app

import com.gratitudegarden.app.model.Mood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The mood scale. Only the score is stored (`gratitude_entries.mood`, checked 1–5 on the
 * server), so the scores and their order must stay as they are.
 */
class MoodTest {

    @Test
    fun scoresRunOneToFiveFromRoughToGreat() {
        assertEquals(listOf(1, 2, 3, 4, 5), Mood.entries.map { it.score })
        assertEquals(listOf("rough", "low", "okay", "good", "great"), Mood.entries.map { it.label })
    }

    @Test
    fun eachScoreMeansItsMood() {
        Mood.entries.forEach { assertEquals(it, Mood.of(it.score)) }
    }

    @Test
    fun noScoreOrAnUnknownOneIsNoMood() {
        assertNull(Mood.of(null))
        assertNull(Mood.of(0))
        assertNull(Mood.of(6))
    }
}
