package com.example.service

import android.app.Application
import android.net.Uri
import com.example.data.download.DownloadException
import com.example.data.download.InstagramDownloader
import com.example.data.download.InstagramParser
import com.example.data.download.VerificationRequiredException
import com.example.data.universal.LinkUtils
import com.example.data.universal.MediaInfo
import com.example.data.universal.Platforms
import com.example.data.universal.ProgressMapper
import com.example.data.universal.UniversalDownloader
import com.example.data.universal.UniversalException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Runs the downloads that start from the share pop-up.
 *
 * It lives outside any screen on purpose: the pop-up can be hidden (or the user can wander off to
 * another app) and the download carries on, kept alive by [DownloadKeepAliveService]. The pop-up
 * simply watches [state].
 */
object ShareDownloads {

    /** What the pop-up's main button should do after a failure. */
    enum class FailAction { Retry, UpdateEngine, OpenApp, None }

    sealed interface State {
        data object Idle : State

        data class Looking(val platform: String, val message: String) : State

        /** Link found: the user picks a quality (the sensible one is pre-selected) and taps Download. */
        data class Choose(val info: MediaInfo, val selectedId: String) : State

        /** [progress] is 0..1, or negative while the size isn't known yet. */
        data class Downloading(
            val title: String,
            val platform: String,
            val thumbnail: String?,
            val progress: Float,
            val phase: String,
            val detail: String
        ) : State

        data class Saved(val title: String, val platform: String, val thumbnail: String?, val uri: Uri) : State

        data class Failed(val message: String, val platform: String, val action: FailAction) : State
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    /** True while the pop-up is on screen. When it isn't, finishing a download posts a notification instead. */
    @Volatile
    var popupVisible: Boolean = false

    @Volatile
    private var libraryDirty = false

    /** The main app calls this when it comes forward: true means a video was saved and the library needs a rescan. */
    fun consumeLibraryDirty(): Boolean {
        val dirty = libraryDirty
        libraryDirty = false
        return dirty
    }

    private var job: Job? = null
    private var processId: String? = null
    private var currentUrl: String? = null
    private var engineReady = false
    private var autoRepaired = false
    private var universalEngine: UniversalDownloader? = null
    private var instagramEngine: InstagramDownloader? = null

    /** Exactly what was shared, so a hand-off to the full app can replay it. */
    var originalText: String? = null
        private set

    private fun universal(app: Application): UniversalDownloader =
        universalEngine ?: UniversalDownloader(app).also { universalEngine = it }

    private fun instagram(app: Application): InstagramDownloader =
        instagramEngine ?: InstagramDownloader(app).also { instagramEngine = it }

    /** Starts handling a shared link. Returns false if another download is already running. */
    fun begin(app: Application, text: String?): Boolean {
        val current = _state.value
        if (current is State.Looking || current is State.Downloading) return false
        job?.cancel()
        originalText = text
        val igUrl = InstagramParser.extractInstagramUrl(text)
        val url = igUrl ?: LinkUtils.extractUrl(text)
        currentUrl = url
        if (url == null) {
            _state.value = State.Failed("I couldn't find a link in what you shared.", "", FailAction.None)
            return true
        }
        if (igUrl != null) startInstagram(app, igUrl) else startLookUp(app, url)
        return true
    }

    fun select(optionId: String) {
        val choose = _state.value as? State.Choose ?: return
        _state.value = choose.copy(selectedId = optionId)
    }

    // ------------------------------------------------------------------------------------------
    // Any link (YouTube, Facebook, TikTok, ...) through the yt-dlp engine
    // ------------------------------------------------------------------------------------------

    private fun startLookUp(app: Application, url: String) {
        job?.cancel()
        job = scope.launch { lookUp(app, url) }
    }

