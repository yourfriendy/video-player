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
import com.example.ui.model.SortOption
import com.example.ui.model.VideoDisplayMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale

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
    val isPermissionGranted: Boolean = true,
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
        loadVideos()
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
                    val isFilterAffectedByHistory = state.selectedFilter == FeedFilter.RecentlyPlayed
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

    fun onPermissionGranted() {
        _uiState.update { it.copy(isPermissionGranted = true, isLoading = true) }
        loadVideos()
    }

    fun loadVideos() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val videos = repository.loadVideos()
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
                    isPermissionGranted = true,
                    totalCount = videos.size,
                    totalStorageFormatted = formatStorageSize(totalBytes)
                )
            }
        }
    }

    fun loadDemoClips() {
        onPermissionGranted()
    }

    fun refreshVideos() {
        onPermissionGranted()
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
                currentIndex = 0
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
            val currentVideo = state.filteredVideos.getOrNull(state.currentIndex)
            val newIdx = if (currentVideo != null) {
                filtered.indexOfFirst { it.id == currentVideo.id }.coerceAtLeast(0)
            } else 0

            state.copy(
                selectedSort = sort,
                isShuffleMode = false,
                filteredVideos = filtered,
                currentIndex = newIdx,
                toastMessage = "Sorted by: ${sort.displayName}"
            )
        }
    }

    fun toggleShuffleMode() {
        val newShuffle = !_uiState.value.isShuffleMode
        settingsManager.setShuffleMode(newShuffle)
        _uiState.update { state ->
            if (newShuffle) {
                // Generate new random permutation
                shuffledVideoIds = state.allVideos.map { it.id }.shuffled()
            }
            val filtered = processVideos(
                videos = state.allVideos,
                filter = state.selectedFilter,
                sort = state.selectedSort,
                isShuffle = newShuffle,
                searchQuery = state.searchQuery,
                historyMap = state.watchHistoryMap
            )
            val currentVideo = state.filteredVideos.getOrNull(state.currentIndex)
            val newIdx = if (currentVideo != null && newShuffle) {
                filtered.indexOfFirst { it.id == currentVideo.id }.coerceAtLeast(0)
            } else 0

            state.copy(
                isShuffleMode = newShuffle,
                filteredVideos = filtered,
                currentIndex = newIdx,
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
        _uiState.update { state ->
            val nextIndex = (state.displayMode.ordinal + 1) % modes.size
            val newMode = modes[nextIndex]
            settingsManager.setDisplayMode(newMode)
            state.copy(
                displayMode = newMode,
                toastMessage = "Display: ${newMode.label}"
            )
        }
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
            state.copy(
                searchQuery = query,
                filteredVideos = filtered,
                currentIndex = 0
            )
        }
    }

    fun toggleSearchOpen() {
        _uiState.update { state ->
            val nextOpen = !state.isSearchOpen
            val query = if (!nextOpen) "" else state.searchQuery
            val filtered = processVideos(
                videos = state.allVideos,
                filter = state.selectedFilter,
                sort = state.selectedSort,
                isShuffle = state.isShuffleMode,
                searchQuery = query,
                historyMap = state.watchHistoryMap
            )
            state.copy(
                isSearchOpen = nextOpen,
                searchQuery = query,
                filteredVideos = filtered,
                currentIndex = 0
            )
        }
    }

    fun toggleGridView() {
        val newGrid = !_uiState.value.isGridView
        settingsManager.setGridView(newGrid)
        _uiState.update { it.copy(isGridView = newGrid) }
    }

    fun selectVideoFromGrid(index: Int) {
        settingsManager.setGridView(false)
        _uiState.update { state ->
            val validIndex = index.coerceIn(0, (state.filteredVideos.size - 1).coerceAtLeast(0))
            state.copy(
                currentIndex = validIndex,
                isGridView = false,
                isPlaying = true
            )
        }
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
                videos.filter { it.durationMs in filter.minMs..filter.maxMs }
            }
            is FeedFilter.BrainMode -> {
                when (filter.modeName.lowercase(Locale.getDefault())) {
                    "focus" -> videos.filter { it.durationMs in 10_000L..180_000L }
                    "calm" -> videos.filter { it.durationMs in 180_000L..600_000L }
                    "dopamine" -> videos.shuffled()
                    "low energy" -> videos.filter { it.durationMs >= 600_000L }
                    else -> videos
                }
            }
            is FeedFilter.ByFolder -> videos.filter { it.bucketDisplayName.equals(filter.folderName, ignoreCase = true) }
        }

        // 2. Search
        if (searchQuery.isNotBlank()) {
            val q = searchQuery.trim().lowercase(Locale.getDefault())
            result = result.filter {
                it.displayName.lowercase(Locale.getDefault()).contains(q) ||
                    it.bucketDisplayName.lowercase(Locale.getDefault()).contains(q)
            }
        }

        // 3. Shuffle or Sort
        if (isShuffle) {
            val idOrder = shuffledVideoIds.withIndex().associate { it.value to it.index }
            result = result.sortedBy { idOrder[it.id] ?: Int.MAX_VALUE }
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
                currentIndex = idx,
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
                currentIndex = 0,
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

    fun pausePlayback() {
        _uiState.update { it.copy(isPlaying = false) }
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
        _uiState.update { state ->
            if (index in state.filteredVideos.indices) {
                val currentVideo = state.filteredVideos[index]
                recordPlayback(currentVideo)
                state.copy(currentIndex = index, isPlaying = true)
            } else {
                state.copy(currentIndex = index)
            }
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
        if (!video.isFavorite) {
            toggleFavorite(video)
        } else {
            _uiState.update { it.copy(toastMessage = "Already in your Favorites ❤️") }
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
        _uiState.update { state ->
            val updatedAll = state.allVideos.filterNot { it.uri.toString() == videoUri }
            val updatedFiltered = processVideos(
                videos = updatedAll,
                filter = state.selectedFilter,
                sort = state.selectedSort,
                isShuffle = state.isShuffleMode,
                searchQuery = state.searchQuery,
                historyMap = state.watchHistoryMap
            )
            val newIndex = if (state.currentIndex >= updatedFiltered.size) {
                (updatedFiltered.size - 1).coerceAtLeast(0)
            } else {
                state.currentIndex
            }
            val totalBytes = updatedAll.sumOf { it.sizeBytes }
            state.copy(
                allVideos = updatedAll,
                filteredVideos = updatedFiltered,
                currentIndex = newIndex,
                pendingDeleteIntentSender = null,
                totalCount = updatedAll.size,
                totalStorageFormatted = formatStorageSize(totalBytes),
                toastMessage = "Video deleted"
            )
        }
    }

    private fun removeVideoFromState(video: LocalVideo) {
        _uiState.update { state ->
            val updatedAll = state.allVideos.filterNot { it.uri == video.uri }
            val updatedFiltered = processVideos(
                videos = updatedAll,
                filter = state.selectedFilter,
                sort = state.selectedSort,
                isShuffle = state.isShuffleMode,
                searchQuery = state.searchQuery,
                historyMap = state.watchHistoryMap
            )
            val newIndex = if (state.currentIndex >= updatedFiltered.size) {
                (updatedFiltered.size - 1).coerceAtLeast(0)
            } else {
                state.currentIndex
            }
            val totalBytes = updatedAll.sumOf { it.sizeBytes }
            state.copy(
                allVideos = updatedAll,
                filteredVideos = updatedFiltered,
                currentIndex = newIndex,
                totalCount = updatedAll.size,
                totalStorageFormatted = formatStorageSize(totalBytes)
            )
        }
    }

    private fun recordPlayback(video: LocalVideo) {
        recordJob?.cancel()
        recordJob = viewModelScope.launch {
            kotlinx.coroutines.delay(1200L)
            repository.recordWatchProgress(video, 0L)
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

