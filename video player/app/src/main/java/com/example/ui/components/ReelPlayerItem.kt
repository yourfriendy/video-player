package com.example.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.net.Uri
import android.os.SystemClock
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.data.model.LocalVideo
import com.example.ui.model.VideoDisplayMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin

private tailrec fun Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

@OptIn(UnstableApi::class)
@Composable
fun ReelPlayerItem(
    video: LocalVideo,
    isCurrentPage: Boolean,
    isPlaying: Boolean,
    isMuted: Boolean,
    playbackSpeed: Float,
    displayMode: VideoDisplayMode,
    seekToRatio: Float?,
    isLoopMode: Boolean = true,
    userActionTrigger: Long = 0L,
    shouldPreload: Boolean = false,
    startPositionMs: Long = 0L,
    onEnded: () -> Unit = {},
    onProgressUpdate: (currentMs: Long, totalMs: Long) -> Unit,
    onSingleTap: () -> Unit,
    onDoubleTap: (Offset) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    var isBuffering by remember { mutableStateOf(false) }
    var hasPlaybackError by remember { mutableStateOf(false) }
    var showPlayPauseBadge by remember { mutableStateOf(false) }
    var lastObservedTrigger by remember { mutableLongStateOf(0L) }
    var doubleTapHeartOffset by remember { mutableStateOf<Offset?>(null) }
    var playerDuration by remember { mutableLongStateOf(video.durationMs) }
    var isAppInForeground by remember { mutableStateOf(true) }
    var isTemporarilyFastForwarding by remember { mutableStateOf(false) }
    var hasAppliedStartPosition by remember { mutableStateOf(false) }

    // Callbacks/flags read from long-lived listeners and gesture handlers must never be stale
    val latestIsCurrentPage by rememberUpdatedState(isCurrentPage)
    val latestOnEnded by rememberUpdatedState(onEnded)
    val latestOnSingleTap by rememberUpdatedState(onSingleTap)
    val latestOnDoubleTap by rememberUpdatedState(onDoubleTap)

    // VLC-style horizontal drag seek states
    var isHorizontalDragging by remember { mutableStateOf(false) }
    var dragStartPosMs by remember { mutableLongStateOf(0L) }
    var dragSeekDeltaMs by remember { mutableLongStateOf(0L) }
    var dragTargetPosMs by remember { mutableLongStateOf(0L) }

    // ExoPlayer reference
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = if (isLoopMode) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            videoScalingMode = androidx.media3.common.C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
        }
    }

    // Prepare the current page, and preload the neighbouring page (paused) so swipes show a frame instantly
    LaunchedEffect(video.uri, isCurrentPage, shouldPreload) {
        if (!video.isSample) {
            if (isCurrentPage || shouldPreload) {
                if (exoPlayer.currentMediaItem == null) {
                    exoPlayer.setMediaItem(MediaItem.fromUri(video.uri))
                    exoPlayer.prepare()
                } else if (exoPlayer.playbackState == Player.STATE_IDLE) {
                    exoPlayer.prepare() // retry after an error
                }
                if (isCurrentPage && !hasAppliedStartPosition) {
                    hasAppliedStartPosition = true
                    if (startPositionMs > 0L) exoPlayer.seekTo(startPositionMs)
                }
                exoPlayer.playWhenReady = isCurrentPage && isPlaying && isAppInForeground
            } else {
                exoPlayer.pause()
                exoPlayer.playWhenReady = false
            }
        }
    }

    LaunchedEffect(isLoopMode) {
        exoPlayer.repeatMode = if (isLoopMode) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
    }

    // Lifecycle observer for strict no-background-play
    DisposableEffect(lifecycleOwner, exoPlayer) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> {
                    val activity = context.findActivity()
                    val inPiP = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                        activity?.isInPictureInPictureMode == true
                    } else false
                    if (!inPiP) {
                        isAppInForeground = false
                        exoPlayer.pause()
                        exoPlayer.playWhenReady = false
                    }
                }
                Lifecycle.Event.ON_RESUME -> {
                    isAppInForeground = true
                    if (isCurrentPage && isPlaying) {
                        exoPlayer.playWhenReady = true
                    }
                }
                Lifecycle.Event.ON_DESTROY -> {
                    isAppInForeground = false
                    exoPlayer.pause()
                    exoPlayer.stop()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Handle play / pause and mute and speed
    LaunchedEffect(isCurrentPage, isPlaying, isMuted, playbackSpeed, isAppInForeground) {
        if (isCurrentPage && isAppInForeground) {
            exoPlayer.volume = if (isMuted) 0f else 1f
            exoPlayer.playbackParameters = PlaybackParameters(playbackSpeed)
            exoPlayer.playWhenReady = isPlaying
        } else {
            exoPlayer.playWhenReady = false
        }
    }

    // Scrubbing seek
    LaunchedEffect(seekToRatio) {
        seekToRatio?.let { ratio ->
            val totalMs = if (playerDuration > 0) playerDuration else video.durationMs
            if (totalMs > 0) {
                val targetMs = (ratio * totalMs).toLong().coerceIn(0L, totalMs)
                exoPlayer.seekTo(targetMs)
            }
        }
    }

    // Progress update loop
    LaunchedEffect(isCurrentPage, isPlaying, isAppInForeground) {
        while (isActive && isCurrentPage && isAppInForeground) {
            if (video.isSample) {
                val current = (SystemClock.uptimeMillis() % (video.durationMs.coerceAtLeast(10000L)))
                onProgressUpdate(current, video.durationMs.coerceAtLeast(10000L))
            } else {
                val current = exoPlayer.currentPosition.coerceAtLeast(0L)
                val total = if (exoPlayer.duration > 0) exoPlayer.duration else video.durationMs
                playerDuration = total
                onProgressUpdate(current, total)
            }
            delay(150L)
        }
    }

    // Listen to Player states
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                isBuffering = playbackState == Player.STATE_BUFFERING
                if (playbackState == Player.STATE_ENDED && latestIsCurrentPage) {
                    latestOnEnded()
                }
                if (playbackState == Player.STATE_READY) {
                    hasPlaybackError = false
                    if (exoPlayer.duration > 0) {
                        playerDuration = exoPlayer.duration
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                hasPlaybackError = true
                isBuffering = false
            }
        }
        exoPlayer.addListener(listener)

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Play/Pause icon badge: ONLY show on explicit user action trigger, NEVER on scroll!
    LaunchedEffect(userActionTrigger) {
        if (userActionTrigger > 0L && userActionTrigger != lastObservedTrigger) {
            lastObservedTrigger = userActionTrigger
            showPlayPauseBadge = true
            delay(650L)
            showPlayPauseBadge = false
        }
    }

    val resizeModeEnum = when (displayMode) {
        VideoDisplayMode.CROP -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        VideoDisplayMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        VideoDisplayMode.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ComposeColor.Black)
            .testTag("reel_player_item_${video.id}")
            .pointerInput(video.id) {
                detectTapGestures(
                    onTap = {
                        latestOnSingleTap()
                    },
                    onDoubleTap = { offset ->
                        doubleTapHeartOffset = offset
                        latestOnDoubleTap(offset)
                    }
                )
            }
            .pointerInput(video.id) {
                detectHorizontalDragGestures(
                    onDragStart = {
                        val current = if (video.isSample) {
                            (SystemClock.uptimeMillis() % (video.durationMs.coerceAtLeast(10000L)))
                        } else {
                            exoPlayer.currentPosition.coerceAtLeast(0L)
                        }
                        dragStartPosMs = current
                        dragSeekDeltaMs = 0L
                        dragTargetPosMs = current
                        isHorizontalDragging = true
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        val totalMs = if (playerDuration > 0) playerDuration else video.durationMs.coerceAtLeast(10000L)
                        // Dynamic sensitivity: 1px translates to ~50ms to 150ms depending on video length
                        val msPerPx = (totalMs / 800f).coerceIn(40f, 160f)
                        dragSeekDeltaMs += (dragAmount * msPerPx).toLong()
                        dragTargetPosMs = (dragStartPosMs + dragSeekDeltaMs).coerceIn(0L, totalMs)
                    },
                    onDragEnd = {
                        if (isHorizontalDragging) {
                            if (!video.isSample) {
                                exoPlayer.seekTo(dragTargetPosMs)
                            }
                            val totalMs = if (playerDuration > 0) playerDuration else video.durationMs.coerceAtLeast(10000L)
                            onProgressUpdate(dragTargetPosMs, totalMs)
                            isHorizontalDragging = false
                        }
                    },
                    onDragCancel = {
                        isHorizontalDragging = false
                    }
                )
            }
    ) {
        if (video.isSample) {
            // High-fidelity cinematic generative visual loop for sample demo reels
            SampleCinematicVisualLoop(
                video = video,
                isPlaying = isPlaying && isCurrentPage && isAppInForeground,
                speed = playbackSpeed
            )
        } else {
            // Native Media3 PlayerView
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        resizeMode = resizeModeEnum
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { playerView ->
                    playerView.player = exoPlayer
                    playerView.resizeMode = resizeModeEnum
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Buffering Indicator
        if (isBuffering && !hasPlaybackError) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = ComposeColor(0xFF8B5CF6),
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        // Playback Error fallback card
        if (hasPlaybackError) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(ComposeColor(0xCC000000)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            ComposeColor(0xFF1E1F2E),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                        )
                        .size(240.dp, 160.dp),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.layout.Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.VideocamOff,
                            contentDescription = "Video error",
                            tint = ComposeColor(0xFFF87171),
                            modifier = Modifier.size(44.dp)
                        )
                        androidx.compose.foundation.layout.Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            text = "Unsupported Codec",
                            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                            color = ComposeColor.White
                        )
                        Text(
                            text = video.displayName,
                            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                            color = ComposeColor.Gray,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // Center Animated Play / Pause transient badge with Frosted Glass look
        AnimatedVisibility(
            visible = showPlayPauseBadge,
            enter = fadeIn(tween(120)) + scaleIn(tween(160), initialScale = 0.65f),
            exit = fadeOut(tween(300)) + scaleOut(tween(300), targetScale = 1.25f),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Box(
                modifier = Modifier
                    .size(86.dp)
                    .background(
                        ComposeColor(0x55000000),
                        shape = androidx.compose.foundation.shape.CircleShape
                    )
                    .border(
                        1.5.dp,
                        ComposeColor(0x66FFFFFF),
                        shape = androidx.compose.foundation.shape.CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = if (isPlaying) "Playing" else "Paused",
                    tint = ComposeColor.White,
                    modifier = Modifier.size(46.dp)
                )
            }
        }

        // Double tap floating heart animation
        doubleTapHeartOffset?.let { offset ->
            FloatingDoubleTapHeart(
                offset = offset,
                onDismiss = { doubleTapHeartOffset = null }
            )
        }

        // VLC-Style Horizontal Drag Seek Overlay
        AnimatedVisibility(
            visible = isHorizontalDragging,
            enter = fadeIn(tween(80)) + scaleIn(tween(80), initialScale = 0.92f),
            exit = fadeOut(tween(180)) + scaleOut(tween(180), targetScale = 0.92f),
            modifier = Modifier.align(Alignment.Center)
        ) {
            val totalMs = if (playerDuration > 0) playerDuration else video.durationMs.coerceAtLeast(10000L)
            VlcSeekHudOverlay(
                deltaMs = dragSeekDeltaMs,
                targetPosMs = dragTargetPosMs,
                totalDurationMs = totalMs
            )
        }
    }
}

