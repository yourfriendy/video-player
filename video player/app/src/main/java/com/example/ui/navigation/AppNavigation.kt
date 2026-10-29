package com.example.ui.navigation

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.components.SettingsScreen
import com.example.ui.components.VideoFeedScreen
import com.example.ui.viewmodel.VideoFeedUiState
import com.example.ui.viewmodel.VideoFeedViewModel

enum class NavRoute {
    Feed,
    Settings
}

/**
 * The app is the feed. Settings opens from the gear in the feed's top bar and slides in on top.
 * (No bottom bar any more: the video gets the whole screen.)
 */
@Composable
fun AppNavigation(
    uiState: VideoFeedUiState,
    viewModel: VideoFeedViewModel,
    onEnterPiP: () -> Unit,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val systemNavInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    NavHost(
        navController = navController,
        startDestination = NavRoute.Feed.name,
        modifier = modifier.fillMaxSize(),
        // Short, cheap transitions (the default 700ms cross-fade makes every screen change feel laggy)
        enterTransition = { slideInHorizontally(tween(220)) { it / 3 } + fadeIn(tween(160)) },
        exitTransition = { fadeOut(tween(120)) },
        popEnterTransition = { fadeIn(tween(140)) },
        popExitTransition = { slideOutHorizontally(tween(200)) { it / 3 } + fadeOut(tween(160)) }
    ) {
        composable(NavRoute.Feed.name) {
            VideoFeedScreen(
                uiState = uiState,
                viewModel = viewModel,
                onEnterPiP = onEnterPiP,
                onOpenSettings = {
                    navController.navigate(NavRoute.Settings.name) { launchSingleTop = true }
                },
                modifier = Modifier.fillMaxSize(),
                bottomOverlayPadding = systemNavInset + 16.dp
            )
        }
        composable(NavRoute.Settings.name) {
            SettingsScreen(
                uiState = uiState,
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize(),
                onBack = { navController.popBackStack() }
            )
        }
    }
}
