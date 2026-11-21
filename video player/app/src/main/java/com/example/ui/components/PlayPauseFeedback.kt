package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.example.ui.theme.rememberMotionEnabled
import kotlinx.coroutines.delay

/**
 * What you see when the video is paused or resumed (a double tap, or the first tap with the controls showing):
 *  - a soft dark veil fades over the paused frame, so "paused" reads at a glance;
 *  - a glass button springs in: a pause symbol for a moment, which morphs into a play symbol and gently breathes;
 *  - a light ring ripples out from the centre on every pause and every resume;
 *  - on resume the button swells and fades away.
 * With animations switched off the same things happen with plain fades. Pages that were already paused when they
 * appeared (swiping to the next video) show the paused state without replaying the pause moment.
 */
@Composable
fun BoxScope.PlayPauseFeedback(isPlaying: Boolean, isCurrentPage: Boolean, userActionTrigger: Long) {
    val motion = rememberMotionEnabled()
    val paused = !isPlaying && isCurrentPage
    val triggerAtStart = remember { userActionTrigger }
    val userJustActed = userActionTrigger != triggerAtStart

    // 1. The veil
    val veil = animateFloatAsState(
        targetValue = if (paused) 0.32f else 0f,
        animationSpec = tween(if (motion) 380 else 120, easing = FastOutSlowInEasing),
        label = "pause_veil"
    )
    Box(modifier = Modifier.fillMaxSize().drawBehind { drawRect(Color.Black, alpha = veil.value) })

    // 2. The ripple ring
    val ring = remember { Animatable(1f) }
    LaunchedEffect(userActionTrigger) {
        if (motion && userJustActed && isCurrentPage) {
            ring.snapTo(0f)
            ring.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
        }
    }
    Canvas(modifier = Modifier.align(Alignment.Center).size(200.dp)) {
        val p = ring.value
        if (p < 1f) {
            drawCircle(
                color = Color(0xFFC4B5FD),
                alpha = (1f - p) * 0.6f,
                radius = size.minDimension / 2f * (0.42f + 0.58f * p),
                style = Stroke(width = (1f + 3f * (1f - p)).dp.toPx())
            )
        }
    }

    // 3. Show the pause symbol for a moment right after pausing, then settle on play
    var showPauseSymbol by remember { mutableStateOf(false) }
    LaunchedEffect(paused, userActionTrigger) {
        if (paused && userJustActed) {
            showPauseSymbol = true
            delay(600L)
        }
        showPauseSymbol = false
    }

    // 4. The glass button
    AnimatedVisibility(
        visible = paused,
        enter = fadeIn(tween(160)) + scaleIn(spring(dampingRatio = 0.55f, stiffness = 380f), initialScale = 0.5f),
        exit = fadeOut(tween(260)) + scaleOut(tween(260), targetScale = 1.3f),
        modifier = Modifier.align(Alignment.Center)
    ) {
        // Runs only while the button is on screen
        val breathe by rememberInfiniteTransition(label = "pause_breathe").animateFloat(
            initialValue = 1f,
            targetValue = 1.06f,
            animationSpec = infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
            label = "pause_breathe_scale"
        )
        Box(
            modifier = Modifier
                .graphicsLayer {
                    val s = if (motion) breathe else 1f
                    scaleX = s
                    scaleY = s
                }
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
                targetState = showPauseSymbol,
                transitionSpec = {
                    (fadeIn(tween(180)) + scaleIn(tween(220), initialScale = 0.6f)) togetherWith
                        (fadeOut(tween(120)) + scaleOut(tween(160), targetScale = 0.6f))
                },
                label = "pause_play_symbol"
            ) { pauseSymbol ->
                Icon(
                    imageVector = if (pauseSymbol) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (pauseSymbol) "Paused" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(46.dp)
                )
            }
        }
    }
}
