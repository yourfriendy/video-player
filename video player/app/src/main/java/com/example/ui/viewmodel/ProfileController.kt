package com.example.ui.viewmodel

import com.example.data.download.DownloadException
import com.example.data.download.InstagramDownloader
import com.example.data.download.InstagramParser
import com.example.data.download.InstagramStoryService
import com.example.data.download.LoginRequiredException
import com.example.data.download.NoStoriesException
import com.example.data.download.PersonRow
import com.example.data.download.PostItem
import com.example.data.download.PostKind
import com.example.data.download.PostsTab
import com.example.data.download.ProfileDetails
import com.example.ui.model.StoryItemDownload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ProfileTab { POSTS, REELS, REPOSTS, TAGGED }

enum class PeopleKind { FOLLOWERS, FOLLOWING }

/** One grid of the profile (Posts, Reels, Reposts or Tagged) and how far it has loaded. */
data class ProfileTabState(
    val items: List<PostItem> = emptyList(),
    val cursor: String? = null,
    val loading: Boolean = false,
    /** The first page has come back (even if it was empty or failed). */
    val loaded: Boolean = false,
    /** There are no more pages. */
    val done: Boolean = false,
    val error: String? = null
)

/** The followers / following list. */
data class ProfilePeopleState(
    val kind: PeopleKind,
    val people: List<PersonRow> = emptyList(),
    val cursor: String? = null,
    val loading: Boolean = false,
    val done: Boolean = false,
    val error: String? = null,
    /** "Load all" is running: pages keep coming until the list is complete. */
    val loadingAll: Boolean = false,
    val query: String = ""
)

/** What the full-screen viewer shows: a post's slides, someone's stories, or a highlight. */
data class ProfileViewerState(
    val title: String,
    val items: List<InstagramParser.StoryItem>,
    val startIndex: Int = 0
)

data class ProfileUiState(
    val visible: Boolean = false,
    val username: String = "",
    val loading: Boolean = false,
    val error: String? = null,
    val needsLogin: Boolean = false,
    val details: ProfileDetails? = null,
    val highlights: List<InstagramParser.HighlightInfo> = emptyList(),
    val hasStories: Boolean = false,
    val tab: ProfileTab = ProfileTab.POSTS,
    val posts: ProfileTabState = ProfileTabState(),
    val reels: ProfileTabState = ProfileTabState(),
    val reposts: ProfileTabState = ProfileTabState(),
    val tagged: ProfileTabState = ProfileTabState(),
    val people: ProfilePeopleState? = null,
    val viewer: ProfileViewerState? = null,
    val viewerLoading: Boolean = false,
    val saves: Map<String, StoryItemDownload> = emptyMap(),
    val toast: String? = null
)

/**
 * Drives the Instagram-style profile screen: the header, highlights, the Posts / Reels / Reposts / Tagged
 * grids (loaded page by page), the followers and following lists, and the viewer for stories and posts.
 * Everything uses the signed-in user's own Instagram session, so it shows what that account can already see.
 */
