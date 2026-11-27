package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.model.FeatureCatalog
import com.example.ui.model.FeatureGroup
import com.example.ui.model.FeatureItem
import com.example.ui.theme.Ds
import com.example.ui.theme.GlassBackground
import com.example.ui.theme.GlassBorder
import com.example.ui.viewmodel.AppUpdateViewModel
import com.example.ui.viewmodel.UpdateState

// What the Updates section shows under the update card: what changed in the newest build, and a full list of
// everything the app does. Both are read-only; the update card above them owns the buttons.

private val SkyBlue = Color(0xFF60A5FA)

private class GroupStyle(val icon: ImageVector, val color: Color)

private fun styleFor(key: String): GroupStyle = when (key) {
    "watch" -> GroupStyle(Icons.Default.PlayCircle, Ds.Palette.Cyan)
    "movie" -> GroupStyle(Icons.Default.Movie, Ds.Palette.Orange)
    "music" -> GroupStyle(Icons.Default.MusicNote, Ds.Palette.VioletSoft)
    "find" -> GroupStyle(Icons.Default.Search, SkyBlue)
    "save" -> GroupStyle(Icons.Default.Download, Ds.Palette.PinkSoft)
    "keep" -> GroupStyle(Icons.Default.Favorite, Ds.Palette.Pink)
    "manage" -> GroupStyle(Icons.Default.Info, Ds.Palette.Warn)
    else -> GroupStyle(Icons.Default.Lock, Ds.Palette.Good)
}

// ------------------------------------------------------------------------------------------------
// What's new
// ------------------------------------------------------------------------------------------------

/** What changed in the newest build. Shows the waiting update's notes if there is one, otherwise the latest known. */
@Composable
fun WhatsNewCard(viewModel: AppUpdateViewModel, modifier: Modifier = Modifier) {
    val latest by viewModel.latest.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pending = (state as? UpdateState.Available)?.release
    val release = pending ?: latest
    val isNew = pending != null
    val accent = if (isNew) Ds.Palette.Warn else Ds.Palette.Cyan
    val shape = RoundedCornerShape(24.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("whats_new_card")
            .animateContentSize(animationSpec = tween(300))
            .clip(shape)
            .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.16f), Ds.Palette.Violet.copy(alpha = 0.08f))))
            .border(1.dp, Brush.linearGradient(listOf(accent.copy(alpha = 0.55f), Color(0x22FFFFFF))), shape)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(Icons.Default.AutoAwesome, accent, 40.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isNew) "What's new in the next update" else "What's new in this version",
                    color = Color.White,
                    fontSize = Ds.Type.BodyLarge,
                    fontWeight = FontWeight.Bold
                )
                release?.let {
                    Text(
                        text = buildString {
                            append("Build ${it.buildNumber}")
                            it.builtAt?.let { built -> append(" · $built") }
                        },
                        color = Ds.Palette.Muted,
                        fontSize = Ds.Type.Small
                    )
                }
            }
            if (isNew) {
                Spacer(modifier = Modifier.width(8.dp))
                Pill("NEW", accent)
            }
        }

        val notes = release?.whatsNew.orEmpty()
        if (notes.isEmpty()) {
            Text(
                text = if (release == null) {
                    "What changed in each update shows up here after the first update check."
                } else {
                    "This build didn't list its changes. The full feature list is just below."
                },
                color = Ds.Palette.Muted,
                fontSize = Ds.Type.Label,
                lineHeight = 19.sp
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                notes.forEach { NoteRow(it, accent) }
            }
        }
    }
}

