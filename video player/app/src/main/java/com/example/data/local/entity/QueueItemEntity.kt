package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playback_queue")
data class QueueItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val videoUri: String,
    val position: Int,
    val addedAt: Long = System.currentTimeMillis()
)
