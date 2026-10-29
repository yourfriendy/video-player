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
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlin.math.abs
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp

/** Plain holder for the video currently on screen (not Compose state on purpose). */
private class PlayedVideoTracker {
    var video: LocalVideo? = null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoFeedScreen(
    uiState: VideoFeedUiState,
    viewModel: VideoFeedViewModel,
    onEnterPiP: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    modifier: Modifier = Modifier,
    /** Space to keep clear at the bottom (floating nav bar + system inset when visible). */
    bottomOverlayPadding: Dp = 80.dp
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    val filteredVideos = uiState.filteredVideos
    // Long-lived effects must always see the CURRENT list, never the one from first composition
    val latestVideos by rememberUpdatedState(filteredVideos)

    val pagerState = rememberPagerState(
        initialPage = uiState.currentIndex.coerceIn(0, (filteredVideos.size - 1).coerceAtLeast(0)),
        pageCount = { filteredVideos.size }
    )

    val showGrid = uiState.isGridView
    val showPager = !uiState.isLoading && filteredVideos.isNotEmpty() && !showGrid

    var currentProgressMs by remember { mutableLongStateOf(0L) }
    var currentTotalMs by remember { mutableLongStateOf(0L) }
    var seekRatio by remember { mutableStateOf<Float?>(null) }
    var isAllFeaturesOpen by remember { mutableStateOf(false) }
    var interactionTick by remember { mutableIntStateOf(0) }
    val tracker = remember { PlayedVideoTracker() }

    // Four shared players for the whole feed: swiping never has to build one
    val playerPool = remember { PlayerPool(context) }
    DisposableEffect(playerPool) {
        onDispose { playerPool.release() }
    }

    val hasOverlay = isAllFeaturesOpen ||
        uiState.playlistForVideo != null ||
        uiState.optionsForVideo != null ||
        uiState.selectedMetadata != null ||
        uiState.videoToDelete != null

    // Auto-hide only during uninterrupted playback; scrubbing, sheets and search reset/suspend it
    LaunchedEffect(
        uiState.isPlaying, uiState.areControlsVisible, pagerState.currentPage,
        uiState.isSearchOpen, interactionTick, hasOverlay
    ) {
        if (uiState.isPlaying && uiState.areControlsVisible && !uiState.isSearchOpen && !hasOverlay) {
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

    // The ViewModel decides where the feed should be (play from library, search result, sort...); obey it
    LaunchedEffect(uiState.scrollRequest) {
        val request = uiState.scrollRequest ?: return@LaunchedEffect
        val size = latestVideos.size
        if (size > 0) {
            // Suspends until the pager is on screen, so this also works while the grid is showing
            pagerState.scrollToPage(request.index.coerceIn(0, size - 1))
        }
        viewModel.consumeScrollRequest(request)
    }

    // Track which video is actually on screen (also fires when the list changes under the same page)
    LaunchedEffect(pagerState, showPager) {
        if (!showPager) return@LaunchedEffect
        try {
            snapshotFlow {
                pagerState.currentPage to latestVideos.getOrNull(pagerState.currentPage)?.id
            }.distinctUntilChanged().collect { (page, _) ->
                val video = latestVideos.getOrNull(page) ?: return@collect
                tracker.video?.let { previous ->
                    if (previous.id != video.id) viewModel.saveWatchPosition(previous, currentProgressMs)
                }
                tracker.video = video
                currentProgressMs = 0L
                currentTotalMs = video.durationMs
                seekRatio = null
                viewModel.onPageChanged(page)
            }
        } finally {
            tracker.video?.let { viewModel.saveWatchPosition(it, currentProgressMs) }
            tracker.video = null
        }
    }

    // Remember where the user stopped when the app goes to the background
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                tracker.video?.let { viewModel.saveWatchPosition(it, currentProgressMs) }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Handle toast messages
    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    val animatedBottomPadding by animateDpAsState(targetValue = bottomOverlayPadding, label = "bottomOverlayPadding")

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
                onRefresh = { viewModel.loadVideos() },
                searchQuery = uiState.searchQuery,
                onClearSearch = { viewModel.clearSearch() }
            )
        } else if (showGrid) {
            // 3-column grid: used for the grid view and for search results
            VideoGridView(
                videos = filteredVideos,
                onSelectVideo = { index -> viewModel.selectVideoFromGrid(index) },
                modifier = Modifier.fillMaxSize(),
                topPadding = statusBarPadding + 100.dp,
                bottomPadding = animatedBottomPadding + 20.dp
            )
        } else {
            // Full Screen Vertical Snap Pager
            VerticalPager(
                state = pagerState,
                beyondViewportPageCount = 1,
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("vertical_video_pager"),
                key = { page ->
                    if (page in filteredVideos.indices) filteredVideos[page].id else page
                }
            ) { pageIndex ->
                val video = filteredVideos.getOrNull(pageIndex) ?: return@VerticalPager
                val isCurrentPage = pageIndex == pagerState.currentPage
                val isNeighbour = !isCurrentPage && abs(pageIndex - pagerState.currentPage) == 1

                // Resume long videos where the user left off
                val savedMs = uiState.watchHistoryMap[video.uri.toString()]?.lastPositionMs ?: 0L
                val resumeMs = if (video.durationMs > 60_000L && savedMs > 5_000L &&
                    savedMs < video.durationMs - 5_000L) savedMs else 0L

                Box(modifier = Modifier.fillMaxSize()) {
                    ReelPlayerItem(
                        video = video,
                        player = playerPool.playerFor(pageIndex),
                        onVolumeAdjusted = { viewModel.unmute() },
                        isCurrentPage = isCurrentPage,
                        isPlaying = uiState.isPlaying && !uiState.isSearchOpen,
                        isMuted = uiState.isMuted,
                        playbackSpeed = uiState.playbackSpeed,
                        displayMode = uiState.displayMode,
                        seekToRatio = if (isCurrentPage) seekRatio else null,
                        isLoopMode = uiState.isLoopMode,
                        userActionTrigger = uiState.playPauseUserActionTrigger,
                        shouldPreload = isNeighbour,
                        startPositionMs = resumeMs,
                        onEnded = {
                            // Loop is off: move on to the next video instead of stalling
                            val next = pagerState.currentPage + 1
                            if (next < latestVideos.size) {
                                coroutineScope.launch { pagerState.animateScrollToPage(next) }
                            }
                        },
                        onProgressUpdate = { cur, tot ->
                            if (isCurrentPage) {
                                currentProgressMs = cur
                                currentTotalMs = tot
                            }
                        },
                        // First tap reveals controls, next tap plays/pauses
                        onSingleTap = { viewModel.onVideoTapped() },
                        onDoubleTap = { viewModel.likeVideo(video) }
                    )

                    // Right Side Action Rail (Clean, minimal, thumb-friendly)
                    androidx.compose.animation.AnimatedVisibility(
                        visible = uiState.areControlsVisible,
                        enter = androidx.compose.animation.fadeIn(),
                        exit = androidx.compose.animation.fadeOut(),
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = animatedBottomPadding)
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
                            .padding(bottom = animatedBottomPadding)
                    ) {
                        VideoInfoOverlay(
                            video = video,
                            currentProgressMsProvider = { currentProgressMs },
                            totalDurationMs = currentTotalMs,
                            onSeek = { ratio ->
                                seekRatio = ratio
                                interactionTick++
                            },
                            onInteraction = { interactionTick++ }
                        )
                    }
                }
            }
        }

