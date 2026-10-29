package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "watch_later")
data class WatchLaterEntity(
    @PrimaryKey val videoUri: String,
    val addedAt: Long = System.currentTimeMillis()
)
