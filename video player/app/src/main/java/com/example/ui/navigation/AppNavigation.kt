package com.example.ui.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.components.VideoFeedScreen
import com.example.ui.viewmodel.VideoFeedUiState
import com.example.ui.viewmodel.VideoFeedViewModel

enum class NavRoute(val title: String, val icon: ImageVector) {
    Feed("Feed", Icons.Default.Home),
    Discover("Discover", Icons.Default.Explore),
    Library("Library", Icons.Default.VideoLibrary),
    Settings("Settings", Icons.Default.Settings)
}

@Composable
fun AppNavigation(
    uiState: VideoFeedUiState,
    viewModel: VideoFeedViewModel,
    onEnterPiP: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: NavRoute.Feed.name

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isCompact = maxWidth < 600.dp
        val isFeedRoute = currentRoute == NavRoute.Feed.name
        val isNavVisible = if (isFeedRoute) {
            uiState.areControlsVisible && !uiState.isInPictureInPicture
        } else {
            !uiState.isInPictureInPicture
        }

        Row(modifier = Modifier.fillMaxSize()) {
            // Wide Screen Navigation Rail (Foldables / Tablets / Landscape)
            this@Row.AnimatedVisibility(
                visible = !isCompact && isNavVisible,
                enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut()
            ) {
                NavigationRail(
                    containerColor = Color(0xF2090A12),
                    contentColor = Color.White
                ) {
                    NavRoute.values().forEach { route ->
                        NavigationRailItem(
                            icon = { Icon(route.icon, contentDescription = route.title) },
                            label = { Text(route.title) },
                            selected = currentRoute == route.name,
                            onClick = {
                                if (currentRoute != route.name) {
                                    navController.navigate(route.name) {
                                        popUpTo(NavRoute.Feed.name) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            }
                        )
                    }
                }
            }

            // Screen Viewport (True Edge-to-Edge Full Screen for Feed)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
            ) {
                NavHost(
                    navController = navController,
                    startDestination = NavRoute.Feed.name,
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable(NavRoute.Feed.name) {
                        VideoFeedScreen(
                            uiState = uiState,
                            viewModel = viewModel,
                            onEnterPiP = onEnterPiP,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    composable(NavRoute.Discover.name) {
                        com.example.ui.components.DiscoverScreen(
                            uiState = uiState,
                            viewModel = viewModel,
                            onNavigateToFeed = {
                                navController.navigate(NavRoute.Feed.name) {
                                    popUpTo(NavRoute.Feed.name) { inclusive = false }
                                    launchSingleTop = true
                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = if (isCompact) 80.dp else 0.dp)
                        )
                    }
                    composable(NavRoute.Library.name) {
                        com.example.ui.components.LibraryScreen(
                            uiState = uiState,
                            viewModel = viewModel,
                            onNavigateToFeed = {
                                navController.navigate(NavRoute.Feed.name) {
                                    popUpTo(NavRoute.Feed.name) { inclusive = false }
                                    launchSingleTop = true
                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = if (isCompact) 80.dp else 0.dp)
                        )
                    }
                    composable(NavRoute.Settings.name) {
                        com.example.ui.components.SettingsScreen(
                            uiState = uiState,
                            viewModel = viewModel,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = if (isCompact) 80.dp else 0.dp)
                        )
                    }
                }

                // Floating Glass Bottom Navigation Bar for Compact Devices
                if (isCompact) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isNavVisible,
                        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                        modifier = Modifier.align(Alignment.BottomCenter)
                    ) {
                        NavigationBar(
                            containerColor = Color(0xF2090A12),
                            contentColor = Color.White,
                            tonalElevation = 8.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    width = 1.dp,
                                    color = Color(0x22FFFFFF),
                                    shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp)
                                )
                        ) {
                            NavRoute.values().forEach { route ->
                                NavigationBarItem(
                                    icon = { Icon(route.icon, contentDescription = route.title) },
                                    label = { Text(route.title) },
                                    selected = currentRoute == route.name,
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Color(0xFFC084FC),
                                        selectedTextColor = Color(0xFFC084FC),
                                        indicatorColor = Color(0xFF7C3AED).copy(alpha = 0.28f),
                                        unselectedIconColor = Color(0xFF94A3B8),
                                        unselectedTextColor = Color(0xFF94A3B8)
                                    ),
                                    onClick = {
                                        if (currentRoute != route.name) {
                                            navController.navigate(route.name) {
                                                popUpTo(NavRoute.Feed.name) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