@Composable
fun FloatingDoubleTapHeart(
    offset: Offset,
    onDismiss: () -> Unit
) {
    val scale = remember { Animatable(0.25f) }
    val alpha = remember { Animatable(1f) }
    val yOffset = remember { Animatable(0f) }
    val rotation = remember { Animatable(-10f) }

    LaunchedEffect(Unit) {
        launch {
            scale.animateTo(1.35f, animationSpec = tween(200, easing = FastOutSlowInEasing))
            scale.animateTo(1.05f, animationSpec = tween(120))
        }
        launch {
            rotation.animateTo(6f, animationSpec = tween(220))
            rotation.animateTo(0f, animationSpec = tween(120))
        }
        delay(220L)
        launch {
            yOffset.animateTo(-80f, animationSpec = tween(340, easing = LinearEasing))
        }
        alpha.animateTo(0f, animationSpec = tween(340))
        onDismiss()
    }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .graphicsLayer {
                    translationX = offset.x - 120f
                    translationY = offset.y - 120f + yOffset.value
                    scaleX = scale.value
                    scaleY = scale.value
                    rotationZ = rotation.value
                    this.alpha = alpha.value
                }
        ) {
            // Radiant glow aura
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = null,
                tint = ComposeColor(0x77FB7185),
                modifier = Modifier
                    .size(96.dp)
                    .graphicsLayer {
                        scaleX = 1.2f
                        scaleY = 1.2f
                    }
            )
            // Vibrant neon heart
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = "Favorited",
                tint = ComposeColor(0xFFFB7185),
                modifier = Modifier.size(96.dp)
            )
        }
    }
}

