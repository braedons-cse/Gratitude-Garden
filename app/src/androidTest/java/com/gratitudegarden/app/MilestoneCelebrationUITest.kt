package com.gratitudegarden.app

import android.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import com.gratitudegarden.app.ui.garden.Milestone
import com.gratitudegarden.app.ui.garden.MilestoneCard
import com.gratitudegarden.app.ui.garden.MilestoneCelebration
import com.gratitudegarden.app.ui.theme.GratitudeGardenTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The 7 / 30 / 100-day moment, and the card its Share button sends. */
class MilestoneCelebrationUITest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun aWeekShowsItsCountAndClosesWithKeepGrowing() {
        var closed = false
        composeTestRule.setContent {
            GratitudeGardenTheme {
                MilestoneCelebration(
                    milestone = Milestone(7),
                    backdropSlug = null,
                    onClose = { closed = true },
                    animate = false,
                )
            }
        }

        composeTestRule.onNodeWithTag("milestone_dialog").assertIsDisplayed()
        composeTestRule.onNodeWithText("7 days").assertIsDisplayed()
        composeTestRule.onNodeWithText("A week of gratitude").assertIsDisplayed()
        composeTestRule.onNodeWithTag("milestone_share").assertIsDisplayed()

        composeTestRule.onNodeWithText("Keep growing").performClick()
        composeTestRule.runOnIdle { assertTrue(closed) }
    }

    @Test
    fun theCardIsFullSizeAndPainted() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val card = MilestoneCard.render(context, 30, "backdrop.misty_forest")

        assertEquals(MilestoneCard.WIDTH, card.width)
        assertEquals(MilestoneCard.HEIGHT, card.height)
        // Not one flat colour: the scene and the text were drawn.
        val corner = card.getPixel(5, 5)
        val stage = card.getPixel(card.width / 2, card.width / 2)
        assertTrue(corner != stage)
        assertTrue(Color.alpha(stage) == 255)
    }
}
