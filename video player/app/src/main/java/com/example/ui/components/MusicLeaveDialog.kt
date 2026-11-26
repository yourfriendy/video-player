package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.music.QueueEntry
import com.example.ui.theme.Ds
import com.example.ui.theme.GlassBackground
import com.example.ui.theme.GlassBorder

/**
 * Shown when you leave the music tab while a song is playing, so the videos never start by surprise.
 * Two clear choices: stay in the music (the safe, highlighted one) or switch to the videos, which pauses the music.
 * Backing out of the pop-up, or tapping outside it, means "stay".
 */
@Composable
internal fun MusicLeaveDialog(
    nowPlaying: QueueEntry?,
    onStay: () -> Unit,
    onSwitchToVideos: () -> Unit
) {
    Dialog(
        onDismissRequest = onStay,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        // The card springs in softly the moment the pop-up appears
        val appear = remember { MutableTransitionState(false).apply { targetState = true } }
        AnimatedVisibility(
            visibleState = appear,
            enter = fadeIn(tween(180)) + scaleIn(spring(dampingRatio = 0.75f, stiffness = 380f), initialScale = 0.88f)
        ) {
            val shape = RoundedCornerShape(Ds.Radius.Sheet)
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .fillMaxWidth()
                    .clip(shape)
                    .background(Color(0xFF14151F))
                    .drawBehind {
                        drawRect(
                            brush = Brush.radialGradient(
                                listOf(Color(0x558B5CF6), Color.Transparent),
                                center = Offset(size.width * 0.15f, 0f),
                                radius = size.width * 0.8f
                            )
                        )
                        drawRect(
                            brush = Brush.radialGradient(
                                listOf(Color(0x44F97316), Color.Transparent),
                                center = Offset(size.width * 0.95f, size.height),
                                radius = size.width * 0.7f
                            )
                        )
                    }
                    .border(1.dp, Brush.verticalGradient(listOf(Color(0x66FFFFFF), Color(0x14FFFFFF))), shape)
                    .padding(horizontal = 22.dp, vertical = 26.dp)
                    .testTag("music_leave_dialog"),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // music  ->  videos
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    IconBubble(Icons.Default.MusicNote, listOf(Ds.Palette.Orange, Ds.Palette.Pink))
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null,
                        tint = Color(0x99FFFFFF), modifier = Modifier.padding(horizontal = 14.dp).size(22.dp)
                    )
                    IconBubble(Icons.Default.Videocam, listOf(Ds.Palette.Violet, Ds.Palette.Cyan))
                }

                Spacer(Modifier.height(20.dp))
                Text(
                    "Switch to your videos?", color = Color.White, fontSize = Ds.Type.TitleLarge,
                    fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center
                )
                Text(
                    "Your music is playing. Going to the videos will pause it.",
                    color = Ds.Palette.Muted, fontSize = Ds.Type.Body, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp)
                )

                if (nowPlaying != null) {
                    Row(
                        modifier = Modifier
                            .padding(top = 18.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(Ds.Radius.Lg))
                            .background(GlassBackground)
                            .border(1.dp, GlassBorder, RoundedCornerShape(Ds.Radius.Lg))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AlbumArt(nowPlaying.albumId, Modifier.size(44.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                nowPlaying.title, color = Color.White, fontSize = Ds.Type.Body, fontWeight = FontWeight.Bold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                nowPlaying.artist, color = Ds.Palette.Muted, fontSize = Ds.Type.Small,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                        EqualizerBars(playing = true, color = Ds.Palette.PinkSoft)
                    }
                }

                Spacer(Modifier.height(22.dp))
                GradientButton(
                    text = "Stay in music",
                    onClick = onStay,
                    modifier = Modifier.fillMaxWidth().testTag("music_leave_stay"),
                    icon = Icons.Default.MusicNote
                )
                Spacer(Modifier.height(10.dp))
                OutlineButton(
                    text = "Switch to videos",
                    onClick = onSwitchToVideos,
                    modifier = Modifier.fillMaxWidth().testTag("music_leave_switch")
                )
            }
        }
    }
}

@Composable
private fun IconBubble(icon: androidx.compose.ui.graphics.vector.ImageVector, colors: List<Color>) {
    Box(
        modifier = Modifier.size(64.dp).background(Brush.linearGradient(colors), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
    }
}
