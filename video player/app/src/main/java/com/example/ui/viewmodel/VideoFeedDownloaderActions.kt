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

/*
 * Instagram downloader, saved people, alerts and reposts.
 *
 * These are extension functions of [VideoFeedViewModel]: same package, same behaviour, same call syntax
 * (viewModel.name(...)). They live here so the ViewModel file holds the state and the core, not everything.
 */

internal fun VideoFeedViewModel.isDownloadBusy(): Boolean {
    val status = _uiState.value.downloadStatus
    return status is DownloadStatus.Fetching || status is DownloadStatus.Downloading
}

/** Opens the downloader sheet. With [autoStart] a freshly shared link starts downloading right away. */
fun VideoFeedViewModel.openDownloader(prefill: String? = null, autoStart: Boolean = false) {
    val busy = isDownloadBusy()
    _uiState.update { state ->
        state.copy(
            isDownloaderOpen = true,
            isIgSignedIn = InstagramStoryService.isSignedIn(),
            downloadUrl = if (prefill != null && !busy) prefill else state.downloadUrl,
            downloadStatus = if (prefill != null && !busy) DownloadStatus.Idle else state.downloadStatus
        )
    }
    if (autoStart && prefill != null && !busy) startDownload()
    if (_uiState.value.downloaderTab == DownloaderTab.STORIES) verifyIgAccount()
}

/** The "View" action after a save: leave the profile and the Downloader so the new video is in front. */
fun VideoFeedViewModel.showLibrary() {
    profile.close()
    closeDownloader()
}

fun VideoFeedViewModel.closeDownloader() {
    _uiState.update { it.copy(isDownloaderOpen = false) }
}

