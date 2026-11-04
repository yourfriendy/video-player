package com.example.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.universal.LinkUtils
import com.example.data.universal.MediaInfo
import com.example.data.universal.ProgressMapper
import com.example.data.universal.QualityOption
import com.example.data.universal.UniversalDownloader
import com.example.data.universal.UniversalException
import com.example.data.universal.engine.EngineManager
import com.example.data.universal.engine.EngineMissingException
import com.example.data.universal.engine.EngineOutdatedException
import com.example.data.update.InstallLaunch
import com.example.service.DownloadKeepAliveService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface UniversalState {
    data object Idle : UniversalState

    /** Setting up the engine, looking a link up, or updating yt-dlp. */
    data class Working(val message: String) : UniversalState

    data class Ready(val info: MediaInfo, val selectedId: String) : UniversalState

    data class Downloading(
        val info: MediaInfo,
        val option: QualityOption,
        val progress: Float,
        val phase: String,
        val detail: String
    ) : UniversalState

    data class Saved(val info: MediaInfo, val option: QualityOption, val uri: Uri) : UniversalState

    data class Failed(val message: String, val canUpdateEngine: Boolean) : UniversalState
}

/** Whether the optional OffScroll Engine (yt-dlp, about 50 MB) is on the phone and usable. */
sealed interface EngineStatus {
    data object Installed : EngineStatus
    data object Missing : EngineStatus
    data class Outdated(val message: String) : EngineStatus
    data class Downloading(val progress: Float) : EngineStatus

    /** Android's installer is open; [needsPermission] means the "allow installs from this app" screen came first. */
    data class Installing(val needsPermission: Boolean) : EngineStatus
    data class Failed(val message: String) : EngineStatus
}

/**
 * Drives the "Any link" tab. Activity-scoped, so a link shared from another app and the tab itself
 * talk to the same instance, and a running download survives the sheet being closed.
 */
class UniversalDownloadViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application
    private val downloader = UniversalDownloader(application)
    private val manager = EngineManager(application)

    private val _engineStatus = MutableStateFlow<EngineStatus>(
        if (manager.isInstalled()) EngineStatus.Installed else EngineStatus.Missing
    )
    val engineStatus: StateFlow<EngineStatus> = _engineStatus.asStateFlow()
    private var outdatedAtVersionCode = -1L

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    private val _state = MutableStateFlow<UniversalState>(UniversalState.Idle)
    val state: StateFlow<UniversalState> = _state.asStateFlow()

    private val _engineVersion = MutableStateFlow("")
    val engineVersion: StateFlow<String> = _engineVersion.asStateFlow()

    private val _engineNote = MutableStateFlow("")
    val engineNote: StateFlow<String> = _engineNote.asStateFlow()

    private var job: Job? = null
    private var processId: String? = null
    private var engineReady = false
    private var autoRepairedThisSession = false

    private fun isBusy(): Boolean {
        val s = _state.value
        return s is UniversalState.Working || s is UniversalState.Downloading
    }

    /** Called when the screen comes back to the front (for example after Android's installer closes). */
    fun refreshEngineStatus() {
        val current = _engineStatus.value
        if (current is EngineStatus.Downloading) return
        val installed = manager.isInstalled()
        _engineStatus.value = when {
            !installed -> if (current is EngineStatus.Failed) current else EngineStatus.Missing
            current is EngineStatus.Outdated && manager.installedVersionCode() == outdatedAtVersionCode -> current
            else -> {
                if (current !is EngineStatus.Installed) engineReady = false
                EngineStatus.Installed
            }
        }
    }

    /** Downloads OffScroll-Engine.apk from GitHub and opens Android's installer for it. */
    fun installEngine() {
        if (_engineStatus.value is EngineStatus.Downloading) return
        viewModelScope.launch {
            _engineStatus.value = EngineStatus.Downloading(0f)
            try {
                val file = manager.download { _engineStatus.value = EngineStatus.Downloading(it) }
                manager.verify(file)
                _engineStatus.value = when (val launch = manager.launchInstall(file)) {
                    InstallLaunch.Failed -> EngineStatus.Failed("Android couldn't open the installer. Use \"Open GitHub page\" and install OffScroll-Engine.apk from there.")
                    else -> EngineStatus.Installing(needsPermission = launch == InstallLaunch.NeedsPermission)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: UniversalException) {
                _engineStatus.value = EngineStatus.Failed(e.message ?: "Couldn't install the engine.")
            } catch (e: java.io.IOException) {
                _engineStatus.value = EngineStatus.Failed("Couldn't reach GitHub. Check your connection and try again.")
            } catch (e: Exception) {
                _engineStatus.value = EngineStatus.Failed("Couldn't install the engine (${e.javaClass.simpleName}).")
            }
        }
    }

    /** The engine is missing or out of date: show the install card instead of an error. */
    private fun noteEngineProblem(e: EngineMissingException) {
        if (e is EngineOutdatedException) {
            outdatedAtVersionCode = manager.installedVersionCode()
            _engineStatus.value = EngineStatus.Outdated(e.message ?: "The engine needs updating.")
        } else {
            _engineStatus.value = if (manager.isInstalled()) EngineStatus.Failed(e.message ?: "The engine can't be reached.") else EngineStatus.Missing
        }
        engineReady = false
        _state.value = UniversalState.Idle
    }

    /** Warms the engine up in the background so the first lookup doesn't wait for it. */
    fun prepareEngine() {
        if (engineReady) return
        if (!manager.isInstalled()) {
            _engineStatus.value = EngineStatus.Missing
            return
        }
        viewModelScope.launch {
            try {
                downloader.prepare()
                engineReady = true
                _engineVersion.value = downloader.engineVersion()
            } catch (e: CancellationException) {
                throw e
            } catch (e: EngineMissingException) {
                noteEngineProblem(e)
            } catch (e: Exception) {
                // Shown properly when the user actually looks something up
            }
        }
    }

    fun setInput(text: String) {
        if (text == _input.value) return
        _input.value = text
        if (!isBusy()) _state.value = UniversalState.Idle
    }

    /** A link shared to OffScroll from another app: fill it in and look it up straight away. */
    fun onSharedLink(text: String?) {
        if (isBusy()) return
        val url = LinkUtils.extractUrl(text)
        if (url == null) {
            _input.value = text?.trim().orEmpty()
            _state.value = UniversalState.Failed("No link found in what you shared.", false)
            return
        }
        _input.value = url
        lookUp()
    }

    fun lookUp() {
        if (isBusy()) return
        val url = LinkUtils.extractUrl(_input.value)
        if (url == null) {
            _state.value = UniversalState.Failed("Paste a link to a video first.", false)
            return
        }
        job?.cancel()
        job = viewModelScope.launch {
            _input.value = url
            _state.value = UniversalState.Working(
                if (engineReady) "Looking up the video…"
                else "Setting up the download engine (first time only)…"
            )
            try {
                val info = fetchWithAutoRepair(url)
                engineReady = true
                _engineVersion.value = downloader.engineVersion()
                _state.value = UniversalState.Ready(info, info.options.first().id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: EngineMissingException) {
                noteEngineProblem(e)
            } catch (e: UniversalException) {
                _state.value = UniversalState.Failed(e.message ?: "Something went wrong.", e.suggestUpdate)
            } catch (e: Exception) {
                _state.value = UniversalState.Failed("Something went wrong (${e.javaClass.simpleName}).", true)
            }
        }
    }

    /** If a lookup fails the way "the site changed" does, update yt-dlp once and try again. */
    private suspend fun fetchWithAutoRepair(url: String): MediaInfo {
        try {
            return downloader.fetchInfo(url)
        } catch (e: UniversalException) {
            if (!e.suggestUpdate || autoRepairedThisSession) throw e
            autoRepairedThisSession = true
            _state.value = UniversalState.Working("The site changed. Updating the download engine…")
            downloader.updateEngine()
            _engineVersion.value = downloader.engineVersion()
            _state.value = UniversalState.Working("Looking up the video…")
            return downloader.fetchInfo(url)
        }
    }

    fun select(optionId: String) {
        val ready = _state.value as? UniversalState.Ready ?: return
        _state.value = ready.copy(selectedId = optionId)
    }

    fun startDownload() {
        val ready = _state.value as? UniversalState.Ready ?: return
        val option = ready.info.options.firstOrNull { it.id == ready.selectedId } ?: ready.info.options.first()
        val id = "dl-${System.nanoTime()}"
        processId = id
        val mapper = ProgressMapper(option.videoShare)
        val title = ready.info.title

        DownloadKeepAliveService.start(app, title)
        job?.cancel()
        job = viewModelScope.launch {
            var lastPercent = -1
            _state.value = UniversalState.Downloading(ready.info, option, 0f, mapper.phaseLabel(), "")
            try {
                val uri = downloader.download(ready.info, option, id) { percent, eta, line ->
                    val overall = mapper.onLine(line, percent)
                    val detail = ProgressMapper.detail(line, eta)
                    _state.value = UniversalState.Downloading(ready.info, option, overall, mapper.phaseLabel(), detail)
                    val whole = (overall * 100).toInt()
                    if (whole != lastPercent) {
                        lastPercent = whole
                        DownloadKeepAliveService.update(app, title, mapper.phaseLabel(), whole)
                    }
                }
                _state.value = UniversalState.Saved(ready.info, option, uri)
            } catch (e: CancellationException) {
                throw e // cancelDownload() has already put the screen back
            } catch (e: EngineMissingException) {
                noteEngineProblem(e)
            } catch (e: UniversalException) {
                _state.value = UniversalState.Failed(e.message ?: "The download failed.", e.suggestUpdate)
            } catch (e: Exception) {
                _state.value = UniversalState.Failed("The download failed (${e.javaClass.simpleName}).", true)
            } finally {
                DownloadKeepAliveService.stop(app)
                processId = null
            }
        }
    }

    fun cancelDownload() {
        val current = _state.value
        processId?.let { downloader.cancel(it) }
        job?.cancel()
        DownloadKeepAliveService.stop(app)
        _state.value = when (current) {
            is UniversalState.Downloading -> UniversalState.Ready(current.info, current.option.id)
            else -> UniversalState.Idle
        }
    }

    /** Updates yt-dlp. With [thenRetry] the failed lookup runs again afterwards. */
    fun updateEngine(thenRetry: Boolean = false) {
        if (isBusy()) return
        val before = _state.value
        job?.cancel()
        job = viewModelScope.launch {
            _state.value = UniversalState.Working("Updating the download engine…")
            try {
                _engineNote.value = downloader.updateEngine()
                _engineVersion.value = downloader.engineVersion()
                _state.value = if (before is UniversalState.Ready) before else UniversalState.Idle
                if (thenRetry) lookUp()
            } catch (e: CancellationException) {
                throw e
            } catch (e: EngineMissingException) {
                noteEngineProblem(e)
            } catch (e: UniversalException) {
                _engineNote.value = e.message ?: "Couldn't update the engine."
                _state.value = UniversalState.Failed(e.message ?: "Couldn't update the engine.", false)
            } catch (e: Exception) {
                _engineNote.value = "Couldn't update the engine."
                _state.value = UniversalState.Failed("Couldn't update the engine.", false)
            }
        }
    }

    /** "Download another". */
    fun reset() {
        if (isBusy()) return
        _input.value = ""
        _state.value = UniversalState.Idle
    }
}
