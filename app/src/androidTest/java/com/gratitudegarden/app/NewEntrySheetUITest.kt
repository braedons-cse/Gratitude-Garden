package com.gratitudegarden.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.gratitudegarden.app.ui.theme.GratitudeGardenTheme
import com.gratitudegarden.app.ui.screens.NewEntrySheet
import com.gratitudegarden.app.ui.components.PhotoDraft
import com.gratitudegarden.app.ui.components.PhotoPickerRow
import com.gratitudegarden.app.ui.components.rememberPhotoDraft
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
                    onSubmit = { text, _, _, _ -> submittedText = text },
                    preparePhoto = { error("not used") },
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
        composeTestRule.onNodeWithTag("plant_it_button").performScrollTo().performClick()

        // 6. Verify the onSubmit callback was called with the exact text
        composeTestRule.runOnIdle {
            assertEquals("The submitted text should match the entered text", testThought, submittedText)
        }
    }

    @Test
    fun aPhotoAloneCantPlantAThought() {
        composeTestRule.setContent {
            GratitudeGardenTheme {
                NewEntrySheet(submitting = false, onDismiss = {}, onSubmit = { _, _, _, _ -> }, preparePhoto = { error("not used") })
            }
        }

        composeTestRule.onNodeWithTag("entry_photo_add").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithTag("plant_it_button").assertIsNotEnabled()
    }

    @Test
    fun aPickedMoodGoesWithTheThought() {
        var submitted: Pair<String, Int?>? = null
        composeTestRule.setContent {
            GratitudeGardenTheme {
                NewEntrySheet(
                    submitting = false,
                    onDismiss = {},
                    onSubmit = { text, _, mood, _ -> submitted = text to mood },
                    preparePhoto = { error("not used") },
                )
            }
        }

        composeTestRule.onNodeWithTag("new_entry_text_field").performTextInput("a slow Sunday")
        composeTestRule.onNodeWithTag("entry_mood_4").performScrollTo().performClick().assertIsSelected()
        composeTestRule.onNodeWithTag("entry_mood_2").assertIsNotSelected()
        composeTestRule.onNodeWithTag("plant_it_button").performScrollTo().performClick()

        composeTestRule.runOnIdle { assertEquals("a slow Sunday" to 4, submitted) }
    }

    @Test
    fun tappingThePickedMoodAgainTakesItOff() {
        var submittedMood: Int? = 0
        composeTestRule.setContent {
            GratitudeGardenTheme {
                NewEntrySheet(
                    submitting = false,
                    onDismiss = {},
                    onSubmit = { _, _, mood, _ -> submittedMood = mood },
                    preparePhoto = { error("not used") },
                )
            }
        }

        composeTestRule.onNodeWithTag("new_entry_text_field").performTextInput("not sure how I feel")
        composeTestRule.onNodeWithTag("entry_mood_4").performScrollTo().performClick()
        composeTestRule.onNodeWithTag("entry_mood_4").performClick().assertIsNotSelected()
        composeTestRule.onNodeWithTag("plant_it_button").performScrollTo().performClick()

        composeTestRule.runOnIdle { assertEquals(null, submittedMood) }
    }

    @Test
    fun aPickedPhotoShowsAndCanBeTakenOffAgain() {
        val cache = InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        val prepared = File(cache, "ui-test-photo.jpg").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        lateinit var draft: PhotoDraft
        composeTestRule.setContent {
            GratitudeGardenTheme {
                draft = rememberPhotoDraft { prepared }
                PhotoPickerRow(draft = draft, hasPhoto = draft.staged != null, shown = draft.staged, onRemove = draft::drop)
            }
        }

        composeTestRule.runOnIdle { draft.use(Uri.EMPTY) }

        composeTestRule.onNodeWithTag("entry_photo_thumbnail").assertIsDisplayed()
        composeTestRule.onNodeWithTag("entry_photo_remove").performClick()
        composeTestRule.onNodeWithTag("entry_photo_add").assertIsDisplayed()
        composeTestRule.runOnIdle {
            assertTrue(draft.staged == null)
            assertFalse("a dropped photo's staged file is deleted", prepared.exists())
        }
    }
}
