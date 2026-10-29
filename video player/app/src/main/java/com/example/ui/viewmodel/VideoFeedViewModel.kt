package com.example.ui.viewmodel

import android.content.Context
import android.content.IntentSender
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.entity.WatchHistory
import com.example.data.model.LocalVideo
import com.example.data.model.VideoMetadataDetails
import com.example.data.repository.DeletionResult
import com.example.data.repository.MetadataExtractor
import com.example.data.repository.VideoRepository
import com.example.ui.model.FeedFilter
import com.example.ui.model.brainModeMatches
import com.example.ui.model.SortOption
import com.example.ui.model.VideoDisplayMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

/** One-shot request for the pager to scroll; [nonce] makes repeated requests to the same index distinct. */
data class ScrollRequest(val index: Int, val nonce: Long)

data class VideoFeedUiState(
    val allVideos: List<LocalVideo> = emptyList(),
    val filteredVideos: List<LocalVideo> = emptyList(),
    val folders: List<String> = emptyList(),
    val selectedFilter: FeedFilter = FeedFilter.All,
    val selectedSort: SortOption = SortOption.DATE_NEWEST,
    val displayMode: VideoDisplayMode = VideoDisplayMode.CROP,
    val isShuffleMode: Boolean = false,
    val isGridView: Boolean = false,
    val isLoopMode: Boolean = true,
    val areControlsVisible: Boolean = true,
    val searchQuery: String = "",
    val isSearchOpen: Boolean = false,
    val currentIndex: Int = 0,
    val isPlaying: Boolean = true,
    val isMuted: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val playPauseUserActionTrigger: Long = 0L,
    val isLoading: Boolean = true,
    val isPermissionGranted: Boolean = false,
    val isDemoMode: Boolean = false,
    val currentVideoId: Long? = null,
    val scrollRequest: ScrollRequest? = null,
    val selectedMetadata: VideoMetadataDetails? = null,
    val videoToDelete: LocalVideo? = null,
    val pendingDeleteIntentSender: IntentSender? = null,
    val pendingDeleteVideoUri: String? = null,
    val toastMessage: String? = null,
    val watchHistoryMap: Map<String, WatchHistory> = emptyMap(),
    val playlists: List<com.example.data.local.entity.PlaylistEntity> = emptyList(),
    val playlistVideosMap: Map<Long, List<String>> = emptyMap(),
    val playlistForVideo: LocalVideo? = null,
    val optionsForVideo: LocalVideo? = null,
    val isInPictureInPicture: Boolean = false,
    val watchLaterUris: Set<String> = emptySet(),
    val totalStorageFormatted: String = "0 B",
    val totalCount: Int = 0
)

