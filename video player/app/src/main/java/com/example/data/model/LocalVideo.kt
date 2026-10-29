package com.example.data.model

import android.net.Uri
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LocalVideo(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val dateAddedSec: Long,
    val dateModifiedSec: Long,
    val relativePath: String,
    val filePath: String,
    val mimeType: String,
    val bucketDisplayName: String,
    val isFavorite: Boolean = false,
    val isSample: Boolean = false
) {
    val formattedDuration: String
        get() {
            if (durationMs <= 0) return "0:00"
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            val hours = minutes / 60
            return if (hours > 0) {
                String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes % 60, seconds)
            } else {
                String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
            }
        }

    val formattedSize: String
        get() {
            if (sizeBytes <= 0) return "0 MB"
            val kb = sizeBytes / 1024.0
            val mb = kb / 1024.0
            val gb = mb / 1024.0
            return when {
                gb >= 1.0 -> String.format(Locale.getDefault(), "%.2f GB", gb)
                mb >= 1.0 -> String.format(Locale.getDefault(), "%.1f MB", mb)
                else -> String.format(Locale.getDefault(), "%.0f KB", kb)
            }
        }

    val formattedResolution: String
        get() {
            return if (width > 0 && height > 0) {
                val orientation = if (height >= width) "Portrait" else "Landscape"
                "${width} × ${height} ($orientation)"
            } else {
                "Unknown Resolution"
            }
        }

    val formattedDateAdded: String
        get() {
            if (dateAddedSec <= 0) return "Unknown date"
            val sdf = SimpleDateFormat("MMM dd, yyyy · hh:mm a", Locale.getDefault())
            return sdf.format(Date(dateAddedSec * 1000L))
        }

    val fileExtension: String
        get() = displayName.substringAfterLast('.', "").uppercase()
}

data class VideoMetadataDetails(
    val video: LocalVideo,
    val frameRate: String = "Unknown",
    val bitrate: String = "Unknown",
    val rotation: String = "0°",
    val audioCodec: String = "Unknown",
    val videoCodec: String = "Unknown",
    val colorStandard: String = "Standard",
    val captureFps: String = ""
)
