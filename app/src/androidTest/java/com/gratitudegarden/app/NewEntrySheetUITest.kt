package com.gratitudegarden.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.gratitudegarden.app.ui.theme.GratitudeGardenTheme
import com.gratitudegarden.app.ui.screens.NewEntrySheet
import org.junit.Rule
import org.junit.Test
import org.junit.Assert.assertEquals

/**
 * UI test for the NewEntrySheet composable in isolation.
 */
class NewEntrySheetUITest {

    @get:Rule
    val composeTestRule = createComposeRule()

    // UI test #3
    @Test
    fun testAddGratitudeEntry_submitsCorrectText() {
        var submittedText: String? = null
        val testThought = "I am grateful for my friends and family."

        composeTestRule.setContent {
            GratitudeGardenTheme {
                NewEntrySheet(
                    submitting = false,
                    onDismiss = {},
                    onSubmit = { text, _ -> submittedText = text }
                )
            }
        }

        // 1. Verify the placeholder is visible
        composeTestRule.onNodeWithText("Something you're grateful for…").assertIsDisplayed()

        // 2. Type a gratitude thought into the text field
        composeTestRule.onNodeWithTag("new_entry_text_field").performTextInput(testThought)

        // 3. Verify the text was entered correctly
        composeTestRule.onNodeWithText(testThought).assertIsDisplayed()

        // 4. Verify the "Plant it" button is now enabled
        composeTestRule.onNodeWithTag("plant_it_button").assertIsEnabled()

        // 5. Click the "Plant it" button
        composeTestRule.onNodeWithTag("plant_it_button").performClick()

        // 6. Verify the onSubmit callback was called with the exact text
        composeTestRule.runOnIdle {
            assertEquals("The submitted text should match the entered text", testThought, submittedText)
        }
    }
}
