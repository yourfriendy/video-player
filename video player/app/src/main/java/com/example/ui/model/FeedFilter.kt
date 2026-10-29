package com.example.ui.model

sealed class FeedFilter(val displayName: String) {
    object All : FeedFilter("All Videos")
    object Favorites : FeedFilter("Favorites")
    object RecentlyAdded : FeedFilter("Recently Added")
    object RecentlyPlayed : FeedFilter("Recently Played")
    object WatchLater : FeedFilter("Watch Later")
    data class Playlist(val playlistId: Long, val playlistTitle: String) : FeedFilter(playlistTitle)
    data class DurationRange(val minMs: Long, val maxMs: Long, val label: String) : FeedFilter(label) {
        /** Half-open [minMs, maxMs) so neighbouring presets never overlap. */
        fun matches(durationMs: Long): Boolean = durationMs >= minMs && durationMs < maxMs
    }
    data class BrainMode(val modeName: String) : FeedFilter(modeName)
    data class ByFolder(val folderName: String) : FeedFilter(folderName)
}

/** Duration rules for the "Brain Modes". Half-open ranges so boundaries never overlap. */
fun brainModeMatches(modeName: String, durationMs: Long): Boolean =
    when (modeName.lowercase()) {
        "focus" -> durationMs < 180_000L
        "calm" -> durationMs in 180_000L until 600_000L
        "low energy" -> durationMs >= 600_000L
        else -> true // "dopamine" and unknown modes accept everything
    }
