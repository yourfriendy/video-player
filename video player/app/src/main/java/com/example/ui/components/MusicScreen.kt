package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.music.MusicPlayerManager
import com.example.data.music.MusicRepository
import com.example.ui.model.MusicSort
import com.example.ui.model.MusicTab
import com.example.ui.model.Song
import com.example.ui.model.countLabel
import com.example.ui.model.filterSongs
import com.example.ui.model.formatTotalDuration
import com.example.ui.model.groupAlbums
import com.example.ui.model.groupArtists
import com.example.ui.model.sortSongs
import com.example.ui.theme.Ds
import com.example.ui.theme.GlassBackground
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.auroraGlow
import com.example.ui.theme.bouncyClickable
import com.example.ui.viewmodel.MusicViewModel

/**
 * The music player tab: your songs, albums, artists and liked songs, a mini player, and the full Now playing screen.
 * Music plays through a background service, so it carries on with the screen off. It plays the music files already on
 * the phone; nothing is streamed or uploaded.
 */
@Composable
fun MusicScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MusicViewModel = viewModel()
) {
    val context = LocalContext.current
    val ui by viewModel.ui.collectAsState()
    val player by MusicPlayerManager.state.collectAsState()
    LaunchedEffect(Unit) { viewModel.attach(context) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onPermissionResult(granted)
    }

    BackHandler(enabled = ui.queueOpen) { viewModel.setQueueOpen(false) }
    BackHandler(enabled = ui.nowPlayingOpen && !ui.queueOpen) { viewModel.setNowPlayingOpen(false) }
    BackHandler(enabled = !ui.nowPlayingOpen && (ui.openAlbumId != null || ui.openArtist != null)) { viewModel.closeDetail() }

    val allAlbums = remember(ui.songs) { groupAlbums(ui.songs) }
    val allArtists = remember(ui.songs) { groupArtists(ui.songs) }
    val visibleSongs = remember(ui.songs, ui.query, ui.sort) { filterSongs(sortSongs(ui.songs, ui.sort), ui.query) }
    val visibleAlbums = remember(ui.songs, ui.query) { groupAlbums(filterSongs(ui.songs, ui.query)) }
    val visibleArtists = remember(ui.songs, ui.query) { groupArtists(filterSongs(ui.songs, ui.query)) }
    val likedSongs = remember(visibleSongs, ui.likedIds) { visibleSongs.filter { it.id in ui.likedIds } }
    val currentSongId = player.current?.songId
    val detailAlbum = ui.openAlbumId?.let { id -> allAlbums.firstOrNull { it.albumId == id } }
    val detailArtist = ui.openArtist?.let { name -> allArtists.firstOrNull { it.name == name } }
    val listBottomPadding = if (player.hasQueue) 104.dp else 24.dp

    // Leaving the music tab while a song plays asks first, so the videos never start by surprise
    var showLeaveDialog by remember { mutableStateOf(false) }
    val requestLeave: () -> Unit = {
        if (player.isPlaying) {
            showLeaveDialog = true
        } else {
            onBack()
        }
    }
    BackHandler(enabled = !ui.nowPlayingOpen && !ui.queueOpen && ui.openAlbumId == null && ui.openArtist == null && !showLeaveDialog) { requestLeave() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF090A10))
            .auroraGlow()
            .testTag("music_screen")
    ) {
        Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
            MusicHeader(
                title = detailAlbum?.name ?: detailArtist?.name ?: "Music",
                isDetail = detailAlbum != null || detailArtist != null,
                searchOpen = ui.searchOpen,
                onBack = { if (detailAlbum != null || detailArtist != null) viewModel.closeDetail() else requestLeave() },
                onSearch = { viewModel.toggleSearch() },
                onRefresh = { viewModel.reload() },
                showTools = ui.hasPermission && ui.songs.isNotEmpty() && detailAlbum == null && detailArtist == null
            )
            if (ui.searchOpen) MusicSearchField(ui.query) { viewModel.setQuery(it) }

            when {
                ui.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = Ds.Palette.Pink)
                }
                !ui.hasPermission -> MusicPermissionGate(
                    onRequest = { permissionLauncher.launch(MusicRepository.audioPermission()) }
                )
                ui.songs.isEmpty() -> MusicEmptyState(
                    title = "No music found",
                    text = "Songs saved on your phone appear here by themselves. Copy some in, then tap Refresh."
                )
                detailAlbum != null -> SongList(
                    songs = detailAlbum.songs, header = countLabel(detailAlbum.songs.size, "song", "songs") + " · " + detailAlbum.artist,
                    currentSongId = currentSongId, isPlaying = player.isPlaying, likedIds = ui.likedIds,
                    bottomPadding = listBottomPadding,
                    onPlay = { index -> viewModel.play(detailAlbum.songs, index) },
                    onShuffle = { viewModel.shuffle(detailAlbum.songs) },
                    onToggleLike = { viewModel.toggleLike(it) }
                )
                detailArtist != null -> SongList(
                    songs = detailArtist.songs, header = countLabel(detailArtist.songs.size, "song", "songs") + " · " + countLabel(detailArtist.albumCount, "album", "albums"),
                    currentSongId = currentSongId, isPlaying = player.isPlaying, likedIds = ui.likedIds,
                    bottomPadding = listBottomPadding,
                    onPlay = { index -> viewModel.play(detailArtist.songs, index) },
                    onShuffle = { viewModel.shuffle(detailArtist.songs) },
                    onToggleLike = { viewModel.toggleLike(it) }
                )
                else -> Column(modifier = Modifier.fillMaxSize()) {
                    MusicTabs(ui.tab, { viewModel.setTab(it) }, Modifier.padding(vertical = 8.dp))
                    when (ui.tab) {
                        MusicTab.SONGS -> SongList(
                            songs = visibleSongs,
                            header = countLabel(visibleSongs.size, "song", "songs") + " · " + formatTotalDuration(visibleSongs.sumOf { it.durationMs }),
                            currentSongId = currentSongId, isPlaying = player.isPlaying, likedIds = ui.likedIds,
                            bottomPadding = listBottomPadding,
                            sortLabel = ui.sort.label,
                            onSort = { viewModel.setSort(if (ui.sort == MusicSort.TITLE) MusicSort.RECENT else MusicSort.TITLE) },
                            onPlay = { index -> viewModel.play(visibleSongs, index) },
                            onShuffle = { viewModel.shuffle(visibleSongs) },
                            onToggleLike = { viewModel.toggleLike(it) }
                        )
                        MusicTab.ALBUMS -> LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = listBottomPadding),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(18.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(visibleAlbums, key = { it.albumId.toString() + it.name }) { album ->
                                AlbumCard(album, onClick = { viewModel.openAlbum(album.albumId) })
                            }
                        }
                        MusicTab.ARTISTS -> LazyColumn(
                            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 8.dp, bottom = listBottomPadding),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            itemsIndexed(visibleArtists, key = { _, a -> a.name }) { _, artist ->
                                ArtistRow(artist, onClick = { viewModel.openArtist(artist.name) })
                            }
                        }
                        MusicTab.LIKED -> if (likedSongs.isEmpty()) {
                            MusicEmptyState(
                                title = "No liked songs yet",
                                text = "Tap the heart on a song you love. It will wait for you here."
                            )
                        } else {
                            SongList(
                                songs = likedSongs, header = countLabel(likedSongs.size, "song", "songs"),
                                currentSongId = currentSongId, isPlaying = player.isPlaying, likedIds = ui.likedIds,
                                bottomPadding = listBottomPadding,
                                onPlay = { index -> viewModel.play(likedSongs, index) },
                                onShuffle = { viewModel.shuffle(likedSongs) },
                                onToggleLike = { viewModel.toggleLike(it) }
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = player.hasQueue && !ui.nowPlayingOpen,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            MiniPlayer(
                player = player,
                onOpen = { viewModel.setNowPlayingOpen(true) },
                modifier = Modifier.navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }

        AnimatedVisibility(
            visible = ui.nowPlayingOpen && player.hasQueue,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut()
        ) {
            MusicNowPlayingScreen(
                player = player,
                liked = (player.current?.songId ?: -1L) in ui.likedIds,
                onClose = { viewModel.setNowPlayingOpen(false) },
                onToggleLike = { player.current?.songId?.let { viewModel.toggleLike(it) } },
                onOpenQueue = { viewModel.setQueueOpen(true) }
            )
        }

        if (ui.queueOpen) MusicQueueSheet(player = player, onDismiss = { viewModel.setQueueOpen(false) })

        if (showLeaveDialog) {
            MusicLeaveDialog(
                nowPlaying = player.current,
                onStay = { showLeaveDialog = false },
                onSwitchToVideos = {
                    showLeaveDialog = false
                    MusicPlayerManager.pauseNow()
                    onBack()
                }
            )
        }
    }
}

