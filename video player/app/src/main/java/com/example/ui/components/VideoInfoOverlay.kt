package com.example.ui.components

import kotlin.math.roundToInt
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.foundation.layout.offset
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Hd
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.LocalVideo
import com.example.ui.model.smartTitle
import com.example.ui.theme.Ds
import com.example.ui.theme.GlassBackground
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassBorderSubtle
import java.util.Locale

@Composable
fun VideoInfoOverlay(
    video: LocalVideo,
    currentProgressMsProvider: () -> Long,
    totalDurationMs: Long,
    onSeek: (Float) -> Unit,
    onInteraction: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    // Read in this scope so the 150ms progress ticks only recompose the overlay, not the whole feed
    val currentProgressMs = currentProgressMsProvider()
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubRatio by remember { mutableFloatStateOf(0f) }
    var barWidthPx by remember { mutableFloatStateOf(1f) }
    val haptics = LocalHapticFeedback.current
    val smart = remember(video.displayName) { smartTitle(video.displayName) }

    val safeTotal = if (totalDurationMs > 0) totalDurationMs else video.durationMs.coerceAtLeast(1L)
    val currentRatio = if (isScrubbing) {
        scrubRatio
    } else {
        (currentProgressMs.toFloat() / safeTotal.toFloat()).coerceIn(0f, 1f)
    }
    val displayProgressMs = if (isScrubbing) (scrubRatio * safeTotal).toLong() else currentProgressMs

    // The bar thickens and the playhead swells while you hold it: the motion shows the bar is "picked up"
    val trackHeight by animateDpAsState(
        targetValue = if (isScrubbing) 8.dp else 4.dp,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 500f),
        label = "scrub_track_height"
    )
    val thumbSize by animateDpAsState(
        targetValue = if (isScrubbing) 20.dp else 10.dp,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 500f),
        label = "scrub_thumb_size"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.35f to Color(0x55000000),
                    0.70f to Color(0xB3000000),
                    1f to Color(0xF2000000)
                )
            )
            .padding(start = 16.dp, end = 84.dp, bottom = 12.dp, top = 56.dp)
    ) {
        // Where it lives, and whether it is HD
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .background(GlassBackground, CircleShape)
                    .border(1.dp, GlassBorder, CircleShape)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = Color(0xFF5EEAD4),
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = video.bucketDisplayName,
                    color = Color(0xEEFFFFFF),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            smart.kind?.let { KindChip(it) }
            if (video.width >= 1080 || video.height >= 1080) {
                Icon(
                    imageVector = Icons.Default.Hd,
                    contentDescription = "HD",
                    tint = Color(0xFFC4B5FD),
                    modifier = Modifier
                        .background(Color(0x33A78BFA), RoundedCornerShape(8.dp))
                        .border(1.dp, Color(0x44A78BFA), RoundedCornerShape(8.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                        .size(14.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // The title is the main thing on screen: big and tight, with an accent bar, readable over any frame
        Row(modifier = Modifier.height(IntrinsicSize.Min), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(Color(0xFF5EEAD4), Color(0xFF8B5CF6))))
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = smart.title,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = Ds.Type.TitleLarge,
                    lineHeight = 26.sp,
                    letterSpacing = (-0.4).sp,
                    shadow = Shadow(color = Color(0x99000000), blurRadius = 8f)
                ),
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Facts as small tinted chips. The date chip shrinks with an ellipsis rather than pushing past the action rail.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            InfoChip("💾", video.formattedSize, Color(0xFF5EEAD4))
            InfoChip("⏱", video.formattedDuration, Color(0xFFA78BFA))
            if (video.formattedDateShort.isNotBlank()) {
                InfoChip("📅", video.formattedDateShort, Color(0xFFFBBF24), Modifier.weight(1f, fill = false))
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // ---- Scrubber: a lit trail from cyan to violet, with a floating time while you drag ----
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .testTag("video_progress_scrubber")
                .onSizeChanged { barWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(video.id) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            onInteraction()
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            isScrubbing = true
                            scrubRatio = (offset.x / barWidthPx).coerceIn(0f, 1f)
                        },
                        onDragEnd = {
                            isScrubbing = false
                            onSeek(scrubRatio)
                        },
                        onDragCancel = {
                            isScrubbing = false
                        },
                        onHorizontalDrag = { change, _ ->
                            change.consume()
                            onInteraction()
                            scrubRatio = (change.position.x / barWidthPx).coerceIn(0f, 1f)
                        }
                    )
                }
                .pointerInput(video.id) {
                    detectTapGestures { offset ->
                        onInteraction()
                        val tapRatio = (offset.x / barWidthPx).coerceIn(0f, 1f)
                        onSeek(tapRatio)
                    }
                },
            contentAlignment = Alignment.CenterStart
        ) {
            // Track
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(trackHeight)
                    .clip(CircleShape)
                    .background(Color(0x38FFFFFF))
            )
            // What has been watched
            Box(
                modifier = Modifier
                    .fillMaxWidth(currentRatio)
                    .height(trackHeight)
                    .clip(CircleShape)
                    .background(Brush.horizontalGradient(listOf(Color(0xFF5EEAD4), Color(0xFF8B5CF6))))
            )
            // Playhead with a soft cyan glow
            Box(modifier = Modifier.fillMaxWidth(currentRatio), contentAlignment = Alignment.CenterEnd) {
                Box(
                    modifier = Modifier
                        .size(thumbSize)
                        .shadow(10.dp, CircleShape, ambientColor = Color(0xFF5EEAD4), spotColor = Color(0xFF5EEAD4))
                        .background(Color.White, CircleShape)
                )
            }
            // Floating time while dragging
            if (isScrubbing) {
                var bubbleWidthPx by remember { mutableIntStateOf(0) }
                Text(
                    text = formatTime(displayProgressMs),
                    style = TimeStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.ExtraBold),
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset {
                            val x = (scrubRatio * barWidthPx - bubbleWidthPx / 2f)
                                .coerceIn(0f, (barWidthPx - bubbleWidthPx).coerceAtLeast(0f))
                            IntOffset(x.roundToInt(), -50.dp.roundToPx())
                        }
                        .onSizeChanged { bubbleWidthPx = it.width }
                        .background(Color(0xF2111827), RoundedCornerShape(12.dp))
                        .border(1.dp, Color(0x805EEAD4), RoundedCornerShape(12.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        // Time indicators
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatTime(displayProgressMs),
                style = TimeStyle,
                color = if (isScrubbing) Color(0xFF5EEAD4) else Color(0xB3FFFFFF)
            )
            Text(
                text = formatTime(safeTotal),
                style = TimeStyle,
                color = Color(0x80FFFFFF)
            )
        }
    }
}

/** Digits keep the same width so the clock doesn't jitter as it counts. */
private val TimeStyle = TextStyle(
    fontSize = 11.sp,
    fontWeight = FontWeight.SemiBold,
    fontFeatureSettings = "tnum"
)

@Composable
private fun InfoChip(icon: String, text: String, tint: Color, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .background(tint.copy(alpha = 0.14f), CircleShape)
            .border(1.dp, tint.copy(alpha = 0.30f), CircleShape)
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        Text(icon, fontSize = Ds.Type.Caption)
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = text,
            color = Color(0xE6FFFFFF),
            fontSize = Ds.Type.Caption,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
    }
}

/** Where the file came from (Camera, Screen recording, WhatsApp), shown beside the folder chip. */
@Composable
private fun KindChip(label: String) {
    Text(
        text = label,
        color = Color(0xFFDDD6FE),
        fontSize = Ds.Type.Caption,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier
            .background(Color(0x338B5CF6), CircleShape)
            .border(1.dp, Color(0x558B5CF6), CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

private fun formatTime(millis: Long): String {
    val totalSec = (millis.coerceAtLeast(0L)) / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format(Locale.getDefault(), "%d:%02d", min, sec)
}

