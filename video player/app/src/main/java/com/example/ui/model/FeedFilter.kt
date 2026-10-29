package com.example.ui.model

sealed class FeedFilter(val displayName: String) {
    object All : FeedFilter("All Videos")
    object Favorites : FeedFilter("Favorites")
    object RecentlyAdded : FeedFilter("Recently Added")
    object RecentlyPlayed : FeedFilter("Recently Played")
    object WatchLater : FeedFilter("Watch Later")
    data class Playlist(val playlistId: Long, val playlistTitle: String) : FeedFilter(playlistTitle)
    data class DurationRange(val minMs: Long, val maxMs: Long, val label: String) : FeedFilter(label)
    data class BrainMode(val modeName: String) : FeedFilter(modeName)
    data class ByFolder(val folderName: String) : FeedFilter(folderName)
}
