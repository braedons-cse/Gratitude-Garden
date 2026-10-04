package com.gratitudegarden.app

import com.gratitudegarden.app.model.Prompts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** [Prompts.forDay]: one question a day, the same wherever it's asked for. */
class PromptsTest {

    private val day = LocalDate.of(2026, 10, 3)

    @Test
    fun aDayAlwaysGetsTheSameQuestion() {
        assertEquals(Prompts.forDay(day), Prompts.forDay(LocalDate.parse("2026-10-03")))
    }

    @Test
    fun theNextDayGetsAnother() {
        assertNotEquals(Prompts.forDay(day), Prompts.forDay(day.plusDays(1)))
    }

    @Test
    fun anotherStepsThroughEveryQuestionAndWraps() {
        val n = Prompts.ALL.size
        val seen = (0 until n).map { Prompts.forDay(day, it) }.toSet()
        assertEquals(Prompts.ALL.toSet(), seen)
        assertEquals(Prompts.forDay(day), Prompts.forDay(day, n))
    }

    @Test
    fun daysBeforeTheEpochStillPickOne() {
        assertTrue(Prompts.forDay(LocalDate.of(1960, 1, 1), -7) in Prompts.ALL)
    }

    @Test
    fun noQuestionIsListedTwice() {
        assertEquals(Prompts.ALL.size, Prompts.ALL.toSet().size)
    }
}
