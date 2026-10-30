package com.example.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.update.AppUpdateService
import com.example.data.update.InstallLaunch
import com.example.data.update.ReleaseInfo
import com.example.data.update.UpdateException
import com.example.data.update.UpdateParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: ReleaseInfo) : UpdateState
    data class Downloading(val release: ReleaseInfo, val progress: Float) : UpdateState
    data class ReadyToInstall(
        val release: ReleaseInfo,
        val file: File,
        /** Android's "allow installs from this app" screen was opened; the user has to come back and tap Install. */
        val needsPermission: Boolean = false
    ) : UpdateState

    /** [release] is set when the failure happened after a newer build was found, so "Try again" can resume. */
    data class Failed(val message: String, val release: ReleaseInfo? = null) : UpdateState
}

/**
 * Shared by the update pop-up (shown over any screen) and the "App updates" card in Settings.
 * Checking is quiet: a failed check never interrupts the user, it only shows up in the card.
 */
class AppUpdateViewModel(application: Application) : AndroidViewModel(application) {

    private val service = AppUpdateService(application)
    private val prefs = application.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val installedBuild: Int = service.currentBuildNumber()
    val versionName: String = service.currentVersionName()

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** Whether the pop-up is showing. Only automatic checks raise it, and only once per build. */
    private val _promptVisible = MutableStateFlow(false)
    val promptVisible: StateFlow<Boolean> = _promptVisible.asStateFlow()

    private var job: Job? = null

    /** Called whenever the app comes to the front. Cheap: it skips the network if it checked recently. */
    fun checkOnLaunch() {
        val current = _state.value
        val canCheck = current is UpdateState.Idle ||
            current is UpdateState.UpToDate ||
            (current is UpdateState.Failed && current.release == null)
        if (!canCheck) return
        val sinceLastGoodCheck = System.currentTimeMillis() - prefs.getLong(KEY_LAST_GOOD_CHECK, 0L)
        if (sinceLastGoodCheck in 0 until MIN_AUTO_CHECK_GAP_MS) return
        runCheck(automatic = true)
    }

    /** The "Check for updates" button. */
    fun checkNow() {
        val current = _state.value
        if (current is UpdateState.Checking || current is UpdateState.Downloading || current is UpdateState.ReadyToInstall) return
        runCheck(automatic = false)
    }

    private fun runCheck(automatic: Boolean) {
        job?.cancel()
        job = viewModelScope.launch {
            _state.value = UpdateState.Checking
            try {
                val release = service.fetchLatest()
                if (UpdateParser.isNewer(installedBuild, release)) {
                    _state.value = UpdateState.Available(release)
                    val alreadyDismissed = prefs.getInt(KEY_DISMISSED_BUILD, 0) == release.buildNumber
                    if (automatic && !alreadyDismissed) _promptVisible.value = true
                } else {
                    prefs.edit().putLong(KEY_LAST_GOOD_CHECK, System.currentTimeMillis()).apply()
                    service.deleteDownloads()
                    _state.value = UpdateState.UpToDate
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: UpdateException) {
                _state.value = UpdateState.Failed(e.message ?: "Couldn't check for updates.")
            } catch (e: IOException) {
                _state.value = UpdateState.Failed("Couldn't reach GitHub. Check your connection and try again.")
            } catch (e: Exception) {
                _state.value = UpdateState.Failed("Couldn't check for updates (${e.javaClass.simpleName}).")
            }
        }
    }

    fun startDownload() {
        val release = when (val current = _state.value) {
            is UpdateState.Available -> current.release
            is UpdateState.Failed -> current.release ?: return
            else -> return
        }
        job?.cancel()
        job = viewModelScope.launch {
            _state.value = UpdateState.Downloading(release, 0f)
            try {
                val file = service.download(release) { progress ->
                    _state.value = UpdateState.Downloading(release, progress)
                }
                service.verify(file)
                _state.value = UpdateState.ReadyToInstall(release, file)
            } catch (e: CancellationException) {
                throw e
            } catch (e: UpdateException) {
                _state.value = UpdateState.Failed(e.message ?: "The update failed.", release)
            } catch (e: IOException) {
                _state.value = UpdateState.Failed("The download was interrupted. Check your connection and try again.", release)
            } catch (e: Exception) {
                _state.value = UpdateState.Failed("The update failed (${e.javaClass.simpleName}).", release)
            }
        }
    }

    fun install() {
        val ready = _state.value as? UpdateState.ReadyToInstall ?: return
        _state.value = when (service.launchInstall(ready.file)) {
            InstallLaunch.Started -> ready.copy(needsPermission = false)
            InstallLaunch.NeedsPermission -> ready.copy(needsPermission = true)
            InstallLaunch.Failed -> UpdateState.Failed("Couldn't open Android's installer.", ready.release)
        }
    }

    /** "Later" / "Hide". Remembers the build, so the same update never pops up twice by itself. */
    fun dismissPrompt() {
        (_state.value as? UpdateState.Available)?.release?.buildNumber?.let { build ->
            prefs.edit().putInt(KEY_DISMISSED_BUILD, build).apply()
        }
        _promptVisible.value = false
    }

    private companion object {
        const val PREFS = "app_update"
        const val KEY_LAST_GOOD_CHECK = "last_good_check_ms"
        const val KEY_DISMISSED_BUILD = "dismissed_build"
        const val MIN_AUTO_CHECK_GAP_MS = 15 * 60 * 1000L
    }
}
