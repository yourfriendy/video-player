package com.example.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.Ds
import com.example.ui.theme.auroraGlow
import com.example.ui.theme.rememberMotionEnabled
import com.example.ui.theme.staggeredEntrance
import com.example.ui.viewmodel.AppUpdateViewModel
import com.example.ui.viewmodel.VideoFeedUiState
import com.example.ui.viewmodel.VideoFeedViewModel
import com.example.ui.viewmodel.clearWatchHistory
import com.example.ui.viewmodel.refreshVideos

/**
 * Settings & Guide, one scrolling page:
 * the app card, your library in numbers, updates, a visual guide to every gesture, appearance, library tools,
 * a copyable problem report and the credits. A glass top bar fades in once you scroll.
 */
// offscroll_settings_motion
@Composable
fun SettingsScreen(
    uiState: VideoFeedUiState,
    viewModel: VideoFeedViewModel,
    updateViewModel: AppUpdateViewModel,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {}
) {
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val context = androidx.compose.ui.platform.LocalContext.current
    val motion = rememberMotionEnabled()
    val listState = rememberLazyListState()
    var showTour by remember { mutableStateOf(false) }
    var confirmClearHistory by remember { mutableStateOf(false) }

    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (_: Exception) {
            ""
        }
    }
    val folderCount = remember(uiState.allVideos) { uiState.allVideos.map { it.bucketDisplayName }.distinct().size }

    val scrolled by remember {
        derivedStateOf { listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 160 }
    }
    val barAlpha by animateFloatAsState(
        targetValue = if (scrolled) 1f else 0f,
        animationSpec = tween(if (motion) 220 else 60),
        label = "settings_bar_alpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF090A10))
            .auroraGlow()
            .testTag("settings_screen")
    ) {
        // Wide screens: keep the column readable and centred
        val sidePad = ((androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.dp - Ds.MaxContentWidth) / 2)
            .coerceAtLeast(16.dp)
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().animateContentSize(),
            contentPadding = PaddingValues(
                start = sidePad, end = sidePad, top = statusBarPadding + 60.dp, bottom = navBarPadding + 32.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Column(modifier = Modifier.staggeredEntrance(0)) {
                    Text(
                        text = stringResource(R.string.settings_guide),
                        color = Color.White,
                        fontSize = Ds.Type.Display,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "The app, your library and how to move around",
                        color = Ds.Palette.Muted,
                        fontSize = Ds.Type.Body,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }

            item { SettingsHero(versionName, updateViewModel.installedBuild, Modifier.staggeredEntrance(1)) }

            // Your library in numbers
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max).staggeredEntrance(2),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatTile(Icons.Default.VideoLibrary, "${uiState.allVideos.size}", "Videos", Ds.Palette.VioletSoft, Modifier.weight(1f).fillMaxHeight())
                    StatTile(Icons.Default.Storage, uiState.totalStorageFormatted, "On this phone", Ds.Palette.Cyan, Modifier.weight(1f).fillMaxHeight())
                    StatTile(Icons.Default.Folder, "$folderCount", "Folders", Ds.Palette.Warn, Modifier.weight(1f).fillMaxHeight())
                }
            }

            // Updates
            item {
                SectionHeader(
                    "Updates", "Stay on the newest build", Icons.Default.SystemUpdate, Ds.Palette.Cyan,
                    Modifier.staggeredEntrance(3)
                )
            }
            item { Box(Modifier.staggeredEntrance(4)) { AppUpdateCard(viewModel = updateViewModel) } }

            // The guide
            item {
                SectionHeader(
                    "Player guide", "Everything works without buttons", Icons.Default.TouchApp, Ds.Palette.PinkSoft,
                    Modifier.staggeredEntrance(5)
                )
            }
            item { GestureMap(Modifier.staggeredEntrance(6)) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.staggeredEntrance(7)) {
                    GestureHelpCard(
                        Icons.Default.Gesture, "Drag left or right",
                        "Anywhere on the screen to seek forward or back, with a live time readout",
                        Ds.Palette.PinkSoft, 1
                    )
                    GestureHelpCard(
                        Icons.Default.Swipe, "Swipe up or down",
                        "Go to the next or previous video",
                        Ds.Palette.VioletSoft, 2
                    )
                    GestureHelpCard(
                        Icons.Default.TouchApp, "Tap, and double tap",
                        "Tap once to show the controls, tap again to hide them for full screen. Double tap anywhere to pause or play",
                        Ds.Palette.Orange, 3
                    )
                    GestureHelpCard(
                        Icons.Default.LightMode, "Brightness",
                        "Slide up or down along the left edge of the screen",
                        Ds.Palette.Warn, 4
                    )
                    GestureHelpCard(
                        Icons.Default.VolumeUp, "Volume",
                        "Slide up or down along the right edge of the screen",
                        Ds.Palette.Cyan, 5
                    )
                    GestureHelpCard(
                        Icons.Default.Favorite, "Like",
                        "Tap the heart on the right to like or unlike. A double tap never likes, so nothing is liked by accident",
                        Ds.Palette.Pink
                    )
                    OutlineButton(
                        text = "Replay the quick tour",
                        onClick = { showTour = true },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Look
            item {
                SectionHeader(
                    "Look", "Make it yours", Icons.Default.AutoAwesome, Ds.Palette.VioletSoft,
                    Modifier.staggeredEntrance(8)
                )
            }
            item {
                ToggleRow(
                    icon = Icons.Default.DarkMode,
                    title = "Pure black background",
                    subtitle = "Saves battery on OLED screens. Light theme is planned.",
                    checked = com.example.ui.theme.Appearance.amoled,
                    onCheckedChange = { com.example.ui.theme.Appearance.setAmoled(context, it) },
                    modifier = Modifier.staggeredEntrance(9)
                )
            }

            // Library tools
            item {
                SectionHeader(
                    "Library tools", "Keep your feed tidy", Icons.Default.Tune, Ds.Palette.Good,
                    Modifier.staggeredEntrance(10)
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max).staggeredEntrance(11),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ToolTile(
                        Icons.Default.Refresh, "Rescan", "Look for new videos on the phone",
                        Ds.Palette.Cyan, { viewModel.refreshVideos() }, Modifier.weight(1f).fillMaxHeight()
                    )
                    ToolTile(
                        Icons.Default.Delete, "Clear history", "Forget what you watched",
                        Ds.Palette.Bad, { confirmClearHistory = true }, Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }

            // Help: one copyable report instead of "it doesn't work"
            item {
                SectionHeader(
                    "If something doesn't work", "Copy a short report", Icons.Default.BugReport, Ds.Palette.Warn,
                    Modifier.staggeredEntrance(12)
                )
            }
            item {
                Column(modifier = Modifier.staggeredEntrance(12), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Copies a short report (app version, phone model, alert status, recent problems) that you can paste into a message. " +
                            "It contains no passwords or login cookies.",
                        color = Ds.Palette.Muted, fontSize = Ds.Type.Label
                    )
                    OutlineButton(
                        text = "Copy diagnostics",
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("OffScroll diagnostics", com.example.data.DiagnosticsReport.build(context)))
                            Feedback.show("Diagnostics copied")
                        }
                    )
                }
            }

            // Credits (last item)
            item {
                Box(Modifier.staggeredEntrance(12)) {
                    DevCreditCard(versionName = versionName, build = updateViewModel.installedBuild)
                }
            }
        }

        // Glass top bar: only the back button at first, the title and a soft backdrop fade in as you scroll
        Box(modifier = Modifier.fillMaxWidth().height(statusBarPadding + 56.dp).align(Alignment.TopCenter)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = barAlpha }
                    .background(Brush.verticalGradient(listOf(Color(0xF2090A10), Color(0xCC090A10))))
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = statusBarPadding).height(56.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back_button")) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text(
                    text = stringResource(R.string.settings_guide),
                    color = Color.White,
                    fontSize = Ds.Type.Title,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.graphicsLayer { alpha = barAlpha }
                )
            }
        }
    }

    if (showTour) GestureTourDialog(onDismiss = { showTour = false })

    if (confirmClearHistory) {
        AlertDialog(
            onDismissRequest = { confirmClearHistory = false },
            title = { Text("Clear watch history?") },
            text = { Text("Recently played and where you stopped in each video are forgotten. Favorites and playlists stay.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearWatchHistory()
                    confirmClearHistory = false
                }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClearHistory = false }) { Text("Cancel") } }
        )
    }
}
