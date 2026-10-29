package com.example.data.local

import android.content.Context
import android.content.SharedPreferences
import com.example.ui.model.SortOption
import com.example.ui.model.VideoDisplayMode

/**
 * Persists and restores user settings across application restarts.
 */
class SettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    companion object {
        private const val PREFS_NAME = "reels_player_settings"
        private const val KEY_DISPLAY_MODE = "display_mode"
        private const val KEY_PLAYBACK_SPEED = "playback_speed"
        private const val KEY_IS_MUTED = "is_muted"
        private const val KEY_SORT_OPTION = "sort_option"
        private const val KEY_IS_GRID_VIEW = "is_grid_view"
        private const val KEY_IS_SHUFFLE_MODE = "is_shuffle_mode"
        private const val KEY_IS_LOOP_MODE = "is_loop_mode"
        private const val KEY_RECENT_SEARCHES = "recent_searches"
    }

    fun getDisplayMode(): VideoDisplayMode {
        val name = prefs.getString(KEY_DISPLAY_MODE, VideoDisplayMode.CROP.name)
        return try {
            VideoDisplayMode.valueOf(name ?: VideoDisplayMode.CROP.name)
        } catch (e: Exception) {
            VideoDisplayMode.CROP
        }
    }

    fun setDisplayMode(mode: VideoDisplayMode) {
        prefs.edit().putString(KEY_DISPLAY_MODE, mode.name).apply()
    }

    fun getPlaybackSpeed(): Float {
        return prefs.getFloat(KEY_PLAYBACK_SPEED, 1.0f)
    }

    fun setPlaybackSpeed(speed: Float) {
        prefs.edit().putFloat(KEY_PLAYBACK_SPEED, speed).apply()
    }

    fun isMuted(): Boolean {
        return prefs.getBoolean(KEY_IS_MUTED, false)
    }

    fun setMuted(muted: Boolean) {
        prefs.edit().putBoolean(KEY_IS_MUTED, muted).apply()
    }

    fun getSortOption(): SortOption {
        val name = prefs.getString(KEY_SORT_OPTION, SortOption.DATE_NEWEST.name)
        return try {
            SortOption.valueOf(name ?: SortOption.DATE_NEWEST.name)
        } catch (e: Exception) {
            SortOption.DATE_NEWEST
        }
    }

    fun setSortOption(sort: SortOption) {
        prefs.edit().putString(KEY_SORT_OPTION, sort.name).apply()
    }

    fun isGridView(): Boolean {
        return prefs.getBoolean(KEY_IS_GRID_VIEW, false)
    }

    fun setGridView(isGrid: Boolean) {
        prefs.edit().putBoolean(KEY_IS_GRID_VIEW, isGrid).apply()
    }

    fun isShuffleMode(): Boolean {
        return prefs.getBoolean(KEY_IS_SHUFFLE_MODE, false)
    }

    fun setShuffleMode(isShuffle: Boolean) {
        prefs.edit().putBoolean(KEY_IS_SHUFFLE_MODE, isShuffle).apply()
    }

    fun isLoopMode(): Boolean {
        return prefs.getBoolean(KEY_IS_LOOP_MODE, true)
    }

    fun setLoopMode(isLoop: Boolean) {
        prefs.edit().putBoolean(KEY_IS_LOOP_MODE, isLoop).apply()
    }

    fun getRecentSearches(): List<String> =
        (prefs.getString(KEY_RECENT_SEARCHES, "") ?: "").split('\n').filter { it.isNotBlank() }

    fun setRecentSearches(list: List<String>) {
        prefs.edit().putString(KEY_RECENT_SEARCHES, list.joinToString("\n")).apply()
    }
}
