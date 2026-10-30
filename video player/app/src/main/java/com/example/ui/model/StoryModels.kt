package com.example.ui.model

import android.net.Uri
import com.example.data.download.InstagramParser

enum class DownloaderTab(val label: String) {
    REELS("Reels"),
    STORIES("Stories")
}

/** What the Stories tab is currently showing. */
sealed interface StoryUiState {
    data object Idle : StoryUiState
    data object Loading : StoryUiState
    data class NeedsLogin(val message: String) : StoryUiState
    data class Loaded(val owner: String, val items: List<InstagramParser.StoryItem>) : StoryUiState
    data class Error(val message: String) : StoryUiState
}

/** Per-story download progress, keyed by story id. */
sealed interface StoryItemDownload {
    /** [progress] is 0..1, or -1 when unknown. */
    data class Downloading(val progress: Float) : StoryItemDownload
    data class Done(val uri: Uri) : StoryItemDownload
    data class Failed(val message: String) : StoryItemDownload
}
