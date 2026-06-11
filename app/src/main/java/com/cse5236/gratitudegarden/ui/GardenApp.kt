package com.cse5236.gratitudegarden.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cse5236.gratitudegarden.ui.auth.AuthViewModel
import com.cse5236.gratitudegarden.ui.components.BottomNav
import com.cse5236.gratitudegarden.ui.screens.GardenRoute
import com.cse5236.gratitudegarden.ui.screens.JournalRoute
import com.cse5236.gratitudegarden.ui.screens.LoginScreen
import com.cse5236.gratitudegarden.ui.screens.MeScreen
import com.cse5236.gratitudegarden.ui.screens.ShopScreen
import com.cse5236.gratitudegarden.ui.screens.SignUpScreen
import com.cse5236.gratitudegarden.ui.theme.GratitudeGardenTheme
import com.cse5236.gratitudegarden.ui.theme.PgBgSage
import io.github.jan.supabase.auth.status.SessionStatus

@Composable
fun GratitudeGardenApp() {
    GratitudeGardenTheme {
        val authVm: AuthViewModel = viewModel(factory = AuthViewModel.Factory)
        val status by authVm.sessionStatus.collectAsStateWithLifecycle()

        Surface(modifier = Modifier.fillMaxSize(), color = PgBgSage) {
            if (status is SessionStatus.Authenticated) {
                HomeScaffold(onSignOut = authVm::signOut)
            } else {
                AuthNav(authVm = authVm)
            }
        }
    }
}

@Composable
private fun AuthNav(authVm: AuthViewModel) {
    val nav = rememberNavController()
    val ui by authVm.ui.collectAsStateWithLifecycle()
    NavHost(navController = nav, startDestination = "login") {
        composable("login") {
            LoginScreen(
                onLogIn = authVm::signIn,
                onSignUp = { authVm.clearError(); nav.navigate("signup") },
                onForgotPassword = {},
                loading = ui.loading,
                error = ui.error,
            )
        }
        composable("signup") {
            SignUpScreen(
                onSignUp = authVm::signUp,
                onBackToLogin = { authVm.clearError(); nav.popBackStack() },
                loading = ui.loading,
                error = ui.error,
            )
        }
    }
}

@Composable
private fun HomeScaffold(onSignOut: () -> Unit) {
    val nav = rememberNavController()
    Scaffold(
        bottomBar = { BottomNav(nav) },
        containerColor = PgBgSage,
    ) { inner ->
        NavHost(
            navController = nav,
            startDestination = "garden",
            modifier = Modifier.padding(inner),
        ) {
            composable("garden") { GardenRoute() }
            composable("shop") { ShopScreen() }
            composable("journal") { JournalRoute() }
            composable("me") { MeScreen(onSignOut = onSignOut) }
        }
    }
}
