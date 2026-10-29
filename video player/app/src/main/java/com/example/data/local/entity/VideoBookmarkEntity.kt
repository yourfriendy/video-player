package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "video_bookmarks")
data class VideoBookmarkEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val videoUri: String,
    val timestampMs: Long,
    val title: String? = null,
    val emoji: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)
