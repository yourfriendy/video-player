package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.music.MusicPlayerManager
import com.example.data.music.MusicPlayerState
import com.example.ui.model.RepeatKind
import com.example.ui.model.SLEEP_TIMER_MINUTES
import com.example.ui.model.formatSongTime
import com.example.ui.theme.Ds
import com.example.ui.theme.bouncyClickable
import kotlinx.coroutines.delay

/**
 * The full player: a big cover that softly breathes while the music plays, the song's name, a seek bar, and the
 * controls (shuffle, previous, play/pause, next, repeat), with like, sleep timer and the queue underneath.
 * The background takes its colours from the album.
 */
@Composable
internal fun MusicNowPlayingScreen(
    player: MusicPlayerState,
    liked: Boolean,
    onClose: () -> Unit,
    onToggleLike: () -> Unit,
    onOpenQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val current = player.current ?: return
    val colors = artColors(current.albumId)
    val top by animateColorAsState(colors[0].copy(alpha = 0.55f), tween(600), label = "np_top")
    val bottom by animateColorAsState(colors[1].copy(alpha = 0.25f), tween(600), label = "np_bottom")
    val artScale by animateFloatAsState(
        targetValue = if (player.isPlaying) 1f else 0.9f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessLow),
        label = "np_art_scale"
    )
    val position by rememberPlaybackPosition(player.isPlaying, current.songId)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF090A10))
            .background(Brush.verticalGradient(listOf(top, bottom, Color.Transparent)))
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Close player", tint = Color.White, modifier = Modifier.size(30.dp))
            }
            Text(
                "Now playing", color = Color(0xCCFFFFFF), fontSize = Ds.Type.Label, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onOpenQueue, modifier = Modifier.size(48.dp)) {
                Icon(Icons.Default.QueueMusic, contentDescription = "Play queue", tint = Color.White)
            }
        }

        BoxWithConstraints(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val side = if (maxWidth < maxHeight) maxWidth else maxHeight
            AlbumArt(
                albumId = current.albumId,
                corner = Ds.Radius.Sheet,
                modifier = Modifier
                    .size(side)
                    .graphicsLayer { scaleX = artScale; scaleY = artScale }
                    .shadow(24.dp, RoundedCornerShape(Ds.Radius.Sheet), ambientColor = colors[0], spotColor = colors[0])
            )
        }

        Spacer(Modifier.height(20.dp))
        Text(
            current.title, color = Color.White, fontSize = Ds.Type.TitleLarge, fontWeight = FontWeight.ExtraBold,
            maxLines = 2, overflow = TextOverflow.Ellipsis
        )
        Text(
            current.artist, color = Color(0xCCFFFFFF), fontSize = Ds.Type.Body,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp)
        )

        NowPlayingSeekBar(position = position, durationMs = player.durationMs)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ControlIcon(
                Icons.Default.Shuffle, "Shuffle", onClick = { MusicPlayerManager.toggleShuffle() },
                tint = if (player.shuffle) Ds.Palette.PinkSoft else Color(0x99FFFFFF)
            )
            ControlIcon(Icons.Default.SkipPrevious, "Previous song", onClick = { MusicPlayerManager.previous() }, size = 40)
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .bouncyClickable(pressedScale = 0.92f, onClick = { MusicPlayerManager.togglePlayPause() })
                    .shadow(16.dp, CircleShape, ambientColor = Ds.Palette.Pink, spotColor = Ds.Palette.Pink)
                    .background(Brush.linearGradient(listOf(Ds.Palette.Orange, Ds.Palette.Pink, Ds.Palette.Violet)), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (player.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (player.isPlaying) "Pause" else "Play",
                    tint = Color.White, modifier = Modifier.size(40.dp)
                )
            }
            ControlIcon(Icons.Default.SkipNext, "Next song", onClick = { MusicPlayerManager.next() }, size = 40)
            ControlIcon(
                if (player.repeat == RepeatKind.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                when (player.repeat) {
                    RepeatKind.OFF -> "Repeat is off"
                    RepeatKind.ALL -> "Repeating all songs"
                    RepeatKind.ONE -> "Repeating this song"
                },
                onClick = { MusicPlayerManager.cycleRepeat() },
                tint = if (player.repeat == RepeatKind.OFF) Color(0x99FFFFFF) else Ds.Palette.PinkSoft
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ControlIcon(
                if (liked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                if (liked) "Remove from liked" else "Like this song",
                onClick = onToggleLike,
                tint = if (liked) Ds.Palette.PinkSoft else Color(0xCCFFFFFF)
            )
            SleepTimerButton(player.sleepEndsAtMs)
        }
    }
}

