package com.example.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay

/**
 * Shared motion language for OffScroll.
 *
 * - [bouncyClickable]: springy press feedback (shrinks on touch, bounces back on release)
 * - [staggeredEntrance]: fade + rise-in, delayed by position so lists cascade in
 * - [auroraGlow]: slowly drifting violet / rose / cyan light behind a dark screen
 */

/** A clickable that squashes slightly while pressed and springs back. */
fun Modifier.bouncyClickable(
    pressedScale: Float = 0.9f,
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "bouncy_press_scale"
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = source,
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
}

/** Fades the content in while it rises a few dp; [index] staggers the start time. */
fun Modifier.staggeredEntrance(
    index: Int = 0,
    stepMs: Int = 55,
    riseDp: Float = 26f
): Modifier = composed {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay((index.coerceIn(0, 12) * stepMs).toLong())
        progress.animateTo(1f, tween(durationMillis = 460, easing = FastOutSlowInEasing))
    }
    this.graphicsLayer {
        alpha = progress.value
        translationY = (1f - progress.value) * riseDp * density
    }
}

/** Slow, soft drifting colour glows. Apply before any padding so it fills the whole screen. */
fun Modifier.auroraGlow(): Modifier = composed {
    val transition = rememberInfiniteTransition(label = "aurora")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 16000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "aurora_phase"
    )
    this.drawBehind {
        val w = size.width
        val h = size.height
        // Violet glow, top left drifting right
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(FrostedViolet.copy(alpha = 0.30f), Color.Transparent),
                center = Offset(w * (0.10f + 0.50f * phase), h * (0.08f + 0.06f * phase)),
                radius = w * 0.95f
            )
        )
        // Rose glow, bottom right drifting left
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(FrostedRose.copy(alpha = 0.16f), Color.Transparent),
                center = Offset(w * (0.95f - 0.45f * phase), h * (0.78f + 0.10f * phase)),
                radius = w * 0.85f
            )
        )
        // Cyan glow, middle, gently breathing
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(FrostedCyan.copy(alpha = 0.10f), Color.Transparent),
                center = Offset(w * (0.30f + 0.30f * phase), h * 0.45f),
                radius = w * 0.75f
            )
        )
    }
}
