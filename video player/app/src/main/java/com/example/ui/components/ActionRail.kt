package com.example.ui.components

import com.example.R
import androidx.compose.ui.res.stringResource
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.LocalVideo
import com.example.ui.theme.Ds
import com.example.ui.theme.FrostedRose
import com.example.ui.theme.FrostedRoseLight
import com.example.ui.theme.GlassBackgroundElevated
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.bouncyClickable
import com.example.ui.theme.staggeredEntrance
import kotlinx.coroutines.launch

/**
 * The buttons down the right edge of the video: Like, playlist, sound, share, details and this video's options.
 * Every one is the same: a 48 dp round button of dark glass (dark enough to read over a bright picture), a thin light
 * rim, a soft shadow and a white 24 dp icon, all on one centre line with equal gaps. Nothing is a different colour;
 * only a switched-on button glows in its own colour (a liked heart in rose, sound off in amber).
 * They fade and rise in one after another whenever the controls appear.
 */
@Composable
fun ActionRail(
    video: LocalVideo,
    isMuted: Boolean,
    onLike: () -> Unit,
    onOpenPlaylist: () -> Unit,
    onToggleMute: () -> Unit,
    onShare: () -> Unit,
    onOpenMore: () -> Unit,
    modifier: Modifier = Modifier,
    onOpenDetails: () -> Unit = {}
) {
    Column(
        modifier = modifier.padding(end = 12.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        FavoriteActionButton(isFavorite = video.isFavorite, onClick = onLike, modifier = Modifier.staggeredEntrance(0))
        RailButton(
            icon = Icons.Default.PlaylistAdd,
            description = "Add to playlist",
            testTag = "action_playlist_button",
            onClick = onOpenPlaylist,
            modifier = Modifier.staggeredEntrance(1)
        )
        RailButton(
            icon = if (isMuted) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
            description = if (isMuted) "Sound is off. Tap to turn on" else "Sound is on. Tap to mute",
            testTag = "action_mute_button",
            glow = if (isMuted) Color(0xFFFBBF24) else null,
            onClick = onToggleMute,
            modifier = Modifier.staggeredEntrance(2)
        )
        RailButton(
            icon = Icons.Default.Share,
            description = "Share",
            testTag = "action_share_button",
            onClick = onShare,
            modifier = Modifier.staggeredEntrance(3)
        )
        RailButton(
            icon = Icons.Default.Info,
            description = "Video details and storage",
            testTag = "action_details_button",
            onClick = onOpenDetails,
            modifier = Modifier.staggeredEntrance(4)
        )
        // Horizontal dots = this video's options; the vertical dots in the top bar are the whole app's
        RailButton(
            icon = Icons.Default.MoreHoriz,
            description = "More for this video",
            testTag = "action_more_button",
            onClick = onOpenMore,
            modifier = Modifier.staggeredEntrance(5)
        )
    }
}

/**
 * The one look every rail button shares. [glow] (when set) tints the fill and rim in that colour for a switched-on
 * state. [behind] draws first, for effects like the like burst.
 */
@Composable
private fun RailCircle(
    modifier: Modifier = Modifier,
    glow: Color? = null,
    behind: androidx.compose.ui.graphics.drawscope.DrawScope.() -> Unit = {},
    content: @Composable () -> Unit
) {
    val glowAlpha by animateFloatAsState(
        targetValue = if (glow != null) 1f else 0f,
        animationSpec = tween(260),
        label = "rail_glow"
    )
    val tint = glow ?: Color.Transparent
    val rim = Brush.linearGradient(listOf(Color(0x59FFFFFF), Color(0x1FFFFFFF)))
    Box(
        modifier = modifier
            .size(48.dp)
            .drawBehind(behind)
            .shadow(8.dp, CircleShape)
            .background(Color(0x99000000), CircleShape)
            .drawBehind { drawCircle(color = tint.copy(alpha = 0.30f * glowAlpha)) }
            .border(1.dp, rim, CircleShape)
            .drawBehind {
                if (glowAlpha > 0f) {
                    drawCircle(
                        color = tint.copy(alpha = 0.75f * glowAlpha),
                        radius = size.minDimension / 2f - 0.5.dp.toPx(),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) { content() }
}

/** One of the standard rail buttons: dark glass circle with a white icon. */
@Composable
private fun RailButton(
    icon: ImageVector,
    description: String,
    testTag: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glow: Color? = null
) {
    val iconTint by animateColorAsState(
        targetValue = glow ?: Color.White,
        animationSpec = tween(260),
        label = "rail_icon_tint"
    )
    Box(modifier = modifier.testTag(testTag).bouncyClickable(pressedScale = 0.88f, onClick = onClick)) {
        RailCircle(glow = glow) {
            Icon(imageVector = icon, contentDescription = description, tint = iconTint, modifier = Modifier.size(24.dp))
        }
    }
}

/** The heart. It looks like every other button, and when you like a video it pops and a rose ring bursts outward. */
@Composable
private fun FavoriteActionButton(
    isFavorite: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pop = remember { Animatable(1f) }
    val burst = remember { Animatable(0f) }
    var wasFavorite by remember { mutableStateOf(isFavorite) }

    // Heart pops and a ring bursts outward the moment a video becomes liked
    LaunchedEffect(isFavorite) {
        if (isFavorite && !wasFavorite) {
            burst.snapTo(0f)
            launch { burst.animateTo(1f, tween(520, easing = FastOutSlowInEasing)) }
            pop.snapTo(0.55f)
            pop.animateTo(
                1f,
                spring(
                    dampingRatio = Spring.DampingRatioHighBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            )
        }
        wasFavorite = isFavorite
    }

    val iconColor by animateColorAsState(
        targetValue = if (isFavorite) FrostedRose else Color.White,
        animationSpec = tween(260),
        label = "heart_color"
    )

    Box(
        modifier = modifier
            .testTag("action_favorite_button")
            .bouncyClickable(pressedScale = 0.86f, onClick = onClick)
    ) {
        RailCircle(
            glow = if (isFavorite) FrostedRose else null,
            behind = {
                val t = burst.value
                if (t > 0f && t < 1f) {
                    drawCircle(
                        color = FrostedRose.copy(alpha = (1f - t) * 0.65f),
                        radius = (size.minDimension / 2f) * (1f + t * 0.9f),
                        style = Stroke(width = 3.dp.toPx() * (1f - t) + 0.5f)
                    )
                }
            }
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = if (isFavorite) "Liked. Tap to remove" else "Like this video",
                tint = iconColor,
                modifier = Modifier
                    .size(26.dp)
                    .graphicsLayer {
                        val s = if (isFavorite) pop.value * 1.15f else 1f
                        scaleX = s
                        scaleY = s
                    }
            )
        }
    }
}
