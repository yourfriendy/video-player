package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.model.FeedFilter
import com.example.ui.theme.GlassBackground
import com.example.ui.theme.GlassBorder
import com.example.ui.viewmodel.VideoFeedUiState
import com.example.ui.viewmodel.VideoFeedViewModel

@Composable
fun DiscoverScreen(
    uiState: VideoFeedUiState,
    viewModel: VideoFeedViewModel,
    onNavigateToFeed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    val totalVideos = uiState.allVideos.size
    val focusCount = uiState.allVideos.count { com.example.ui.model.brainModeMatches("focus", it.durationMs) }
    val calmCount = uiState.allVideos.count { com.example.ui.model.brainModeMatches("calm", it.durationMs) }
    val lowEnergyCount = uiState.allVideos.count { com.example.ui.model.brainModeMatches("low energy", it.durationMs) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF090A10))
            .padding(top = statusBarPadding)
            .testTag("discover_screen")
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            item {
                Column {
                    Text(
                        text = "Discover",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Mood-based curations & instant random picks",
                        color = Color(0xFF94A3B8),
                        fontSize = 14.sp
                    )
                }
            }

            // Decide For Me (Instant Random Pick)
            item {
                DecideForMeCard(
                    totalVideos = totalVideos,
                    onClick = {
                        val picked = viewModel.playRandomVideo()
                        if (picked != null) {
                            onNavigateToFeed()
                        }
                    }
                )
            }

            // Brain Modes
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Brain Modes",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    BrainModesGrid(
                        focusCount = focusCount,
                        calmCount = calmCount,
                        dopamineCount = totalVideos,
                        lowEnergyCount = lowEnergyCount,
                        onSelectMode = { mode ->
                            if (mode == "Dopamine") {
                                if (!uiState.isShuffleMode) {
                                    viewModel.toggleShuffleMode()
                                }
                                viewModel.playFilter(FeedFilter.All)
                            } else {
                                viewModel.playFilter(FeedFilter.BrainMode(mode))
                            }
                            onNavigateToFeed()
                        }
                    )
                }
            }

            // Time Available Filters
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Time Available",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    TimeAvailableFilters(
                        uiState = uiState,
                        onSelectRange = { minMs, maxMs, label ->
                            viewModel.playFilter(FeedFilter.DurationRange(minMs, maxMs, label))
                            onNavigateToFeed()
                        }
                    )
                }
            }

            // Browse by Folder
            if (uiState.folders.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = "Browse Folders",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(uiState.folders) { folderName ->
                                val count = uiState.allVideos.count { it.bucketDisplayName.equals(folderName, ignoreCase = true) }
                                FolderChip(
                                    folderName = folderName,
                                    videoCount = count,
                                    onClick = {
                                        viewModel.playFilter(FeedFilter.ByFolder(folderName))
                                        onNavigateToFeed()
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecideForMeCard(
    totalVideos: Int,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("decide_for_me_button"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        listOf(Color(0xFF7C3AED), Color(0xFFC026D3), Color(0xFFEC4899))
                    ),
                    shape = RoundedCornerShape(22.dp)
                )
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(22.dp)
                )
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(Color.White.copy(alpha = 0.2f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Decide For Me 🎲",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(modifier = Modifier.height(3.dp))
                    Text(
                        text = if (totalVideos > 0) "Pick from $totalVideos videos instantly" else "Pick a random video",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 13.sp
                    )
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.White, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play",
                        tint = Color(0xFF7C3AED),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun BrainModesGrid(
    focusCount: Int,
    calmCount: Int,
    dopamineCount: Int,
    lowEnergyCount: Int,
    onSelectMode: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BrainModeCard(
                title = "Focus",
                subtitle = "$focusCount short clips (<3m)",
                icon = Icons.Default.SelfImprovement,
                tint = Color(0xFF34D399),
                modifier = Modifier.weight(1f),
                onClick = { onSelectMode("Focus") }
            )
            BrainModeCard(
                title = "Calm",
                subtitle = "$calmCount smooth clips (3-10m)",
                icon = Icons.Default.Spa,
                tint = Color(0xFF60A5FA),
                modifier = Modifier.weight(1f),
                onClick = { onSelectMode("Calm") }
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            BrainModeCard(
                title = "Dopamine",
                subtitle = "$dopamineCount shuffled quick hits",
                icon = Icons.Default.Bolt,
                tint = Color(0xFFFBBF24),
                modifier = Modifier.weight(1f),
                onClick = { onSelectMode("Dopamine") }
            )
            BrainModeCard(
                title = "Low Energy",
                subtitle = "$lowEnergyCount long relaxers (10m+)",
                icon = Icons.Default.Bedtime,
                tint = Color(0xFFA78BFA),
                modifier = Modifier.weight(1f),
                onClick = { onSelectMode("Low Energy") }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrainModeCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.testTag("brain_mode_${title.lowercase()}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = GlassBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, GlassBorder, RoundedCornerShape(18.dp))
                .padding(14.dp),
            horizontalAlignment = Alignment.Start
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(tint.copy(alpha = 0.18f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = title,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                color = Color(0xFF94A3B8),
                fontSize = 12.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
fun TimeAvailableFilters(
    uiState: VideoFeedUiState,
    onSelectRange: (Long, Long, String) -> Unit
) {
    val options = listOf(
        Triple(0L, 300_000L, "< 5 min"),
        Triple(300_000L, 900_000L, "5 - 15 min"),
        Triple(900_000L, 1_800_000L, "15 - 30 min"),
        Triple(1_800_000L, Long.MAX_VALUE, "30+ min")
    )

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(options) { (minMs, maxMs, label) ->
            val matchCount = uiState.allVideos.count { it.durationMs >= minMs && it.durationMs < maxMs }
            Button(
                onClick = { onSelectRange(minMs, maxMs, label) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = GlassBackground,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(14.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, GlassBorder),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = null,
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "$label ($matchCount)",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
            }
        }
    }
}

@Composable
fun FolderChip(
    folderName: String,
    videoCount: Int,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .background(GlassBackground, RoundedCornerShape(14.dp))
            .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = Color(0xFFFBBF24),
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "$folderName ($videoCount)",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
