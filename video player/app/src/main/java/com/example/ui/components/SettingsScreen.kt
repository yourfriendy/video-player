package com.example.ui.components

import com.example.ui.viewmodel.refreshVideos
import com.example.ui.viewmodel.clearWatchHistory
import com.example.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.animateContentSize

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.GlassBackground
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.auroraGlow
import com.example.ui.theme.staggeredEntrance
import com.example.ui.viewmodel.AppUpdateViewModel
import com.example.ui.viewmodel.VideoFeedUiState
import com.example.ui.viewmodel.VideoFeedViewModel
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.IconButton
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.navigationBars

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
    val versionName = remember {
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF090A10))
            .auroraGlow()
            .padding(top = statusBarPadding)
            .testTag("settings_screen")
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .animateContentSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = navBarPadding + 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.staggeredEntrance(0),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Column {
                        Text(
                            text = stringResource(R.string.settings_guide),
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.gestures_and_storage_info),
                            color = Color(0xFF94A3B8),
                            fontSize = 14.sp
                        )
                    }
                }
            }

            // In-app updates (reads this app's GitHub releases)
            item {
                Text(
                    text = stringResource(R.string.app_updates),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.staggeredEntrance(1)
                )
            }

            item { Box(Modifier.staggeredEntrance(2)) { AppUpdateCard(viewModel = updateViewModel) } }

            // Gesture Cheatsheet
            item {
                Text(
                    text = stringResource(R.string.player_gestures),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.staggeredEntrance(3)
                )
            }

            item {
                Column(
                    modifier = Modifier.staggeredEntrance(4),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    GestureHelpCard(
                        icon = Icons.Default.Gesture,
                        title = "Horizontal Drag (VLC Style)",
                        description = "Drag left or right anywhere on screen to seek forward or backward with live HUD"
                    )
                    GestureHelpCard(
                        icon = Icons.Default.Swipe,
                        title = "Vertical Swipe",
                        description = "Swipe up or down to seamlessly switch to the next or previous video"
                    )
                    GestureHelpCard(
                        icon = Icons.Default.TouchApp,
                        title = "Tap & Double Tap",
                        description = "Tap once to show the controls, tap again to play or pause; double tap anywhere to like a video"
                    )
                    GestureHelpCard(
                        icon = Icons.Default.Swipe,
                        title = "Brightness (left edge)",
                        description = "Slide up or down along the left edge of the screen to change brightness"
                    )
                    GestureHelpCard(
                        icon = Icons.Default.Swipe,
                        title = "Volume (right edge)",
                        description = "Slide up or down along the right edge of the screen to change the volume"
                    )
                }
            }

            // Storage & Library Management
            item {
                Text(
                    text = stringResource(R.string.storage),
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.staggeredEntrance(5)
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth().staggeredEntrance(6),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = GlassBackground)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, GlassBorder, RoundedCornerShape(20.dp))
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Storage,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "${uiState.allVideos.size} Videos Indexed",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Total Size: ${uiState.totalStorageFormatted}",
                                    color = Color(0xFF94A3B8),
                                    fontSize = 13.sp
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Button(
                                onClick = { viewModel.refreshVideos() },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E293B)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.rescan), fontSize = 13.sp)
                            }

                            Button(
                                onClick = { viewModel.clearWatchHistory() },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3B1E2B)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFB7185), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(stringResource(R.string.clear_history), color = Color(0xFFFB7185), fontSize = 13.sp)
                            }
                        }
                    }
                }
            }

            // About
            item {
                Card(
                    modifier = Modifier.fillMaxWidth().staggeredEntrance(7),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = GlassBackground)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, GlassBorder, RoundedCornerShape(20.dp))
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFFA78BFA),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = stringResource(R.string.offscroll),
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "OffScroll${if (versionName.isNotBlank()) " v$versionName" else ""} • Offline Reel Engine",
                                color = Color(0xFF94A3B8),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // Help: one copyable report instead of "it doesn't work"
            item {
                Column(modifier = Modifier.staggeredEntrance(7), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("If something doesn't work", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Copies a short report (app version, phone model, alert status, recent problems) that you can paste into a message. " +
                            "It contains no passwords or login cookies.",
                        color = Color(0xFF94A3B8), fontSize = 13.sp, lineHeight = 18.sp
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

            // Dev credit (last item)
            item {
                Box(Modifier.staggeredEntrance(8)) {
                    DevCreditCard(versionName = versionName, build = updateViewModel.installedBuild)
                }
            }
        }
    }
}

@Composable
fun GestureHelpCard(
    icon: ImageVector,
    title: String,
    description: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .background(GlassBackground, RoundedCornerShape(16.dp))
            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(Color(0xFF7C3AED).copy(alpha = 0.18f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFFC084FC),
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = description,
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}