class ProfileController(
    private val scope: CoroutineScope,
    private val downloader: InstagramDownloader,
    private val onMediaSaved: () -> Unit
) {
    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    private val service get() = downloader.stories
    private val history = ArrayList<String>()
    private var loadJob: Job? = null
    private var peopleJob: Job? = null
    private var viewerJob: Job? = null
    private val tabJobs = HashMap<ProfileTab, Job>()

    // ------------------------------------------------------------------------------------------
    // Opening, closing, going back
    // ------------------------------------------------------------------------------------------

    /** Opens someone's profile. From inside another profile, Back returns to the one you came from. */
    fun open(rawName: String, autoStories: Boolean = false) {
        val name = rawName.trim().removePrefix("@").trim()
        if (name.isBlank()) return
        val current = _state.value
        if (current.visible) {
            if (current.username.isNotBlank() && !current.username.equals(name, ignoreCase = true)) {
                history.add(current.username)
            }
        } else {
            history.clear()
        }
        load(name, autoStories)
    }

    fun retry() {
        val name = _state.value.username
        if (name.isNotBlank()) load(name, false)
    }

    fun back() {
        val s = _state.value
        when {
            s.viewer != null -> _state.update { it.copy(viewer = null) }
            s.people != null -> closePeople()
            history.isNotEmpty() -> load(history.removeAt(history.lastIndex), false)
            else -> close()
        }
    }

    fun close() {
        cancelAll()
        history.clear()
        _state.value = ProfileUiState()
    }

    private fun cancelAll() {
        loadJob?.cancel()
        peopleJob?.cancel()
        viewerJob?.cancel()
        tabJobs.values.forEach { it.cancel() }
        tabJobs.clear()
    }

    private fun load(name: String, autoStories: Boolean) {
        cancelAll()
        _state.value = ProfileUiState(visible = true, username = name, loading = true)
        if (!InstagramStoryService.isSignedIn()) {
            _state.update { it.copy(loading = false, needsLogin = true) }
            return
        }
        loadJob = scope.launch {
            try {
                val details = service.profileDetails(name)
                _state.update {
                    it.copy(details = details, username = details.username.ifBlank { name }, loading = false)
                }
                // None of these is essential, so they load alongside each other
                launch {
                    val list = try {
                        service.highlights(details.id)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        emptyList()
                    }
                    _state.update { it.copy(highlights = list) }
                }
                launch {
                    val has = service.hasStories(details.id)
                    _state.update { it.copy(hasStories = has) }
                    if (autoStories && has) viewStories()
                }
                loadTab(ProfileTab.POSTS)
            } catch (e: CancellationException) {
                throw e
            } catch (e: LoginRequiredException) {
                _state.update { it.copy(loading = false, needsLogin = true, error = e.message) }
            } catch (e: DownloadException) {
                _state.update { it.copy(loading = false, error = e.message ?: "Couldn't load this profile.") }
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = "Something went wrong. Please try again.") }
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // The grids
    // ------------------------------------------------------------------------------------------

    private fun tabOf(s: ProfileUiState, tab: ProfileTab): ProfileTabState = when (tab) {
        ProfileTab.POSTS -> s.posts
        ProfileTab.REELS -> s.reels
        ProfileTab.REPOSTS -> s.reposts
        ProfileTab.TAGGED -> s.tagged
    }

    private fun updateTab(tab: ProfileTab, change: (ProfileTabState) -> ProfileTabState) {
        _state.update { s ->
            when (tab) {
                ProfileTab.POSTS -> s.copy(posts = change(s.posts))
                ProfileTab.REELS -> s.copy(reels = change(s.reels))
                ProfileTab.REPOSTS -> s.copy(reposts = change(s.reposts))
                ProfileTab.TAGGED -> s.copy(tagged = change(s.tagged))
            }
        }
    }

    fun selectTab(tab: ProfileTab) {
        _state.update { it.copy(tab = tab) }
        val s = _state.value
        val ts = tabOf(s, tab)
        if (s.details != null && !ts.loaded && !ts.loading) loadTab(tab)
    }

    /** Called when the grid scrolls near its end. */
    fun loadMore() {
        val s = _state.value
        val ts = tabOf(s, s.tab)
        if (s.tab == ProfileTab.REPOSTS || !ts.loaded || ts.done || ts.loading || ts.error != null) return
        loadTab(s.tab)
    }

    fun retryTab() {
        val tab = _state.value.tab
        updateTab(tab) { it.copy(error = null) }
        loadTab(tab)
    }

    private fun repostToPost(item: InstagramParser.StoryItem) = PostItem(
        id = item.id,
        code = item.code,
        kind = PostKind.REEL,
        thumbUrl = item.thumbnailUrl ?: item.mediaUrl,
        caption = item.caption,
        likes = item.likes,
        comments = item.comments,
        plays = item.plays,
        takenAtSec = item.repostedAtSec ?: item.takenAtSec,
        isPinned = false,
        media = listOf(item)
    )

    private fun loadTab(tab: ProfileTab) {
        val details = _state.value.details ?: return
        if (tabJobs[tab]?.isActive == true) return
        updateTab(tab) { it.copy(loading = true, error = null) }
        tabJobs[tab] = scope.launch {
            try {
                if (tab == ProfileTab.REPOSTS) {
                    val result = service.loadReposts(details.username, null)
                    val items = result.items.map { repostToPost(it) }
                    updateTab(tab) { it.copy(items = items, loading = false, loaded = true, done = true) }
                } else {
                    val kind = when (tab) {
                        ProfileTab.REELS -> PostsTab.REELS
                        ProfileTab.TAGGED -> PostsTab.TAGGED
                        else -> PostsTab.POSTS
                    }
                    val cursor = tabOf(_state.value, tab).cursor
                    val page = service.postsPage(details.id, details.username, kind, cursor)
                    updateTab(tab) { old ->
                        old.copy(
                            items = (old.items + page.items).distinctBy { it.id },
                            cursor = page.nextCursor,
                            loading = false,
                            loaded = true,
                            done = page.nextCursor == null || page.items.isEmpty()
                        )
                    }
                }
            } catch (e: CancellationException) {
                updateTab(tab) { it.copy(loading = false) }
                throw e
            } catch (e: NoStoriesException) {
                // "No reposts" is an answer, not a failure
                updateTab(tab) { it.copy(loading = false, loaded = true, done = true) }
            } catch (e: LoginRequiredException) {
                _state.update { it.copy(needsLogin = true) }
                updateTab(tab) { it.copy(loading = false, error = e.message) }
            } catch (e: DownloadException) {
                updateTab(tab) { it.copy(loading = false, loaded = true, error = e.message ?: "Couldn't load this.") }
            } catch (e: Exception) {
                updateTab(tab) { it.copy(loading = false, loaded = true, error = "Something went wrong. Please try again.") }
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Stories, highlights, posts: the viewer
    // ------------------------------------------------------------------------------------------

    fun viewStories() {
        val name = _state.value.details?.username ?: return
        openViewerFrom("Stories") {
            service.load(InstagramParser.StoryTarget.User(name)).items
        }
    }

    fun openHighlight(highlight: InstagramParser.HighlightInfo) {
        val id = highlight.id.removePrefix("highlight:")
        openViewerFrom(highlight.title.ifBlank { "Highlight" }) {
            service.load(InstagramParser.StoryTarget.Highlight(id)).items
        }
    }

    fun openPost(post: PostItem, startIndex: Int = 0) {
        _state.update {
            it.copy(
                viewer = ProfileViewerState(
                    title = "@" + (it.details?.username ?: it.username),
                    items = post.media,
                    startIndex = startIndex.coerceIn(0, (post.media.size - 1).coerceAtLeast(0))
                )
            )
        }
    }

    fun closeViewer() {
        _state.update { it.copy(viewer = null) }
    }

    private fun openViewerFrom(title: String, fetch: suspend () -> List<InstagramParser.StoryItem>) {
        if (_state.value.viewerLoading) return
        _state.update { it.copy(viewerLoading = true) }
        viewerJob = scope.launch {
            try {
                val items = fetch()
                _state.update { it.copy(viewerLoading = false, viewer = ProfileViewerState(title, items)) }
            } catch (e: CancellationException) {
                _state.update { it.copy(viewerLoading = false) }
                throw e
            } catch (e: NoStoriesException) {
                _state.update { it.copy(viewerLoading = false, toast = e.message ?: "Nothing to show here.") }
            } catch (e: LoginRequiredException) {
                _state.update { it.copy(viewerLoading = false, needsLogin = true, toast = e.message) }
            } catch (e: DownloadException) {
                _state.update { it.copy(viewerLoading = false, toast = e.message ?: "Couldn't load this.") }
            } catch (e: Exception) {
                _state.update { it.copy(viewerLoading = false, toast = "Something went wrong. Please try again.") }
            }
        }
    }

    /** A reposted reel only arrives as a picture: look up its real video when it is about to be shown. */
    fun resolveNow(item: InstagramParser.StoryItem) {
        if (!item.needsResolve) return
        scope.launch {
            val resolved = try {
                downloader.resolveRepost(item)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                item
            }
            replaceItem(resolved)
        }
    }

    private fun replaceItem(item: InstagramParser.StoryItem) {
        _state.update { s ->
            s.copy(
                viewer = s.viewer?.let { v -> v.copy(items = v.items.map { if (it.id == item.id) item else it }) },
                reposts = s.reposts.copy(
                    items = s.reposts.items.map { post ->
                        if (post.media.any { it.id == item.id }) {
                            post.copy(
                                media = post.media.map { if (it.id == item.id) item else it },
                                caption = item.caption ?: post.caption,
                                likes = item.likes ?: post.likes,
                                comments = item.comments ?: post.comments,
                                plays = item.plays ?: post.plays
                            )
                        } else post
                    }
                )
            )
        }
    }

    // ------------------------------------------------------------------------------------------
    // Saving
    // ------------------------------------------------------------------------------------------

    private fun setSave(id: String, status: StoryItemDownload?) {
        _state.update { s -> s.copy(saves = if (status == null) s.saves - id else s.saves + (id to status)) }
    }

    fun save(item: InstagramParser.StoryItem) {
        if (_state.value.saves[item.id] is StoryItemDownload.Downloading) return
        scope.launch { saveNow(item) }
    }

    fun saveAll(items: List<InstagramParser.StoryItem>) {
        scope.launch {
            for (item in items) {
                if (_state.value.saves[item.id] is StoryItemDownload.Done) continue
                saveNow(item)
            }
        }
    }

    private suspend fun saveNow(item: InstagramParser.StoryItem): Boolean {
        val owner = _state.value.details?.username ?: item.author ?: "instagram"
        val target = if (item.needsResolve) {
            val resolved = try {
                downloader.resolveRepost(item)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                item
            }
            replaceItem(resolved)
            resolved
        } else item
        if (target.needsResolve) {
            _state.update { it.copy(toast = "Couldn't get the video for this reel. Try again in a moment.") }
            return false
        }
        setSave(target.id, StoryItemDownload.Downloading(-1f))
        return try {
            val uri = downloader.downloadMedia(
                url = target.mediaUrl,
                // Same naming as the rest of the app: the account name, then the date and time are added
                baseName = (target.author ?: owner).replace(Regex("[^A-Za-z0-9._-]"), "_"),
                isVideo = target.isVideo
            ) { progress -> setSave(target.id, StoryItemDownload.Downloading(progress)) }
            setSave(target.id, StoryItemDownload.Done(uri))
            onMediaSaved()
            true
        } catch (e: CancellationException) {
            setSave(target.id, null)
            throw e
        } catch (e: DownloadException) {
            setSave(target.id, StoryItemDownload.Failed(e.message ?: "Download failed."))
            false
        } catch (e: Exception) {
            setSave(target.id, StoryItemDownload.Failed("Download failed."))
            false
        }
    }

    fun consumeToast() {
        _state.update { it.copy(toast = null) }
    }

    // ------------------------------------------------------------------------------------------
    // Followers and following
    // ------------------------------------------------------------------------------------------

    fun openPeople(kind: PeopleKind) {
        if (_state.value.details == null) return
        peopleJob?.cancel()
        _state.update { it.copy(people = ProfilePeopleState(kind)) }
        loadPeople()
    }

    fun switchPeople(kind: PeopleKind) {
        if (_state.value.people?.kind == kind) return
        openPeople(kind)
    }

    fun closePeople() {
        peopleJob?.cancel()
        _state.update { it.copy(people = null) }
    }

    fun setPeopleQuery(query: String) {
        _state.update { s -> s.copy(people = s.people?.copy(query = query)) }
    }

    /** Called when the list scrolls near its end. */
    fun loadMorePeople() {
        val p = _state.value.people ?: return
        if (p.loading || p.done || p.error != null) return
        loadPeople()
    }

    fun retryPeople() {
        _state.update { s -> s.copy(people = s.people?.copy(error = null)) }
        loadPeople()
    }

    /** Keeps fetching pages until the whole list is here (or the user taps Stop). */
    fun toggleLoadAllPeople() {
        val p = _state.value.people ?: return
        if (p.loadingAll) {
            peopleJob?.cancel()
            _state.update { s -> s.copy(people = s.people?.copy(loadingAll = false, loading = false)) }
            return
        }
        _state.update { s -> s.copy(people = s.people?.copy(loadingAll = true, error = null)) }
        peopleJob?.cancel()
        loadPeople()
    }

    private fun loadPeople() {
        val details = _state.value.details ?: return
        if (peopleJob?.isActive == true) return
        peopleJob = scope.launch {
            try {
                while (true) {
                    val current = _state.value.people ?: return@launch
                    if (current.done) break
                    _state.update { s -> s.copy(people = s.people?.copy(loading = true, error = null)) }
                    val page = service.peoplePage(details.id, current.kind == PeopleKind.FOLLOWERS, current.cursor)
                    _state.update { s ->
                        s.copy(
                            people = s.people?.let { old ->
                                old.copy(
                                    people = (old.people + page.people).distinctBy { it.id },
                                    cursor = page.nextCursor,
                                    done = page.nextCursor == null || page.people.isEmpty(),
                                    loading = false
                                )
                            }
                        )
                    }
                    val after = _state.value.people ?: return@launch
                    if (!after.loadingAll || after.done) break
                    delay(900) // gentle on Instagram, so a long list doesn't get the account throttled
                }
                _state.update { s -> s.copy(people = s.people?.copy(loading = false, loadingAll = false)) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: LoginRequiredException) {
                _state.update { it.copy(needsLogin = true, people = it.people?.copy(loading = false, loadingAll = false, error = e.message)) }
            } catch (e: DownloadException) {
                _state.update { s ->
                    s.copy(people = s.people?.copy(loading = false, loadingAll = false, error = e.message ?: "Couldn't load this list."))
                }
            } catch (e: Exception) {
                _state.update { s ->
                    s.copy(people = s.people?.copy(loading = false, loadingAll = false, error = "Something went wrong. Please try again."))
                }
            }
        }
    }
}
