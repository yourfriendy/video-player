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
import com.example.data.download.InstagramStoryService
import com.example.data.download.LoginRequiredException
import com.example.ui.model.DownloadStatus
import com.example.ui.model.DownloaderTab
import com.example.ui.model.StoryItemDownload
import com.example.ui.model.StoryUiState
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
    val downloaderTab: DownloaderTab = DownloaderTab.REELS,
    val storyInput: String = "",
    val storyState: StoryUiState = StoryUiState.Idle,
    val storyDownloads: Map<String, StoryItemDownload> = emptyMap(),
    val isIgSignedIn: Boolean = false,
    val isIgLoginOpen: Boolean = false,
    /** Who the saved Instagram session belongs to (photo, name, username). Proof the connection works. */
    val igAccount: com.example.data.download.InstagramParser.ProfileInfo? = null,
    val isCheckingIgAccount: Boolean = false,
    /** People saved for one-tap access (and optional new-story alerts). */
    val people: List<com.example.data.download.SavedPerson> = emptyList(),
    val storyKind: com.example.ui.model.StoryKind = com.example.ui.model.StoryKind.STORIES,
    val repostsState: StoryUiState = StoryUiState.Idle,
    /** Every repost loaded for the current person; [repostsState] shows the part inside the chosen date range. */
    val repostsAll: List<com.example.data.download.InstagramParser.StoryItem> = emptyList(),
    /** Start / end of the chosen range (epoch seconds), or null for no limit. */
    val repostFrom: Long? = null,
    val repostTo: Long? = null,
    val highlights: List<com.example.data.download.InstagramParser.HighlightInfo> = emptyList(),
    val igAccountError: String? = null,
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
    internal val repository: VideoRepository,
    internal val metadataExtractor: MetadataExtractor,
    internal val settingsManager: com.example.data.local.SettingsManager,
    internal val downloader: InstagramDownloader,
    internal val peopleStore: com.example.data.download.PeopleStore,
    internal val appContext: Context
) : ViewModel() {

    internal val _uiState: MutableStateFlow<VideoFeedUiState>
    val uiState: StateFlow<VideoFeedUiState>

    /** The Instagram-style profile screen (photo, bio, highlights, posts, followers and more). */
    val profile = ProfileController(
        scope = viewModelScope,
        downloader = downloader,
        onMediaSaved = {
            viewModelScope.launch { reloadLibrary(showLoading = false) }
            com.example.ui.components.Feedback.show("Saved to your library", "View") { showLibrary() }
        },
        onProfileLoaded = { rememberPerson(it.username, it.picUrl, it.fullName) }
    )

    internal var shuffledVideoIds: List<Long> = emptyList()
    internal var recordJob: kotlinx.coroutines.Job? = null
    internal var hasLoadedOnce = false
    internal var demoMode = false
    internal var resumeAfterForeground = false

    // Search engine state. Declared before init{} on purpose: init-time coroutines read these.
    private data class SearchInput(val query: String, val options: SearchOptions, val version: Int)

    internal val searchTrigger = MutableStateFlow(SearchInput("", SearchOptions(), 0))
    internal var searchVersion = 0

    // Normalised name/folder text per video, computed once instead of on every keystroke
    private class SearchEntry(val name: String, val folder: String)
    internal val searchIndex = java.util.concurrent.ConcurrentHashMap<Long, SearchEntry>()

    internal fun scrollTo(index: Int) = ScrollRequest(index.coerceAtLeast(0), System.nanoTime())

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
                people = peopleStore.all(),
                isLoopMode = initialLoop
            )
        )
        uiState = _uiState.asStateFlow()
        observeFavoritesAndWatchHistory()
        startSearchEngine()
        // Loading is triggered by onPermissionResult() once the Activity knows the permission state.
    }

    internal fun observeFavoritesAndWatchHistory() {
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
    internal suspend fun reloadLibrary(showLoading: Boolean) {
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

    override fun onCleared() {
        downloader.release()
        super.onCleared()
    }

    // ------------------------------------------------------------------------------------------
    // Instagram downloader
    // ------------------------------------------------------------------------------------------

    internal var downloadJob: kotlinx.coroutines.Job? = null

    /** Browser identity shared by the Instagram sign-in page and the API calls. */
    val webUserAgent: String get() = downloader.webUserAgent

    internal var storyJob: kotlinx.coroutines.Job? = null
    internal var storyDownloadJob: kotlinx.coroutines.Job? = null

    internal var accountJob: kotlinx.coroutines.Job? = null

    internal var repostJob: kotlinx.coroutines.Job? = null
    internal var enrichJob: kotlinx.coroutines.Job? = null
    internal var repostsLoadedFrom: Long? = null
    internal var highlightsFor: String? = null

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val database = AppDatabase.getDatabase(context)
        val repository = VideoRepository(context, database)
        val metadataExtractor = MetadataExtractor(context)
        val settingsManager = com.example.data.local.SettingsManager(context)
        return VideoFeedViewModel(repository, metadataExtractor, settingsManager, InstagramDownloader(context.applicationContext), com.example.data.download.PeopleStore(context.applicationContext), context.applicationContext) as T
    }
}

