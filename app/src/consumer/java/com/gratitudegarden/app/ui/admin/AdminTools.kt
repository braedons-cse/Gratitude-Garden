package com.gratitudegarden.app.ui.admin

import androidx.compose.runtime.Composable

/**
 * Consumer half of the admin seam: no dashboard ships in the Play build. RLS is still the
 * real guard — this only keeps the client-side admin surface out of the store APK.
 */
object AdminTools {
    const val AVAILABLE = false

    @Suppress("UNUSED_PARAMETER")
    @Composable
    fun Dashboard(onBack: () -> Unit) = Unit
}
