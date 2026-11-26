package com.example.data.music

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.example.ui.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Reads the phone's music files from MediaStore. Needs the music (audio) permission. */
class MusicRepository(private val context: Context) {

    suspend fun loadSongs(): List<Song> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.TRACK
        )
        // Music only (not ringtones, alarms or recordings), and nothing shorter than 15 seconds
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND ${MediaStore.Audio.Media.DURATION} >= 15000"
        val songs = ArrayList<Song>()
        try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val addedCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val trackCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                while (c.moveToNext()) {
                    songs.add(
                        Song(
                            id = c.getLong(idCol),
                            title = c.getString(titleCol)?.takeIf { it.isNotBlank() } ?: "Untitled",
                            artist = cleanTag(c.getString(artistCol), "Unknown artist"),
                            album = cleanTag(c.getString(albumCol), "Unknown album"),
                            albumId = c.getLong(albumIdCol),
                            durationMs = c.getLong(durationCol),
                            dateAddedSec = c.getLong(addedCol),
                            track = c.getInt(trackCol)
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            // Permission was taken away: show an empty library
        }
        songs
    }

    private fun cleanTag(value: String?, fallback: String): String =
        value?.takeIf { it.isNotBlank() && it != "<unknown>" } ?: fallback

    companion object {
        /** The permission that lets us read music files on this Android version. */
        fun audioPermission(): String =
            if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
            else Manifest.permission.READ_EXTERNAL_STORAGE

        fun hasPermission(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, audioPermission()) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED

        fun songUri(id: Long): Uri =
            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

        /** Cover art for an album (shared by all its songs). */
        fun artUri(albumId: Long): Uri =
            ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), albumId)

        /** What the player and the notification need to know about a song. */
        fun mediaItem(song: Song): MediaItem =
            MediaItem.Builder()
                .setMediaId(song.id.toString())
                .setUri(songUri(song.id))
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(song.title)
                        .setArtist(song.artist)
                        .setAlbumTitle(song.album)
                        .setArtworkUri(artUri(song.albumId))
                        .build()
                )
                .build()
    }
}
