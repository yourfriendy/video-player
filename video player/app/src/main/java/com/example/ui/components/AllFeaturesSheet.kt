package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.PictureInPictureAlt
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.ViewStream
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.LocalVideo
import com.example.ui.model.FeedFilter
import com.example.ui.model.SortOption
import com.example.ui.model.VideoDisplayMode
import com.example.ui.theme.GlassBackground
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassBorderSubtle
import com.example.ui.theme.Slate950

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AllFeaturesSheet(
    currentVideo: LocalVideo?,
    displayMode: VideoDisplayMode,
    playbackSpeed: Float,
    isMuted: Boolean,
    isLoopMode: Boolean,
    isShuffleMode: Boolean,
    isGridView: Boolean,
    selectedSort: SortOption,
    currentFilter: FeedFilter,
    folders: List<String>,
    onSelectDisplayMode: (VideoDisplayMode) -> Unit,
    onSelectSpeed: (Float) -> Unit,
    onToggleMute: () -> Unit,
    onToggleLoopMode: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleGridView: () -> Unit,
    onSelectSort: (SortOption) -> Unit,
    onSelectFilter: (FeedFilter) -> Unit,
    onEnterPiP: () -> Unit,
    onRefreshLibrary: () -> Unit,
    onOpenInfo: (LocalVideo) -> Unit,
    onAddToPlaylist: (LocalVideo) -> Unit,
    onClearHistory: () -> Unit,
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
    val displayModes = listOf(VideoDisplayMode.CROP, VideoDisplayMode.FIT, VideoDisplayMode.STRETCH)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Slate950,
        contentColor = Color.White,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(42.dp)
                    .height(4.dp)
                    .background(Color(0x66FFFFFF), CircleShape)
            )
        },
        modifier = Modifier.testTag("all_features_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "All Features & Controls",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Text(
                        text = "Everything in one place • Settings saved automatically",
                        fontSize = 12.sp,
                        color = Color(0xFF94A3B8)
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.testTag("close_features_sheet_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color(0xFF94A3B8)
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Highlighted Action: Native System Pop-up (PiP)
            Surface(
                onClick = {
                    onDismiss()
                    onEnterPiP()
                },
                shape = RoundedCornerShape(16.dp),
                color = Color(0x337C3AED),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.5.dp, Color(0xFF8B5CF6), RoundedCornerShape(16.dp))
                    .testTag("feature_enter_pip_button")
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .background(Color(0xFF7C3AED), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PictureInPictureAlt,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "System Pop-up Player",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "PiP",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFC4B5FD),
                                modifier = Modifier
                                    .background(Color(0x337C3AED), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Float video on home screen & over any other app",
                            fontSize = 12.sp,
                            color = Color(0xFFCBD5E1)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = GlassBorderSubtle)
            Spacer(modifier = Modifier.height(14.dp))

            // Quick Toggle Row: Grid View, Mute, Loop, Shuffle
            Text(
                text = "QUICK TOGGLES",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF94A3B8),
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FeatureToggleButton(
                    icon = if (isGridView) Icons.Default.ViewStream else Icons.Default.GridView,
                    title = if (isGridView) "Reels" else "Grid",
                    isActive = isGridView,
                    activeColor = Color(0xFF38BDF8),
                    onClick = onToggleGridView,
                    modifier = Modifier.weight(1f)
                )

                FeatureToggleButton(
                    icon = if (isMuted) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                    title = if (isMuted) "Muted" else "Sound On",
                    isActive = isMuted,
                    activeColor = Color(0xFFF43F5E),
                    onClick = onToggleMute,
                    modifier = Modifier.weight(1f)
                )

                FeatureToggleButton(
                    icon = Icons.Default.Loop,
                    title = if (isLoopMode) "Loop On" else "Loop Off",
                    isActive = isLoopMode,
                    activeColor = Color(0xFF10B981),
                    onClick = onToggleLoopMode,
                    modifier = Modifier.weight(1f)
                )

                FeatureToggleButton(
                    icon = Icons.Default.Shuffle,
                    title = if (isShuffleMode) "Shuffle" else "In Order",
                    isActive = isShuffleMode,
                    activeColor = Color(0xFFA855F7),
                    onClick = onToggleShuffle,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Playback Speed
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Speed, contentDescription = null, tint = Color(0xFFA78BFA), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Playback Speed",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                speeds.forEach { speed ->
                    val isSelected = kotlin.math.abs(playbackSpeed - speed) < 0.01f
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) Color(0xFF7C3AED) else GlassBackground)
                            .border(
                                1.dp,
                                if (isSelected) Color(0xFFC4B5FD) else GlassBorder,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { onSelectSpeed(speed) }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${speed}x",
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color.White else Color(0xFFCBD5E1)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Aspect Ratio / Display Mode
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.AspectRatio, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Screen Fit / Aspect Ratio",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                displayModes.forEach { mode ->
                    val isSelected = displayMode == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) Color(0xFF0284C7) else GlassBackground)
                            .border(
                                1.dp,
                                if (isSelected) Color(0xFF7DD3FC) else GlassBorder,
                                RoundedCornerShape(10.dp)
                            )
                            .clickable { onSelectDisplayMode(mode) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = mode.label,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color.White else Color(0xFFCBD5E1)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Sort Videos
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Sort, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Sort Feed",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SortOption.values().forEach { sort ->
                    val isSelected = selectedSort == sort && !isShuffleMode
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) Color(0xFFD97706) else GlassBackground)
                            .border(
                                1.dp,
                                if (isSelected) Color(0xFFFDE68A) else GlassBorder,
                                RoundedCornerShape(8.dp)
                            )
                            .clickable { onSelectSort(sort) }
                            .padding(horizontal = 12.dp, vertical = 7.dp)
                    ) {
                        Text(
                            text = sort.displayName,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) Color.White else Color(0xFFCBD5E1)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            HorizontalDivider(color = GlassBorderSubtle)
            Spacer(modifier = Modifier.height(14.dp))

            // Current Video Actions (if available)
            if (currentVideo != null) {
                Text(
                    text = "CURRENT VIDEO ACTIONS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF94A3B8),
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FeatureActionButton(
                        icon = Icons.Default.PlaylistAdd,
                        title = "Add to Playlist",
                        onClick = {
                            onDismiss()
                            onAddToPlaylist(currentVideo)
                        },
                        modifier = Modifier.weight(1f)
                    )

                    FeatureActionButton(
                        icon = Icons.Default.Info,
                        title = "Details & Info",
                        onClick = {
                            onDismiss()
                            onOpenInfo(currentVideo)
                        },
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Library Utilities
            Text(
                text = "LIBRARY UTILITIES",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF94A3B8),
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FeatureActionButton(
                    icon = Icons.Default.Refresh,
                    title = "Rescan Library",
                    onClick = {
                        onRefreshLibrary()
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                )

                FeatureActionButton(
                    icon = Icons.Default.Delete,
                    title = "Clear History",
                    tint = Color(0xFFFB7185),
                    onClick = {
                        onClearHistory()
                        onDismiss()
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

@Composable
private fun FeatureToggleButton(
    icon: ImageVector,
    title: String,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (isActive) activeColor.copy(alpha = 0.22f) else GlassBackground)
            .border(
                1.dp,
                if (isActive) activeColor else GlassBorder,
                RoundedCornerShape(12.dp)
            )
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isActive) activeColor else Color(0xFFCBD5E1),
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = title,
                fontSize = 11.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                color = if (isActive) Color.White else Color(0xFF94A3B8)
            )
        }
    }
}

@Composable
private fun FeatureActionButton(
    icon: ImageVector,
    title: String,
    tint: Color = Color(0xFFC084FC),
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = GlassBackground,
        modifier = modifier.border(1.dp, GlassBorder, RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}
