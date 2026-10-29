package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.WatchLaterEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchLaterDao {
    @Query("SELECT * FROM watch_later ORDER BY addedAt DESC")
    fun getAllWatchLater(): Flow<List<WatchLaterEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addWatchLater(watchLater: WatchLaterEntity)

    @Query("DELETE FROM watch_later WHERE videoUri = :videoUri")
    suspend fun removeWatchLater(videoUri: String)
}
