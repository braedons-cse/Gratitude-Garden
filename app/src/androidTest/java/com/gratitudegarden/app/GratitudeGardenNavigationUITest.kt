package com.gratitudegarden.app

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import org.junit.Rule
import org.junit.Test

/**
 * UI tests for Gratitude Garden navigation and launch
 *
 * Verifies:
 * 1. App launch and the login screen.
 * 2. Navigation between authentication screens.
 */
class GratitudeGardenNavigationUITest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    // UI test #1
    @Test
    fun testAppLaunch_displaysLogin() {

        // App starts at the Login screen.
        // We verify that the "Welcome back" header and login fields are present.
        composeTestRule.onNodeWithText("Welcome back").assertIsDisplayed()
        composeTestRule.onNodeWithTag("login_email_field").assertIsDisplayed()
        composeTestRule.onNodeWithTag("login_password_field").assertIsDisplayed()
        composeTestRule.onNodeWithTag("login_button").assertIsDisplayed()
    }

    // UI test #2
    @Test
    fun testNavigation_LoginToSignUpAndBack() {

        // 1. Click the "Plant your first seed" link to go to Sign Up
        composeTestRule.onNodeWithTag("login_to_signup_link").performClick()

        // 2. Verify we are on the sign up screen by checking for "Plant a seed"
        composeTestRule.onNodeWithText("Plant a seed").assertIsDisplayed()
        composeTestRule.onNodeWithTag("signup_name_field").assertIsDisplayed()
        composeTestRule.onNodeWithTag("signup_button").assertIsDisplayed()

        // 3. Navigate back to Login screen using the "Log in" link
        composeTestRule.onNodeWithTag("signup_to_login_link").performClick()

        // 4. Verify we are back on the login screen
        composeTestRule.onNodeWithText("Welcome back").assertIsDisplayed()
    }
}