@Composable
private fun MusicHeader(
    title: String,
    isDetail: Boolean,
    searchOpen: Boolean,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onRefresh: () -> Unit,
    showTools: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.size(48.dp).testTag("music_back_button")) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
        if (!isDetail) {
            Box(
                modifier = Modifier.size(36.dp).background(Brush.linearGradient(listOf(Ds.Palette.Orange, Ds.Palette.Pink, Ds.Palette.Violet)), CircleShape),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.width(10.dp))
        }
        Text(
            title, color = Color.White, fontSize = Ds.Type.TitleLarge, fontWeight = FontWeight.ExtraBold,
            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
        )
        if (showTools) {
            IconButton(onClick = onRefresh, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh music", tint = Color(0xCCFFFFFF))
            }
            IconButton(onClick = onSearch, modifier = Modifier.size(48.dp)) {
                Icon(if (searchOpen) Icons.Default.Close else Icons.Default.Search, contentDescription = "Search music", tint = Color.White)
            }
        }
    }
}

@Composable
private fun MusicSearchField(query: String, onChange: (String) -> Unit) {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
            .height(48.dp)
            .clip(CircleShape)
            .background(GlassBackground)
            .border(1.dp, GlassBorder, CircleShape)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Search, contentDescription = null, tint = Ds.Palette.Muted, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) Text("Search songs, artists, albums", color = Color(0x80FFFFFF), fontSize = Ds.Type.Body)
            BasicTextField(
                value = query,
                onValueChange = onChange,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = Ds.Type.Body),
                cursorBrush = SolidColor(Ds.Palette.Pink),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth().testTag("music_search_field")
            )
        }
    }
}

