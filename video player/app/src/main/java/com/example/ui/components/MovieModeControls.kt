package com.example.ui.components

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.exoplayer.ExoPlayer
import com.example.ui.model.MOVIE_SKIP_MS
import com.example.ui.model.MovieFit
import com.example.ui.model.formatMovieTime
import com.example.ui.model.skipTarget
import com.example.ui.theme.Ds
import com.example.ui.theme.GlassBorder
import kotlinx.coroutines.delay

/** One "skip 10 seconds" the double tap just did, so the screen can flash it. [stamp] makes repeats distinct. */
internal data class SkipEvent(val forward: Boolean, val stamp: Long)

internal fun Context.findHostActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findHostActivity()
    else -> null
}

/** A quick "+10 s" or "-10 s" badge on the side that was double tapped. */
@Composable
internal fun BoxScope.MovieSkipFlash(event: SkipEvent?) {
    var shown by remember { mutableStateOf(false) }
    var forward by remember { mutableStateOf(true) }
    LaunchedEffect(event) {
        if (event != null) {
            forward = event.forward
            shown = true
            delay(650L)
            shown = false
        }
    }
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(tween(100)) + scaleIn(tween(160), initialScale = 0.8f),
        exit = fadeOut(tween(250)),
        modifier = Modifier.align(if (forward) Alignment.CenterEnd else Alignment.CenterStart).padding(horizontal = 56.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.size(92.dp).background(Color(0x66000000), CircleShape).padding(top = 18.dp)
        ) {
            Icon(
                imageVector = if (forward) Icons.Default.Forward10 else Icons.Default.Replay10,
                contentDescription = if (forward) "Forward 10 seconds" else "Back 10 seconds",
                tint = Color.White,
                modifier = Modifier.size(40.dp)
            )
            Text(if (forward) "+10 s" else "-10 s", color = Color.White, fontSize = Ds.Type.Small, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * Movie mode controls, drawn over the landscape video:
 * back and title, speed, fit, audio and subtitle choices and a lock along the top; back-10, play/pause and
 * forward-10 in the middle; a seek bar with the time and time left along the bottom.
 * Locked: everything else disappears and only the lock stays, so a pocket or a child cannot change anything.
 */
@Composable
internal fun MovieModeOverlay(
    title: String,
    player: ExoPlayer,
    isPlaying: Boolean,
    progressMsProvider: () -> Long,
    durationMs: Long,
    controlsVisible: Boolean,
    locked: Boolean,
    fit: MovieFit,
    speed: Float,
    onExit: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeekRatio: (Float) -> Unit,
    onCycleSpeed: () -> Unit,
    onCycleFit: () -> Unit,
    onToggleLock: () -> Unit,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Whatever audio or subtitle choice was made here is undone when movie mode ends
    DisposableEffect(player) {
        onDispose {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .build()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(220)),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top + WindowInsetsSides.Bottom)
                    )
            ) {
                if (locked) {
                    RoundGlassButton(
                        icon = Icons.Default.Lock,
                        description = "Unlock controls",
                        onClick = { onToggleLock() },
                        modifier = Modifier.align(Alignment.CenterStart).padding(start = 24.dp),
                        size = 56
                    )
                } else {
                    // soft fades so the text reads over any frame
                    Box(
                        modifier = Modifier.fillMaxWidth().height(110.dp).align(Alignment.TopCenter)
                            .background(Brush.verticalGradient(listOf(Color(0xB3000000), Color.Transparent)))
                    )
                    Box(
                        modifier = Modifier.fillMaxWidth().height(150.dp).align(Alignment.BottomCenter)
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                    )
                    MovieTopBar(
                        title = title, player = player, fit = fit, speed = speed,
                        onExit = onExit, onCycleSpeed = { onInteraction(); onCycleSpeed() },
                        onCycleFit = { onInteraction(); onCycleFit() },
                        onToggleLock = onToggleLock, onInteraction = onInteraction,
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(36.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RoundGlassButton(Icons.Default.Replay10, "Back 10 seconds", {
                            onInteraction()
                            onSeekRatio(skipTarget(progressMsProvider(), -MOVIE_SKIP_MS, durationMs).toFloat() / durationMs.coerceAtLeast(1L))
                        }, size = 52)
                        RoundGlassButton(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            if (isPlaying) "Pause" else "Play",
                            { onInteraction(); onTogglePlay() },
                            size = 72, iconSize = 40
                        )
                        RoundGlassButton(Icons.Default.Forward10, "Forward 10 seconds", {
                            onInteraction()
                            onSeekRatio(skipTarget(progressMsProvider(), MOVIE_SKIP_MS, durationMs).toFloat() / durationMs.coerceAtLeast(1L))
                        }, size = 52)
                    }
                    MovieSeekBar(
                        progressMsProvider = progressMsProvider,
                        durationMs = durationMs,
                        onSeekRatio = onSeekRatio,
                        onInteraction = onInteraction,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 20.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MovieTopBar(
    title: String,
    player: ExoPlayer,
    fit: MovieFit,
    speed: Float,
    onExit: () -> Unit,
    onCycleSpeed: () -> Unit,
    onCycleFit: () -> Unit,
    onToggleLock: () -> Unit,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        RoundGlassButton(Icons.AutoMirrored.Filled.ArrowBack, "Leave movie mode", onExit, size = 44)
        Text(
            text = title,
            color = Color.White,
            fontSize = Ds.Type.Title,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        TracksButton(player = player, onInteraction = onInteraction)
        PillButton(label = fit.label, onClick = onCycleFit)
        PillButton(label = speedLabel(speed), onClick = onCycleSpeed)
        RoundGlassButton(Icons.Default.LockOpen, "Lock controls", onToggleLock, size = 44)
    }
}

private fun speedLabel(speed: Float): String =
    (if (speed % 1f == 0f) speed.toInt().toString() else speed.toString()) + "×"

@Composable
private fun MovieSeekBar(
    progressMsProvider: () -> Long,
    durationMs: Long,
    onSeekRatio: (Float) -> Unit,
    onInteraction: () -> Unit,
    modifier: Modifier = Modifier
) {
    val progressMs = progressMsProvider()
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val ratio = if (durationMs > 0) (progressMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val shownMs = if (dragging) (dragValue * durationMs).toLong() else progressMs

    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(formatMovieTime(shownMs), color = Color.White, fontSize = Ds.Type.Label, fontWeight = FontWeight.SemiBold)
        Slider(
            value = if (dragging) dragValue else ratio,
            onValueChange = {
                dragging = true
                dragValue = it
                onInteraction()
            },
            onValueChangeFinished = {
                onSeekRatio(dragValue)
                dragging = false
            },
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Ds.Palette.Pink,
                inactiveTrackColor = Color(0x33FFFFFF)
            ),
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
        )
        Text(
            "-" + formatMovieTime((durationMs - shownMs).coerceAtLeast(0L)),
            color = Color(0xCCFFFFFF), fontSize = Ds.Type.Label
        )
    }
}

/** Audio and subtitle choices; only shown when the file has more than one audio track or any subtitles. */
@Composable
private fun TracksButton(player: ExoPlayer, onInteraction: () -> Unit) {
    var tracks by remember(player) { mutableStateOf(player.currentTracks) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks2: Tracks) {
                tracks = tracks2
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    val audio = tracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO && it.isSupported }
    val text = tracks.groups.filter { it.type == C.TRACK_TYPE_TEXT && it.isSupported }
    if (audio.size <= 1 && text.isEmpty()) return

    var open by remember { mutableStateOf(false) }
    Box {
        RoundGlassButton(Icons.Default.Subtitles, "Audio and subtitles", { onInteraction(); open = true }, size = 44)
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (audio.size > 1) {
                MenuHeader("Audio")
                audio.forEachIndexed { i, group ->
                    DropdownMenuItem(
                        text = { Text((if (group.isSelected) "✓ " else "") + group.label(i)) },
                        onClick = { selectTrack(player, group); open = false }
                    )
                }
            }
            if (text.isNotEmpty()) {
                MenuHeader("Subtitles")
                DropdownMenuItem(
                    text = { Text((if (text.none { it.isSelected }) "✓ " else "") + "Off") },
                    onClick = {
                        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true).build()
                        open = false
                    }
                )
                text.forEachIndexed { i, group ->
                    DropdownMenuItem(
                        text = { Text((if (group.isSelected) "✓ " else "") + group.label(i)) },
                        onClick = { selectTrack(player, group); open = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun MenuHeader(text: String) {
    Text(text.uppercase(), color = Ds.Palette.Muted, fontSize = Ds.Type.Caption, fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
}

private fun Tracks.Group.label(index: Int): String {
    val format = getTrackFormat(0)
    val language = format.language?.takeIf { it.isNotBlank() && it != "und" }
        ?.let { java.util.Locale.forLanguageTag(it).displayLanguage }
        ?.takeIf { it.isNotBlank() }
    return format.label ?: language ?: "Track ${index + 1}"
}

private fun selectTrack(player: ExoPlayer, group: Tracks.Group) {
    player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
        .setTrackTypeDisabled(group.type, false)
        .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, 0))
        .build()
}

@Composable
private fun RoundGlassButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Int = 48,
    iconSize: Int = 24
) {
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Color(0x59000000))
            .border(1.dp, GlassBorder, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(iconSize.dp))
    }
}

@Composable
private fun PillButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(44.dp)
            .clip(CircleShape)
            .background(Color(0x59000000))
            .border(1.dp, GlassBorder, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, fontSize = Ds.Type.Label, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}
