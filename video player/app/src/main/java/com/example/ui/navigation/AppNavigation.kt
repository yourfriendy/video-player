package com.example.ui.navigation

import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.FastOutSlowInEasing

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.components.SettingsScreen
import com.example.ui.components.VideoFeedScreen
import com.example.ui.viewmodel.AppUpdateViewModel
import com.example.ui.viewmodel.VideoFeedUiState
import com.example.ui.viewmodel.VideoFeedViewModel

enum class NavRoute {
    Feed,
    Settings,
    Downloader
}

/**
 * The app is the feed. Settings opens from the gear in the feed's top bar and slides in on top.
 * (No bottom bar any more: the video gets the whole screen.)
 */
@Composable
fun AppNavigation(
    uiState: VideoFeedUiState,
    viewModel: VideoFeedViewModel,
    updateViewModel: AppUpdateViewModel,
    onEnterPiP: () -> Unit,
    onRequestVideoAccess: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val systemNavInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // The download icon, a shared link, or a story notification all just set this flag: show the full page
    LaunchedEffect(uiState.isDownloaderOpen) {
        if (uiState.isDownloaderOpen) {
            navController.navigate(NavRoute.Downloader.name) { launchSingleTop = true }
        }
    }

    NavHost(
        navController = navController,
        startDestination = NavRoute.Feed.name,
        modifier = modifier.fillMaxSize(),
        // Short, cheap transitions (the default 700ms cross-fade makes every screen change feel laggy)
        // offscroll_motion_navigation
        enterTransition = {
            slideInHorizontally(
                animationSpec = tween(280, easing = FastOutSlowInEasing)
            ) { it / 4 } +
            fadeIn(tween(240)) +
            scaleIn(initialScale = 0.985f, animationSpec = tween(240))
        },
        exitTransition = {
            fadeOut(tween(160)) +
            scaleOut(targetScale = 0.985f, animationSpec = tween(160))
        },
        popEnterTransition = {
            fadeIn(tween(220)) +
            scaleIn(initialScale = 0.985f, animationSpec = tween(220))
        },
        popExitTransition = {
            slideOutHorizontally(
                animationSpec = tween(260, easing = FastOutSlowInEasing)
            ) { it / 4 } + fadeOut(tween(180))
        }
    ) {
        composable(NavRoute.Feed.name) {
            VideoFeedScreen(
                uiState = uiState,
                viewModel = viewModel,
                onEnterPiP = onEnterPiP,
                onRequestVideoAccess = onRequestVideoAccess,
                onOpenSettings = {
                    navController.navigate(NavRoute.Settings.name) { launchSingleTop = true }
                },
                modifier = Modifier.fillMaxSize(),
                bottomOverlayPadding = systemNavInset + 16.dp
            )
        }
        composable(NavRoute.Downloader.name) {
            com.example.ui.components.DownloaderScreen(
                uiState = uiState,
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                modifier = Modifier.fillMaxSize()
            )
        }
        composable(NavRoute.Settings.name) {
            SettingsScreen(
                uiState = uiState,
                viewModel = viewModel,
                updateViewModel = updateViewModel,
                modifier = Modifier.fillMaxSize(),
                onBack = { navController.popBackStack() }
            )
        }
    }
}
