package com.example.ui.navigation

import com.example.ui.viewmodel.closeDownloader
import com.example.ui.viewmodel.openPerson
import com.example.ui.viewmodel.setDownloaderTab
import com.example.ui.viewmodel.setPersonAlerts
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.runtime.getValue
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
    Downloader,

    /** Story and repost alerts: its own screen. Deep link: offscroll://alerts */
    Alerts
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

    // Back on the feed means the Downloader (and any profile open inside it) is finished
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    LaunchedEffect(currentRoute) {
        if (currentRoute == NavRoute.Feed.name && uiState.isDownloaderOpen) {
            viewModel.profile.close()
            viewModel.closeDownloader()
        }
    }

    // Deep links while the app is already running (the activity is single-task, so they arrive as new intents)
    val activity = androidx.compose.ui.platform.LocalContext.current as? androidx.activity.ComponentActivity
    androidx.compose.runtime.DisposableEffect(activity, navController) {
        val listener = androidx.core.util.Consumer<android.content.Intent> { navController.handleDeepLink(it) }
        activity?.addOnNewIntentListener(listener)
        onDispose { activity?.removeOnNewIntentListener(listener) }
    }
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
                onOpenAlerts = { navController.navigate(NavRoute.Alerts.name) { launchSingleTop = true } },
                modifier = Modifier.fillMaxSize()
            )
        }
        composable(
            NavRoute.Alerts.name,
            deepLinks = listOf(androidx.navigation.navDeepLink { uriPattern = "offscroll://alerts" })
        ) {
            com.example.ui.components.AlertsScreen(
                people = uiState.people,
                onSetAlerts = { name, on -> viewModel.setPersonAlerts(name, on) },
                onOpenPerson = { name ->
                    navController.popBackStack()
                    viewModel.setDownloaderTab(com.example.ui.model.DownloaderTab.STORIES)
                    viewModel.openPerson(name)
                },
                onBack = { navController.popBackStack() }
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
