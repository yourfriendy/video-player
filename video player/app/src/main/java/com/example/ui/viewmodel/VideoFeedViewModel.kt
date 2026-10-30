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
import com.example.data.download.DownloadException
import com.example.data.download.InstagramDownloader
import com.example.data.download.InstagramParser
import com.example.ui.model.DownloadStatus
import com.example.ui.model.FeedFilter
import com.example.ui.model.SearchOptions
import com.example.ui.model.SearchSort
import com.example.ui.model.brainModeMatches
import com.example.ui.model.SortOption
import com.example.ui.model.VideoDisplayMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.withContext
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
    /** The search currently applied to the feed (shown as a chip). Empty = no search active. */
    val searchQuery: String = "",
    /** Feed is restricted to these video ids, in order, while a search is applied. */
    val searchScopeIds: List<Long>? = null,
    /** Filter to restore when the applied search is cleared. */
    val preSearchFilter: FeedFilter? = null,
    /** True while the full-screen search page is showing. */
    val isSearchOpen: Boolean = false,
    val searchDraft: String = "",
    val searchOptions: SearchOptions = SearchOptions(),
    val searchResults: List<LocalVideo> = emptyList(),
    val isSearchRunning: Boolean = false,
    val recentSearches: List<String> = emptyList(),
    val isDownloaderOpen: Boolean = false,
    val downloadUrl: String = "",
    val downloadStatus: DownloadStatus = DownloadStatus.Idle,
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
    private val settingsManager: com.example.data.local.SettingsManager,
    private val downloader: InstagramDownloader
) : ViewModel() {

    private val _uiState: MutableStateFlow<VideoFeedUiState>
    val uiState: StateFlow<VideoFeedUiState>

    private var shuffledVideoIds: List<Long> = emptyList()
    private var recordJob: kotlinx.coroutines.Job? = null
    private var hasLoadedOnce = false
    private var demoMode = false
    private var resumeAfterForeground = false

    // Search engine state. Declared before init{} on purpose: init-time coroutines read these.
    private data class SearchInput(val query: String, val options: SearchOptions, val version: Int)

    private val searchTrigger = MutableStateFlow(SearchInput("", SearchOptions(), 0))
    private var searchVersion = 0

    // Normalised name/folder text per video, computed once instead of on every keystroke
    private class SearchEntry(val name: String, val folder: String)
    private val searchIndex = java.util.concurrent.ConcurrentHashMap<Long, SearchEntry>()

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
                recentSearches = settingsManager.getRecentSearches(),
                isLoopMode = initialLoop
            )
        )
        uiState = _uiState.asStateFlow()
        observeFavoritesAndWatchHistory()
        startSearchEngine()
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
                            scopeIds = state.searchScopeIds,
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
        viewModelScope.launch { reloadLibrary(showLoading = true) }
    }

    /** [showLoading] = false refreshes the list in place (used after a download) without blanking the feed. */
    private suspend fun reloadLibrary(showLoading: Boolean) {
        run {
            if (showLoading) _uiState.update { it.copy(isLoading = true) }
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
                    scopeIds = state.searchScopeIds,
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

    // ------------------------------------------------------------------------------------------
    // Instagram downloader
    // ------------------------------------------------------------------------------------------

    private var downloadJob: kotlinx.coroutines.Job? = null

    private fun isDownloadBusy(): Boolean {
        val status = _uiState.value.downloadStatus
        return status is DownloadStatus.Fetching || status is DownloadStatus.Downloading
    }

    /** Opens the downloader sheet. With [autoStart] a freshly shared link starts downloading right away. */
    fun openDownloader(prefill: String? = null, autoStart: Boolean = false) {
        val busy = isDownloadBusy()
        _uiState.update { state ->
            state.copy(
                isDownloaderOpen = true,
                downloadUrl = if (prefill != null && !busy) prefill else state.downloadUrl,
                downloadStatus = if (prefill != null && !busy) DownloadStatus.Idle else state.downloadStatus
            )
        }
        if (autoStart && prefill != null && !busy) startDownload()
    }

    fun closeDownloader() {
        _uiState.update { it.copy(isDownloaderOpen = false) }
    }

    fun setDownloadUrl(text: String) {
        _uiState.update { state ->
            val status = state.downloadStatus
            state.copy(
                downloadUrl = text,
                downloadStatus = if (status is DownloadStatus.Failed || status is DownloadStatus.Success) {
                    DownloadStatus.Idle
                } else status
            )
        }
    }

    /** Called with whatever another app shared to us (e.g. Instagram's Share → OffScroll). */
    fun handleSharedText(text: String?) {
        val url = InstagramParser.extractInstagramUrl(text)
        if (url == null) {
            _uiState.update { it.copy(toastMessage = "No Instagram link found in what you shared") }
            return
        }
        openDownloader(prefill = url, autoStart = true)
    }

    fun startDownload() {
        if (isDownloadBusy()) return
        val url = InstagramParser.extractInstagramUrl(_uiState.value.downloadUrl)
        if (url == null) {
            _uiState.update {
                it.copy(downloadStatus = DownloadStatus.Failed("Paste a link to an Instagram reel (instagram.com/reel/…)."))
            }
            return
        }
        downloadJob = viewModelScope.launch {
            try {
                _uiState.update { it.copy(downloadStatus = DownloadStatus.Fetching) }
                val reel = downloader.resolve(url)
                _uiState.update { it.copy(downloadStatus = DownloadStatus.Downloading(-1f)) }
                val savedUri = downloader.download(reel) { progress ->
                    _uiState.update { state ->
                        if (state.downloadStatus is DownloadStatus.Downloading) {
                            state.copy(downloadStatus = DownloadStatus.Downloading(progress))
                        } else state
                    }
                }
                reloadLibrary(showLoading = false)
                _uiState.update { state ->
                    state.copy(
                        downloadStatus = DownloadStatus.Success(savedUri, reel.title ?: "Instagram reel"),
                        toastMessage = if (!state.isDownloaderOpen) "Reel saved to your library ✔" else state.toastMessage
                    )
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                _uiState.update { it.copy(downloadStatus = DownloadStatus.Idle) }
                throw e
            } catch (e: DownloadException) {
                _uiState.update { it.copy(downloadStatus = DownloadStatus.Failed(e.message ?: "Download failed.")) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(downloadStatus = DownloadStatus.Failed("Something went wrong. Please try again."))
                }
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
    }

    /** "Play now" after a successful download: jump straight to the new video in the feed. */
    fun playDownloaded() {
        val status = _uiState.value.downloadStatus as? DownloadStatus.Success ?: return
        val video = _uiState.value.allVideos.firstOrNull { it.uri == status.uri }
        closeDownloader()
        if (video != null) {
            playVideo(video, FeedFilter.All)
        } else {
            _uiState.update { it.copy(toastMessage = "Saved to Movies/OffScroll") }
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
                scopeIds = null,
                historyMap = state.watchHistoryMap
            )
            state.copy(
                selectedFilter = filter,
                filteredVideos = filtered,
                searchQuery = "",
                searchScopeIds = null,
                preSearchFilter = null,
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
                scopeIds = state.searchScopeIds,
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
                scopeIds = state.searchScopeIds,
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

    // ------------------------------------------------------------------------------------------
    // Search: a separate full-screen page. The feed is only touched when a result is picked.
    // ------------------------------------------------------------------------------------------

    fun openSearch() {
        _uiState.update { it.copy(isSearchOpen = true) }
        triggerSearch()
    }

    /** Leaves the search page without changing the feed or the video that was playing. */
    fun closeSearch() {
        _uiState.update { it.copy(isSearchOpen = false) }
    }

    fun setSearchDraft(text: String) {
        _uiState.update { it.copy(searchDraft = text, isSearchRunning = true) }
        triggerSearch()
    }

    fun setSearchOptions(options: SearchOptions) {
        _uiState.update { it.copy(searchOptions = options, isSearchRunning = true) }
        triggerSearch()
    }

    /**
     * Applies the current results as the feed (starting at [video]) and closes the search page.
     * The feed keeps showing these results until the search chip is cleared; clearing it returns
     * to the full feed on whatever video is playing at that moment.
     */
    fun openSearchResult(video: LocalVideo) {
        val draft = _uiState.value.searchDraft.trim()
        if (draft.isNotEmpty()) rememberSearch(draft)
        _uiState.update { state ->
            val ids = state.searchResults.map { it.id }.let { if (video.id in it) it else it + video.id }
            val list = processVideos(
                videos = state.allVideos,
                filter = FeedFilter.All,
                sort = state.selectedSort,
                isShuffle = state.isShuffleMode,
                scopeIds = ids,
                historyMap = state.watchHistoryMap
            )
            val idx = list.indexOfFirst { it.id == video.id }.coerceAtLeast(0)
            state.copy(
                searchScopeIds = ids,
                searchQuery = draft.ifBlank { "Filtered videos" },
                preSearchFilter = state.preSearchFilter ?: state.selectedFilter,
                selectedFilter = FeedFilter.All,
                filteredVideos = list,
                isSearchOpen = false,
                isGridView = false,
                currentIndex = idx,
                currentVideoId = video.id,
                scrollRequest = scrollTo(idx),
                isPlaying = true
            )
        }
    }

    /** The ✕ on the search chip: back to the full feed, staying on the video that is playing now. */
    fun clearSearch() {
        _uiState.update { state ->
            if (state.searchScopeIds == null) return@update state.copy(searchQuery = "")
            val restoreFilter = state.preSearchFilter ?: FeedFilter.All
            fun build(filter: FeedFilter) = processVideos(
                videos = state.allVideos,
                filter = filter,
                sort = state.selectedSort,
                isShuffle = state.isShuffleMode,
                scopeIds = null,
                historyMap = state.watchHistoryMap
            )
            var filter = restoreFilter
            var list = build(filter)
            var idx = list.indexOfFirst { it.id == state.currentVideoId }
            if (idx < 0) {
                // The playing video isn't in the old filter: keep it playing by showing everything
                filter = FeedFilter.All
                list = build(filter)
                idx = list.indexOfFirst { it.id == state.currentVideoId }.coerceAtLeast(0)
            }
            state.copy(
                searchQuery = "",
                searchScopeIds = null,
                preSearchFilter = null,
                selectedFilter = filter,
                filteredVideos = list,
                currentIndex = idx,
                currentVideoId = list.getOrNull(idx)?.id,
                scrollRequest = scrollTo(idx)
            )
        }
    }

    fun removeRecentSearch(query: String) {
        val updated = _uiState.value.recentSearches.filterNot { it.equals(query, ignoreCase = true) }
        settingsManager.setRecentSearches(updated)
        _uiState.update { it.copy(recentSearches = updated) }
    }

    fun clearRecentSearches() {
        settingsManager.setRecentSearches(emptyList())
        _uiState.update { it.copy(recentSearches = emptyList()) }
    }

    private fun rememberSearch(query: String) {
        val updated = (listOf(query) + _uiState.value.recentSearches.filterNot { it.equals(query, ignoreCase = true) })
            .take(8)
        settingsManager.setRecentSearches(updated)
        _uiState.update { it.copy(recentSearches = updated) }
    }


    private fun triggerSearch() {
        val s = _uiState.value
        searchTrigger.value = SearchInput(s.searchDraft, s.searchOptions, ++searchVersion)
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private fun startSearchEngine() {
        viewModelScope.launch {
            searchTrigger
                .debounce(80L)
                .mapLatest { input ->
                    withContext(Dispatchers.Default) { runSearch(input.query, input.options, _uiState.value.allVideos) }
                }
                .collect { results ->
                    _uiState.update { it.copy(searchResults = results, isSearchRunning = false) }
                }
        }
    }


    private fun entryFor(video: LocalVideo): SearchEntry = searchIndex.getOrPut(video.id) {
        SearchEntry(
            name = normalizeForSearch(video.displayName.substringBeforeLast('.', video.displayName)),
            folder = normalizeForSearch(video.bucketDisplayName)
        )
    }

    /** Returns matches best-first. An empty query with default options deliberately returns nothing. */
    private fun runSearch(query: String, options: SearchOptions, videos: List<LocalVideo>): List<LocalVideo> {
        val tokens = normalizeForSearch(query).split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty() && options.isDefault) return emptyList()

        val scored = ArrayList<Pair<LocalVideo, Int>>()
        for (video in videos) {
            if (options.favoritesOnly && !video.isFavorite) continue
            if (!options.duration.matches(video.durationMs)) continue
            if (options.folder != null && video.bucketDisplayName != options.folder) continue
            var score = 0
            if (tokens.isNotEmpty()) {
                val entry = entryFor(video)
                var matchedAll = true
                for (token in tokens) {
                    val inName = entry.name.indexOf(token)
                    val points = when {
                        inName == 0 -> 100
                        inName > 0 && entry.name[inName - 1] == ' ' -> 60
                        inName > 0 -> 30
                        entry.folder.contains(token) -> 10
                        else -> -1
                    }
                    if (points < 0) {
                        matchedAll = false
                        break
                    }
                    score += points
                }
                if (!matchedAll) continue
                if (video.isFavorite) score += 5
            }
            scored.add(video to score)
        }
        val sorted = when (options.sort) {
            SearchSort.BEST_MATCH -> scored.sortedWith(
                compareByDescending<Pair<LocalVideo, Int>> { it.second }.thenByDescending { it.first.dateAddedSec }
            )
            SearchSort.NEWEST -> scored.sortedByDescending { it.first.dateAddedSec }
            SearchSort.OLDEST -> scored.sortedBy { it.first.dateAddedSec }
            SearchSort.LONGEST -> scored.sortedByDescending { it.first.durationMs }
            SearchSort.SHORTEST -> scored.sortedBy { it.first.durationMs }
            SearchSort.NAME -> scored.sortedBy { it.first.displayName.lowercase(Locale.getDefault()) }
        }
        return sorted.map { it.first }
    }

    /** Raising the volume with the side swipe should always make sound audible. */
    fun unmute() {
        if (_uiState.value.isMuted) {
            settingsManager.setMuted(false)
            _uiState.update { it.copy(isMuted = false) }
        }
    }

    fun toggleGridView() {
        val newGrid = !_uiState.value.isGridView
        settingsManager.setGridView(newGrid)
        _uiState.update { it.copy(isGridView = newGrid) }
    }

    /** Opens the tapped video from the grid at its position in the current feed. */
    fun selectVideoFromGrid(index: Int) {
        val before = _uiState.value
        val video = before.filteredVideos.getOrNull(index) ?: return
        if (before.isGridView) settingsManager.setGridView(false)
        _uiState.update { state ->
            state.copy(
                isGridView = false,
                currentIndex = index,
                currentVideoId = video.id,
                scrollRequest = scrollTo(index),
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
        scopeIds: List<Long>?,
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

        // 2. Search scope: the feed shows exactly the picked search results, in result order
        val scopeOrder = scopeIds?.withIndex()?.associate { it.value to it.index }
        if (scopeOrder != null) {
            result = result.filter { it.id in scopeOrder }.sortedBy { scopeOrder[it.id] }
        }

        // 3. Shuffle or Sort
        val isDopamine = filter is FeedFilter.BrainMode && filter.modeName.equals("dopamine", ignoreCase = true)
        if (isShuffle || isDopamine) {
            val rank = shuffleRank(result)
            result = result.sortedBy { rank[it.id] ?: Int.MAX_VALUE }
        } else if (scopeOrder == null && filter !is FeedFilter.RecentlyAdded && filter !is FeedFilter.RecentlyPlayed) {
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
                scopeIds = null,
                historyMap = state.watchHistoryMap,
                watchLaterUris = state.watchLaterUris,
                playlistVideosMap = state.playlistVideosMap
            )
            val idx = filtered.indexOfFirst { it.id == video.id }.coerceAtLeast(0)
            state.copy(
                selectedFilter = filter,
                filteredVideos = filtered,
                searchQuery = "",
                searchScopeIds = null,
                preSearchFilter = null,
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
                scopeIds = null,
                historyMap = state.watchHistoryMap,
                watchLaterUris = state.watchLaterUris,
                playlistVideosMap = state.playlistVideosMap
            )
            state.copy(
                selectedFilter = filter,
                filteredVideos = filtered,
                searchQuery = "",
                searchScopeIds = null,
                preSearchFilter = null,
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
                    scopeIds = state.searchScopeIds,
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
            scopeIds = state.searchScopeIds,
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
        return VideoFeedViewModel(repository, metadataExtractor, settingsManager, InstagramDownloader(context.applicationContext)) as T
    }
}

