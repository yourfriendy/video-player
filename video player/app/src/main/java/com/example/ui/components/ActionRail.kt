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
import kotlinx.coroutines.launch

/**
 * The buttons down the right edge of the video, in two tidy groups:
 *  - Like: the one big heart (56 dp), with its label;
 *  - one glass capsule holding the rest as equal 44 dp round buttons: playlist, sound, share, details, this video's options.
 * Everything shares one centre line, one icon size and one spacing, and the whole stack is about 310 dp tall, so on a
 * small phone it can never run up into the top bar. Switched-on states (muted, liked) glow in their own colour.
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
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        FavoriteActionButton(isFavorite = video.isFavorite, onClick = onLike)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .shadow(10.dp, CircleShape)
                .background(GlassBackgroundElevated, CircleShape)
                .border(
                    1.dp,
                    Brush.verticalGradient(listOf(Color(0x66FFFFFF), Color(0x14FFFFFF))),
                    CircleShape
                )
                .padding(horizontal = 4.dp, vertical = 6.dp)
        ) {
            RailButton(
                icon = Icons.Default.PlaylistAdd,
                description = "Add to playlist",
                testTag = "action_playlist_button",
                onClick = onOpenPlaylist
            )
            RailButton(
                icon = if (isMuted) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
                description = if (isMuted) "Sound is off. Tap to turn on" else "Sound is on. Tap to mute",
                testTag = "action_mute_button",
                glow = if (isMuted) Color(0xFFFBBF24) else null,
                onClick = onToggleMute
            )
            RailButton(
                icon = Icons.Default.Share,
                description = "Share",
                testTag = "action_share_button",
                onClick = onShare
            )
            RailButton(
                icon = Icons.Default.Info,
                description = "Video details and storage",
                testTag = "action_details_button",
                accent = Brush.linearGradient(listOf(Color(0xCC8B5CF6), Color(0xCC22D3EE))),
                onClick = onOpenDetails
            )
            // Horizontal dots = this video's options; the vertical dots in the top bar are the whole app's
            RailButton(
                icon = Icons.Default.MoreHoriz,
                description = "More for this video",
                testTag = "action_more_button",
                onClick = onOpenMore
            )
        }
    }
}

/** One round 44 dp button in the capsule. [glow] tints it while switched on; [accent] gives it a gradient fill. */
@Composable
private fun RailButton(
    icon: ImageVector,
    description: String,
    testTag: String,
    onClick: () -> Unit,
    glow: Color? = null,
    accent: Brush? = null
) {
    val glowAlpha by animateFloatAsState(
        targetValue = if (glow != null) 1f else 0f,
        animationSpec = tween(260),
        label = "rail_glow"
    )
    val glowColor = glow ?: Color.Transparent
    val iconTint by animateColorAsState(
        targetValue = glow ?: Color.White,
        animationSpec = tween(260),
        label = "rail_icon_tint"
    )
    Box(
        modifier = Modifier
            .testTag(testTag)
            .size(44.dp)
            .bouncyClickable(pressedScale = 0.88f, onClick = onClick)
            .then(
                if (accent != null) {
                    Modifier
                        .shadow(8.dp, CircleShape, ambientColor = Color(0xFF8B5CF6), spotColor = Color(0xFF8B5CF6))
                        .background(accent, CircleShape)
                } else {
                    Modifier.drawBehind {
                        drawCircle(color = glowColor.copy(alpha = 0.18f * glowAlpha))
                    }
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = iconTint,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
private fun FavoriteActionButton(
    isFavorite: Boolean,
    onClick: () -> Unit
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

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.testTag("action_favorite_button")
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .bouncyClickable(pressedScale = 0.86f, onClick = onClick)
                .drawBehind {
                    val t = burst.value
                    if (t > 0f && t < 1f) {
                        drawCircle(
                            color = FrostedRose.copy(alpha = (1f - t) * 0.65f),
                            radius = (size.minDimension / 2f) * (1f + t * 0.9f),
                            style = Stroke(width = 3.dp.toPx() * (1f - t) + 0.5f)
                        )
                    }
                }
                .shadow(
                    if (isFavorite) 14.dp else 10.dp, CircleShape,
                    ambientColor = if (isFavorite) FrostedRose else Color.Black,
                    spotColor = if (isFavorite) FrostedRose else Color.Black
                )
                .background(
                    brush = if (isFavorite) {
                        Brush.linearGradient(listOf(Color(0x66FB7185), Color(0x66F43F5E)))
                    } else {
                        SolidColor(GlassBackgroundElevated)
                    },
                    shape = CircleShape
                )
                .border(
                    1.dp,
                    if (isFavorite) FrostedRose.copy(alpha = 0.6f) else GlassBorder,
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = if (isFavorite) "Liked" else "Like video",
                tint = iconColor,
                modifier = Modifier
                    .size(28.dp)
                    .graphicsLayer {
                        val s = if (isFavorite) pop.value * 1.15f else 1f
                        scaleX = s
                        scaleY = s
                    }
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (isFavorite) "Liked" else "Like",
            color = if (isFavorite) FrostedRoseLight else Color(0xCCFFFFFF),
            fontSize = Ds.Type.Caption,
            fontWeight = FontWeight.SemiBold,
            style = androidx.compose.ui.text.TextStyle(
                shadow = androidx.compose.ui.graphics.Shadow(
                    color = Color.Black,
                    blurRadius = 4f
                )
            )
        )
    }
}
