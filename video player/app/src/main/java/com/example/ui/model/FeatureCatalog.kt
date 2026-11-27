package com.example.ui.model

/** One thing OffScroll can do: a short name and one plain line that explains it. */
data class FeatureItem(val title: String, val detail: String)

/**
 * A group of features shown as one card in the Updates section. [key] only picks the icon and colour in the UI,
 * so this file stays plain Kotlin and can be unit tested.
 */
data class FeatureGroup(
    val key: String,
    val name: String,
    val tagline: String,
    val items: List<FeatureItem>
)

/**
 * The full list of what the app does, in the order people meet it. Keep it in step with the README's Features
 * section: when a feature is added or removed, change both.
 */
object FeatureCatalog {

    val groups: List<FeatureGroup> = listOf(
        FeatureGroup(
            key = "watch",
            name = "Watch",
            tagline = "A reels-style feed for your own videos",
            items = listOf(
                FeatureItem("Vertical swipe feed", "Swipe through the videos on your phone, full screen."),
                FeatureItem("Like with the heart", "Tap the heart to keep what you love."),
                FeatureItem("Tap and double-tap", "Tap for controls, tap again for full screen, double-tap to pause or play."),
                FeatureItem("Scrub bar", "Drag the bar or swipe sideways to seek, with a live time bubble."),
                FeatureItem("Brightness and volume", "Slide on the left edge for brightness and the right edge for volume."),
                FeatureItem("Auto-advance", "Moves to the next video by itself when loop is off."),
                FeatureItem("Resume where you left off", "Long videos pick up from your last spot."),
                FeatureItem("Loop, mute and speed", "Loop one video, mute it, or play from 0.5x to 2x."),
                FeatureItem("Crop, Fit or Stretch", "Choose how every video fills the screen."),
                FeatureItem("Picture-in-picture", "Keep watching in a floating window over other apps.")
            )
        ),
        FeatureGroup(
            key = "movie",
            name = "Movie mode",
            tagline = "A landscape player for wide videos",
            items = listOf(
                FeatureItem("Landscape player", "Wide videos open in their own full-screen player."),
                FeatureItem("Skip 10 seconds", "Jump back or forward with one tap."),
                FeatureItem("Speed and screen fit", "Change speed, and switch between fit, stretch and zoom."),
                FeatureItem("Audio and subtitles", "Pick the audio track and subtitles you want."),
                FeatureItem("Controls lock", "Lock the screen so a stray touch can't interrupt."),
                FeatureItem("Starts by itself", "Opens automatically for wide videos of 5 minutes or more. Change it in Settings.")
            )
        ),
        FeatureGroup(
            key = "music",
            name = "Music",
            tagline = "The songs already on your phone",
            items = listOf(
                FeatureItem("Songs, Albums, Artists, Liked", "Browse your music files the way you expect."),
                FeatureItem("Search", "Find a song by title, artist or album."),
                FeatureItem("Mini player and Now playing", "A small player while you browse, a full screen when you want it."),
                FeatureItem("Shuffle, repeat and queue", "Control what plays next."),
                FeatureItem("Sleep timer", "Stops the music after the time you set."),
                FeatureItem("Plays in the background", "Notification and lock-screen controls keep it in reach."),
                FeatureItem("Local files only", "Nothing is streamed or uploaded.")
            )
        ),
        FeatureGroup(
            key = "find",
            name = "Find",
            tagline = "Get to the right video fast",
            items = listOf(
                FeatureItem("Search page", "Instant results with thumbnails and highlighted matches."),
                FeatureItem("Smart matching", "Ignores case, punctuation and extensions."),
                FeatureItem("Narrow it down", "Filter by length, favorites, folder and sort order."),
                FeatureItem("Recent searches and quick picks", "Plus Recently added and Continue watching."),
                FeatureItem("Play the exact result", "Tap a result and the feed becomes your results; one tap returns to the full feed."),
                FeatureItem("Sort and shuffle", "Sort by date, length, size or name. Shuffle keeps your place.")
            )
        ),
        FeatureGroup(
            key = "save",
            name = "Save",
            tagline = "Keep videos from Instagram and more",
            items = listOf(
                FeatureItem("Instagram reel downloader", "Paste a public reel link and save it."),
                FeatureItem("Share to OffScroll", "Use Instagram's Share button and the save starts by itself."),
                FeatureItem("Downloader page", "Save people once, then watch their Stories and Reposts before you save."),
                FeatureItem("Story alerts", "Get a notification when someone you follow posts a new story. Looking never tells them."),
                FeatureItem("Instagram stories", "Save one story, all of them, or a highlight: videos and photos."),
                FeatureItem("Save videos from other apps", "YouTube, TikTok, Facebook, Snapchat, X and 1000+ more sites, with the optional Engine app."),
                FeatureItem("Saved to your feed", "Videos land in Movies/OffScroll and show up instantly.")
            )
        ),
        FeatureGroup(
            key = "keep",
            name = "Keep",
            tagline = "Your own collections",
            items = listOf(
                FeatureItem("Favorites", "Everything you liked, in one place."),
                FeatureItem("Watch Later", "Set a video aside for another day."),
                FeatureItem("Playlists with emoji", "Group videos your way and give each a look."),
                FeatureItem("Watch history", "See what you watched, and clear it any time.")
            )
        ),
        FeatureGroup(
            key = "manage",
            name = "Manage",
            tagline = "Control over your files",
            items = listOf(
                FeatureItem("Details for any video", "Size, length, resolution, bitrate, storage and where it lives."),
                FeatureItem("Share a video", "Send it with your usual share sheet."),
                FeatureItem("Delete safely", "Uses Android's own confirmation. Favorites and playlists are cleaned up only once the file is gone."),
                FeatureItem("Updates from the developer", "Find and install new versions right here, with what changed.")
            )
        ),
        FeatureGroup(
            key = "private",
            name = "Private by design",
            tagline = "Yours alone",
            items = listOf(
                FeatureItem("Plays offline", "Your videos are read from your phone and never leave it."),
                FeatureItem("Minimal permission", "Asks only to read your videos; music access is asked when you open Music."),
                FeatureItem("No accounts, no ads", "Install it and start watching.")
            )
        )
    )

    /** How many individual features there are across every group. */
    val totalFeatures: Int get() = groups.sumOf { it.items.size }
}