        // Top Navigation & Filter Bar
        androidx.compose.animation.AnimatedVisibility(
            visible = uiState.areControlsVisible,
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
                isSearchActive = uiState.searchQuery.isNotBlank(),
                folders = uiState.folders,
                totalVideosCount = filteredVideos.size,
                currentIndex = if (showPager) pagerState.currentPage else uiState.currentIndex,
                totalStorageFormatted = uiState.totalStorageFormatted,
                // The ViewModel issues the scroll requests, so no manual scrollToPage(0) here
                onSelectFilter = { filter -> viewModel.selectFilter(filter) },
                onSelectSort = { sort -> viewModel.selectSort(sort) },
                onToggleShuffle = { viewModel.toggleShuffleMode() },
                onToggleGridView = { viewModel.toggleGridView() },
                onOpenSearch = { viewModel.openSearch() },
                onOpenSettings = onOpenSettings,
                onRefresh = { viewModel.loadVideos() },
                onOpenPopup = onEnterPiP,
                onOpenAllFeatures = { isAllFeaturesOpen = true }
            )
        }

        // "Search active" chip. The ✕ returns to the full feed on the SAME video that is playing now.
        val chipTop by animateDpAsState(
            targetValue = if (uiState.areControlsVisible) statusBarPadding + 108.dp else statusBarPadding + 10.dp,
            label = "searchChipTop"
        )
        androidx.compose.animation.AnimatedVisibility(
            visible = uiState.searchQuery.isNotBlank() && !uiState.isSearchOpen,
            enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.scaleIn(initialScale = 0.9f),
            exit = androidx.compose.animation.fadeOut(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 16.dp, top = chipTop)
        ) {
            SearchActiveChip(
                query = uiState.searchQuery,
                count = filteredVideos.size,
                onOpen = { viewModel.openSearch() },
                onClear = { viewModel.clearSearch() }
            )
        }

        // Full-screen search page
        androidx.compose.animation.AnimatedVisibility(
            visible = uiState.isSearchOpen,
            enter = androidx.compose.animation.fadeIn(tween(160)) +
                androidx.compose.animation.slideInVertically(tween(220)) { it / 12 },
            exit = androidx.compose.animation.fadeOut(tween(120)),
            modifier = Modifier.fillMaxSize()
        ) {
            SearchScreen(uiState = uiState, viewModel = viewModel)
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
                isWatchLater = uiState.watchLaterUris.contains(targetVideo.uri.toString()),
                onToggleWatchLater = { viewModel.toggleWatchLater(targetVideo) },
                onDismiss = { viewModel.closeOptions() }
            )
        }

        // All Features & Controls Comprehensive 3-Dots Sheet
        if (isAllFeaturesOpen) {
            AllFeaturesSheet(
                currentVideo = filteredVideos.firstOrNull { it.id == uiState.currentVideoId }
                    ?: filteredVideos.getOrNull(pagerState.currentPage),
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
                onSelectSort = { sort -> viewModel.selectSort(sort) },
                onSelectFilter = { filter -> viewModel.selectFilter(filter) },
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

@Composable
private fun SearchActiveChip(
    query: String,
    count: Int,
    onOpen: () -> Unit,
    onClear: () -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .height(36.dp)
            .shadow(6.dp, shape)
            .clip(shape)
            .background(Brush.horizontalGradient(listOf(Color(0xE68B5CF6), Color(0xE66366F1))))
            .clickable(onClick = onOpen)
            .padding(start = 12.dp, end = 4.dp)
            .testTag("search_active_chip"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "$query · $count",
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 220.dp)
        )
        IconButton(
            onClick = onClear,
            modifier = Modifier
                .size(30.dp)
                .testTag("search_chip_clear")
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Clear search",
                tint = Color.White,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
