package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.example.data.local.entity.PlaylistEntity
import com.example.data.local.entity.PlaylistVideoEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY updatedAt DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Update
    suspend fun updatePlaylist(playlist: PlaylistEntity)

    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)

    @Query("SELECT * FROM playlist_videos WHERE playlistId = :playlistId ORDER BY position ASC")
    fun getVideosForPlaylist(playlistId: Long): Flow<List<PlaylistVideoEntity>>

    @Query("SELECT * FROM playlist_videos")
    fun getAllPlaylistVideos(): Flow<List<PlaylistVideoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistVideo(playlistVideo: PlaylistVideoEntity)

    @Delete
    suspend fun deletePlaylistVideo(playlistVideo: PlaylistVideoEntity)

    @Query("DELETE FROM playlist_videos WHERE videoUri = :videoUri")
    suspend fun removeVideoFromAllPlaylists(videoUri: String)
}
