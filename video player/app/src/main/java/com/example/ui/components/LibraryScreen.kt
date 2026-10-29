package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.PlaylistEntity
import com.example.data.model.LocalVideo
import com.example.ui.model.FeedFilter
import com.example.ui.theme.GlassBackground
import com.example.ui.theme.GlassBorder
import com.example.ui.viewmodel.VideoFeedUiState
import com.example.ui.viewmodel.VideoFeedViewModel
import java.util.Locale

sealed class LibrarySubView {
    object Main : LibrarySubView()
    object Liked : LibrarySubView()
    object Playlists : LibrarySubView()
    object History : LibrarySubView()
    data class PlaylistDetail(val playlist: PlaylistEntity) : LibrarySubView()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    uiState: VideoFeedUiState,
    viewModel: VideoFeedViewModel,
    onNavigateToFeed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    var currentSubView by remember { mutableStateOf<LibrarySubView>(LibrarySubView.Main) }
    var showCreatePlaylistDialog by remember { mutableStateOf(false) }
    var showAddVideosDialogForPlaylist by remember { mutableStateOf<PlaylistEntity?>(null) }

    val favoriteVideos = remember(uiState.allVideos) {
        uiState.allVideos.filter { it.isFavorite }
    }

    val watchLaterVideos = remember(uiState.allVideos, uiState.watchLaterUris) {
        uiState.allVideos.filter { uiState.watchLaterUris.contains(it.uri.toString()) }
    }

    val historyVideos = remember(uiState.allVideos, uiState.watchHistoryMap) {
        uiState.allVideos
            .filter { uiState.watchHistoryMap.containsKey(it.uri.toString()) }
            .sortedByDescending { uiState.watchHistoryMap[it.uri.toString()]?.lastPlayedAt ?: 0L }
    }

