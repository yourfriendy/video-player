package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "playlist_videos",
    primaryKeys = ["playlistId", "videoUri"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["playlistId"])]
)
data class PlaylistVideoEntity(
    val playlistId: Long,
    val videoUri: String,
    val position: Int,
    val addedAt: Long = System.currentTimeMillis()
)
