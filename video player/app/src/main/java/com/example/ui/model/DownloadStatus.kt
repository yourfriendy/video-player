package com.example.ui.model

import android.net.Uri

/** Where the Instagram downloader currently is. Shown by the downloader sheet. */
sealed interface DownloadStatus {
    data object Idle : DownloadStatus
    data object Fetching : DownloadStatus

    /** [progress] is 0..1, or -1 when the file size isn't known. */
    data class Downloading(val progress: Float) : DownloadStatus

    data class Success(val uri: Uri, val title: String) : DownloadStatus
    data class Failed(val message: String) : DownloadStatus
}
