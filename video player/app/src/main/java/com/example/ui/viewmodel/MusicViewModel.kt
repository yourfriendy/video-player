package com.example.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.music.MusicPlayerManager
import com.example.data.music.MusicRepository
import com.example.ui.model.MusicSort
import com.example.ui.model.MusicTab
import com.example.ui.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MusicUiState(
    val hasPermission: Boolean = false,
    val isLoading: Boolean = true,
    val songs: List<Song> = emptyList(),
    val tab: MusicTab = MusicTab.SONGS,
    val sort: MusicSort = MusicSort.TITLE,
    val query: String = "",
    val searchOpen: Boolean = false,
    val likedIds: Set<Long> = emptySet(),
    /** An album or artist opened from its tab to show its songs. */
    val openAlbumId: Long? = null,
    val openArtist: String? = null,
    val nowPlayingOpen: Boolean = false,
    val queueOpen: Boolean = false
)

/** The music library and what the music screens have open. Playing itself is MusicPlayerManager's job. */
class MusicViewModel : ViewModel() {
    private var repository: MusicRepository? = null
    private var prefsContext: Context? = null

    private val _ui = MutableStateFlow(MusicUiState())
    val ui: StateFlow<MusicUiState> = _ui.asStateFlow()

    /** Called by the screen with an application context (the view model itself holds no Activity). */
    fun attach(context: Context) {
        if (repository != null) return
        val app = context.applicationContext
        repository = MusicRepository(app)
        prefsContext = app
        _ui.update { it.copy(likedIds = readLiked(app), hasPermission = MusicRepository.hasPermission(app)) }
        MusicPlayerManager.connect()
        reload()
    }

    fun onPermissionResult(granted: Boolean) {
        _ui.update { it.copy(hasPermission = granted) }
        if (granted) reload()
    }

    fun reload() {
        val repo = repository ?: return
        val app = prefsContext ?: return
        val granted = MusicRepository.hasPermission(app)
        if (!granted) {
            _ui.update { it.copy(hasPermission = false, isLoading = false, songs = emptyList()) }
            return
        }
        viewModelScope.launch {
            val songs = repo.loadSongs()
            _ui.update { it.copy(hasPermission = true, isLoading = false, songs = songs) }
        }
    }

    fun setTab(tab: MusicTab) = _ui.update { it.copy(tab = tab, openAlbumId = null, openArtist = null) }
    fun setSort(sort: MusicSort) = _ui.update { it.copy(sort = sort) }
    fun setQuery(query: String) = _ui.update { it.copy(query = query) }
    fun toggleSearch() = _ui.update { it.copy(searchOpen = !it.searchOpen, query = if (it.searchOpen) "" else it.query) }
    fun openAlbum(id: Long) = _ui.update { it.copy(openAlbumId = id, openArtist = null) }
    fun openArtist(name: String) = _ui.update { it.copy(openArtist = name, openAlbumId = null) }
    fun closeDetail() = _ui.update { it.copy(openAlbumId = null, openArtist = null) }
    fun setNowPlayingOpen(open: Boolean) = _ui.update { it.copy(nowPlayingOpen = open, queueOpen = if (open) it.queueOpen else false) }
    fun setQueueOpen(open: Boolean) = _ui.update { it.copy(queueOpen = open) }

    fun toggleLike(songId: Long) {
        val app = prefsContext ?: return
        _ui.update { state ->
            val next = if (songId in state.likedIds) state.likedIds - songId else state.likedIds + songId
            writeLiked(app, next)
            state.copy(likedIds = next)
        }
    }

    /** Plays [list] starting at [index] and opens nothing else: the mini player appears by itself. */
    fun play(list: List<Song>, index: Int) = MusicPlayerManager.playSongs(list, index)

    fun shuffle(list: List<Song>) {
        if (list.isEmpty()) return
        MusicPlayerManager.playSongs(list, (list.indices).random(), shuffle = true)
    }

    private fun readLiked(context: Context): Set<Long> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(KEY_LIKED, emptySet())
            .orEmpty()
            .mapNotNull { it.toLongOrNull() }
            .toSet()

    private fun writeLiked(context: Context, ids: Set<Long>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putStringSet(KEY_LIKED, ids.map { it.toString() }.toSet()).apply()
    }

    private companion object {
        const val PREFS = "offscroll_music"
        const val KEY_LIKED = "liked_song_ids"
    }
}
