package com.gratitudegarden.app

import com.gratitudegarden.app.data.SubmitResult
import com.gratitudegarden.app.ui.garden.submitMessage
import org.junit.Assert.assertEquals
import org.junit.Test

/** [submitMessage] — what the Garden's toast and the reminder's inline reply both say. */
class SubmitMessageTest {

    @Test
    fun aPlantedThoughtReportsWhatWasAwarded() {
        assertEquals(
            "+3 coins · +10 XP · a kind thought planted 🌱",
            submitMessage(SubmitResult.Planted(coins = 3, xp = 10), freezing = false),
        )
    }

    @Test
    fun nothingAwardedSaysNoNumbers() {
        assertEquals(
            "a kind thought planted 🌱",
            submitMessage(SubmitResult.Planted(coins = null, xp = null), freezing = false),
        )
    }

    @Test
    fun aHeldStreakCreditsTheFreeze() {
        assertEquals(
            "+2 coins · +10 XP · a streak freeze kept your streak going ❄️",
            submitMessage(SubmitResult.Planted(coins = 2, xp = 10), freezing = true),
        )
    }

    @Test
    fun savedPromisesToPlantLater() {
        assertEquals(
            "Saved in your journal. It'll be planted as soon as it syncs.",
            submitMessage(SubmitResult.Saved, freezing = false),
        )
    }

    // The server's own words are never shown: only the reasons we raise on purpose.
    @Test
    fun refusalsUseFriendlyCopyOrTheGeneralLine() {
        assertEquals(
            "That's all your thoughts for today. Come back tomorrow.",
            submitMessage(SubmitResult.Refused("daily entry cap (10) reached"), freezing = false),
        )
        assertEquals(
            "The garden couldn't take that thought.",
            submitMessage(SubmitResult.Refused("entry id already used"), freezing = false),
        )
    }
}
