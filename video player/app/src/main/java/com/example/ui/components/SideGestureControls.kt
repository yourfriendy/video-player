package com.example.ui.components

import android.app.Activity
import android.media.AudioManager
import android.provider.Settings
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessHigh
import androidx.compose.material.icons.filled.BrightnessLow
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

enum class SideGestureKind { BRIGHTNESS, VOLUME }

/** Tiny state holder so only the HUD recomposes while a finger is dragging. */
@Stable
class SideGestureState {
    var kind by mutableStateOf(SideGestureKind.BRIGHTNESS)
    var fraction by mutableFloatStateOf(0f)
    var active by mutableStateOf(false)
}

private fun currentBrightness(activity: Activity?): Float {
    val windowValue = activity?.window?.attributes?.screenBrightness ?: -1f
    if (windowValue >= 0f) return windowValue
    return try {
        val resolver = activity?.contentResolver ?: return 0.5f
        Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS) / 255f
    } catch (_: Exception) {
        0.5f
    }
}

private fun applyBrightness(activity: Activity?, value: Float) {
    val window = activity?.window ?: return
    val params = window.attributes
    params.screenBrightness = value.coerceIn(0.02f, 1f)
    window.attributes = params
}

/**
 * Edge swipes like every video app: slide up/down on the LEFT edge for brightness and on the
 * RIGHT edge for volume. Swipes that start in the middle are left alone, so the feed still
 * scrolls, taps still work, and horizontal seeking is unaffected.
 */
fun Modifier.sideSwipeControls(
    state: SideGestureState,
    activity: Activity?,
    audioManager: AudioManager,
    onVolumeRaised: () -> Unit
): Modifier = pointerInput(activity, audioManager) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val edge = size.width * 0.22f
        val isLeft = down.position.x < edge
        val isRight = down.position.x > size.width - edge
        if (!isLeft && !isRight) return@awaitEachGesture

        var overSlopY = 0f
        val slopChange = awaitVerticalTouchSlopOrCancellation(down.id) { change, overSlop ->
            change.consume()
            overSlopY = overSlop
        } ?: return@awaitEachGesture

        val kind = if (isLeft) SideGestureKind.BRIGHTNESS else SideGestureKind.VOLUME
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
        val startFraction = if (kind == SideGestureKind.BRIGHTNESS) {
            currentBrightness(activity)
        } else {
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume
        }
        val travelPx = size.height * 0.7f // a 70%-of-screen swipe covers the full range
        var totalDy = overSlopY
        var lastAppliedStep = -1
        var raisedReported = false

        state.kind = kind
        state.fraction = startFraction
        state.active = true

        fun update(fraction: Float) {
            state.fraction = fraction
            if (kind == SideGestureKind.BRIGHTNESS) {
                // ~100 steps is plenty and avoids hammering the window manager
                val step = (fraction * 100).roundToInt()
                if (step != lastAppliedStep) {
                    lastAppliedStep = step
                    applyBrightness(activity, fraction)
                }
            } else {
                val step = (fraction * maxVolume).roundToInt()
                if (step != lastAppliedStep) {
                    lastAppliedStep = step
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, step, 0)
                }
                if (!raisedReported && fraction > startFraction) {
                    raisedReported = true
                    onVolumeRaised()
                }
            }
        }

        update((startFraction - totalDy / travelPx).coerceIn(0f, 1f))
        verticalDrag(slopChange.id) { change ->
            totalDy += change.positionChange().y
            change.consume()
            update((startFraction - totalDy / travelPx).coerceIn(0f, 1f))
        }
        state.active = false
    }
}

/** The floating level indicator shown while a side swipe is in progress. */
@Composable
fun SideGestureHud(state: SideGestureState, modifier: Modifier = Modifier) {
    val alpha by animateFloatAsState(
        targetValue = if (state.active) 1f else 0f,
        animationSpec = if (state.active) tween(90) else tween(280, delayMillis = 650),
        label = "sideHudAlpha"
    )
    Box(modifier = modifier.fillMaxSize()) {
        if (alpha > 0.01f) {
            val isBrightness = state.kind == SideGestureKind.BRIGHTNESS
            val fraction = state.fraction
            val icon = when {
                isBrightness && fraction < 0.5f -> Icons.Default.BrightnessLow
                isBrightness -> Icons.Default.BrightnessHigh
                fraction <= 0.001f -> Icons.Default.VolumeOff
                fraction < 0.5f -> Icons.Default.VolumeDown
                else -> Icons.Default.VolumeUp
            }
            val accent = if (isBrightness) {
                listOf(Color(0xFFFDE68A), Color(0xFFF59E0B))
            } else {
                listOf(Color(0xFF5EEAD4), Color(0xFF8B5CF6))
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(if (isBrightness) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 26.dp)
                    .graphicsLayer { this.alpha = alpha }
                    .background(Color(0xB3000000), RoundedCornerShape(26.dp))
                    .padding(horizontal = 14.dp, vertical = 16.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = if (isBrightness) "Brightness" else "Volume",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .width(7.dp)
                        .height(130.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0x33FFFFFF))
                ) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .fillMaxHeight(fraction.coerceIn(0f, 1f))
                            .background(Brush.verticalGradient(accent.reversed()))
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "${(fraction * 100).roundToInt()}%",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
