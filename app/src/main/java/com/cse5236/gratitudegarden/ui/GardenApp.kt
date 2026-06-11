package com.cse5236.gratitudegarden.ui

import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.cse5236.gratitudegarden.ui.screens.LoginScreen
import com.cse5236.gratitudegarden.ui.theme.GratitudeGardenTheme
import com.cse5236.gratitudegarden.ui.theme.PgBgSage
import com.cse5236.gratitudegarden.util.LogTags

/**
 * Root of the Positivity Garden app. For this milestone it lands on the Login
 * screen; navigation to Sign Up / Garden will hang off the callbacks here.
 */
@Composable
fun GratitudeGardenApp() {
    GratitudeGardenTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = PgBgSage) {
            LoginScreen(
                onLogIn = { email, _ ->
                    Log.d(LogTags.ACTION, "Log in tapped (email=$email)")
                },
                onForgotPassword = { Log.d(LogTags.ACTION, "Forgot password tapped") },
                onSignUp = { Log.d(LogTags.ACTION, "Sign up tapped") },
            )
        }
    }
}