fun VideoFeedViewModel.setDownloadUrl(text: String) {
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
fun VideoFeedViewModel.handleSharedText(text: String?) {
    val url = InstagramParser.extractInstagramUrl(text)
    if (url == null) {
        _uiState.update { it.copy(toastMessage = "No Instagram link found in what you shared") }
        return
    }
    if (InstagramParser.isStoryLink(url)) {
        _uiState.update {
            it.copy(
                isDownloaderOpen = true,
                downloaderTab = DownloaderTab.STORIES,
                storyInput = url,
                storyState = StoryUiState.Idle,
                storyDownloads = emptyMap(),
                isIgSignedIn = InstagramStoryService.isSignedIn()
            )
        }
        findStories()
        return
    }
    _uiState.update { it.copy(downloaderTab = DownloaderTab.REELS) }
    openDownloader(prefill = url, autoStart = true)
}

// ------------------------------------------------------------------------------------------
// Instagram stories (needs the user's own Instagram sign-in)
// ------------------------------------------------------------------------------------------

fun VideoFeedViewModel.setDownloaderTab(tab: DownloaderTab) {
    _uiState.update { it.copy(downloaderTab = tab, isIgSignedIn = InstagramStoryService.isSignedIn()) }
    if (tab == DownloaderTab.STORIES) verifyIgAccount()
}

/** Asks Instagram who we are signed in as, and shows it, so it's visible the app really is connected. */
fun VideoFeedViewModel.verifyIgAccount() {
    if (!InstagramStoryService.isSignedIn()) {
        _uiState.update { it.copy(igAccount = null, isCheckingIgAccount = false, igAccountError = null) }
        return
    }
    if (accountJob?.isActive == true) return
    accountJob = viewModelScope.launch {
        _uiState.update { it.copy(isCheckingIgAccount = true, igAccountError = null) }
        try {
            val me = downloader.stories.whoAmI()
            _uiState.update { it.copy(igAccount = me, isCheckingIgAccount = false, igAccountError = null) }
        } catch (e: kotlinx.coroutines.CancellationException) {
            _uiState.update { it.copy(isCheckingIgAccount = false) }
            throw e
        } catch (e: LoginRequiredException) {
            InstagramStoryService.signOut()
            _uiState.update {
                it.copy(isIgSignedIn = false, igAccount = null, isCheckingIgAccount = false, igAccountError = e.message)
            }
        } catch (e: DownloadException) {
            _uiState.update { it.copy(isCheckingIgAccount = false, igAccountError = e.message) }
        } catch (e: Exception) {
            _uiState.update { it.copy(isCheckingIgAccount = false, igAccountError = "Couldn't check the connection to Instagram.") }
        }
    }
}

fun VideoFeedViewModel.setStoryInput(text: String) {
    _uiState.update { state ->
        if (text == state.storyInput) state
        else state.copy(
            storyInput = text,
            repostsState = StoryUiState.Idle,
            repostsAll = emptyList(),
            highlights = emptyList(),
            storyState = if (state.storyState is StoryUiState.Loading) state.storyState else StoryUiState.Idle,
            storyDownloads = if (state.storyState is StoryUiState.Loading) state.storyDownloads else emptyMap()
        )
    }
}

fun VideoFeedViewModel.openIgLogin() {
    _uiState.update { it.copy(isIgLoginOpen = true) }
}

/** Called when the sign-in page closes. If the user did sign in, carry on with what they were doing. */
fun VideoFeedViewModel.onIgLoginClosed() {
    val signedIn = InstagramStoryService.isSignedIn()
    _uiState.update {
        it.copy(
            isIgLoginOpen = false,
            isIgSignedIn = signedIn,
            storyState = if (signedIn && it.storyState is StoryUiState.NeedsLogin) StoryUiState.Idle else it.storyState
        )
    }
    if (signedIn) verifyIgAccount()
    if (signedIn && profile.state.value.visible) {
        profile.retry()
        return
    }
    if (signedIn && _uiState.value.storyInput.isNotBlank()) findStories()
}

fun VideoFeedViewModel.igSignOut() {
    InstagramStoryService.signOut()
    _uiState.update {
        it.copy(
            isIgSignedIn = false, igAccount = null, igAccountError = null,
            storyState = StoryUiState.Idle, storyDownloads = emptyMap()
        )
    }
}

// ------------------------------------------------------------------------------------------
// Saved people, alerts and reposts
// ------------------------------------------------------------------------------------------

internal fun VideoFeedViewModel.refreshPeople() {
    _uiState.update { it.copy(people = peopleStore.all()) }
}

internal fun VideoFeedViewModel.cleanName(raw: String): String = raw.trim().removePrefix("@").trim()

/** Keeps the newest photo and name of someone already saved (Instagram photo links expire, so this refreshes them). */
fun VideoFeedViewModel.rememberPerson(username: String, picUrl: String?, fullName: String) {
    val name = cleanName(username)
    if (picUrl.isNullOrBlank() && fullName.isBlank()) return
    val existing = peopleStore.all().firstOrNull { it.username.equals(name, ignoreCase = true) } ?: return
    val newPic = picUrl?.takeIf { it.isNotBlank() } ?: existing.picUrl
    val newName = fullName.ifBlank { existing.fullName }
    if (newPic == existing.picUrl && newName == existing.fullName) return
    peopleStore.update(name) { it.copy(picUrl = newPic, fullName = newName) }
    refreshPeople()
}

fun VideoFeedViewModel.savePerson(username: String) {
    val name = cleanName(username)
    if (InstagramParser.parseStoryTarget(name) !is InstagramParser.StoryTarget.User) return
    val all = peopleStore.all()
    if (all.none { it.username.equals(name, ignoreCase = true) }) {
        val open = profile.state.value.details?.takeIf { it.username.equals(name, ignoreCase = true) }
        peopleStore.saveAll(
            all + com.example.data.download.SavedPerson(
                username = name,
                picUrl = open?.picUrl,
                fullName = open?.fullName.orEmpty()
            )
        )
    }
    refreshPeople()
}

fun VideoFeedViewModel.removePerson(username: String) {
    peopleStore.saveAll(peopleStore.all().filterNot { it.username.equals(username, ignoreCase = true) })
    com.example.data.download.StoryAlertScheduler.sync(appContext)
    refreshPeople()
}

/** Turning alerts on saves the person too. The first background check only records what is already there. */
fun VideoFeedViewModel.setPersonAlerts(username: String, enabled: Boolean) {
    savePerson(username)
    peopleStore.update(cleanName(username)) {
        it.copy(alerts = enabled, baselined = if (enabled) false else it.baselined, seenIds = if (enabled) emptySet() else it.seenIds)
    }
    com.example.data.download.StoryAlertScheduler.sync(appContext)
    refreshPeople()
    _uiState.update {
        it.copy(
            toastMessage = if (enabled) "Alerts on for @${cleanName(username)} (checked about every 5 min)"
            else "Alerts off for @${cleanName(username)}"
        )
    }
}

/** One tap on a saved person (or on a story notification): open the page and load their stories. */
fun VideoFeedViewModel.openPerson(username: String, autoStories: Boolean = false) {
    val name = cleanName(username)
    _uiState.update {
        it.copy(
            isDownloaderOpen = true,
            downloaderTab = DownloaderTab.STORIES,
            storyKind = com.example.ui.model.StoryKind.STORIES,
            storyInput = "@$name",
            storyState = StoryUiState.Idle,
            repostsState = StoryUiState.Idle,
            storyDownloads = emptyMap(),
            isIgSignedIn = InstagramStoryService.isSignedIn()
        )
    }
    profile.open(name, autoStories)
    verifyIgAccount()
}

/** Opens the full profile of [username] on top of the downloader. */
fun VideoFeedViewModel.openProfile(username: String, autoStories: Boolean = false) {
    _uiState.update {
        it.copy(
            isDownloaderOpen = true,
            downloaderTab = DownloaderTab.STORIES,
            isIgSignedIn = InstagramStoryService.isSignedIn()
        )
    }
    profile.open(username, autoStories)
    verifyIgAccount()
}

fun VideoFeedViewModel.setStoryKind(kind: com.example.ui.model.StoryKind) {
    _uiState.update { it.copy(storyKind = kind) }
    if (kind == com.example.ui.model.StoryKind.REPOSTS && _uiState.value.repostsState is StoryUiState.Idle) findReposts()
}

internal fun VideoFeedViewModel.effectiveTime(item: InstagramParser.StoryItem): Long =
    if (item.repostedAtSec != null && item.repostedAtSec > 0) item.repostedAtSec else item.takenAtSec

/** Shows only the reposts inside the chosen dates (by repost time when known, else by original post time). */
internal fun VideoFeedViewModel.applyRepostRange() {
    _uiState.update { s ->
        val loaded = s.repostsState as? StoryUiState.Loaded ?: return@update s
        val shown = s.repostsAll.filter {
            val t = effectiveTime(it)
            (s.repostFrom == null || t >= s.repostFrom) && (s.repostTo == null || t <= s.repostTo)
        }
        s.copy(repostsState = loaded.copy(items = shown))
    }
}

fun VideoFeedViewModel.setRepostRange(from: Long?, to: Long?) {
    val previousFrom = repostsLoadedFrom
    _uiState.update { it.copy(repostFrom = from, repostTo = to) }
    applyRepostRange()
    // Loading stops once it has gone back past the start date. A date further back needs more loading.
    val needMore = previousFrom != null && (from == null || from < previousFrom)
    if (needMore) findReposts(force = true)
}

fun VideoFeedViewModel.findReposts(force: Boolean = false) {
    if (repostJob?.isActive == true) {
        if (!force) return
        repostJob?.cancel()
    }
    val target = InstagramParser.parseStoryTarget(_uiState.value.storyInput)
    val username = when (target) {
        is InstagramParser.StoryTarget.User -> target.username
        is InstagramParser.StoryTarget.Story -> target.username
        else -> null
    }
    if (username == null) {
        _uiState.update { it.copy(repostsState = StoryUiState.Error("Enter a username first.")) }
        return
    }
    repostJob = viewModelScope.launch {
        try {
            _uiState.update { it.copy(repostsState = StoryUiState.Loading) }
            val from = _uiState.value.repostFrom
            val result = downloader.stories.loadReposts(username, from)
            repostsLoadedFrom = from
            _uiState.update {
                it.copy(
                    repostsAll = result.items,
                    repostsState = StoryUiState.Loaded(result.owner, result.items, result.profile)
                )
            }
            applyRepostRange()
            enrichReposts()
        } catch (e: kotlinx.coroutines.CancellationException) {
            _uiState.update { if (it.repostsState is StoryUiState.Loading) it.copy(repostsState = StoryUiState.Idle) else it }
            throw e
        } catch (e: DownloadException) {
            _uiState.update { it.copy(repostsState = StoryUiState.Error(e.message ?: "Couldn't load reposts.")) }
        } catch (e: Exception) {
            _uiState.update { it.copy(repostsState = StoryUiState.Error("Something went wrong. Please try again.")) }
        }
    }
}

internal fun VideoFeedViewModel.replaceRepost(item: InstagramParser.StoryItem) {
    _uiState.update { s -> s.copy(repostsAll = s.repostsAll.map { if (it.id == item.id) item else it }) }
    applyRepostRange()
}

internal suspend fun VideoFeedViewModel.resolveAndReplace(item: InstagramParser.StoryItem): InstagramParser.StoryItem {
    val resolved = try {
        downloader.resolveRepost(item)
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        item
    }
    replaceRepost(resolved)
    return resolved
}

/**
 * The Reposts tab only sends previews (a picture). In the background, one at a time and gently, look up each
 * reel's real video, caption, counts and times, so the list fills in while you browse.
 */
internal fun VideoFeedViewModel.enrichReposts() {
    enrichJob?.cancel()
    enrichJob = viewModelScope.launch {
        val ids = _uiState.value.repostsAll.filter { it.needsResolve || it.caption == null }.map { it.id }.take(60)
        for (id in ids) {
            val current = _uiState.value.repostsAll.firstOrNull { it.id == id } ?: continue
            if (current.needsResolve || current.caption == null) resolveAndReplace(current)
            kotlinx.coroutines.delay(350L)
        }
    }
}

/** Called when the viewer shows a reel that hasn't been looked up yet. */
fun VideoFeedViewModel.resolveRepostNow(item: InstagramParser.StoryItem) {
    if (!item.needsResolve) return
    viewModelScope.launch { resolveAndReplace(item) }
}

fun VideoFeedViewModel.loadHighlights(userId: String) {
    if (highlightsFor == userId) return
    highlightsFor = userId
    viewModelScope.launch {
        val list = try { downloader.stories.highlights(userId) } catch (e: Exception) { emptyList() }
        _uiState.update { it.copy(highlights = list) }
    }
}

fun VideoFeedViewModel.openHighlight(highlight: InstagramParser.HighlightInfo) {
    val id = highlight.id.removePrefix("highlight:")
    _uiState.update { it.copy(storyInput = "https://www.instagram.com/stories/highlights/$id/", storyState = StoryUiState.Idle, storyDownloads = emptyMap()) }
    findStories()
}

/** Saves one story or repost (whichever list it came from). */
fun VideoFeedViewModel.saveMediaItem(item: com.example.data.download.InstagramParser.StoryItem) {
    val state = _uiState.value
    val owner = (state.storyState as? StoryUiState.Loaded)?.owner
        ?: (state.repostsState as? StoryUiState.Loaded)?.owner ?: return
    if (state.storyDownloads[item.id] is StoryItemDownload.Downloading) return
    viewModelScope.launch {
        val target = if (item.needsResolve) resolveAndReplace(item) else item
        if (target.needsResolve) {
            _uiState.update { it.copy(toastMessage = "Couldn't get the video for this reel. Try again in a moment.") }
            return@launch
        }
        if (saveStoryItem(target, owner)) reloadLibrary(showLoading = false)
    }
}

fun VideoFeedViewModel.saveAllReposts() {
    val loaded = _uiState.value.repostsState as? StoryUiState.Loaded ?: return
    if (storyDownloadJob?.isActive == true) return
    storyDownloadJob = viewModelScope.launch {
        for (item in loaded.items) {
            if (_uiState.value.storyDownloads[item.id] is StoryItemDownload.Done) continue
            val target = if (item.needsResolve) resolveAndReplace(item) else item
            if (target.needsResolve) continue
            saveStoryItem(target, loaded.owner)
        }
        reloadLibrary(showLoading = false)
    }
}

fun VideoFeedViewModel.findStories() {
    if (storyJob?.isActive == true) return
    val target = InstagramParser.parseStoryTarget(_uiState.value.storyInput)
    if (target == null) {
        _uiState.update {
            it.copy(storyState = StoryUiState.Error("Enter an Instagram username (like @name) or paste a story link."))
        }
        return
    }
    if (target is InstagramParser.StoryTarget.User) {
        // A username opens the full profile; story links and highlight links keep the old results view
        openProfile(target.username)
        return
    }
    if (!InstagramStoryService.isSignedIn()) {
        _uiState.update {
            it.copy(isIgSignedIn = false, storyState = StoryUiState.NeedsLogin("Sign in to Instagram to view stories."))
        }
        return
    }
    storyJob = viewModelScope.launch {
        try {
            _uiState.update { it.copy(storyState = StoryUiState.Loading, storyDownloads = emptyMap()) }
            val result = downloader.stories.load(target)
            result.profile?.let { rememberPerson(it.username, it.picUrl, it.fullName) }
            _uiState.update {
                it.copy(
                    storyState = StoryUiState.Loaded(result.owner, result.items, result.profile),
                    repostsState = StoryUiState.Idle,
                    igAccount = result.signedInAs ?: it.igAccount,
                    igAccountError = null
                )
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            _uiState.update { it.copy(storyState = StoryUiState.Idle) }
            throw e
        } catch (e: LoginRequiredException) {
            InstagramStoryService.signOut() // the saved session is no longer valid
            _uiState.update {
                it.copy(isIgSignedIn = false, storyState = StoryUiState.NeedsLogin(e.message ?: "Please sign in again."))
            }
        } catch (e: DownloadException) {
            _uiState.update { it.copy(storyState = StoryUiState.Error(e.message ?: "Couldn't load stories.")) }
        } catch (e: Exception) {
            _uiState.update { it.copy(storyState = StoryUiState.Error("Something went wrong. Please try again.")) }
        }
    }
}

fun VideoFeedViewModel.downloadStoryItem(item: com.example.data.download.InstagramParser.StoryItem) {
    val loaded = _uiState.value.storyState as? StoryUiState.Loaded ?: return
    if (_uiState.value.storyDownloads[item.id] is StoryItemDownload.Downloading) return
    viewModelScope.launch {
        if (saveStoryItem(item, loaded.owner)) reloadLibrary(showLoading = false)
    }
}

fun VideoFeedViewModel.downloadAllStories() {
    val loaded = _uiState.value.storyState as? StoryUiState.Loaded ?: return
    if (storyDownloadJob?.isActive == true) return
    storyDownloadJob = viewModelScope.launch {
        var saved = 0
        for (item in loaded.items) {
            if (_uiState.value.storyDownloads[item.id] is StoryItemDownload.Done) continue
            if (saveStoryItem(item, loaded.owner)) saved++
        }
        reloadLibrary(showLoading = false)
        if (saved > 0) {
            _uiState.update { state ->
                state.copy(
                    toastMessage = if (!state.isDownloaderOpen) "Saved $saved ${if (saved == 1) "story" else "stories"} ✔" else state.toastMessage
                )
            }
        }
    }
}

fun VideoFeedViewModel.cancelStoryDownloads() {
    storyDownloadJob?.cancel()
}

/** Saves one story; returns true on success. Failures are recorded per item instead of thrown. */
internal suspend fun VideoFeedViewModel.saveStoryItem(
    item: com.example.data.download.InstagramParser.StoryItem,
    owner: String
): Boolean {
    fun setStatus(status: StoryItemDownload?) = _uiState.update { state ->
        state.copy(
            storyDownloads = if (status == null) state.storyDownloads - item.id
            else state.storyDownloads + (item.id to status)
        )
    }
    setStatus(StoryItemDownload.Downloading(-1f))
    return try {
        val uri = downloader.downloadMedia(
            url = item.mediaUrl,
            baseName = (item.author ?: owner).replace(Regex("[^A-Za-z0-9._-]"), "_"),
            isVideo = item.isVideo
        ) { progress -> setStatus(StoryItemDownload.Downloading(progress)) }
        setStatus(StoryItemDownload.Done(uri))
        true
    } catch (e: kotlinx.coroutines.CancellationException) {
        setStatus(null)
        throw e
    } catch (e: DownloadException) {
        setStatus(StoryItemDownload.Failed(e.message ?: "Couldn't save."))
        false
    } catch (e: Exception) {
        setStatus(StoryItemDownload.Failed("Couldn't save."))
        false
    }
}

fun VideoFeedViewModel.startDownload() {
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
            _uiState.update { it.copy(downloadStatus = DownloadStatus.Failed(e.message ?: "Couldn't save.")) }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(downloadStatus = DownloadStatus.Failed("Something went wrong. Please try again."))
            }
        }
    }
}

