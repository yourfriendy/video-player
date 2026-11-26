package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.music.MusicPlayerManager
import com.example.data.music.MusicPlayerState
import com.example.data.music.MusicRepository
import com.example.ui.model.AlbumGroup
import com.example.ui.model.ArtistGroup
import com.example.ui.model.MusicTab
import com.example.ui.model.Song
import com.example.ui.model.countLabel
import com.example.ui.model.formatSongTime
import com.example.ui.theme.Ds
import com.example.ui.theme.GlassBackground
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.bouncyClickable
import com.example.ui.theme.rememberMotionEnabled
import kotlinx.coroutines.delay

private val ArtGradients = listOf(
    listOf(Color(0xFF7C3AED), Color(0xFFDB2777)),
    listOf(Color(0xFF0EA5E9), Color(0xFF6366F1)),
    listOf(Color(0xFFF97316), Color(0xFFE11D48)),
    listOf(Color(0xFF10B981), Color(0xFF0EA5E9)),
    listOf(Color(0xFFF59E0B), Color(0xFFEF4444)),
    listOf(Color(0xFF8B5CF6), Color(0xFF22D3EE))
)

/** A colour pair for an album, always the same for the same album, used when it has no cover picture. */
internal fun artColors(albumId: Long): List<Color> =
    ArtGradients[albumId.mod(ArtGradients.size.toLong()).toInt()]

/** The album cover, or a colourful tile with a note while there is none. */
@Composable
internal fun AlbumArt(albumId: Long, modifier: Modifier = Modifier, corner: androidx.compose.ui.unit.Dp = Ds.Radius.Md) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(corner)
    Box(
        modifier = modifier.clip(shape).background(Brush.linearGradient(artColors(albumId))),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color(0x66FFFFFF), modifier = Modifier.fillMaxSize(0.4f))
        if (albumId > 0) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(MusicRepository.artUri(albumId)).crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/** Three little bars that dance while the song plays. */
