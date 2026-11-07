package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/*
 * Loading that has something to look at. Instead of a lone spinner: a ring in the app's sunset colours turning
 * around the person's photo, a status line that changes ("Fetching info…", "Reading the stories…"), bouncing dots,
 * and shimmering placeholders where the content will appear. If it takes long, it says so.
 */

private val RingColors = listOf(Color(0xFFF59E0B), Color(0xFFEC4899), Color(0xFF8B5CF6), Color(0xFF22D3EE), Color(0xFFF59E0B))

/** A soft highlight sweeping across, for placeholder shapes. */
@Composable
internal fun shimmerBrush(): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val shift by transition.animateFloat(
        initialValue = -400f, targetValue = 1200f,
        animationSpec = infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer_shift"
    )
    return Brush.linearGradient(
        colors = listOf(Color(0x14FFFFFF), Color(0x38FFFFFF), Color(0x14FFFFFF)),
        start = Offset(shift, 0f),
        end = Offset(shift + 400f, 300f)
    )
}

@Composable
internal fun ShimmerBox(modifier: Modifier = Modifier, shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(10.dp)) {
    Box(modifier = modifier.clip(shape).background(shimmerBrush()))
}

/** Placeholder tiles shaped like the profile grid, shimmering while the real ones load. */
@Composable
internal fun PostGridSkeleton(columns: Int = 3, rows: Int = 2, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        repeat(rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
                repeat(columns) {
                    ShimmerBox(
                        modifier = Modifier.weight(1f).aspectRatio(0.8f),
                        shape = RoundedCornerShape(2.dp)
                    )
                }
            }
        }
    }
}

/** Three dots that bounce one after another. */
@Composable
internal fun BouncingDots(modifier: Modifier = Modifier, color: Color = Color.White) {
    val transition = rememberInfiniteTransition(label = "dots")
    val phase by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "dots_phase"
    )
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            // Each dot hops a little after the one before it
            val local = ((phase - index * 0.14f) % 1f + 1f) % 1f
            val lift = if (local < 0.4f) -6f * kotlin.math.sin(Math.PI * (local / 0.4f)).toFloat() else 0f
            // Lift the dot by wrapping it in a box that is shifted up; alpha follows the lift
            Box(modifier = Modifier.size(width = 7.dp, height = 20.dp), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .graphicsLayer { translationY = lift.dp.toPx() }
                        .size(7.dp)
                        .alpha(0.55f + (-lift / 6f) * 0.45f)
                        .background(color, CircleShape)
                )
            }
        }
    }
}


/**
 * The main loading display: a turning ring (around [avatar] when there is one), a status line that cycles through
 * [steps], dots, and a hint if it takes longer than expected. [compact] is for small spaces.
 */
@Composable
internal fun FetchingLoader(
    steps: List<String>,
    modifier: Modifier = Modifier,
    avatar: (@Composable (Dp) -> Unit)? = null,
    icon: ImageVector = Icons.Default.Group,
    compact: Boolean = false,
    textColor: Color = Color.White,
    mutedColor: Color = Color(0xFF94A3B8)
) {
    val size = if (compact) 64.dp else 92.dp
    val transition = rememberInfiniteTransition(label = "fetching")
    val angle by transition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart),
        label = "ring_angle"
    )
    val breathe by transition.animateFloat(
        initialValue = 0.94f, targetValue = 1.0f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "ring_breathe"
    )
    var stepIndex by remember { mutableIntStateOf(0) }
    LaunchedEffect(steps) {
        stepIndex = 0
        while (steps.size > 1) {
            delay(1700)
            stepIndex = (stepIndex + 1) % steps.size
        }
    }
    val seconds by produceState(0, steps) {
        value = 0
        while (true) {
            delay(1000)
            value += 1
        }
    }

    Column(
        modifier = modifier.fillMaxWidth().padding(vertical = if (compact) 8.dp else 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.size(size), contentAlignment = Alignment.Center) {
            val stroke = if (compact) 3.dp else 4.dp
            Canvas(modifier = Modifier.size(size)) {
                val width = stroke.toPx()
                val inset = width / 2
                // A faint full circle underneath, then the bright arc turning on top of it
                drawArc(
                    color = Color(0x22FFFFFF), startAngle = 0f, sweepAngle = 360f, useCenter = false,
                    topLeft = Offset(inset, inset), size = Size(this.size.width - width, this.size.height - width),
                    style = Stroke(width)
                )
                rotate(angle) {
                    drawArc(
                        brush = Brush.sweepGradient(RingColors), startAngle = 0f, sweepAngle = 250f, useCenter = false,
                        topLeft = Offset(inset, inset), size = Size(this.size.width - width, this.size.height - width),
                        style = Stroke(width, cap = StrokeCap.Round)
                    )
                }
            }
            Box(modifier = Modifier.size(size - stroke * 4).scale(breathe), contentAlignment = Alignment.Center) {
                if (avatar != null) avatar(size - stroke * 4) else GradientTile(icon, size - stroke * 4)
            }
        }
        Spacer(modifier = Modifier.height(if (compact) 8.dp else 16.dp))
        AnimatedContent(
            targetState = steps.getOrElse(stepIndex) { "" },
            transitionSpec = {
                (fadeIn(tween(260)) + slideInVertically(tween(260)) { it / 2 }) togetherWith
                    (fadeOut(tween(160)) + slideOutVertically(tween(160)) { -it / 2 })
            },
            label = "fetching_step"
        ) { line ->
            Text(
                text = line, color = textColor, fontSize = if (compact) 13.sp else 15.sp,
                fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        BouncingDots(color = textColor.copy(alpha = 0.85f))
        if (seconds >= 12) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = if (seconds >= 30) "Still working… Instagram is being slow (${seconds}s)." else "Taking a little longer than usual (${seconds}s)…",
                color = mutedColor, fontSize = 12.sp, textAlign = TextAlign.Center
            )
        }
    }
}

/** A full-screen dim with the loader in the middle, for the moment between tapping a story ring and the story opening. */
@Composable
internal fun FullScreenFetching(
    steps: List<String>,
    avatar: (@Composable (Dp) -> Unit)? = null,
    onCancel: (() -> Unit)? = null
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xE6000000))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { /* block touches */ },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            FetchingLoader(steps = steps, avatar = avatar)
            if (onCancel != null) {
                Spacer(modifier = Modifier.width(1.dp).height(6.dp))
                Text(
                    "Cancel", color = Color(0xFFFB7185), fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onCancel).padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }
    }
}