/**
 * Procedural offline reels visual animation generator for demo sample clips
 */
@Composable
fun SampleCinematicVisualLoop(
    video: LocalVideo,
    isPlaying: Boolean,
    speed: Float
) {
    val infiniteTransition = rememberInfiniteTransition(label = "sample_loop")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween((6000 / speed).toInt().coerceAtLeast(1000), easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val colorSeed = (video.id % 4).toInt()
    val bgGradients = remember(colorSeed) {
        when (colorSeed) {
            0 -> listOf(ComposeColor(0xFF0F172A), ComposeColor(0xFF6B21A8), ComposeColor(0xFF0284C7))
            1 -> listOf(ComposeColor(0xFF1E1B4B), ComposeColor(0xFFBE185D), ComposeColor(0xFFF97316))
            2 -> listOf(ComposeColor(0xFF022C22), ComposeColor(0xFF065F46), ComposeColor(0xFF10B981))
            else -> listOf(ComposeColor(0xFF18181B), ComposeColor(0xFF3B82F6), ComposeColor(0xFF9333EA))
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(bgGradients))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val centerX = width / 2
            val centerY = height / 2

            val rad = Math.toRadians(phase.toDouble())
            val pulse = (sin(rad) * 40f).toFloat()

            // Dynamic light orbs
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(ComposeColor.White.copy(alpha = 0.35f), ComposeColor.Transparent),
                    center = Offset(centerX + (cos(rad) * 120f).toFloat(), centerY + (sin(rad) * 160f).toFloat()),
                    radius = 280f + pulse
                ),
                radius = 280f + pulse,
                center = Offset(centerX + (cos(rad) * 120f).toFloat(), centerY + (sin(rad) * 160f).toFloat())
            )

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(ComposeColor(0xFFFF007A).copy(alpha = 0.4f), ComposeColor.Transparent),
                    center = Offset(centerX - (cos(rad) * 100f).toFloat(), centerY - (sin(rad) * 120f).toFloat()),
                    radius = 220f
                ),
                radius = 220f,
                center = Offset(centerX - (cos(rad) * 100f).toFloat(), centerY - (sin(rad) * 120f).toFloat())
            )

            // Neon Film strip decorative marks
            for (i in 0..12) {
                val yPos = (height / 12) * i + ((phase * 2f) % (height / 12))
                drawRect(
                    color = ComposeColor.White.copy(alpha = 0.08f),
                    topLeft = Offset(16f, yPos),
                    size = androidx.compose.ui.geometry.Size(20f, 14f)
                )
                drawRect(
                    color = ComposeColor.White.copy(alpha = 0.08f),
                    topLeft = Offset(width - 36f, yPos),
                    size = androidx.compose.ui.geometry.Size(20f, 14f)
                )
            }
        }
    }
}