@Composable
internal fun EqualizerBars(playing: Boolean, modifier: Modifier = Modifier, color: Color = Color.White) {
    val motion = rememberMotionEnabled()
    val transition = rememberInfiniteTransition(label = "equalizer")
    val a by transition.animateFloat(0.3f, 1f, infiniteRepeatable(tween(520, easing = LinearEasing), RepeatMode.Reverse), label = "eq_a")
    val b by transition.animateFloat(0.5f, 1f, infiniteRepeatable(tween(380, easing = LinearEasing), RepeatMode.Reverse), label = "eq_b")
    val c by transition.animateFloat(0.25f, 1f, infiniteRepeatable(tween(640, easing = LinearEasing), RepeatMode.Reverse), label = "eq_c")
    val heights = if (playing && motion) listOf(a, b, c) else listOf(0.4f, 0.7f, 0.5f)
    Row(
        modifier = modifier.height(16.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        heights.forEach { h -> Box(Modifier.width(3.dp).fillMaxHeight(h).background(color, CircleShape)) }
    }
}

/** The song's current position, refreshed a few times a second while it plays. */
@Composable
internal fun rememberPlaybackPosition(isPlaying: Boolean, songKey: Any?): State<Long> =
    produceState(MusicPlayerManager.positionMs(), isPlaying, songKey) {
        while (true) {
            value = MusicPlayerManager.positionMs()
            delay(if (isPlaying) 300L else 1000L)
        }
    }

/** One song in a list: cover, title, artist and album, length and a heart. The playing one is highlighted. */
@Composable
internal fun SongRow(
    song: Song,
    isCurrent: Boolean,
    isPlaying: Boolean,
    liked: Boolean,
    onClick: () -> Unit,
    onToggleLike: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(Ds.Radius.Lg))
            .background(if (isCurrent) Ds.Palette.Pink.copy(alpha = 0.10f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(48.dp)) {
            AlbumArt(song.albumId, Modifier.fillMaxSize())
            if (isCurrent) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color(0x99000000), RoundedCornerShape(Ds.Radius.Md)),
                    contentAlignment = Alignment.Center
                ) { EqualizerBars(playing = isPlaying) }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                song.title,
                color = if (isCurrent) Ds.Palette.PinkSoft else Color.White,
                fontSize = Ds.Type.Body,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                song.artist + " · " + song.album,
                color = Ds.Palette.Muted,
                fontSize = Ds.Type.Small,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(formatSongTime(song.durationMs), color = Ds.Palette.Muted, fontSize = Ds.Type.Caption, modifier = Modifier.padding(start = 8.dp))
        IconButton(onClick = onToggleLike, modifier = Modifier.size(44.dp)) {
            Icon(
                if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = if (liked) "Remove from liked" else "Like",
                tint = if (liked) Ds.Palette.PinkSoft else Ds.Palette.Muted,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
internal fun AlbumCard(album: AlbumGroup, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .bouncyClickable(pressedScale = 0.96f, onClick = onClick)
            .fillMaxWidth()
    ) {
        AlbumArt(album.albumId, Modifier.fillMaxWidth().aspectRatio(1f), corner = Ds.Radius.Lg)
        Text(
            album.name, color = Color.White, fontSize = Ds.Type.Body, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            album.artist + " · " + countLabel(album.songs.size, "song", "songs"),
            color = Ds.Palette.Muted, fontSize = Ds.Type.Small, maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun ArtistRow(artist: ArtistGroup, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = artColors(artist.name.hashCode().toLong())
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(Ds.Radius.Lg))
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(48.dp).background(Brush.linearGradient(colors), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                artist.name.firstOrNull()?.uppercase() ?: "?",
                color = Color.White, fontSize = Ds.Type.Title, fontWeight = FontWeight.ExtraBold
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(artist.name, color = Color.White, fontSize = Ds.Type.Body, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                countLabel(artist.songs.size, "song", "songs") + " · " + countLabel(artist.albumCount, "album", "albums"),
                color = Ds.Palette.Muted, fontSize = Ds.Type.Small
            )
        }
    }
}

/** Songs, Albums, Artists and Liked as pills. The chosen one is filled with the app's sunset gradient. */
@Composable
internal fun MusicTabs(selected: MusicTab, onSelect: (MusicTab) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MusicTab.entries.forEach { tab ->
            val isSelected = tab == selected
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .clip(CircleShape)
                    .then(
                        if (isSelected) Modifier.background(Brush.linearGradient(listOf(Ds.Palette.Orange, Ds.Palette.Pink, Ds.Palette.Violet)))
                        else Modifier.background(GlassBackground).border(1.dp, GlassBorder, CircleShape)
                    )
                    .clickable { onSelect(tab) }
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(tab.label, color = Color.White, fontSize = Ds.Type.Label, fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold)
            }
        }
    }
}

/** The small player docked at the bottom: tap it to open Now playing. */
@Composable
internal fun MiniPlayer(
    player: MusicPlayerState,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    val current = player.current ?: return
    val position by rememberPlaybackPosition(player.isPlaying, current.songId)
    val ratio = if (player.durationMs > 0) (position.toFloat() / player.durationMs).coerceIn(0f, 1f) else 0f
    val shape = RoundedCornerShape(Ds.Radius.Xl)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0xE61A1B26))
            .border(1.dp, Brush.verticalGradient(listOf(Color(0x66FFFFFF), Color(0x14FFFFFF))), shape)
            .clickable(onClick = onOpen)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AlbumArt(current.albumId, Modifier.size(44.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(current.title, color = Color.White, fontSize = Ds.Type.Body, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(current.artist, color = Ds.Palette.Muted, fontSize = Ds.Type.Small, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = { MusicPlayerManager.togglePlayPause() }, modifier = Modifier.size(48.dp)) {
                Icon(
                    if (player.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (player.isPlaying) "Pause" else "Play",
                    tint = Color.White, modifier = Modifier.size(28.dp)
                )
            }
            IconButton(onClick = { MusicPlayerManager.next() }, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.SkipNext, contentDescription = "Next song", tint = Color.White, modifier = Modifier.size(26.dp))
            }
        }
        // thin progress line along the bottom edge
        Box(modifier = Modifier.fillMaxWidth().height(3.dp).background(Color(0x22FFFFFF))) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(ratio)
                    .background(Brush.horizontalGradient(listOf(Ds.Palette.Orange, Ds.Palette.Pink)))
            )
        }
    }
}
