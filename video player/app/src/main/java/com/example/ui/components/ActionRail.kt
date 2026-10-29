package com.example.ui.components

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.LocalVideo
import com.example.ui.theme.FrostedRose
import com.example.ui.theme.FrostedRoseLight
import com.example.ui.theme.GlassBackgroundElevated
import com.example.ui.theme.GlassBorder

@Composable
fun ActionRail(
    video: LocalVideo,
    isMuted: Boolean,
    onLike: () -> Unit,
    onOpenPlaylist: () -> Unit,
    onToggleMute: () -> Unit,
    onShare: () -> Unit,
    onOpenMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(end = 12.dp, bottom = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Favorite / Like Heart Button (Cannot be unliked, addictive bounce)
        FavoriteActionButton(
            isFavorite = video.isFavorite,
            onClick = onLike
        )

        // 2. Add to Playlist Button (Replaces Watch Later)
        ActionRailItem(
            icon = Icons.Default.PlaylistAdd,
            label = "Playlist",
            tint = Color(0xFFC4B5FD),
            testTag = "action_playlist_button",
            onClick = onOpenPlaylist
        )

        // 3. Sound Mute / Unmute
        ActionRailItem(
            icon = if (isMuted) Icons.Default.VolumeMute else Icons.Default.VolumeUp,
            label = if (isMuted) "Muted" else "Sound",
            tint = if (isMuted) Color(0xFFFBBF24) else Color.White,
            testTag = "action_mute_button",
            onClick = onToggleMute
        )

        // 4. Share Button
        ActionRailItem(
            icon = Icons.Default.Share,
            label = "Share",
            testTag = "action_share_button",
            onClick = onShare
        )

        // 5. More Options (Speed, Display Aspect, Info, Delete)
        ActionRailItem(
            icon = Icons.Default.MoreVert,
            label = "More",
            testTag = "action_more_button",
            onClick = onOpenMore
        )
    }
}

@Composable
private fun FavoriteActionButton(
    isFavorite: Boolean,
    onClick: () -> Unit
) {
    val heartScale by animateFloatAsState(
        targetValue = if (isFavorite) 1.25f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "heart_scale"
    )

    val iconColor by animateColorAsState(
        targetValue = if (isFavorite) FrostedRose else Color.White,
        label = "heart_color"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.testTag("action_favorite_button")
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .shadow(10.dp, CircleShape)
                .background(
                    if (isFavorite) Color(0x33FB7185) else GlassBackgroundElevated,
                    CircleShape
                )
                .border(
                    1.dp,
                    if (isFavorite) FrostedRose.copy(alpha = 0.6f) else GlassBorder,
                    CircleShape
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true, radius = 24.dp),
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = if (isFavorite) "Liked" else "Like video",
                tint = iconColor,
                modifier = Modifier
                    .size(24.dp)
                    .scale(heartScale)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = if (isFavorite) "Liked" else "Like",
            color = if (isFavorite) FrostedRoseLight else Color(0xCCFFFFFF),
            fontSize = 10.sp,
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

@Composable
private fun ActionRailItem(
    icon: ImageVector,
    label: String,
    testTag: String,
    tint: Color = Color.White,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.testTag(testTag)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .shadow(8.dp, CircleShape)
                .background(GlassBackgroundElevated, CircleShape)
                .border(1.dp, GlassBorder, CircleShape)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = true, radius = 24.dp),
                    onClick = onClick
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = Color(0xCCFFFFFF),
            fontSize = 10.sp,
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
