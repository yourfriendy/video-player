package com.example.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
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

    val safeTotal = if (totalDurationMs > 0) totalDurationMs else video.durationMs.coerceAtLeast(1L)
    val currentRatio = if (isScrubbing) {
        scrubRatio
    } else {
        (currentProgressMs.toFloat() / safeTotal.toFloat()).coerceIn(0f, 1f)
    }

    val displayProgressMs = if (isScrubbing) (scrubRatio * safeTotal).toLong() else currentProgressMs

    val progressBarHeight by animateDpAsState(
        targetValue = if (isScrubbing) 6.dp else 4.dp,
        label = "progress_bar_height"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color(0x66000000),
                        Color(0xE6000000)
                    )
                )
            )
            .padding(start = 16.dp, end = 84.dp, bottom = 14.dp, top = 32.dp)
    ) {
        // Frosted Glass Folder Pill
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(GlassBackground, CircleShape)
                .border(1.dp, GlassBorder, CircleShape)
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                tint = Color(0xFF38BDF8),
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

        Spacer(modifier = Modifier.height(8.dp))

        // Video Display Name
        Text(
            text = video.displayName,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                letterSpacing = (-0.2).sp
            ),
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Dot-separated Subtitle metadata: Folder • Size • Duration • HD
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "${video.formattedSize}  •  ${video.formattedDuration}",
                color = Color(0x99FFFFFF),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )

            if (video.width >= 1080 || video.height >= 1080) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(Color(0x33A78BFA), RoundedCornerShape(4.dp))
                        .border(1.dp, Color(0x44A78BFA), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Hd,
                        contentDescription = "HD",
                        tint = Color(0xFFC4B5FD),
                        modifier = Modifier.size(12.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Frosted Glass Progress Scrubbing Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(20.dp)
                .testTag("video_progress_scrubber")
                .onSizeChanged { barWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(video.id) {
                    detectHorizontalDragGestures(
                        onDragStart = { offset ->
                            onInteraction()
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
            // Track background
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(progressBarHeight)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color(0x33FFFFFF))
            )

            // Active progress fill (Frosted pure white with subtle gradient)
            Box(
                modifier = Modifier
                    .fillMaxWidth(currentRatio)
                    .height(progressBarHeight)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Color.White)
            )

            // Scrubber thumb
            if (isScrubbing) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(currentRatio),
                    contentAlignment = Alignment.CenterEnd
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .shadow(4.dp, CircleShape)
                            .background(Color.White, CircleShape)
                    )
                }
            }
        }

        // Time indicators (Current / Total)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatTime(displayProgressMs),
                color = if (isScrubbing) Color(0xFFC4B5FD) else Color(0x66FFFFFF),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
            Text(
                text = formatTime(safeTotal),
                color = Color(0x66FFFFFF),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp
            )
        }
    }
}

private fun formatTime(millis: Long): String {
    val totalSec = (millis.coerceAtLeast(0L)) / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return String.format(Locale.getDefault(), "%d:%02d", min, sec)
}

