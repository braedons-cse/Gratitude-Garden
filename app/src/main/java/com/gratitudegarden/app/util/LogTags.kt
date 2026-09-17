package com.gratitudegarden.app.util

object LogTags {
    // App-wide tags
    const val LIFECYCLE = "GG_LIFECYCLE"
    const val SCREEN = "GG_SCREEN"
    const val ACTION = "GG_ACTION"

    // Compose-lifecycle logging (see util.LogComposableLifecycle)
    const val MAIN_ACTIVITY = "MainActivityLifecycle"
    const val LOGIN_SCREEN = "LoginScreenLifecycle"
    const val SIGNUP_SCREEN = "SignUpScreenLifecycle"
    const val GARDEN_SCREEN = "GardenScreenLifecycle"
    const val JOURNAL_SCREEN = "JournalScreenLifecycle"
    const val ENTRY_SCREEN = "EntryScreenLifecycle"
    const val APP_LOGIC = "GratitudeGardenLogic"
}
