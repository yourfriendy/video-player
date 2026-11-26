package com.example.ui

import com.example.ui.model.MusicSort
import com.example.ui.model.Song
import com.example.ui.model.countLabel
import com.example.ui.model.filterSongs
import com.example.ui.model.formatTotalDuration
import com.example.ui.model.groupAlbums
import com.example.ui.model.groupArtists
import com.example.ui.model.sortSongs
import org.junit.Assert.assertEquals
import org.junit.Test

class MusicModelsTest {
    private fun song(id: Long, title: String, artist: String, album: String, albumId: Long, added: Long = id, track: Int = 0) =
        Song(id, title, artist, album, albumId, 180_000L, added, track)

    private val songs = listOf(
        song(1, "Zebra Song", "Beta", "Second Album", 20, added = 100, track = 2),
        song(2, "apple tune", "Alpha", "First Album", 10, added = 300, track = 2),
        song(3, "Mango", "Alpha", "First Album", 10, added = 200, track = 1),
        song(4, "Berry", "Beta", "Second Album", 20, added = 400, track = 1)
    )

    @Test
    fun titleSortIgnoresCase() {
        assertEquals(listOf(2L, 4L, 3L, 1L), sortSongs(songs, MusicSort.TITLE).map { it.id })
    }

    @Test
    fun recentSortPutsNewestFirst() {
        assertEquals(listOf(4L, 2L, 3L, 1L), sortSongs(songs, MusicSort.RECENT).map { it.id })
    }

    @Test
    fun searchNeedsEveryWordAnywhereInTitleArtistOrAlbum() {
        assertEquals(listOf(2L, 3L), filterSongs(songs, "alpha first").map { it.id })
        assertEquals(listOf(1L), filterSongs(songs, "ZEBRA").map { it.id })
        assertEquals(songs, filterSongs(songs, "   "))
        assertEquals(emptyList<Song>(), filterSongs(songs, "alpha second"))
    }

    @Test
    fun albumsAreSortedByNameAndSongsFollowTheTrackOrder() {
        val albums = groupAlbums(songs)
        assertEquals(listOf("First Album", "Second Album"), albums.map { it.name })
        assertEquals(listOf(3L, 2L), albums[0].songs.map { it.id })
        assertEquals("Alpha", albums[0].artist)
    }

    @Test
    fun artistsAreSortedAndCountTheirAlbums() {
        val artists = groupArtists(songs)
        assertEquals(listOf("Alpha", "Beta"), artists.map { it.name })
        assertEquals(1, artists[0].albumCount)
        assertEquals(2, artists[0].songs.size)
    }

    @Test
    fun totalDurationReadsNaturally() {
        assertEquals("12 min", formatTotalDuration(12 * 60_000L + 5_000L))
        assertEquals("1 h 5 min", formatTotalDuration(65 * 60_000L))
        assertEquals("0 min", formatTotalDuration(20_000L))
    }

    @Test
    fun countsUseTheRightNoun() {
        assertEquals("1 song", countLabel(1, "song", "songs"))
        assertEquals("12 songs", countLabel(12, "song", "songs"))
        assertEquals("0 songs", countLabel(0, "song", "songs"))
    }
}