/** A list of songs with a header: how many, the sort choice, and Play and Shuffle buttons. */
@Composable
private fun SongList(
    songs: List<Song>,
    header: String,
    currentSongId: Long?,
    isPlaying: Boolean,
    likedIds: Set<Long>,
    bottomPadding: androidx.compose.ui.unit.Dp,
    onPlay: (Int) -> Unit,
    onShuffle: () -> Unit,
    onToggleLike: (Long) -> Unit,
    sortLabel: String? = null,
    onSort: (() -> Unit)? = null
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 4.dp, bottom = bottomPadding),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(header, color = Ds.Palette.Muted, fontSize = Ds.Type.Small, modifier = Modifier.weight(1f))
                    if (sortLabel != null && onSort != null) {
                        Text(
                            sortLabel, color = Ds.Palette.PinkSoft, fontSize = Ds.Type.Small, fontWeight = FontWeight.Bold,
                            modifier = Modifier.clip(CircleShape).clickable(onClick = onSort).padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
                Row(modifier = Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PlayButton("Play", Icons.Default.PlayArrow, filled = true, modifier = Modifier.weight(1f)) { onPlay(0) }
                    PlayButton("Shuffle", Icons.Default.Shuffle, filled = false, modifier = Modifier.weight(1f), onClick = onShuffle)
                }
            }
        }
        itemsIndexed(songs, key = { _, s -> s.id }) { index, song ->
            SongRow(
                song = song,
                isCurrent = song.id == currentSongId,
                isPlaying = isPlaying,
                liked = song.id in likedIds,
                onClick = { onPlay(index) },
                onToggleLike = { onToggleLike(song.id) }
            )
        }
    }
}

@Composable
private fun PlayButton(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector, filled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = CircleShape
    Row(
        modifier = modifier
            .height(48.dp)
            .bouncyClickable(pressedScale = 0.96f, onClick = onClick)
            .clip(shape)
            .then(
                if (filled) Modifier.background(Brush.linearGradient(listOf(Ds.Palette.Orange, Ds.Palette.Pink, Ds.Palette.Violet)))
                else Modifier.background(GlassBackground).border(1.dp, GlassBorder, shape)
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        Text("  $text", color = Color.White, fontSize = Ds.Type.Body, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun MusicPermissionGate(onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier.size(96.dp).background(Brush.linearGradient(listOf(Ds.Palette.Orange, Ds.Palette.Pink, Ds.Palette.Violet)), CircleShape),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp)) }
        Spacer(Modifier.height(24.dp))
        Text("Your music, in one place", color = Color.White, fontSize = Ds.Type.TitleLarge, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Text(
            "OffScroll plays the music files on your phone. To list them, Android needs your OK to read audio (only music, never photos or contacts). Nothing is uploaded.",
            color = Ds.Palette.Muted, fontSize = Ds.Type.Body, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp, bottom = 24.dp)
        )
        GradientButton(text = "Show my music", onClick = onRequest, modifier = Modifier.fillMaxWidth(), icon = Icons.Default.MusicNote)
    }
}

@Composable
private fun MusicEmptyState(title: String, text: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color(0x33FFFFFF), modifier = Modifier.size(64.dp))
        Text(title, color = Color.White, fontSize = Ds.Type.Title, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
        Text(text, color = Ds.Palette.Muted, fontSize = Ds.Type.Body, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
    }
}