    androidx.activity.compose.BackHandler(enabled = currentSubView != LibrarySubView.Main) {
        currentSubView = LibrarySubView.Main
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF090A10))
            .padding(top = statusBarPadding)
            .testTag("library_screen")
    ) {
        when (val subView = currentSubView) {
            is LibrarySubView.Main -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Column {
                            Text(
                                text = "Library",
                                color = Color.White,
                                fontSize = 28.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${uiState.allVideos.size} clips • ${uiState.playlists.size} playlists • ${uiState.totalStorageFormatted}",
                                color = Color(0xFF94A3B8),
                                fontSize = 14.sp
                            )
                        }
                    }

                    // Liked Videos Card
                    item {
                        LibraryCard(
                            title = "Liked Videos",
                            subtitle = "${favoriteVideos.size} videos favorited",
                            icon = Icons.Default.Favorite,
                            tint = Color(0xFFFB7185),
                            onClick = { currentSubView = LibrarySubView.Liked }
                        )
                    }

                    // Playlists Card (Replacing Watch Later)
                    item {
                        LibraryCard(
                            title = "Playlists",
                            subtitle = "${uiState.playlists.size} playlists • Tap to browse or create",
                            icon = Icons.Default.PlaylistAdd,
                            tint = Color(0xFFC4B5FD),
                            onClick = { currentSubView = LibrarySubView.Playlists }
                        )
                    }

                    // Watch History Card
                    item {
                        LibraryCard(
                            title = "Watch History",
                            subtitle = "${historyVideos.size} videos watched recently",
                            icon = Icons.Default.History,
                            tint = Color(0xFFFBBF24),
                            onClick = { currentSubView = LibrarySubView.History }
                        )
                    }

                    // Playlists Section Header
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp, bottom = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Playlists (${uiState.playlists.size})",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Button(
                                onClick = { showCreatePlaylistDialog = true },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF7C3AED),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("create_playlist_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "New",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    // User Playlists List
                    if (uiState.playlists.isEmpty()) {
                        item {
                            EmptyPlaylistsCard(
                                onCreateClick = { showCreatePlaylistDialog = true }
                            )
                        }
                    } else {
                        items(uiState.playlists, key = { it.id }) { playlist ->
                            val count = uiState.playlistVideosMap[playlist.id]?.size ?: 0
                            PlaylistRowCard(
                                playlist = playlist,
                                videoCount = count,
                                onClick = { currentSubView = LibrarySubView.PlaylistDetail(playlist) },
                                onPlayClick = {
                                    viewModel.playFilter(FeedFilter.Playlist(playlist.id, playlist.name))
                                    onNavigateToFeed()
                                },
                                onDeleteClick = {
                                    viewModel.deletePlaylist(playlist)
                                }
                            )
                        }
                    }
                }
            }

            is LibrarySubView.Liked -> {
                CollectionDetailView(
                    title = "Liked Videos",
                    subtitle = "${favoriteVideos.size} videos",
                    videos = favoriteVideos,
                    onBack = { currentSubView = LibrarySubView.Main },
                    onPlayAll = {
                        viewModel.playFilter(FeedFilter.Favorites)
                        onNavigateToFeed()
                    },
                    onPlayVideo = { video ->
                        viewModel.playVideo(video, FeedFilter.Favorites)
                        onNavigateToFeed()
                    },
                    onRemoveVideo = null
                )
            }

            is LibrarySubView.Playlists -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { currentSubView = LibrarySubView.Main }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Playlists",
                                color = Color.White,
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${uiState.playlists.size} playlists",
                                color = Color(0xFF94A3B8),
                                fontSize = 13.sp
                            )
                        }
                        IconButton(onClick = { showCreatePlaylistDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "New Playlist",
                                tint = Color(0xFFC4B5FD)
                            )
                        }
                    }

                    if (uiState.playlists.isEmpty()) {
                        EmptyPlaylistsCard(onCreateClick = { showCreatePlaylistDialog = true })
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(uiState.playlists, key = { it.id }) { playlist ->
                                val videoUris = uiState.playlistVideosMap[playlist.id] ?: emptyList()
                                PlaylistRowCard(
                                    playlist = playlist,
                                    videoCount = videoUris.size,
                                    onClick = { currentSubView = LibrarySubView.PlaylistDetail(playlist) },
                                    onPlayClick = {
                                        viewModel.playFilter(FeedFilter.Playlist(playlist.id, playlist.name))
                                        onNavigateToFeed()
                                    },
                                    onDeleteClick = { viewModel.deletePlaylist(playlist) }
                                )
                            }
                        }
                    }
                }
            }

            is LibrarySubView.History -> {
                CollectionDetailView(
                    title = "Watch History",
                    subtitle = "${historyVideos.size} videos",
                    videos = historyVideos,
                    onBack = { currentSubView = LibrarySubView.Main },
                    extraActionText = if (historyVideos.isNotEmpty()) "Clear" else null,
                    onExtraAction = { viewModel.clearWatchHistory() },
                    onPlayAll = {
                        viewModel.playFilter(FeedFilter.RecentlyPlayed)
                        onNavigateToFeed()
                    },
                    onPlayVideo = { video ->
                        viewModel.playVideo(video, FeedFilter.RecentlyPlayed)
                        onNavigateToFeed()
                    }
                )
            }

            is LibrarySubView.PlaylistDetail -> {
                val playlist = subView.playlist
                val playlistUris = uiState.playlistVideosMap[playlist.id]?.toSet() ?: emptySet()
                val playlistVideos = uiState.allVideos.filter { playlistUris.contains(it.uri.toString()) }

                CollectionDetailView(
                    title = "${playlist.emojiOrIcon ?: "📁"} ${playlist.name}",
                    subtitle = "${playlistVideos.size} videos",
                    videos = playlistVideos,
                    onBack = { currentSubView = LibrarySubView.Main },
                    extraActionText = "+ Add Videos",
                    onExtraAction = { showAddVideosDialogForPlaylist = playlist },
                    onPlayAll = {
                        viewModel.playFilter(FeedFilter.Playlist(playlist.id, playlist.name))
                        onNavigateToFeed()
                    },
                    onPlayVideo = { video ->
                        viewModel.playVideo(video, FeedFilter.Playlist(playlist.id, playlist.name))
                        onNavigateToFeed()
                    },
                    onRemoveVideo = { video ->
                        viewModel.removeVideoFromPlaylist(playlist.id, video)
                    }
                )
            }
        }

        // Create Playlist Dialog
        if (showCreatePlaylistDialog) {
            CreatePlaylistDialog(
                onDismiss = { showCreatePlaylistDialog = false },
                onCreate = { name, emoji ->
                    viewModel.createPlaylist(name, emoji)
                    showCreatePlaylistDialog = false
                }
            )
        }

        // Add Videos to Playlist Dialog
        showAddVideosDialogForPlaylist?.let { playlist ->
            AddVideosToPlaylistDialog(
                playlist = playlist,
                allVideos = uiState.allVideos,
                existingUris = uiState.playlistVideosMap[playlist.id]?.toSet() ?: emptySet(),
                onDismiss = { showAddVideosDialogForPlaylist = null },
                onToggleVideo = { video, isCurrentlyIn ->
                    if (isCurrentlyIn) {
                        viewModel.removeVideoFromPlaylist(playlist.id, video)
                    } else {
                        viewModel.addVideoToPlaylist(playlist.id, video)
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("library_card_${title.lowercase().replace(" ", "_")}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = GlassBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, GlassBorder, RoundedCornerShape(18.dp))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .background(tint.copy(alpha = 0.16f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(26.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp
                )
            }

            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Open",
                tint = Color(0xFF64748B),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun PlaylistRowCard(
    playlist: PlaylistEntity,
    videoCount: Int,
    onClick: () -> Unit,
    onPlayClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(GlassBackground, RoundedCornerShape(16.dp))
            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Color(0xFF7C3AED).copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = playlist.emojiOrIcon ?: "📁",
                    fontSize = 22.sp
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = playlist.name,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "$videoCount videos",
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp
                )
            }

            // Quick Play Button
            IconButton(
                onClick = onPlayClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play Playlist",
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(22.dp)
                )
            }

            // Delete Playlist Button
            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete Playlist",
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun EmptyPlaylistsCard(
    onCreateClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(GlassBackground, RoundedCornerShape(16.dp))
            .border(1.dp, GlassBorder, RoundedCornerShape(16.dp))
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.PlaylistPlay,
                contentDescription = null,
                tint = Color(0xFF64748B),
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "No custom playlists yet",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Create a playlist to organize your videos",
                color = Color(0xFF94A3B8),
                fontSize = 13.sp
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onCreateClick,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Create Playlist")
            }
        }
    }
}

