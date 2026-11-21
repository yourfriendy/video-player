package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.example.ui.theme.rememberMotionEnabled
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * One short moment each time the video is paused or resumed, never anything that stays on screen afterwards:
 *  - Pause: a glass button pops in showing only a pause symbol whose two bars squeeze together, and a ring
 *    closes in towards the centre. The button then shrinks away.
 *  - Play: a glass button pops in showing only a play symbol that slides and turns into place, and a ring
 *    spreads outwards. The button then swells and fades.
 * The video itself is never dimmed. With animations switched off it is a plain short fade. A page that was
 * already paused when it appeared (swiping to the next video) shows nothing.
 */
@Composable
fun BoxScope.PlayPauseFeedback(isPlaying: Boolean, isCurrentPage: Boolean, userActionTrigger: Long) {
    val motion = rememberMotionEnabled()
    val triggerAtStart = remember { userActionTrigger }
    var visible by remember { mutableStateOf(false) }
    var wasPause by remember { mutableStateOf(false) }
    val ring = remember { Animatable(1f) }

    LaunchedEffect(userActionTrigger) {
        if (userActionTrigger == triggerAtStart || !isCurrentPage) return@LaunchedEffect
        wasPause = !isPlaying
        visible = true
        if (motion) {
            ring.snapTo(0f)
            launch { ring.animateTo(1f, tween(600, easing = FastOutSlowInEasing)) }
        }
        delay(if (motion) 700L else 450L)
        visible = false
    }

    // The ring: closes in for a pause, spreads out for a play
    Canvas(modifier = Modifier.align(Alignment.Center).size(200.dp)) {
        val p = ring.value
        if (p < 1f) {
            val factor = if (wasPause) 1f - 0.55f * p else 0.42f + 0.58f * p
            drawCircle(
                color = Color(0xFFC4B5FD),
                alpha = (1f - p) * 0.6f,
                radius = size.minDimension / 2f * factor,
                style = Stroke(width = (1f + 3f * (1f - p)).dp.toPx())
            )
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(120)) + scaleIn(spring(dampingRatio = 0.55f, stiffness = 420f), initialScale = 0.5f),
        exit = fadeOut(tween(260)) + scaleOut(tween(260), targetScale = if (wasPause) 0.85f else 1.3f),
        modifier = Modifier.align(Alignment.Center)
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .background(Color(0x66000000), CircleShape)
                .border(
                    1.5.dp,
                    Brush.linearGradient(listOf(Color(0x99C4B5FD), Color(0x33F43F5E))),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = wasPause,
                transitionSpec = { fadeIn(tween(90)) togetherWith fadeOut(tween(90)) },
                label = "pause_or_play_symbol"
            ) { pause ->
                if (pause) PauseSymbol(motion) else PlaySymbol(motion)
            }
        }
    }
}

/** The pause bars start wide and squeeze together with a little bounce. */
@Composable
private fun PauseSymbol(motion: Boolean) {
    val t = remember { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (motion) t.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 300f))
    }
    Icon(
        imageVector = Icons.Default.Pause,
        contentDescription = "Paused",
        tint = Color.White,
        modifier = Modifier
            .size(46.dp)
            .graphicsLayer {
                scaleX = 1.5f - 0.5f * t.value
                scaleY = 0.75f + 0.25f * t.value
                alpha = t.value.coerceIn(0f, 1f)
            }
    )
}

/** The play triangle slides in from the left, turning upright as it lands. */
@Composable
private fun PlaySymbol(motion: Boolean) {
    val t = remember { Animatable(if (motion) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (motion) t.animateTo(1f, spring(dampingRatio = 0.6f, stiffness = 300f))
    }
    Icon(
        imageVector = Icons.Default.PlayArrow,
        contentDescription = "Playing",
        tint = Color.White,
        modifier = Modifier
            .size(46.dp)
            .graphicsLayer {
                translationX = (1f - t.value) * -14.dp.toPx()
                rotationZ = (1f - t.value) * -25f
                scaleX = 0.6f + 0.4f * t.value
                scaleY = 0.6f + 0.4f * t.value
                alpha = t.value.coerceIn(0f, 1f)
            }
    )
}