@Composable
private fun ControlIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    tint: Color = Color.White,
    size: Int = 26
) {
    IconButton(onClick = onClick, modifier = Modifier.size(52.dp)) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(size.dp))
    }
}

@Composable
private fun NowPlayingSeekBar(position: Long, durationMs: Long) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val ratio = if (durationMs > 0) (position.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val shownMs = if (dragging) (dragValue * durationMs).toLong() else position
    Column(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Slider(
            value = if (dragging) dragValue else ratio,
            onValueChange = {
                dragging = true
                dragValue = it
            },
            onValueChangeFinished = {
                MusicPlayerManager.seekTo((dragValue * durationMs).toLong())
                dragging = false
            },
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Ds.Palette.Pink,
                inactiveTrackColor = Color(0x33FFFFFF)
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatSongTime(shownMs), color = Color(0xCCFFFFFF), fontSize = Ds.Type.Small)
            Text("-" + formatSongTime((durationMs - shownMs).coerceAtLeast(0L)), color = Color(0x99FFFFFF), fontSize = Ds.Type.Small)
        }
    }
}

/** A moon button: pick how long until the music stops by itself. Shows the minutes left while it is set. */
@Composable
private fun SleepTimerButton(endsAtMs: Long) {
    var open by remember { mutableStateOf(false) }
    val minutesLeft by produceState(0, endsAtMs) {
        while (true) {
            value = if (endsAtMs > 0) ((endsAtMs - System.currentTimeMillis()) / 60_000L + 1).toInt().coerceAtLeast(0) else 0
            delay(15_000L)
        }
    }
    val active = endsAtMs > 0
    Box {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .bouncyClickable(pressedScale = 0.95f, onClick = { open = true })
                .background(if (active) Ds.Palette.Violet.copy(alpha = 0.25f) else Color(0x1AFFFFFF), CircleShape)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Bedtime, contentDescription = "Sleep timer", tint = if (active) Ds.Palette.VioletSoft else Color.White, modifier = Modifier.size(20.dp))
            Text(
                if (active) "  $minutesLeft min left" else "  Sleep timer",
                color = Color.White, fontSize = Ds.Type.Label, fontWeight = FontWeight.SemiBold
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SLEEP_TIMER_MINUTES.forEach { minutes ->
                DropdownMenuItem(
                    text = { Text("Stop in $minutes minutes") },
                    onClick = {
                        MusicPlayerManager.setSleepTimer(minutes)
                        open = false
                    }
                )
            }
            if (active) {
                DropdownMenuItem(
                    text = { Text("Turn timer off") },
                    onClick = {
                        MusicPlayerManager.setSleepTimer(null)
                        open = false
                    }
                )
            }
        }
    }
}

/** What is queued: tap a song to jump to it, the X takes one out. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun MusicQueueSheet(player: MusicPlayerState, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF12131C),
        contentColor = Color.White
    ) {
        Text(
            "Up next", color = Color.White, fontSize = Ds.Type.Title, fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
        LazyColumn(
            modifier = Modifier.fillMaxWidth().height(420.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        ) {
            itemsIndexed(player.queue) { index, entry ->
                val isCurrent = index == player.currentIndex
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(Ds.Radius.Lg))
                        .background(if (isCurrent) Ds.Palette.Pink.copy(alpha = 0.10f) else Color.Transparent)
                        .bouncyClickable(pressedScale = 0.98f, onClick = { MusicPlayerManager.jumpTo(index) })
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AlbumArt(entry.albumId, Modifier.size(40.dp))
                    Spacer(Modifier.size(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            entry.title, color = if (isCurrent) Ds.Palette.PinkSoft else Color.White,
                            fontSize = Ds.Type.Body, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Text(entry.artist, color = Ds.Palette.Muted, fontSize = Ds.Type.Small, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    if (!isCurrent) {
                        IconButton(onClick = { MusicPlayerManager.removeAt(index) }, modifier = Modifier.size(44.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Remove from queue", tint = Ds.Palette.Muted, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }
        }
    }
}