@Composable
fun CollectionDetailView(
    title: String,
    subtitle: String,
    videos: List<LocalVideo>,
    onBack: () -> Unit,
    extraActionText: String? = null,
    onExtraAction: (() -> Unit)? = null,
    onPlayAll: () -> Unit,
    onPlayVideo: (LocalVideo) -> Unit,
    onRemoveVideo: ((LocalVideo) -> Unit)? = null
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Sub-view Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                Text(
                    text = subtitle,
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp
                )
            }

            if (extraActionText != null && onExtraAction != null) {
                TextButton(onClick = onExtraAction) {
                    Text(
                        text = extraActionText,
                        color = Color(0xFF38BDF8),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }

        // Play All Banner
        if (videos.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Button(
                    onClick = onPlayAll,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF7C3AED),
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(vertical = 12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Play All (${videos.size} clips)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }

        // Videos List
        if (videos.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = null,
                        tint = Color(0xFF475569),
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No videos in this collection",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Add videos or browse from your feed",
                        color = Color(0xFF94A3B8),
                        fontSize = 13.sp
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(videos, key = { it.id }) { video ->
                    VideoItemRow(
                        video = video,
                        onPlay = { onPlayVideo(video) },
                        onRemove = if (onRemoveVideo != null) {
                            { onRemoveVideo(video) }
                        } else null
                    )
                }
            }
        }
    }
}

@Composable
fun VideoItemRow(
    video: LocalVideo,
    onPlay: () -> Unit,
    onRemove: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(GlassBackground, RoundedCornerShape(14.dp))
            .border(1.dp, GlassBorder, RoundedCornerShape(14.dp))
            .clickable { onPlay() }
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(Color(0xFF1E293B), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Movie,
                    contentDescription = null,
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = video.displayName,
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${formatDuration(video.durationMs)} • ${video.bucketDisplayName}",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                )
            }

            if (onRemove != null) {
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.RemoveCircleOutline,
                        contentDescription = "Remove",
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(18.dp)
                    )
                }
            } else {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color(0xFF38BDF8),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (name: String, emoji: String) -> Unit
) {
    var playlistName by remember { mutableStateOf("") }
    var selectedEmoji by remember { mutableStateOf("📁") }
    val emojiOptions = listOf("📁", "🎬", "🎵", "⭐", "🔥", "🏆", "💡", "🚀")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Create Playlist",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = playlistName,
                    onValueChange = { playlistName = it },
                    label = { Text("Playlist Name") },
                    placeholder = { Text("e.g. Favorite Clips") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = Color(0xFF7C3AED),
                        unfocusedBorderColor = Color(0xFF475569)
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Pick an icon:",
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    emojiOptions.forEach { emoji ->
                        val isSelected = emoji == selectedEmoji
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(
                                    if (isSelected) Color(0xFF7C3AED) else Color(0xFF1E293B),
                                    CircleShape
                                )
                                .clickable { selectedEmoji = emoji },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = emoji, fontSize = 18.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (playlistName.isNotBlank()) {
                        onCreate(playlistName.trim(), selectedEmoji)
                    }
                },
                enabled = playlistName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color(0xFF94A3B8))
            }
        },
        containerColor = Color(0xFF181824)
    )
}

@Composable
fun AddVideosToPlaylistDialog(
    playlist: PlaylistEntity,
    allVideos: List<LocalVideo>,
    existingUris: Set<String>,
    onDismiss: () -> Unit,
    onToggleVideo: (LocalVideo, isCurrentlyIn: Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Add to ${playlist.name}",
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Box(modifier = Modifier.height(380.dp).fillMaxWidth()) {
                if (allVideos.isEmpty()) {
                    Text(
                        text = "No videos available on device.",
                        color = Color(0xFF94A3B8),
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(allVideos, key = { it.id }) { video ->
                            val isInPlaylist = existingUris.contains(video.uri.toString())
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF1E293B), RoundedCornerShape(10.dp))
                                    .clickable { onToggleVideo(video, isInPlaylist) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isInPlaylist,
                                    onCheckedChange = { onToggleVideo(video, isInPlaylist) },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = Color(0xFF7C3AED),
                                        uncheckedColor = Color(0xFF64748B)
                                    )
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = video.displayName,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = formatDuration(video.durationMs),
                                        color = Color(0xFF94A3B8),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))
            ) {
                Text("Done")
            }
        },
        containerColor = Color(0xFF181824)
    )
}

private fun formatDuration(ms: Long): String {
    val sec = (ms / 1000) % 60
    val min = (ms / (1000 * 60)) % 60
    val hr = ms / (1000 * 60 * 60)
    return if (hr > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hr, min, sec)
    } else {
        String.format(Locale.US, "%02d:%02d", min, sec)
    }
}
