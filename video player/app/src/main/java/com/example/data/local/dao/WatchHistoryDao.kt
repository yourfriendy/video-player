package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.WatchHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchHistoryDao {
    @Query("SELECT * FROM watch_history ORDER BY lastPlayedAt DESC")
    fun getAllWatchHistory(): Flow<List<WatchHistory>>

    @Query("SELECT * FROM watch_history WHERE videoUri = :uri LIMIT 1")
    suspend fun getWatchHistory(uri: String): WatchHistory?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun recordWatch(watchHistory: WatchHistory)

    @Query("DELETE FROM watch_history WHERE videoUri = :uri")
    suspend fun removeWatchHistory(uri: String)

    @Query("DELETE FROM watch_history")
    suspend fun clearAllHistory()
}