fun VideoFeedViewModel.cancelDownload() {
    downloadJob?.cancel()
}

/** "Play now" after a successful download: jump straight to the new video in the feed. */
fun VideoFeedViewModel.playDownloaded() {
    val status = _uiState.value.downloadStatus as? DownloadStatus.Success ?: return
    val video = _uiState.value.allVideos.firstOrNull { it.uri == status.uri }
    closeDownloader()
    if (video != null) {
        playVideo(video, FeedFilter.All)
    } else {
        _uiState.update { it.copy(toastMessage = "Saved to Movies/OffScroll") }
    }
}

/** The any-link downloader saved a video: reload the library in place, without blanking the feed. */
fun VideoFeedViewModel.onExternalVideoSaved() {
    viewModelScope.launch { reloadLibrary(showLoading = false) }
}

/** "Play now" for a video saved by the any-link downloader. */
fun VideoFeedViewModel.playSavedVideo(uri: android.net.Uri) {
    viewModelScope.launch {
        reloadLibrary(showLoading = false)
        val video = _uiState.value.allVideos.firstOrNull { it.uri == uri }
        closeDownloader()
        if (video != null) {
            playVideo(video, FeedFilter.All)
        } else {
            _uiState.update { it.copy(toastMessage = "Saved to Movies/OffScroll") }
        }
    }
}