@Composable
private fun NoteRow(text: String, accent: Color) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier.size(20.dp).clip(CircleShape).background(accent.copy(alpha = 0.20f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Check, contentDescription = null, tint = accent, modifier = Modifier.size(13.dp))
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            color = Ds.Palette.SoftText,
            fontSize = Ds.Type.Body,
            lineHeight = 20.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

// ------------------------------------------------------------------------------------------------
// Everything OffScroll does
// ------------------------------------------------------------------------------------------------

/** Every feature, grouped. One group is open at a time so the list stays calm; tap a group to open or close it. */
@Composable
fun FeatureCatalogCard(modifier: Modifier = Modifier) {
    val groups = FeatureCatalog.groups
    var openKey by rememberSaveable { mutableStateOf<String?>(groups.first().key) }

    Column(
        modifier = modifier.fillMaxWidth().testTag("feature_catalog"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatPill("${groups.size}", "Areas", Ds.Palette.Cyan, Modifier.weight(1f))
            StatPill("${FeatureCatalog.totalFeatures}", "Features", Ds.Palette.VioletSoft, Modifier.weight(1f))
            StatPill("0", "Ads or accounts", Ds.Palette.PinkSoft, Modifier.weight(1f))
        }
        groups.forEach { group ->
            FeatureGroupCard(
                group = group,
                expanded = openKey == group.key,
                onToggle = { openKey = if (openKey == group.key) null else group.key }
            )
        }
    }
}

@Composable
private fun FeatureGroupCard(group: FeatureGroup, expanded: Boolean, onToggle: () -> Unit) {
    val style = styleFor(group.key)
    val shape = RoundedCornerShape(20.dp)
    val chevron by animateFloatAsState(if (expanded) 180f else 0f, tween(250), label = "feature_chevron")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("feature_group_${group.key}")
            .clip(shape)
            .background(if (expanded) style.color.copy(alpha = 0.08f) else GlassBackground)
            .border(1.dp, if (expanded) style.color.copy(alpha = 0.45f) else GlassBorder, shape)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .heightIn(min = 64.dp)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconBadge(style.icon, style.color, 40.dp)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(group.name, color = Color.White, fontSize = Ds.Type.BodyLarge, fontWeight = FontWeight.Bold)
                Text(group.tagline, color = Ds.Palette.Muted, fontSize = Ds.Type.Small)
            }
            Pill("${group.items.size}", style.color)
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse ${group.name}" else "Expand ${group.name}",
                tint = Ds.Palette.Muted,
                modifier = Modifier.size(22.dp).rotate(chevron)
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(tween(280)) + fadeIn(tween(280)),
            exit = shrinkVertically(tween(220)) + fadeOut(tween(160))
        ) {
            Column(
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                group.items.forEach { FeatureRow(it, style.color) }
            }
        }
    }
}

@Composable
private fun FeatureRow(item: FeatureItem, accent: Color) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .padding(top = 7.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(accent)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(item.title, color = Ds.Palette.SoftText, fontSize = Ds.Type.Body, fontWeight = FontWeight.SemiBold)
            Text(item.detail, color = Ds.Palette.Muted, fontSize = Ds.Type.Small, lineHeight = 17.sp)
        }
    }
}

// ------------------------------------------------------------------------------------------------
// Small shared pieces
// ------------------------------------------------------------------------------------------------

@Composable
private fun IconBadge(icon: ImageVector, tint: Color, size: Dp) {
    Box(
        modifier = Modifier.size(size).clip(CircleShape).background(tint.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

@Composable
private fun Pill(text: String, tint: Color) {
    Text(
        text = text,
        color = tint,
        fontSize = Ds.Type.Caption,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
        modifier = Modifier
            .background(tint.copy(alpha = 0.16f), CircleShape)
            .border(1.dp, tint.copy(alpha = 0.38f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
private fun StatPill(value: String, label: String, tint: Color, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(tint.copy(alpha = 0.10f))
            .border(1.dp, tint.copy(alpha = 0.30f), shape)
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(value, color = tint, fontSize = Ds.Type.TitleLarge, fontWeight = FontWeight.ExtraBold)
        Text(label, color = Ds.Palette.Muted, fontSize = Ds.Type.Caption, maxLines = 1)
    }
}
