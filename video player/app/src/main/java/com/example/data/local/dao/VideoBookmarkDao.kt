package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.VideoBookmarkEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VideoBookmarkDao {
    @Query("SELECT * FROM video_bookmarks WHERE videoUri = :videoUri ORDER BY timestampMs ASC")
    fun getBookmarksForVideo(videoUri: String): Flow<List<VideoBookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: VideoBookmarkEntity)

    @Delete
    suspend fun deleteBookmark(bookmark: VideoBookmarkEntity)

    @Query("DELETE FROM video_bookmarks WHERE videoUri = :videoUri")
    suspend fun removeBookmarksForVideo(videoUri: String)
}