/** Opt-in demo mode (only reachable from the permission screen). */
fun VideoFeedViewModel.loadDemoClips() {
    demoMode = true
    _uiState.update { it.copy(isDemoMode = true, isLoading = true) }
    loadVideos()
}

fun VideoFeedViewModel.refreshVideos() {
    if (_uiState.value.isPermissionGranted || demoMode) loadVideos()
}

fun VideoFeedViewModel.selectFilter(filter: FeedFilter) {
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

fun VideoFeedViewModel.selectSort(sort: SortOption) {
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

fun VideoFeedViewModel.toggleShuffleMode() {
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

fun VideoFeedViewModel.setDisplayMode(mode: VideoDisplayMode) {
    settingsManager.setDisplayMode(mode)
    _uiState.update {
        it.copy(
            displayMode = mode,
            toastMessage = "Display: ${mode.label}"
        )
    }
}

fun VideoFeedViewModel.cycleDisplayMode() {
    val modes = VideoDisplayMode.values()
    val newMode = modes[(_uiState.value.displayMode.ordinal + 1) % modes.size]
    settingsManager.setDisplayMode(newMode)
    _uiState.update { it.copy(displayMode = newMode, toastMessage = "Display: ${newMode.label}") }
}

// ------------------------------------------------------------------------------------------
// Search: a separate full-screen page. The feed is only touched when a result is picked.
// ------------------------------------------------------------------------------------------
