package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.data.local.entity.FavoriteVideo
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Query("SELECT * FROM favorite_videos ORDER BY dateFavorited DESC")
    fun getAllFavorites(): Flow<List<FavoriteVideo>>

    @Query("SELECT EXISTS(SELECT 1 FROM favorite_videos WHERE videoUri = :uri)")
    fun isFavorite(uri: String): Flow<Boolean>

    @Query("SELECT videoUri FROM favorite_videos")
    suspend fun getAllFavoriteUris(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addFavorite(favorite: FavoriteVideo)

    @Query("DELETE FROM favorite_videos WHERE videoUri = :uri")
    suspend fun removeFavorite(uri: String)
}