@Composable
private fun VlcSeekHudOverlay(
    deltaMs: Long,
    targetPosMs: Long,
    totalDurationMs: Long
) {
    val isForward = deltaMs >= 0
    val deltaSeconds = kotlin.math.abs(deltaMs) / 1000
    val deltaMinutes = deltaSeconds / 60
    val remSeconds = deltaSeconds % 60
    val deltaSign = if (isForward) "+" else "-"
    val deltaFormatted = if (deltaMinutes > 0) {
        String.format(java.util.Locale.US, "%s%d:%02ds", deltaSign, deltaMinutes, remSeconds)
    } else {
        String.format(java.util.Locale.US, "%s%ds", deltaSign, deltaSeconds)
    }

    val progressRatio = (targetPosMs.toFloat() / totalDurationMs.coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .background(
                color = ComposeColor(0xF00F101A),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
            )
            .border(
                1.5.dp,
                brush = Brush.linearGradient(
                    listOf(ComposeColor(0x808B5CF6), ComposeColor(0x40EC4899))
                ),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 28.dp, vertical = 20.dp)
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (isForward) Icons.Default.FastForward else Icons.Default.FastRewind,
                contentDescription = if (isForward) "Fast Forward" else "Rewind",
                tint = if (isForward) ComposeColor(0xFF38BDF8) else ComposeColor(0xFFFB7185),
                modifier = Modifier.size(44.dp)
            )

            Text(
                text = deltaFormatted,
                style = androidx.compose.material3.MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold
                ),
                color = ComposeColor.White
            )

            Text(
                text = "${formatVlcTime(targetPosMs)} / ${formatVlcTime(totalDurationMs)}",
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                ),
                color = ComposeColor(0xFFCBD5E1)
            )

            // Progress bar
            Box(
                modifier = Modifier
                    .size(width = 160.dp, height = 5.dp)
                    .background(
                        color = ComposeColor(0x40FFFFFF),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(3.dp)
                    )
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = progressRatio
                            transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                        }
                        .background(
                            brush = Brush.horizontalGradient(
                                listOf(ComposeColor(0xFF8B5CF6), ComposeColor(0xFFEC4899))
                            ),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(3.dp)
                        )
                )
            }
        }
    }
}

private fun formatVlcTime(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format(java.util.Locale.US, "%02d:%02d", min, sec)
}

