package com.example.ui.model

import com.example.data.model.LocalVideo
import java.util.Locale

/** A horizontal video this long counts as a "movie" (5 minutes or more). */
const val MOVIE_MIN_DURATION_MS = 5 * 60_000L

/** How far the skip buttons and the double tap on the left or right side jump. */
const val MOVIE_SKIP_MS = 10_000L

/** When movie mode starts on its own. */
enum class MovieAutoMode {
    /** Horizontal videos of 5 minutes or more (the default). */
    LONG_ONLY,

    /** Every horizontal video. */
    ALL_HORIZONTAL,

    /** Never by itself; only from the Movie mode button. */
    OFF
}

/** How the picture fills the screen in movie mode. */
enum class MovieFit {
    /** The whole picture, with black bars where needed. */
    FIT,

    /** Stretched to the screen edges (can distort). */
    STRETCH,

    /** Fills the screen and crops what does not fit. */
    ZOOM;

    fun next(): MovieFit = entries[(ordinal + 1) % entries.size]

    val label: String
        get() = when (this) {
            FIT -> "Fit"
            STRETCH -> "Stretch"
            ZOOM -> "Zoom"
        }
}

fun isHorizontal(width: Int, height: Int): Boolean = width > 0 && height > 0 && width > height

fun LocalVideo.isHorizontal(): Boolean = isHorizontal(width, height)

/** True when movie mode should start by itself for this video under the chosen setting. */
fun shouldAutoEnterMovieMode(
    width: Int,
    height: Int,
    durationMs: Long,
    isSample: Boolean,
    mode: MovieAutoMode
): Boolean {
    if (isSample || !isHorizontal(width, height)) return false
    return when (mode) {
        MovieAutoMode.OFF -> false
        MovieAutoMode.ALL_HORIZONTAL -> true
        MovieAutoMode.LONG_ONLY -> durationMs >= MOVIE_MIN_DURATION_MS
    }
}

fun shouldAutoEnterMovieMode(video: LocalVideo, mode: MovieAutoMode): Boolean =
    shouldAutoEnterMovieMode(video.width, video.height, video.durationMs, video.isSample, mode)

/** Where a skip lands: never before the start, never past the end (when the length is known). */
fun skipTarget(currentMs: Long, deltaMs: Long, durationMs: Long): Long {
    val max = if (durationMs > 0) durationMs else Long.MAX_VALUE
    return (currentMs + deltaMs).coerceIn(0L, max)
}

/** 1:05 or 1:02:05, for the movie controls. */
fun formatMovieTime(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L)) / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}
