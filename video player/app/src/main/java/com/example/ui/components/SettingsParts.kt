package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.Ds
import com.example.ui.theme.GlassBackground
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.bouncyClickable
import com.example.ui.theme.rememberMotionEnabled

private val SunsetBrush = Brush.linearGradient(listOf(Ds.Palette.Orange, Ds.Palette.Pink, Ds.Palette.Violet))
private val InkColor = Color(0xFF0B0D17)

/** The top card of Settings & Guide: the app's mark, its tagline and which build this is. */
@Composable
internal fun SettingsHero(versionName: String, build: Int, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(Ds.Radius.Sheet)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF2E1065), Color(0xFF831843), Color(0xFF7C2D12))))
            .drawBehind {
                drawRect(
                    brush = Brush.radialGradient(
                        listOf(Color(0x66F59E0B), Color.Transparent),
                        center = Offset(size.width * 0.95f, size.height * 0.05f),
                        radius = size.width * 0.6f
                    )
                )
                drawRect(
                    brush = Brush.radialGradient(
                        listOf(Color(0x558B5CF6), Color.Transparent),
                        center = Offset(size.width * 0.05f, size.height * 0.95f),
                        radius = size.width * 0.6f
                    )
                )
            }
            .border(1.dp, Brush.linearGradient(listOf(Color(0x66FFFFFF), Color(0x14FFFFFF))), shape)
            .padding(20.dp)
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(64.dp).background(SunsetBrush, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(34.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("OffScroll", color = Color.White, fontSize = Ds.Type.Headline, fontWeight = FontWeight.ExtraBold)
                    Text(
                        "Your videos. Your feed. Offline.",
                        color = Color(0xCCFFFFFF),
                        fontSize = Ds.Type.Body,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (versionName.isNotBlank()) HeroPill("v$versionName")
                if (build > 0) HeroPill("Build $build")
                HeroPill("No ads")
            }
        }
    }
}

@Composable
private fun HeroPill(text: String) {
    Text(
        text = text,
        color = Color.White,
        fontSize = Ds.Type.Small,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier
            .background(Color(0x26FFFFFF), CircleShape)
            .border(1.dp, Color(0x33FFFFFF), CircleShape)
            .padding(horizontal = 12.dp, vertical = 5.dp)
    )
}

/** One number about the library: a tinted icon, the value and what it is. */
@Composable
internal fun StatTile(icon: ImageVector, value: String, label: String, tint: Color, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(Ds.Radius.Xl)
    Column(
        modifier = modifier
            .clip(shape)
            .background(GlassBackground)
            .border(1.dp, GlassBorder, shape)
            .padding(horizontal = 8.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(36.dp).background(tint.copy(alpha = 0.16f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            value,
            color = Color.White,
            fontSize = Ds.Type.Title,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(label, color = Ds.Palette.Muted, fontSize = Ds.Type.Caption, maxLines = 1)
    }
}

/** A section title with a tinted icon tile and a one-line hint. */
@Composable
internal fun SectionHeader(title: String, subtitle: String, icon: ImageVector, tint: Color, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(tint.copy(alpha = 0.16f), RoundedCornerShape(Ds.Radius.Md))
                .border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(Ds.Radius.Md)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = Color.White, fontSize = Ds.Type.Title, fontWeight = FontWeight.ExtraBold)
            Text(subtitle, color = Ds.Palette.Muted, fontSize = Ds.Type.Small)
        }
    }
}

/** A small coloured circle with a number: ties a spot on the phone drawing to its card below it. */
@Composable
private fun NumberDot(number: Int, color: Color) {
    Box(modifier = Modifier.size(18.dp).background(color, CircleShape), contentAlignment = Alignment.Center) {
        Text("$number", color = InkColor, fontSize = Ds.Type.Caption, fontWeight = FontWeight.ExtraBold)
    }
}

/**
 * A drawing of a phone with every gesture marked on it: the edges (brightness, volume), the middle (swipe, drag,
 * tap). The numbers match the gesture cards under it. The icons breathe gently unless animations are off.
 */
