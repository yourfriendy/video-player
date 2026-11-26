package com.example.ui.model

import java.util.Locale

/** One music file on the phone. Plain data (no Android types) so the sorting and grouping can be tested. */
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val dateAddedSec: Long,
    val track: Int
)

data class AlbumGroup(val albumId: Long, val name: String, val artist: String, val songs: List<Song>)

data class ArtistGroup(val name: String, val songs: List<Song>) {
    val albumCount: Int get() = songs.map { it.albumId }.distinct().size
}

enum class MusicTab(val label: String) {
    SONGS("Songs"),
    ALBUMS("Albums"),
    ARTISTS("Artists"),
    LIKED("Liked")
}

enum class MusicSort(val label: String) {
    TITLE("A to Z"),
    RECENT("Recently added")
}

enum class RepeatKind { OFF, ALL, ONE }

/** Sleep timer choices, in minutes. */
val SLEEP_TIMER_MINUTES = listOf(15, 30, 45, 60)

fun sortSongs(songs: List<Song>, sort: MusicSort): List<Song> = when (sort) {
    MusicSort.TITLE -> songs.sortedBy { it.title.lowercase(Locale.ROOT) }
    MusicSort.RECENT -> songs.sortedByDescending { it.dateAddedSec }
}

/** Every word typed must appear somewhere in the title, artist or album (any case). */
fun filterSongs(songs: List<Song>, query: String): List<Song> {
    val words = query.trim().lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (words.isEmpty()) return songs
    return songs.filter { song ->
        val haystack = (song.title + " " + song.artist + " " + song.album).lowercase(Locale.ROOT)
        words.all { haystack.contains(it) }
    }
}

fun groupAlbums(songs: List<Song>): List<AlbumGroup> =
    songs.groupBy { it.albumId to it.album }
        .map { (key, list) ->
            val artist = list.groupingBy { it.artist }.eachCount().maxByOrNull { it.value }?.key ?: ""
            AlbumGroup(
                albumId = key.first,
                name = key.second,
                artist = artist,
                songs = list.sortedWith(compareBy({ it.track % 1000 }, { it.title.lowercase(Locale.ROOT) }))
            )
        }
        .sortedBy { it.name.lowercase(Locale.ROOT) }

fun groupArtists(songs: List<Song>): List<ArtistGroup> =
    songs.groupBy { it.artist }
        .map { (name, list) -> ArtistGroup(name, list.sortedBy { it.title.lowercase(Locale.ROOT) }) }
        .sortedBy { it.name.lowercase(Locale.ROOT) }

/** "12 min" or "1 h 5 min", for the header of a list of songs. */
fun formatTotalDuration(ms: Long): String {
    val minutes = (ms / 60_000L).toInt()
    return if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"
}

/** "3:05" for a song length or position. */
fun formatSongTime(ms: Long): String = formatMovieTime(ms)

/** The count with its noun: "1 song", "12 songs". */
fun countLabel(count: Int, one: String, many: String): String = if (count == 1) "1 $one" else "$count $many"
