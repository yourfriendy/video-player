package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.QueueItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QueueDao {
    @Query("SELECT * FROM playback_queue ORDER BY position ASC")
    fun getQueue(): Flow<List<QueueItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertQueueItem(item: QueueItemEntity)

    @Query("DELETE FROM playback_queue WHERE id = :id")
    suspend fun removeQueueItem(id: Long)

    @Query("DELETE FROM playback_queue")
    suspend fun clearQueue()

    @Query("DELETE FROM playback_queue WHERE videoUri = :videoUri")
    suspend fun removeVideoFromQueue(videoUri: String)
}
