package com.example.ui.viewmodel

import com.example.data.model.LocalVideo
import com.example.ui.model.MovieAutoMode
import com.example.ui.model.isHorizontal
import com.example.ui.model.shouldAutoEnterMovieMode
import kotlinx.coroutines.flow.update

/**
 * Decides movie mode whenever a new video comes on screen:
 *  - while in movie mode, a video that is not horizontal ends it (a tall video makes no sense sideways);
 *  - otherwise movie mode starts by itself if the setting says so for this video, unless the user just left it
 *    on this very video.
 * Pure (state in, state out) so it can be tested without a phone.
 */
internal fun applyMovieRules(
    state: VideoFeedUiState,
    id: Long,
    width: Int,
    height: Int,
    durationMs: Long,
    isSample: Boolean
): VideoFeedUiState {
    val optOut = state.movieOptOutVideoId?.takeIf { it == id }
    return when {
        state.isMovieMode && !com.example.ui.model.isHorizontal(width, height) ->
            state.copy(isMovieMode = false, isMovieLocked = false, movieOptOutVideoId = null)
        !state.isMovieMode && optOut == null &&
            shouldAutoEnterMovieMode(width, height, durationMs, isSample, state.movieAutoMode) ->
            state.copy(isMovieMode = true, isMovieLocked = false, areControlsVisible = true, movieOptOutVideoId = null)
        else -> state.copy(movieOptOutVideoId = optOut)
    }
}

internal fun applyMovieRules(state: VideoFeedUiState, video: LocalVideo): VideoFeedUiState =
    applyMovieRules(state, video.id, video.width, video.height, video.durationMs, video.isSample)

/** Starts movie mode for the video on screen (the Movie mode button). */
fun VideoFeedViewModel.enterMovieMode() {
    _uiState.update { state ->
        val horizontal = state.filteredVideos.getOrNull(state.currentIndex)?.isHorizontal() ?: true
        if (!horizontal) state
        else state.copy(isMovieMode = true, isMovieLocked = false, areControlsVisible = true, isPlaying = true, movieOptOutVideoId = null)
    }
}

/** Leaves movie mode. It will not start again on this same video by itself. */
fun VideoFeedViewModel.exitMovieMode() {
    _uiState.update { state ->
        state.copy(
            isMovieMode = false,
            isMovieLocked = false,
            areControlsVisible = true,
            movieOptOutVideoId = state.currentVideoId
        )
    }
}

fun VideoFeedViewModel.toggleMovieLock() {
    _uiState.update { state ->
        val locked = !state.isMovieLocked
        state.copy(
            isMovieLocked = locked,
            areControlsVisible = true,
            toastMessage = if (locked) "Locked. Tap the lock to unlock." else "Unlocked"
        )
    }
}

fun VideoFeedViewModel.cycleMovieFit() {
    _uiState.update { state ->
        val next = state.movieFit.next()
        state.copy(movieFit = next, toastMessage = next.label)
    }
}

fun VideoFeedViewModel.setMovieAutoMode(mode: MovieAutoMode) {
    settingsManager.setMovieAutoMode(mode)
    _uiState.update { it.copy(movieAutoMode = mode, movieOptOutVideoId = null) }
}
