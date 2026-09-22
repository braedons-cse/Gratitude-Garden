package com.gratitudegarden.app.ui.admin

import androidx.compose.runtime.Composable
import com.gratitudegarden.app.ui.screens.AdminDashboardScreen

/**
 * Staging half of the admin seam. The consumer flavor has a same-named stub, so `main`
 * compiles against either and the dashboard's code never reaches the Play build.
 */
object AdminTools {
    const val AVAILABLE = true

    @Composable
    fun Dashboard(onBack: () -> Unit) = AdminDashboardScreen(onBack = onBack)
}
