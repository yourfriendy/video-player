package com.example.data.repository

import android.app.Activity
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.example.data.local.AppDatabase
import com.example.data.local.entity.FavoriteVideo
import com.example.data.local.entity.WatchHistory
import com.example.data.model.LocalVideo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class VideoRepository(
    private val context: Context,
    private val database: AppDatabase
) {
    private val contentResolver: ContentResolver = context.contentResolver
    private val favoriteDao = database.favoriteDao()
    private val watchHistoryDao = database.watchHistoryDao()

    val favoritesFlow: Flow<List<FavoriteVideo>> = favoriteDao.getAllFavorites()
    val watchHistoryFlow: Flow<List<WatchHistory>> = watchHistoryDao.getAllWatchHistory()

    /**
     * Observe MediaStore video changes via ContentObserver and query videos.
     */
    fun getVideosFlow(): Flow<List<LocalVideo>> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                trySend(queryDeviceVideos())
            }
        }

        contentResolver.registerContentObserver(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            true,
            observer
        )

        // Send initial list
        trySend(queryDeviceVideos())

        awaitClose {
            contentResolver.unregisterContentObserver(observer)
        }
    }.combine(favoritesFlow) { videos, favorites ->
        val favoriteUriSet = favorites.map { it.videoUri }.toSet()
        videos.map { video ->
            video.copy(isFavorite = favoriteUriSet.contains(video.uri.toString()))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Query all videos from MediaStore
     */
    suspend fun loadVideos(allowSamples: Boolean = false): List<LocalVideo> = withContext(Dispatchers.IO) {
        val favoriteUris = favoriteDao.getAllFavoriteUris().toSet()
        if (allowSamples) {
            // Explicit demo mode: never mixes fake clips into a real library
            return@withContext loadOrGenerateSampleVideos(favoriteUris)
        }
        queryDeviceVideos().map { video ->
            video.copy(isFavorite = favoriteUris.contains(video.uri.toString()))
        }
    }

    private fun queryDeviceVideos(): List<LocalVideo> {
        val videoList = mutableListOf<LocalVideo>()
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.MIME_TYPE,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.RELATIVE_PATH
            } else {
                MediaStore.Video.Media.DATA
            },
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.BUCKET_DISPLAY_NAME
            } else {
                MediaStore.Video.Media.DATA
            }
        )

        val sortOrder = "${MediaStore.Video.Media.DATE_ADDED} DESC"

        try {
            contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                val dateAddCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val dateModCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val pathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH)
                } else {
                    cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                }
                val bucketCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                } else {
                    cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                }

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                        id
                    )
                    val name = cursor.getString(nameCol) ?: "Video_$id.mp4"
                    val duration = cursor.getLong(durCol)
                    val size = cursor.getLong(sizeCol)
                    val width = cursor.getInt(widthCol)
                    val height = cursor.getInt(heightCol)
                    val dateAdded = cursor.getLong(dateAddCol)
                    val dateMod = cursor.getLong(dateModCol)
                    val mime = cursor.getString(mimeCol) ?: "video/mp4"
                    val relPath = if (pathCol >= 0) cursor.getString(pathCol) ?: "" else ""
                    val bucketName = if (bucketCol >= 0) {
                        cursor.getString(bucketCol) ?: "Gallery"
                    } else {
                        "Gallery"
                    }

                    videoList.add(
                        LocalVideo(
                            id = id,
                            uri = contentUri,
                            displayName = name,
                            durationMs = duration,
                            sizeBytes = size,
                            width = width,
                            height = height,
                            dateAddedSec = dateAdded,
                            dateModifiedSec = dateMod,
                            relativePath = relPath,
                            filePath = contentUri.toString(),
                            mimeType = mime,
                            bucketDisplayName = bucketName.ifEmpty { "Internal Storage" }
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return videoList
    }

    /**
     * Provide demo / fallback offline video samples for testing in emulator if no gallery videos exist.
     */
    private suspend fun loadOrGenerateSampleVideos(favoriteUris: Set<String>): List<LocalVideo> = withContext(Dispatchers.IO) {
        val sampleList = mutableListOf<LocalVideo>()
        val sampleConfigs = listOf(
            SampleVideoSpec(
                id = 1001L,
                title = "Neon_Cyber_Sunset_Loop.mp4",
                durationMs = 15000L,
                sizeBytes = 8_400_000L,
                width = 1080,
                height = 1920,
                folder = "Reels",
                mime = "video/mp4"
            ),
            SampleVideoSpec(
                id = 1002L,
                title = "Urban_Skate_Park_Trick.mp4",
                durationMs = 24000L,
                sizeBytes = 14_200_000L,
                width = 1080,
                height = 1920,
                folder = "Downloads",
                mime = "video/mp4"
            ),
            SampleVideoSpec(
                id = 1003L,
                title = "Coffee_Brewing_Aesthetic.mp4",
                durationMs = 18000L,
                sizeBytes = 11_500_000L,
                width = 1080,
                height = 1920,
                folder = "Camera",
                mime = "video/mp4"
            ),
            SampleVideoSpec(
                id = 1004L,
                title = "Drone_Mountain_Flight_4K.mp4",
                durationMs = 32000L,
                sizeBytes = 28_000_000L,
                width = 1920,
                height = 1080,
                folder = "Saved Reels",
                mime = "video/mp4"
            )
        )

        sampleConfigs.forEach { spec ->
            val sampleUri = Uri.parse("asset:///sample_videos/${spec.title}")
            sampleList.add(
                LocalVideo(
                    id = spec.id,
                    uri = sampleUri,
                    displayName = spec.title,
                    durationMs = spec.durationMs,
                    sizeBytes = spec.sizeBytes,
                    width = spec.width,
                    height = spec.height,
                    dateAddedSec = System.currentTimeMillis() / 1000 - (spec.id * 3600),
                    dateModifiedSec = System.currentTimeMillis() / 1000,
                    relativePath = "Movies/${spec.folder}",
                    filePath = "content://media/external/video/media/${spec.id}",
                    mimeType = spec.mime,
                    bucketDisplayName = spec.folder,
                    isFavorite = favoriteUris.contains(sampleUri.toString()),
                    isSample = true
                )
            )
        }

        sampleList
    }

    suspend fun likeVideo(video: LocalVideo): Boolean = withContext(Dispatchers.IO) {
        val uriString = video.uri.toString()
        favoriteDao.addFavorite(
            FavoriteVideo(
                videoUri = uriString,
                displayName = video.displayName,
                dateFavorited = System.currentTimeMillis()
            )
        )
        true
    }

    suspend fun toggleFavorite(video: LocalVideo): Boolean = withContext(Dispatchers.IO) {
        val uriString = video.uri.toString()
        val allUris = favoriteDao.getAllFavoriteUris()
        if (allUris.contains(uriString)) {
            favoriteDao.removeFavorite(uriString)
            false
        } else {
            favoriteDao.addFavorite(
                FavoriteVideo(
                    videoUri = uriString,
                    displayName = video.displayName,
                    dateFavorited = System.currentTimeMillis()
                )
            )
            true
        }
    }

    /**
     * Called once a video has been on screen for a moment. Only counts as a new play if the last one
     * was more than 30s ago, and never wipes the saved resume position.
     */
    suspend fun recordWatchStart(video: LocalVideo) = withContext(Dispatchers.IO) {
        val uriStr = video.uri.toString()
        val existing = watchHistoryDao.getWatchHistory(uriStr)
        val now = System.currentTimeMillis()
        val isNewPlay = existing == null || now - existing.lastPlayedAt > 30_000L
        watchHistoryDao.recordWatch(
            WatchHistory(
                videoUri = uriStr,
                lastPlayedAt = if (isNewPlay) now else existing!!.lastPlayedAt,
                playCount = (existing?.playCount ?: 0) + if (isNewPlay) 1 else 0,
                lastPositionMs = existing?.lastPositionMs ?: 0L
            )
        )
    }

    suspend fun updateWatchPosition(video: LocalVideo, positionMs: Long) = withContext(Dispatchers.IO) {
        val existing = watchHistoryDao.getWatchHistory(video.uri.toString()) ?: return@withContext
        if (existing.lastPositionMs != positionMs) {
            watchHistoryDao.recordWatch(existing.copy(lastPositionMs = positionMs))
        }
    }

    private val playlistDao = database.playlistDao()
    private val watchLaterDao = database.watchLaterDao()
    private val queueDao = database.queueDao()
    private val videoBookmarkDao = database.videoBookmarkDao()

    val playlistsFlow: Flow<List<com.example.data.local.entity.PlaylistEntity>> = playlistDao.getAllPlaylists()
    val allPlaylistVideosFlow: Flow<List<com.example.data.local.entity.PlaylistVideoEntity>> = playlistDao.getAllPlaylistVideos()
    val watchLaterFlow: Flow<List<com.example.data.local.entity.WatchLaterEntity>> = watchLaterDao.getAllWatchLater()

    suspend fun createPlaylist(name: String, emoji: String? = "📁"): Long = withContext(Dispatchers.IO) {
        playlistDao.insertPlaylist(com.example.data.local.entity.PlaylistEntity(name = name, emojiOrIcon = emoji))
    }

    suspend fun deletePlaylist(playlist: com.example.data.local.entity.PlaylistEntity) = withContext(Dispatchers.IO) {
        playlistDao.deletePlaylist(playlist)
    }

    suspend fun addVideoToPlaylist(playlistId: Long, videoUri: String) = withContext(Dispatchers.IO) {
        playlistDao.insertPlaylistVideo(com.example.data.local.entity.PlaylistVideoEntity(playlistId = playlistId, videoUri = videoUri, position = 0))
    }

    suspend fun removeVideoFromPlaylist(playlistId: Long, videoUri: String) = withContext(Dispatchers.IO) {
        playlistDao.deletePlaylistVideo(com.example.data.local.entity.PlaylistVideoEntity(playlistId = playlistId, videoUri = videoUri, position = 0))
    }

    suspend fun addWatchLater(videoUri: String) = withContext(Dispatchers.IO) {
        watchLaterDao.addWatchLater(com.example.data.local.entity.WatchLaterEntity(videoUri = videoUri))
    }

    suspend fun removeWatchLater(videoUri: String) = withContext(Dispatchers.IO) {
        watchLaterDao.removeWatchLater(videoUri)
    }

    suspend fun clearWatchHistory() = withContext(Dispatchers.IO) {
        watchHistoryDao.clearAllHistory()
    }

    /**
     * Delete video with Scoped Storage support on Android 11+ (API 30+).
     * Returns IntentSender if system confirmation is required, or null if deleted directly.
     */
    suspend fun deleteVideo(video: LocalVideo): DeletionResult = withContext(Dispatchers.IO) {
        if (video.isSample) {
            cleanupVideoRecords(video.uri.toString())
            return@withContext DeletionResult.Success
        }

        try {
            val rowsDeleted = contentResolver.delete(video.uri, null, null)
            if (rowsDeleted > 0) {
                cleanupVideoRecords(video.uri.toString())
                DeletionResult.Success
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val pendingIntent = MediaStore.createDeleteRequest(
                        contentResolver,
                        listOf(video.uri)
                    )
                    DeletionResult.RequiresPermission(pendingIntent.intentSender)
                } else {
                    DeletionResult.Error("Could not delete file")
                }
            }
        } catch (securityException: SecurityException) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                securityException is android.app.RecoverableSecurityException) {
                DeletionResult.RequiresPermission(securityException.userAction.actionIntent.intentSender)
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val pendingIntent = MediaStore.createDeleteRequest(
                    contentResolver,
                    listOf(video.uri)
                )
                DeletionResult.RequiresPermission(pendingIntent.intentSender)
            } else {
                DeletionResult.Error(securityException.localizedMessage ?: "Permission denied to delete")
            }
        } catch (e: Exception) {
            DeletionResult.Error(e.localizedMessage ?: "Failed to delete video")
        }
    }

    /** Removes favourites, history, playlists, etc. for a video. Call only once the file is really deleted. */
    suspend fun cleanupVideoRecords(videoUri: String) = withContext(Dispatchers.IO) {
        favoriteDao.removeFavorite(videoUri)
        watchHistoryDao.removeWatchHistory(videoUri)
        playlistDao.removeVideoFromAllPlaylists(videoUri)
        watchLaterDao.removeWatchLater(videoUri)
        queueDao.removeVideoFromQueue(videoUri)
        videoBookmarkDao.removeBookmarksForVideo(videoUri)
    }

    private data class SampleVideoSpec(
        val id: Long,
        val title: String,
        val durationMs: Long,
        val sizeBytes: Long,
        val width: Int,
        val height: Int,
        val folder: String,
        val mime: String
    )
}

sealed class DeletionResult {
    object Success : DeletionResult()
    data class RequiresPermission(val intentSender: IntentSender) : DeletionResult()
    data class Error(val message: String) : DeletionResult()
}
