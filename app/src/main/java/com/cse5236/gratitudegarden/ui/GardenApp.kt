package com.cse5236.gratitudegarden.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cse5236.gratitudegarden.ui.auth.AuthViewModel
import com.cse5236.gratitudegarden.ui.components.BottomNav
import com.cse5236.gratitudegarden.ui.components.HomeNavItems
import com.cse5236.gratitudegarden.ui.screens.AdminDashboardScreen
import com.cse5236.gratitudegarden.ui.screens.GardenRoute
import com.cse5236.gratitudegarden.ui.screens.JournalRoute
import com.cse5236.gratitudegarden.ui.screens.LoginScreen
import com.cse5236.gratitudegarden.ui.screens.MeScreen
import com.cse5236.gratitudegarden.ui.screens.ShopRoute
import com.cse5236.gratitudegarden.ui.screens.SignUpScreen
import com.cse5236.gratitudegarden.ui.theme.GratitudeGardenTheme
import com.cse5236.gratitudegarden.ui.theme.PgBgSage
import com.cse5236.gratitudegarden.util.LogComposableLifecycle
import com.cse5236.gratitudegarden.util.LogTags
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

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
            LogComposableLifecycle(LogTags.LOGIN_SCREEN)
            LoginScreen(
                onLogIn = authVm::signIn,
                onSignUp = { authVm.clearError(); nav.navigate("signup") },
                onForgotPassword = {},
                loading = ui.loading,
                error = ui.error,
            )
        }
        composable(
            route = "signup",
            // Sign-up slides in from the right over login, and back out on return.
            enterTransition = { slideInHorizontally(tween(320)) { it } + fadeIn(tween(220)) },
            exitTransition = { fadeOut(tween(180)) },
            popExitTransition = { slideOutHorizontally(tween(280)) { it } + fadeOut(tween(200)) },
        ) {
            LogComposableLifecycle(LogTags.SIGNUP_SCREEN)
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
    val tabs = HomeNavItems
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()
    var showAdmin by rememberSaveable { mutableStateOf(false) }
    // Seed the user tapped "Plant" on in the Shop; the Garden opens in placement
    // mode so they can tap a spot for it.
    var pendingPlacement by rememberSaveable { mutableStateOf<String?>(null) }
    // While a plant is being dragged in the Garden, freeze the pager so the swipe
    // gesture doesn't fight the drag.
    var gardenDragging by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                BottomNav(
                    items = tabs,
                    selectedIndex = pagerState.currentPage,
                    onSelect = { i -> scope.launch { pagerState.animateScrollToPage(i) } },
                )
            },
            containerColor = PgBgSage,
        ) { inner ->
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize().padding(inner),
                beyondViewportPageCount = 1,
                userScrollEnabled = !gardenDragging,
                key = { tabs[it].route },
            ) { page ->
                // As a page is dragged away it shrinks + fades; the incoming page
                // grows + fades in. This is the "tab arrives" motion, driven by the
                // swipe itself (and by animateScrollToPage when a tab is tapped).
                val offset =
                    ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction)
                        .absoluteValue.coerceIn(0f, 1f)
                Box(
                    Modifier.fillMaxSize().graphicsLayer {
                        val t = 1f - offset
                        alpha = 0.45f + 0.55f * t
                        val s = 0.90f + 0.10f * t
                        scaleX = s; scaleY = s
                    },
                ) {
                    when (tabs[page].route) {
                        "garden" -> GardenRoute(
                            placingItemId = pendingPlacement,
                            onPlacementDone = { pendingPlacement = null },
                            onDragActive = { gardenDragging = it },
                        )
                        "shop" -> ShopRoute(
                            onRequestPlant = { itemId ->
                                pendingPlacement = itemId
                                scope.launch { pagerState.animateScrollToPage(0) }
                            },
                        )
                        "journal" -> JournalRoute()
                        "me" -> MeScreen(onSignOut = onSignOut, onOpenAdmin = { showAdmin = true })
                    }
                }
            }
        }

        // Admin dashboard slides in over the whole shell (it isn't a tab).
        AnimatedVisibility(
            visible = showAdmin,
            enter = slideInHorizontally(tween(320)) { it } + fadeIn(tween(200)),
            exit = slideOutHorizontally(tween(280)) { it } + fadeOut(tween(180)),
        ) {
            Box(Modifier.fillMaxSize().background(PgBgSage).systemBarsPadding()) {
                AdminDashboardScreen(onBack = { showAdmin = false })
            }
        }
    }

    // Hardware back closes the admin overlay before leaving the app.
    BackHandler(enabled = showAdmin) { showAdmin = false }
}