class VideoFeedViewModel(
    private val repository: VideoRepository,
    private val metadataExtractor: MetadataExtractor,
    private val settingsManager: com.example.data.local.SettingsManager
) : ViewModel() {

    private val _uiState: MutableStateFlow<VideoFeedUiState>
    val uiState: StateFlow<VideoFeedUiState>

    private var shuffledVideoIds: List<Long> = emptyList()
    private var recordJob: kotlinx.coroutines.Job? = null
    private var hasLoadedOnce = false
    private var demoMode = false
    private var resumeAfterForeground = false

    private fun scrollTo(index: Int) = ScrollRequest(index.coerceAtLeast(0), System.nanoTime())

    init {
        val initialDisplayMode = settingsManager.getDisplayMode()
        val initialPlaybackSpeed = settingsManager.getPlaybackSpeed()
        val initialMuted = settingsManager.isMuted()
        val initialSort = settingsManager.getSortOption()
        val initialGridView = settingsManager.isGridView()
        val initialShuffle = settingsManager.isShuffleMode()
        val initialLoop = settingsManager.isLoopMode()

        _uiState = MutableStateFlow(
            VideoFeedUiState(
                displayMode = initialDisplayMode,
                playbackSpeed = initialPlaybackSpeed,
                isMuted = initialMuted,
                selectedSort = initialSort,
                isGridView = initialGridView,
                isShuffleMode = initialShuffle,
                isLoopMode = initialLoop
            )
        )
        uiState = _uiState.asStateFlow()
        observeFavoritesAndWatchHistory()
        // Loading is triggered by onPermissionResult() once the Activity knows the permission state.
    }

    private fun observeFavoritesAndWatchHistory() {
        viewModelScope.launch {
            combine(
                repository.favoritesFlow,
                repository.watchHistoryFlow,
                repository.watchLaterFlow,
                repository.playlistsFlow,
                repository.allPlaylistVideosFlow
            ) { favorites, historyList, watchLaterList, playlistsList, playlistVideosList ->
                val favMap = favorites.associateBy { it.videoUri }
                val histMap = historyList.associateBy { it.videoUri }
                val watchLaterSet = watchLaterList.map { it.videoUri }.toSet()
                val playlistMap = playlistVideosList.groupBy({ it.playlistId }, { it.videoUri })

                Triple(Pair(favMap, histMap), watchLaterSet, Pair(playlistsList, playlistMap))
            }.collect { (favAndHist, watchLaterSet, playlistData) ->
                val (favMap, histMap) = favAndHist
                val (playlistsList, playlistMap) = playlistData

                _uiState.update { state ->
                    val updatedAll = state.allVideos.map { video ->
                        video.copy(isFavorite = favMap.containsKey(video.uri.toString()))
                    }
                    val isFilterAffectedByHistory = state.selectedFilter == FeedFilter.RecentlyPlayed &&
                        histMap.keys != state.watchHistoryMap.keys
                    val isFilterAffectedByWatchLater = state.selectedFilter == FeedFilter.WatchLater
                    val isFilterAffectedByPlaylist = state.selectedFilter is FeedFilter.Playlist

                    val shouldReprocess = isFilterAffectedByHistory ||
                        isFilterAffectedByWatchLater ||
                        isFilterAffectedByPlaylist ||
                        updatedAll != state.allVideos

                    val updatedFiltered = if (shouldReprocess) {
                        processVideos(
                            videos = updatedAll,
                            filter = state.selectedFilter,
                            sort = state.selectedSort,
                            isShuffle = state.isShuffleMode,
                            searchQuery = state.searchQuery,
                            historyMap = histMap,
                            watchLaterUris = watchLaterSet,
                            playlistVideosMap = playlistMap
                        )
                    } else {
                        state.filteredVideos
                    }
                    val folders = updatedAll.map { it.bucketDisplayName }.distinct().sorted()
                    val totalBytes = updatedAll.sumOf { it.sizeBytes }
                    state.copy(
                        allVideos = updatedAll,
                        filteredVideos = updatedFiltered,
                        folders = folders,
                        watchHistoryMap = histMap,
                        watchLaterUris = watchLaterSet,
                        playlists = playlistsList,
                        playlistVideosMap = playlistMap,
                        totalCount = updatedAll.size,
                        totalStorageFormatted = formatStorageSize(totalBytes)
                    )
                }
            }
        }
    }

    /** Called by the Activity with the real permission state (on create, after the dialog, and on resume). */
    fun onPermissionResult(granted: Boolean) {
        if (granted) {
            val s = _uiState.value
            if (!s.isPermissionGranted || !hasLoadedOnce || s.isDemoMode) {
                demoMode = false
                _uiState.update { it.copy(isPermissionGranted = true, isDemoMode = false, isLoading = true) }
                loadVideos()
            }
        } else if (!demoMode) {
            _uiState.update {
                it.copy(
                    isPermissionGranted = false,
                    isLoading = false,
                    allVideos = emptyList(),
                    filteredVideos = emptyList()
                )
            }
        }
    }

    fun onPermissionGranted() = onPermissionResult(true)

    fun loadVideos() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val videos = repository.loadVideos(allowSamples = demoMode)
            hasLoadedOnce = true
            val folders = videos.map { it.bucketDisplayName }.distinct().sorted()
            val totalBytes = videos.sumOf { it.sizeBytes }
            _uiState.update { state ->
                val filtered = processVideos(
                    videos = videos,
                    filter = state.selectedFilter,
                    sort = state.selectedSort,
                    isShuffle = state.isShuffleMode,
                    searchQuery = state.searchQuery,
                    historyMap = state.watchHistoryMap
                )
                state.copy(
                    allVideos = videos,
                    filteredVideos = filtered,
                    folders = folders,
                    isLoading = false,
                    totalCount = videos.size,
                    totalStorageFormatted = formatStorageSize(totalBytes)
                )
            }
        }
    }

    /** Opt-in demo mode (only reachable from the permission screen). */
    fun loadDemoClips() {
        demoMode = true
        _uiState.update { it.copy(isDemoMode = true, isLoading = true) }
        loadVideos()
    }

    fun refreshVideos() {
        if (_uiState.value.isPermissionGranted || demoMode) loadVideos()
    }

    fun selectFilter(filter: FeedFilter) {
        _uiState.update { state ->
            val filtered = processVideos(
                videos = state.allVideos,
                filter = filter,
                sort = state.selectedSort,
                isShuffle = state.isShuffleMode,
                searchQuery = state.searchQuery,
                historyMap = state.watchHistoryMap
            )
            state.copy(
                selectedFilter = filter,
                filteredVideos = filtered,
                currentIndex = 0,
                currentVideoId = filtered.firstOrNull()?.id,
                scrollRequest = scrollTo(0)
            )
        }
    }

    fun selectSort(sort: SortOption) {
        settingsManager.setSortOption(sort)
        settingsManager.setShuffleMode(false)
        _uiState.update { state ->
            val filtered = processVideos(
                videos = state.allVideos,
                filter = state.selectedFilter,
                sort = sort,
                isShuffle = false, // Selecting a sort option turns off random shuffle
                searchQuery = state.searchQuery,
                historyMap = state.watchHistoryMap
            )
            val newIdx = filtered.indexOfFirst { it.id == state.currentVideoId }.coerceAtLeast(0)

            state.copy(
                selectedSort = sort,
                isShuffleMode = false,
                filteredVideos = filtered,
                currentIndex = newIdx,
                scrollRequest = scrollTo(newIdx),
                toastMessage = "Sorted by: ${sort.displayName}"
            )
        }
    }

    fun toggleShuffleMode() {
        val newShuffle = !_uiState.value.isShuffleMode
        settingsManager.setShuffleMode(newShuffle)
        if (newShuffle) {
            // Fresh random permutation (done outside update{} because it mutates a field)
            shuffledVideoIds = _uiState.value.allVideos.map { it.id }.shuffled()
        }
        _uiState.update { state ->
            val filtered = processVideos(
                videos = state.allVideos,
                filter = state.selectedFilter,
                sort = state.selectedSort,
                isShuffle = newShuffle,
                searchQuery = state.searchQuery,
                historyMap = state.watchHistoryMap
            )
            // Keep the video the user is watching in both directions
            val newIdx = filtered.indexOfFirst { it.id == state.currentVideoId }.coerceAtLeast(0)

            state.copy(
                isShuffleMode = newShuffle,
                filteredVideos = filtered,
                currentIndex = newIdx,
                scrollRequest = scrollTo(newIdx),
                toastMessage = if (newShuffle) "Shuffle Mode: ON 🔀" else "Shuffle Mode: OFF"
            )
        }
    }

    fun setDisplayMode(mode: VideoDisplayMode) {
        settingsManager.setDisplayMode(mode)
        _uiState.update {
            it.copy(
                displayMode = mode,
                toastMessage = "Display: ${mode.label}"
            )
        }
    }

    fun cycleDisplayMode() {
        val modes = VideoDisplayMode.values()
        val newMode = modes[(_uiState.value.displayMode.ordinal + 1) % modes.size]
        settingsManager.setDisplayMode(newMode)
        _uiState.update { it.copy(displayMode = newMode, toastMessage = "Display: ${newMode.label}") }
    }

    fun setSearchQuery(query: String) {
        _uiState.update { state ->
            val filtered = processVideos(
                videos = state.allVideos,
                filter = state.selectedFilter,
                sort = state.selectedSort,
                isShuffle = state.isShuffleMode,
                searchQuery = query,
                historyMap = state.watchHistoryMap
            )
            // Cleared query -> go back to what was playing; otherwise results start at the top.
            val idx = if (query.isBlank()) {
                filtered.indexOfFirst { it.id == state.currentVideoId }.coerceAtLeast(0)
            } else 0
            state.copy(
                searchQuery = query,
                filteredVideos = filtered,
                currentIndex = idx,
                scrollRequest = scrollTo(idx)
            )
        }
    }

    fun toggleSearchOpen() {
        _uiState.update { state ->
            val nextOpen = !state.isSearchOpen
            val query = if (nextOpen) state.searchQuery else ""
            val filtered = processVideos(
                videos = state.allVideos,
                filter = state.selectedFilter,
                sort = state.selectedSort,
                isShuffle = state.isShuffleMode,
                searchQuery = query,
                historyMap = state.watchHistoryMap
            )
            // Closing search returns to the video that was playing before the search
            val idx = if (nextOpen) state.currentIndex
            else filtered.indexOfFirst { it.id == state.currentVideoId }.coerceAtLeast(0)
            state.copy(
                isSearchOpen = nextOpen,
                searchQuery = query,
                filteredVideos = filtered,
                currentIndex = idx,
                scrollRequest = if (nextOpen) state.scrollRequest else scrollTo(idx)
            )
        }
    }

    fun toggleGridView() {
        val newGrid = !_uiState.value.isGridView
        settingsManager.setGridView(newGrid)
        _uiState.update { it.copy(isGridView = newGrid) }
    }

    /** Opens the tapped video from the grid or from search results, at the right pager page. */
    fun selectVideoFromGrid(index: Int) {
        val before = _uiState.value
        val video = before.filteredVideos.getOrNull(index) ?: return
        if (before.isGridView) settingsManager.setGridView(false)
        _uiState.update { state ->
            val list = processVideos(
                videos = state.allVideos,
                filter = state.selectedFilter,
                sort = state.selectedSort,
                isShuffle = state.isShuffleMode,
                searchQuery = "",
                historyMap = state.watchHistoryMap
            )
            val idx = list.indexOfFirst { it.id == video.id }.coerceAtLeast(0)
            state.copy(
                filteredVideos = list,
                searchQuery = "",
                isSearchOpen = false,
                isGridView = false,
                currentIndex = idx,
                currentVideoId = video.id,
                scrollRequest = scrollTo(idx),
                isPlaying = true
            )
        }
    }

    fun consumeScrollRequest(request: ScrollRequest) {
        _uiState.update { if (it.scrollRequest == request) it.copy(scrollRequest = null) else it }
    }

    private fun processVideos(
        videos: List<LocalVideo>,
        filter: FeedFilter,
        sort: SortOption,
        isShuffle: Boolean,
        searchQuery: String,
        historyMap: Map<String, WatchHistory>,
        watchLaterUris: Set<String> = _uiState.value.watchLaterUris,
        playlistVideosMap: Map<Long, List<String>> = _uiState.value.playlistVideosMap
    ): List<LocalVideo> {
        // 1. Filter
        var result = when (filter) {
            is FeedFilter.All -> videos
            is FeedFilter.Favorites -> videos.filter { it.isFavorite }
            is FeedFilter.RecentlyAdded -> videos.sortedByDescending { it.dateAddedSec }
            is FeedFilter.RecentlyPlayed -> {
                videos.filter { historyMap.containsKey(it.uri.toString()) }
                    .sortedByDescending { historyMap[it.uri.toString()]?.lastPlayedAt ?: 0L }
            }
            is FeedFilter.WatchLater -> {
                videos.filter { watchLaterUris.contains(it.uri.toString()) }
            }
            is FeedFilter.Playlist -> {
                val uris = playlistVideosMap[filter.playlistId] ?: emptyList()
                val uriSet = uris.toSet()
                videos.filter { uriSet.contains(it.uri.toString()) }
            }
            is FeedFilter.DurationRange -> {
                videos.filter { filter.matches(it.durationMs) }
            }
            is FeedFilter.BrainMode -> {
                // "dopamine" is randomised below via the stable shuffle order
                videos.filter { brainModeMatches(filter.modeName, it.durationMs) }
            }
            is FeedFilter.ByFolder -> videos.filter { it.bucketDisplayName.equals(filter.folderName, ignoreCase = true) }
        }

        // 2. Search: every word must appear in the file name (without extension) or folder name,
        //    ignoring case and punctuation, so "neon cyber" finds "Neon_Cyber_Sunset_Loop.mp4".
        val tokens = normalizeForSearch(searchQuery).split(' ').filter { it.isNotEmpty() }
        if (tokens.isNotEmpty()) {
            result = result.filter { video ->
                val haystack = normalizeForSearch(video.displayName.substringBeforeLast('.', video.displayName)) +
                    " " + normalizeForSearch(video.bucketDisplayName)
                tokens.all { haystack.contains(it) }
            }
        }

        // 3. Shuffle or Sort
        val isDopamine = filter is FeedFilter.BrainMode && filter.modeName.equals("dopamine", ignoreCase = true)
        if (isShuffle || isDopamine) {
            val rank = shuffleRank(result)
            result = result.sortedBy { rank[it.id] ?: Int.MAX_VALUE }
        } else if (filter !is FeedFilter.RecentlyAdded && filter !is FeedFilter.RecentlyPlayed) {
            result = when (sort) {
                SortOption.DATE_NEWEST -> result.sortedByDescending { it.dateAddedSec }
                SortOption.DATE_OLDEST -> result.sortedBy { it.dateAddedSec }
                SortOption.DURATION_LONGEST -> result.sortedByDescending { it.durationMs }
                SortOption.DURATION_SHORTEST -> result.sortedBy { it.durationMs }
                SortOption.SIZE_LARGEST -> result.sortedByDescending { it.sizeBytes }
                SortOption.NAME_AZ -> result.sortedBy { it.displayName.lowercase(Locale.getDefault()) }
            }
        }

        return result
    }

    private fun normalizeForSearch(text: String): String =
        text.lowercase(Locale.getDefault()).map { if (it.isLetterOrDigit()) it else ' ' }.joinToString("")

    /** Stable random order; videos not seen before (e.g. after a refresh) are appended in random order. */
    private fun shuffleRank(videos: List<LocalVideo>): Map<Long, Int> {
        val known = shuffledVideoIds.toHashSet()
        val missing = videos.filter { it.id !in known }.map { it.id }
        if (missing.isNotEmpty()) shuffledVideoIds = shuffledVideoIds + missing.shuffled()
        return shuffledVideoIds.withIndex().associate { it.value to it.index }
    }

    fun playRandomVideo(): LocalVideo? {
        val available = _uiState.value.allVideos
        if (available.isEmpty()) return null
        val randomVideo = available.random()
        playVideo(randomVideo, FeedFilter.All)
        _uiState.update { it.copy(toastMessage = "Now playing: ${randomVideo.displayName} 🎲") }
        return randomVideo
    }

    fun playVideo(video: LocalVideo, filter: FeedFilter = FeedFilter.All) {
        _uiState.update { state ->
            val filtered = processVideos(
                videos = state.allVideos,
                filter = filter,
                sort = state.selectedSort,
                isShuffle = state.isShuffleMode,
                searchQuery = "",
                historyMap = state.watchHistoryMap,
                watchLaterUris = state.watchLaterUris,
                playlistVideosMap = state.playlistVideosMap
            )
            val idx = filtered.indexOfFirst { it.id == video.id }.coerceAtLeast(0)
            state.copy(
                selectedFilter = filter,
                filteredVideos = filtered,
                searchQuery = "",
                isSearchOpen = false,
                isGridView = false,
                currentIndex = idx,
                currentVideoId = video.id,
                scrollRequest = scrollTo(idx),
                isPlaying = true
            )
        }
    }

    fun playFilter(filter: FeedFilter) {
        _uiState.update { state ->
            val filtered = processVideos(
                videos = state.allVideos,
                filter = filter,
                sort = state.selectedSort,
                isShuffle = state.isShuffleMode,
                searchQuery = "",
                historyMap = state.watchHistoryMap,
                watchLaterUris = state.watchLaterUris,
                playlistVideosMap = state.playlistVideosMap
            )
            state.copy(
                selectedFilter = filter,
                filteredVideos = filtered,
                searchQuery = "",
                isSearchOpen = false,
                isGridView = false,
                currentIndex = 0,
                currentVideoId = filtered.firstOrNull()?.id,
                scrollRequest = scrollTo(0),
                isPlaying = true,
                toastMessage = "Playing: ${filter.displayName}"
            )
        }
    }

    fun openAddToPlaylist(video: LocalVideo) {
        _uiState.update { it.copy(playlistForVideo = video) }
    }

    fun closeAddToPlaylist() {
        _uiState.update { it.copy(playlistForVideo = null) }
    }

    fun openOptions(video: LocalVideo) {
        _uiState.update { it.copy(optionsForVideo = video) }
    }

    fun closeOptions() {
        _uiState.update { it.copy(optionsForVideo = null) }
    }

    fun toggleLoopMode() {
        val newLoop = !_uiState.value.isLoopMode
        settingsManager.setLoopMode(newLoop)
        _uiState.update {
            it.copy(
                isLoopMode = newLoop,
                toastMessage = if (newLoop) "Loop Video Enabled 🔁" else "Loop Video Disabled"
            )
        }
    }

    /** App left the foreground: pause, but remember whether to resume. */
    fun pausePlayback() {
        if (_uiState.value.isPlaying) resumeAfterForeground = true
        _uiState.update { it.copy(isPlaying = false) }
    }

    /** App is back in the foreground: resume only if it was playing when it left. */
    fun resumePlayback() {
        if (resumeAfterForeground) {
            resumeAfterForeground = false
            _uiState.update { it.copy(isPlaying = true) }
        }
    }

    /** Tap on the video: first tap reveals the controls, tap with controls visible toggles play/pause. */
    fun onVideoTapped() {
        if (!_uiState.value.areControlsVisible) {
            setControlsVisible(true)
        } else {
            togglePlayPause()
        }
    }

    fun toggleControlsVisibility() {
        _uiState.update { it.copy(areControlsVisible = !it.areControlsVisible) }
    }

    fun setControlsVisible(visible: Boolean) {
        _uiState.update { it.copy(areControlsVisible = visible) }
    }

    fun updatePictureInPictureMode(isInPiP: Boolean) {
        _uiState.update { it.copy(isInPictureInPicture = isInPiP) }
    }

    fun toggleVideoInPlaylist(playlistId: Long, video: LocalVideo) {
        viewModelScope.launch {
            val currentVideos = _uiState.value.playlistVideosMap[playlistId] ?: emptyList()
            val uriStr = video.uri.toString()
            val playlist = _uiState.value.playlists.find { it.id == playlistId }
            val playlistName = playlist?.name ?: "Playlist"
            if (currentVideos.contains(uriStr)) {
                repository.removeVideoFromPlaylist(playlistId, uriStr)
                _uiState.update { it.copy(toastMessage = "Removed from $playlistName") }
            } else {
                repository.addVideoToPlaylist(playlistId, uriStr)
                _uiState.update { it.copy(toastMessage = "Added to $playlistName ✨") }
            }
        }
    }

    fun createPlaylistAndAdd(name: String, emoji: String, video: LocalVideo) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val newId = repository.createPlaylist(name.trim(), emoji)
            repository.addVideoToPlaylist(newId, video.uri.toString())
            _uiState.update { it.copy(toastMessage = "Saved to new playlist '$name' $emoji") }
        }
    }

    fun createPlaylist(name: String, emoji: String = "📁") {
        if (name.isBlank()) return
        viewModelScope.launch {
            repository.createPlaylist(name.trim(), emoji)
            _uiState.update { it.copy(toastMessage = "Playlist '$name' created! 📁") }
        }
    }

    fun deletePlaylist(playlist: com.example.data.local.entity.PlaylistEntity) {
        viewModelScope.launch {
            repository.deletePlaylist(playlist)
            _uiState.update { it.copy(toastMessage = "Playlist '${playlist.name}' deleted") }
        }
    }

    fun addVideoToPlaylist(playlistId: Long, video: LocalVideo) {
        viewModelScope.launch {
            repository.addVideoToPlaylist(playlistId, video.uri.toString())
            val playlist = _uiState.value.playlists.find { it.id == playlistId }
            _uiState.update { it.copy(toastMessage = "Added to ${playlist?.name ?: "playlist"}") }
        }
    }

    fun removeVideoFromPlaylist(playlistId: Long, video: LocalVideo) {
        viewModelScope.launch {
            repository.removeVideoFromPlaylist(playlistId, video.uri.toString())
            _uiState.update { it.copy(toastMessage = "Removed from playlist") }
        }
    }

    fun clearWatchHistory() {
        viewModelScope.launch {
            repository.clearWatchHistory()
            _uiState.update { it.copy(toastMessage = "Watch history cleared") }
        }
    }

    fun onPageChanged(index: Int) {
        val video = _uiState.value.filteredVideos.getOrNull(index)
        if (video != null) recordPlayback(video)
        _uiState.update { state ->
            if (video != null) {
                state.copy(currentIndex = index, currentVideoId = video.id, isPlaying = true)
            } else {
                state.copy(currentIndex = index)
            }
        }
    }

    /** Saves where the user stopped so long videos can resume. Near-the-end counts as finished. */
    fun saveWatchPosition(video: LocalVideo, positionMs: Long) {
        val finished = video.durationMs > 0 && positionMs >= video.durationMs - 5_000L
        viewModelScope.launch {
            repository.updateWatchPosition(video, if (finished) 0L else positionMs.coerceAtLeast(0L))
        }
    }

    fun togglePlayPause() {
        _uiState.update {
            it.copy(
                isPlaying = !it.isPlaying,
                playPauseUserActionTrigger = System.currentTimeMillis()
            )
        }
    }

    fun setPlaying(playing: Boolean) {
        _uiState.update { it.copy(isPlaying = playing) }
    }

    fun toggleMute() {
        val newMuted = !_uiState.value.isMuted
        settingsManager.setMuted(newMuted)
        _uiState.update {
            it.copy(
                isMuted = newMuted,
                toastMessage = if (newMuted) "Muted 🔇" else "Sound On 🔊"
            )
        }
    }

    fun setPlaybackSpeed(speed: Float) {
        settingsManager.setPlaybackSpeed(speed)
        _uiState.update {
            it.copy(
                playbackSpeed = speed,
                toastMessage = "Speed: ${speed}x"
            )
        }
    }

    fun cyclePlaybackSpeed() {
        val speeds = listOf(0.5f, 1.0f, 1.25f, 1.5f, 2.0f)
        val currentIdx = speeds.indexOfFirst { kotlin.math.abs(it - _uiState.value.playbackSpeed) < 0.01f }
        val nextIdx = if (currentIdx in 0 until speeds.size - 1) currentIdx + 1 else 0
        val newSpeed = speeds[nextIdx]
        settingsManager.setPlaybackSpeed(newSpeed)
        _uiState.update { state ->
            state.copy(
                playbackSpeed = newSpeed,
                toastMessage = "Speed: ${newSpeed}x"
            )
        }
    }

    fun toggleFavorite(video: LocalVideo) {
        viewModelScope.launch {
            val isNowFavorite = repository.toggleFavorite(video)
            _uiState.update { state ->
                val updatedAll = state.allVideos.map {
                    if (it.uri == video.uri) it.copy(isFavorite = isNowFavorite) else it
                }
                val updatedFiltered = processVideos(
                    videos = updatedAll,
                    filter = state.selectedFilter,
                    sort = state.selectedSort,
                    isShuffle = state.isShuffleMode,
                    searchQuery = state.searchQuery,
                    historyMap = state.watchHistoryMap
                )
                val toast = if (isNowFavorite) "Added to Favorites ❤️" else "Removed from Favorites"
                state.copy(
                    allVideos = updatedAll,
                    filteredVideos = updatedFiltered,
                    toastMessage = toast
                )
            }
        }
    }

    fun likeVideo(video: LocalVideo) {
        val alreadyFavorite = _uiState.value.allVideos.firstOrNull { it.uri == video.uri }?.isFavorite
            ?: video.isFavorite
        if (!alreadyFavorite) {
            toggleFavorite(video)
        } else {
            _uiState.update { it.copy(toastMessage = "Already in your Favorites ❤️") }
        }
    }

    fun toggleWatchLater(video: LocalVideo) {
        val uri = video.uri.toString()
        val isSaved = _uiState.value.watchLaterUris.contains(uri)
        viewModelScope.launch {
            if (isSaved) {
                repository.removeWatchLater(uri)
                _uiState.update { it.copy(toastMessage = "Removed from Watch Later") }
            } else {
                repository.addWatchLater(uri)
                _uiState.update { it.copy(toastMessage = "Saved to Watch Later ⏰") }
            }
        }
    }

    fun openMetadata(video: LocalVideo) {
        viewModelScope.launch {
            val details = metadataExtractor.extractDetails(video)
            _uiState.update { it.copy(selectedMetadata = details) }
        }
    }

    fun closeMetadata() {
        _uiState.update { it.copy(selectedMetadata = null) }
    }

    fun requestDeleteConfirmation(video: LocalVideo) {
        _uiState.update { it.copy(videoToDelete = video) }
    }

    fun cancelDelete() {
        _uiState.update { it.copy(
            videoToDelete = null, 
            pendingDeleteIntentSender = null,
            pendingDeleteVideoUri = null,
            toastMessage = "Deletion cancelled"
        ) }
    }

    fun confirmDelete(video: LocalVideo) {
        viewModelScope.launch {
            when (val result = repository.deleteVideo(video)) {
                is DeletionResult.Success -> {
                    removeVideoFromState(video)
                    _uiState.update {
                        it.copy(
                            videoToDelete = null,
                            toastMessage = "Video removed from feed"
                        )
                    }
                }
                is DeletionResult.RequiresPermission -> {
                    _uiState.update {
                        it.copy(
                            videoToDelete = null,
                            pendingDeleteIntentSender = result.intentSender,
                            pendingDeleteVideoUri = video.uri.toString()
                        )
                    }
                }
                is DeletionResult.Error -> {
                    _uiState.update {
                        it.copy(
                            videoToDelete = null,
                            toastMessage = "Could not delete: ${result.message}"
                        )
                    }
                }
            }
        }
    }

    fun onDeletePermissionGranted(videoUri: String?) {
        // The system confirmed the delete: only now is it safe to drop the video's favourites/playlists/etc.
        if (videoUri != null) {
            viewModelScope.launch { repository.cleanupVideoRecords(videoUri) }
        }
        _uiState.update { state ->
            val updatedAll = state.allVideos.filterNot { it.uri.toString() == videoUri }
            removeFromLists(state, updatedAll).copy(
                pendingDeleteIntentSender = null,
                pendingDeleteVideoUri = null,
                toastMessage = "Video deleted"
            )
        }
    }

    private fun removeVideoFromState(video: LocalVideo) {
        _uiState.update { state ->
            removeFromLists(state, state.allVideos.filterNot { it.uri == video.uri })
        }
    }

    private fun removeFromLists(state: VideoFeedUiState, updatedAll: List<LocalVideo>): VideoFeedUiState {
        val updatedFiltered = processVideos(
            videos = updatedAll,
            filter = state.selectedFilter,
            sort = state.selectedSort,
            isShuffle = state.isShuffleMode,
            searchQuery = state.searchQuery,
            historyMap = state.watchHistoryMap
        )
        // Stay on the same video if it still exists; otherwise the next one takes its place.
        val keptIdx = updatedFiltered.indexOfFirst { it.id == state.currentVideoId }
        val newIndex = if (keptIdx >= 0) keptIdx
        else state.currentIndex.coerceIn(0, (updatedFiltered.size - 1).coerceAtLeast(0))
        val totalBytes = updatedAll.sumOf { it.sizeBytes }
        return state.copy(
            allVideos = updatedAll,
            filteredVideos = updatedFiltered,
            currentIndex = newIndex,
            currentVideoId = updatedFiltered.getOrNull(newIndex)?.id,
            folders = updatedAll.map { it.bucketDisplayName }.distinct().sorted(),
            totalCount = updatedAll.size,
            totalStorageFormatted = formatStorageSize(totalBytes)
        )
    }

    private fun recordPlayback(video: LocalVideo) {
        recordJob?.cancel()
        recordJob = viewModelScope.launch {
            kotlinx.coroutines.delay(1200L)
            repository.recordWatchStart(video)
        }
    }

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    private fun formatStorageSize(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        return String.format(Locale.getDefault(), "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }
}

class VideoFeedViewModelFactory(private val context: Context) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val database = AppDatabase.getDatabase(context)
        val repository = VideoRepository(context, database)
        val metadataExtractor = MetadataExtractor(context)
        val settingsManager = com.example.data.local.SettingsManager(context)
        return VideoFeedViewModel(repository, metadataExtractor, settingsManager) as T
    }
}

