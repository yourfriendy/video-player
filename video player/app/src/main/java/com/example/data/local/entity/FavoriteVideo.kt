package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorite_videos")
data class FavoriteVideo(
    @PrimaryKey val videoUri: String,
    val displayName: String = "",
    val dateFavorited: Long = System.currentTimeMillis()
)
