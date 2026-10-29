package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.data.local.dao.FavoriteDao
import com.example.data.local.dao.WatchHistoryDao
import com.example.data.local.entity.FavoriteVideo
import com.example.data.local.entity.WatchHistory

import com.example.data.local.dao.PlaylistDao
import com.example.data.local.dao.QueueDao
import com.example.data.local.dao.VideoBookmarkDao
import com.example.data.local.dao.WatchLaterDao
import com.example.data.local.entity.PlaylistEntity
import com.example.data.local.entity.PlaylistVideoEntity
import com.example.data.local.entity.QueueItemEntity
import com.example.data.local.entity.VideoBookmarkEntity
import com.example.data.local.entity.WatchLaterEntity

@Database(
    entities = [
        FavoriteVideo::class, 
        WatchHistory::class, 
        PlaylistEntity::class, 
        PlaylistVideoEntity::class,
        WatchLaterEntity::class,
        QueueItemEntity::class,
        VideoBookmarkEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun watchLaterDao(): WatchLaterDao
    abstract fun queueDao(): QueueDao
    abstract fun videoBookmarkDao(): VideoBookmarkDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "offscroll_database"
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
