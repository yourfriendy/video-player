package com.example.ui.components

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.LocalVideo
import com.example.ui.model.FeedFilter
import com.example.ui.viewmodel.VideoFeedUiState
import com.example.ui.viewmodel.VideoFeedViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.material3.ExperimentalMaterial3Api

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoFeedScreen(
    uiState: VideoFeedUiState,
    viewModel: VideoFeedViewModel,
    onEnterPiP: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val filteredVideos = uiState.filteredVideos
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { filteredVideos.size }
    )

    var currentProgressMs by remember { mutableLongStateOf(0L) }
    var currentTotalMs by remember { mutableLongStateOf(0L) }
    var seekRatio by remember { mutableStateOf<Float?>(null) }
    var isAllFeaturesOpen by remember { mutableStateOf(false) }
    
    // Always keep controls visible when searching or when search query is active
    LaunchedEffect(uiState.isSearchOpen, uiState.searchQuery) {
        if (uiState.isSearchOpen || uiState.searchQuery.isNotEmpty()) {
            viewModel.setControlsVisible(true)
        }
    }

    LaunchedEffect(uiState.isPlaying, uiState.areControlsVisible, pagerState.currentPage, uiState.isSearchOpen, uiState.searchQuery) {
        // Peaceful auto-hide only during normal uninterrupted playback
        if (uiState.isPlaying && uiState.areControlsVisible && !uiState.isSearchOpen && uiState.searchQuery.isEmpty()) {
            delay(3500L)
            viewModel.setControlsVisible(false)
        }
    }

    val deleteIntentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onDeletePermissionGranted(uiState.pendingDeleteVideoUri)
        } else {
            viewModel.cancelDelete()
        }
    }

    // Launch system delete consent dialog if needed
    LaunchedEffect(uiState.pendingDeleteIntentSender) {
        uiState.pendingDeleteIntentSender?.let { intentSender ->
            val request = IntentSenderRequest.Builder(intentSender).build()
            deleteIntentLauncher.launch(request)
        }
    }

    // Observe pager scroll page changes
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            if (page in filteredVideos.indices) {
                currentProgressMs = 0L
                currentTotalMs = filteredVideos[page].durationMs
                seekRatio = null
                viewModel.onPageChanged(page)
            }
        }
    }

    // Handle toast messages
    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("video_feed_screen")
    ) {
        if (uiState.isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color(0xFF8B5CF6))
            }
        } else if (filteredVideos.isEmpty()) {
            EmptyFeedView(
                filter = uiState.selectedFilter,
                onResetFilter = { viewModel.selectFilter(FeedFilter.All) },
                onRefresh = { viewModel.loadVideos() }
            )
        } else if (uiState.isGridView) {
            // 3-Column Frosted Grid View
            VideoGridView(
                videos = filteredVideos,
                onSelectVideo = { index ->
                    viewModel.toggleGridView()
                    coroutineScope.launch {
                        pagerState.scrollToPage(index)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Full Screen Vertical Snap Pager
            VerticalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("vertical_video_pager"),
                key = { page ->
                    if (page in filteredVideos.indices) filteredVideos[page].id else page
                }
            ) { pageIndex ->
                val video = filteredVideos[pageIndex]
                val isCurrentPage = pageIndex == pagerState.currentPage

                Box(modifier = Modifier.fillMaxSize()) {
                    // Player Canvas / Surface
                    ReelPlayerItem(
                        video = video,
                        isCurrentPage = isCurrentPage,
                        isPlaying = uiState.isPlaying,
                        isMuted = uiState.isMuted,
                        playbackSpeed = uiState.playbackSpeed,
                        displayMode = uiState.displayMode,
                        seekToRatio = if (isCurrentPage) seekRatio else null,
                        isLoopMode = uiState.isLoopMode,
                        userActionTrigger = uiState.playPauseUserActionTrigger,
                        onProgressUpdate = { cur, tot ->
                            if (isCurrentPage) {
                                currentProgressMs = cur
                                currentTotalMs = tot
                            }
                        },
                        onSingleTap = {
                            viewModel.toggleControlsVisibility()
                        },
                        onDoubleTap = {
                            viewModel.likeVideo(video)
                        }
                    )

                    // Right Side Action Rail (Clean, minimal, thumb-friendly)
                    androidx.compose.animation.AnimatedVisibility(
                        visible = uiState.areControlsVisible,
                        enter = androidx.compose.animation.fadeIn(),
                        exit = androidx.compose.animation.fadeOut(),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 80.dp)
                    ) {
                        ActionRail(
                            video = video,
                            isMuted = uiState.isMuted,
                            onLike = { viewModel.toggleFavorite(video) },
                            onOpenPlaylist = { viewModel.openAddToPlaylist(video) },
                            onToggleMute = { viewModel.toggleMute() },
                            onShare = { shareVideo(context, video) },
                            onOpenMore = { viewModel.openOptions(video) }
                        )
                    }

                    // Bottom Metadata & TikTok Scrubbing Progress Bar
                    androidx.compose.animation.AnimatedVisibility(
                        visible = uiState.areControlsVisible,
                        enter = androidx.compose.animation.fadeIn(),
                        exit = androidx.compose.animation.fadeOut(),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(bottom = 80.dp)
                    ) {
                        VideoInfoOverlay(
                            video = video,
                            currentProgressMs = currentProgressMs,
                            totalDurationMs = currentTotalMs,
                            onSeek = { ratio ->
                                seekRatio = ratio
                            }
                        )
                    }
                }
            }
        }

        // Top Navigation & Filter Bar
        val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        androidx.compose.animation.AnimatedVisibility(
            visible = uiState.areControlsVisible || uiState.isSearchOpen || uiState.searchQuery.isNotEmpty(),
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = statusBarPadding)
        ) {
            FeedTopBar(
                currentFilter = uiState.selectedFilter,
                selectedSort = uiState.selectedSort,
                isShuffleMode = uiState.isShuffleMode,
                isGridView = uiState.isGridView,
                searchQuery = uiState.searchQuery,
                isSearchOpen = uiState.isSearchOpen,
                folders = uiState.folders,
                totalVideosCount = filteredVideos.size,
                currentIndex = pagerState.currentPage,
                totalStorageFormatted = uiState.totalStorageFormatted,
                onSelectFilter = { filter ->
                    viewModel.selectFilter(filter)
                    coroutineScope.launch {
                        pagerState.scrollToPage(0)
                    }
                },
                onSelectSort = { sort ->
                    viewModel.selectSort(sort)
                    coroutineScope.launch {
                        pagerState.scrollToPage(0)
                    }
                },
                onToggleShuffle = {
                    viewModel.toggleShuffleMode()
                    coroutineScope.launch {
                        pagerState.scrollToPage(0)
                    }
                },
                onToggleGridView = { viewModel.toggleGridView() },
                onSearchQueryChange = { query ->
                    viewModel.setSearchQuery(query)
                },
                onToggleSearch = { viewModel.toggleSearchOpen() },
                onRefresh = { viewModel.loadVideos() },
                onOpenPopup = onEnterPiP,
                onOpenAllFeatures = { isAllFeaturesOpen = true }
            )
        }

        // Add to Playlist Bottom Sheet
        uiState.playlistForVideo?.let { targetVideo ->
            AddToPlaylistSheet(
                video = targetVideo,
                playlists = uiState.playlists,
                playlistVideosMap = uiState.playlistVideosMap,
                onToggleVideoInPlaylist = { playlistId, _ ->
                    viewModel.toggleVideoInPlaylist(playlistId, targetVideo)
                },
                onCreatePlaylistAndAdd = { name, emoji ->
                    viewModel.createPlaylistAndAdd(name, emoji, targetVideo)
                },
                onDismiss = { viewModel.closeAddToPlaylist() }
            )
        }

        // Video Options Bottom Sheet (Speed, Display Aspect, Info, Delete)
        uiState.optionsForVideo?.let { targetVideo ->
            VideoOptionsSheet(
                video = targetVideo,
                playbackSpeed = uiState.playbackSpeed,
                displayMode = uiState.displayMode,
                onSelectSpeed = { speed -> viewModel.setPlaybackSpeed(speed) },
                onSelectDisplayMode = { mode -> viewModel.setDisplayMode(mode) },
                onOpenInfo = { viewModel.openMetadata(targetVideo) },
                onDelete = { viewModel.requestDeleteConfirmation(targetVideo) },
                onEnterPiP = onEnterPiP,
                onDismiss = { viewModel.closeOptions() }
            )
        }

        // All Features & Controls Comprehensive 3-Dots Sheet
        if (isAllFeaturesOpen) {
            AllFeaturesSheet(
                currentVideo = filteredVideos.getOrNull(pagerState.currentPage),
                displayMode = uiState.displayMode,
                playbackSpeed = uiState.playbackSpeed,
                isMuted = uiState.isMuted,
                isLoopMode = uiState.isLoopMode,
                isShuffleMode = uiState.isShuffleMode,
                isGridView = uiState.isGridView,
                selectedSort = uiState.selectedSort,
                currentFilter = uiState.selectedFilter,
                folders = uiState.folders,
                onSelectDisplayMode = { mode -> viewModel.setDisplayMode(mode) },
                onSelectSpeed = { speed -> viewModel.setPlaybackSpeed(speed) },
                onToggleMute = { viewModel.toggleMute() },
                onToggleLoopMode = { viewModel.toggleLoopMode() },
                onToggleShuffle = { viewModel.toggleShuffleMode() },
                onToggleGridView = { viewModel.toggleGridView() },
                onSelectSort = { sort ->
                    viewModel.selectSort(sort)
                    coroutineScope.launch { pagerState.scrollToPage(0) }
                },
                onSelectFilter = { filter ->
                    viewModel.selectFilter(filter)
                    coroutineScope.launch { pagerState.scrollToPage(0) }
                },
                onEnterPiP = onEnterPiP,
                onRefreshLibrary = { viewModel.loadVideos() },
                onOpenInfo = { v -> viewModel.openMetadata(v) },
                onAddToPlaylist = { v -> viewModel.openAddToPlaylist(v) },
                onClearHistory = { viewModel.clearWatchHistory() },
                onDismiss = { isAllFeaturesOpen = false }
            )
        }

        // Metadata Bottom Sheet
        uiState.selectedMetadata?.let { details ->
            MetadataBottomSheet(
                details = details,
                onDismiss = { viewModel.closeMetadata() }
            )
        }

        // Delete Confirmation Dialog
        uiState.videoToDelete?.let { video ->
            DeleteConfirmDialog(
                video = video,
                onConfirm = { viewModel.confirmDelete(video) },
                onDismiss = { viewModel.cancelDelete() }
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

private fun shareVideo(context: Context, video: LocalVideo) {
    try {
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = video.mimeType.ifEmpty { "video/*" }
            putExtra(Intent.EXTRA_STREAM, video.uri)
            putExtra(Intent.EXTRA_TEXT, "Shared from OffScroll: ${video.displayName}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(shareIntent, "Share video"))
    } catch (e: Exception) {
        Toast.makeText(context, "Could not share video: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}