@Composable
internal fun GestureMap(modifier: Modifier = Modifier) {
    val motion = rememberMotionEnabled()
    val pulse by rememberInfiniteTransition(label = "gesture_map_pulse").animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "gesture_map_pulse_alpha"
    )
    val glow = if (motion) pulse else 1f
    val phoneShape = RoundedCornerShape(Ds.Radius.Sheet)

    GlassCard(modifier = modifier) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(width = 156.dp, height = 276.dp)
                    .clip(phoneShape)
                    .background(InkColor)
                    .border(2.dp, Brush.linearGradient(listOf(Color(0x99FFFFFF), Color(0x22FFFFFF))), phoneShape)
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 10.dp)
                        .size(width = 40.dp, height = 5.dp)
                        .background(Color(0x33FFFFFF), CircleShape)
                )
                EdgeStrip(Modifier.align(Alignment.CenterStart).padding(start = 8.dp), 4, Icons.Default.LightMode, Ds.Palette.Warn, glow)
                EdgeStrip(Modifier.align(Alignment.CenterEnd).padding(end = 8.dp), 5, Icons.Default.VolumeUp, Ds.Palette.Cyan, glow)
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    MapItem(2, Icons.Default.SwapVert, Ds.Palette.VioletSoft, glow)
                    MapItem(1, Icons.Default.SwapHoriz, Ds.Palette.PinkSoft, glow)
                    MapItem(3, Icons.Default.TouchApp, Ds.Palette.Orange, glow)
                }
            }
        }
        Text(
            "The numbers match the cards below",
            color = Ds.Palette.Muted,
            fontSize = Ds.Type.Small,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}

@Composable
private fun EdgeStrip(modifier: Modifier, number: Int, icon: ImageVector, color: Color, glow: Float) {
    Column(
        modifier = modifier
            .size(width = 28.dp, height = 156.dp)
            .background(color.copy(alpha = 0.12f), CircleShape)
            .border(1.dp, color.copy(alpha = 0.45f), CircleShape)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        NumberDot(number, color)
        Icon(icon, contentDescription = null, tint = color.copy(alpha = glow), modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun MapItem(number: Int, icon: ImageVector, color: Color, glow: Float) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        NumberDot(number, color)
        Icon(icon, contentDescription = null, tint = color.copy(alpha = glow), modifier = Modifier.size(30.dp))
    }
}

/**
 * One gesture: a tinted icon tile (with its number when it appears on the phone drawing), a title and a sentence.
 * Also used by the first-run tour.
 */
@Composable
fun GestureHelpCard(
    icon: ImageVector,
    title: String,
    description: String,
    accent: Color = Ds.Palette.VioletSoft,
    number: Int? = null
) {
    val shape = RoundedCornerShape(Ds.Radius.Lg)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(GlassBackground)
            .border(1.dp, GlassBorder, shape)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(accent.copy(alpha = 0.16f), RoundedCornerShape(Ds.Radius.Md))
                .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(Ds.Radius.Md)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
            if (number != null) {
                Box(modifier = Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp)) { NumberDot(number, accent) }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = Ds.Type.Body, fontWeight = FontWeight.Bold)
            Text(
                description,
                color = Ds.Palette.Muted,
                fontSize = Ds.Type.Small,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}

/** A setting with an on/off switch. */
@Composable
internal fun ToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(Ds.Radius.Xl)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(GlassBackground)
            .border(1.dp, GlassBorder, shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).background(Ds.Palette.VioletSoft.copy(alpha = 0.16f), RoundedCornerShape(Ds.Radius.Md)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = Ds.Palette.VioletSoft, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = Ds.Type.Body, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Ds.Palette.Muted, fontSize = Ds.Type.Small, lineHeight = 16.sp)
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Ds.Palette.Violet
            )
        )
    }
}

/** A big tappable tile for one action: icon, name and what it does. */
@Composable
internal fun ToolTile(
    icon: ImageVector,
    title: String,
    hint: String,
    tint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(Ds.Radius.Xl)
    Column(
        modifier = modifier
            .heightIn(min = 104.dp)
            .bouncyClickable(pressedScale = 0.96f, onClick = onClick)
            .clip(shape)
            .background(tint.copy(alpha = 0.10f))
            .border(1.dp, tint.copy(alpha = 0.30f), shape)
            .padding(14.dp)
    ) {
        Box(
            modifier = Modifier.size(36.dp).background(tint.copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(title, color = Color.White, fontSize = Ds.Type.Body, fontWeight = FontWeight.Bold)
        Text(hint, color = Ds.Palette.Muted, fontSize = Ds.Type.Small, lineHeight = 16.sp)
    }
}