    private suspend fun lookUp(app: Application, url: String) {
        val platform = Platforms.nameFor(null, url)
        _state.value = State.Looking(
            platform,
            if (engineReady) "Looking up the video…" else "Setting up the download engine (first time only)…"
        )
        try {
            val info = fetchWithAutoRepair(app, url, platform)
            engineReady = true
            // Best-first list: pre-select the best option up to 1080p so a 4K file never starts by surprise
            val option = info.options.firstOrNull { it.quality in 1..1080 } ?: info.options.first()
            _state.value = State.Choose(info, option.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: UniversalException) {
            _state.value = State.Failed(
                e.message ?: "Something went wrong.",
                platform,
                if (e.suggestUpdate) FailAction.UpdateEngine else FailAction.Retry
            )
        } catch (e: Exception) {
            _state.value = State.Failed(
                "Something went wrong (${e.javaClass.simpleName}).",
                platform,
                FailAction.UpdateEngine
            )
        }
    }

    /** If a lookup fails the way "the site changed" does, update yt-dlp once and try again. */
    private suspend fun fetchWithAutoRepair(app: Application, url: String, platform: String): MediaInfo {
        val engine = universal(app)
        try {
            return engine.fetchInfo(url)
        } catch (e: UniversalException) {
            if (!e.suggestUpdate || autoRepaired) throw e
            autoRepaired = true
            _state.value = State.Looking(platform, "The site changed. Updating the download engine…")
            engine.updateEngine()
            _state.value = State.Looking(platform, "Looking up the video…")
            return engine.fetchInfo(url)
        }
    }

    fun startDownload(app: Application) {
        val choose = _state.value as? State.Choose ?: return
        val info = choose.info
        val option = info.options.firstOrNull { it.id == choose.selectedId } ?: info.options.first()
        val id = "dl-${System.nanoTime()}"
        processId = id
        val mapper = ProgressMapper(option.videoShare)
        val title = info.title
        val platform = info.platform
        val thumbnail = info.thumbnail

        DownloadKeepAliveService.start(app, title)
        job?.cancel()
        job = scope.launch {
            var lastPercent = -1
            _state.value = State.Downloading(title, platform, thumbnail, 0f, mapper.phaseLabel(), "")
            try {
                val uri = universal(app).download(info, option, id) { percent, eta, line ->
                    val overall = mapper.onLine(line, percent)
                    val detail = ProgressMapper.detail(line, eta)
                    _state.value = State.Downloading(title, platform, thumbnail, overall, mapper.phaseLabel(), detail)
                    val whole = (overall * 100).toInt()
                    if (whole != lastPercent) {
                        lastPercent = whole
                        DownloadKeepAliveService.update(app, title, mapper.phaseLabel(), whole)
                    }
                }
                onSaved(app, title, platform, thumbnail, uri)
            } catch (e: CancellationException) {
                throw e // cancel() has already reset the pop-up
            } catch (e: UniversalException) {
                onFailed(
                    app,
                    e.message ?: "The download failed.",
                    platform,
                    if (e.suggestUpdate) FailAction.UpdateEngine else FailAction.Retry
                )
            } catch (e: Exception) {
                onFailed(app, "The download failed (${e.javaClass.simpleName}).", platform, FailAction.UpdateEngine)
            } finally {
                DownloadKeepAliveService.stop(app)
                processId = null
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Instagram reels (the app's own downloader, no quality choice, starts straight away)
    // ------------------------------------------------------------------------------------------

    private fun startInstagram(app: Application, url: String) {
        job?.cancel()
        job = scope.launch {
            val platform = "Instagram"
            var title = "Instagram reel"
            _state.value = State.Looking(platform, "Finding the reel…")
            try {
                val engine = instagram(app)
                val reel = engine.resolve(url)
                title = reel.title?.takeIf { it.isNotBlank() } ?: "Instagram reel"
                DownloadKeepAliveService.start(app, title)
                _state.value = State.Downloading(title, platform, null, -1f, "Downloading", "")
                var lastPercent = -2
                val uri = engine.download(reel) { progress ->
                    _state.value = State.Downloading(title, platform, null, progress, "Downloading", "")
                    val whole = if (progress < 0f) -1 else (progress * 100).toInt()
                    if (whole != lastPercent) {
                        lastPercent = whole
                        DownloadKeepAliveService.update(app, title, "Downloading", whole)
                    }
                }
                onSaved(app, title, platform, null, uri)
            } catch (e: CancellationException) {
                throw e
            } catch (e: VerificationRequiredException) {
                onFailed(
                    app,
                    e.message ?: "Instagram wants you to confirm it's you first.",
                    platform,
                    FailAction.OpenApp
                )
            } catch (e: DownloadException) {
                onFailed(app, e.message ?: "The download failed.", platform, FailAction.Retry)
            } catch (e: Exception) {
                onFailed(app, "Something went wrong. Please try again.", platform, FailAction.Retry)
            } finally {
                DownloadKeepAliveService.stop(app)
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Pop-up controls
    // ------------------------------------------------------------------------------------------

    fun cancel(app: Application) {
        val id = processId
        val engine = universalEngine
        if (id != null && engine != null) engine.cancel(id)
        job?.cancel()
        DownloadKeepAliveService.stop(app)
        processId = null
        _state.value = State.Idle
    }

    /** Forget a finished, failed or not-yet-started result once the pop-up has been closed. */
    fun clear() {
        val current = _state.value
        if (current is State.Choose || current is State.Saved || current is State.Failed) {
            _state.value = State.Idle
        }
    }

    fun retry(app: Application) {
        val text = originalText ?: return
        begin(app, text)
    }

    fun updateEngineAndRetry(app: Application) {
        val url = currentUrl ?: return
        val platform = Platforms.nameFor(null, url)
        job?.cancel()
        job = scope.launch {
            _state.value = State.Looking(platform, "Updating the download engine…")
            try {
                universal(app).updateEngine()
                autoRepaired = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val reason = (e as? UniversalException)?.message
                    ?: "Couldn't update the download engine. Check your connection and try again."
                _state.value = State.Failed(reason, platform, FailAction.Retry)
                return@launch
            }
            lookUp(app, url)
        }
    }

    private fun onSaved(app: Application, title: String, platform: String, thumbnail: String?, uri: Uri) {
        libraryDirty = true
        _state.value = State.Saved(title, platform, thumbnail, uri)
        if (!popupVisible) {
            DownloadKeepAliveService.notifyFinished(app, "Saved to OffScroll", title, success = true)
        }
    }

    private fun onFailed(app: Application, message: String, platform: String, action: FailAction) {
        _state.value = State.Failed(message, platform, action)
        if (!popupVisible) {
            DownloadKeepAliveService.notifyFinished(app, "Download failed", message, success = false)
        }
    }
}
