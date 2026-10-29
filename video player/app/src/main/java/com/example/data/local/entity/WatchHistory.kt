package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watch_history")
data class WatchHistory(
    @PrimaryKey val videoUri: String,
    val lastPlayedAt: Long = System.currentTimeMillis(),
    val playCount: Int = 1,
    val lastPositionMs: Long = 0L
)
