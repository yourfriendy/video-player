package com.example.ui.components

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.model.DownloaderTab
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.Slate950
import kotlin.math.roundToInt

/*
 * The shared look of the downloader: a slowly moving aurora background, frosted cards, gradient icon tiles,
 * use-case hero cards and a sliding tab bar. Screens compose these instead of styling everything by hand.
 */

internal val DlCyan = Color(0xFF5EEAD4)
internal val DlViolet = Color(0xFF8B5CF6)
internal val DlPink = Color(0xFFEC4899)
internal val DlOrange = Color(0xFFF59E0B)
internal val DlMuted = Color(0xFF94A3B8)
internal val DlSoftText = Color(0xFFE2E8F0)
internal val DlGood = Color(0xFF34D399)
internal val DlWarn = Color(0xFFFBBF24)
internal val DlBad = Color(0xFFFB7185)

/** The signature orange → pink → violet. */
internal val DlSunset = listOf(DlOrange, DlPink, DlViolet)

/** Deep navy with three soft colour glows that drift slowly (cheap: three gradients, no bitmaps). */
@Composable
internal fun AuroraBackground(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "aurora")
    val a by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(15_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "aurora_a"
    )
    val b by transition.animateFloat(
        initialValue = 1f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(21_000, easing = LinearEasing), RepeatMode.Reverse),
        label = "aurora_b"
    )
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Slate950)
            .drawBehind {
                val w = size.width
                val h = size.height
                drawRect(
                    Brush.radialGradient(
                        listOf(DlViolet.copy(alpha = 0.32f), Color.Transparent),
                        center = Offset(w * (0.70f + 0.25f * a), h * (0.02f + 0.06f * b)), radius = w * 1.0f
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(DlPink.copy(alpha = 0.20f), Color.Transparent),
                        center = Offset(w * (0.02f + 0.30f * b), h * (0.10f + 0.10f * a)), radius = w * 0.9f
                    )
                )
                drawRect(
                    Brush.radialGradient(
                        listOf(DlCyan.copy(alpha = 0.10f), Color.Transparent),
                        center = Offset(w * (0.95f - 0.35f * a), h * (0.88f + 0.05f * b)), radius = w * 0.95f
                    )
                )
            }
    )
}

/** A frosted card: soft white gradient fill, bright top edge. */
@Composable
internal fun GlassCard(
    modifier: Modifier = Modifier,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0x24FFFFFF), Color(0x0AFFFFFF))))
            .border(1.dp, Brush.verticalGradient(listOf(Color(0x47FFFFFF), Color(0x10FFFFFF))), shape)
            .padding(padding),
        content = content
    )
}

/** A rounded square icon in gradient colour with a soft glow of the same colour. */
@Composable
internal fun GradientTile(icon: ImageVector, size: Dp = 48.dp, colors: List<Color> = DlSunset) {
    val shape = RoundedCornerShape(size * 0.32f)
    Box(
        modifier = Modifier
            .size(size)
            .shadow(14.dp, shape, ambientColor = colors.first(), spotColor = colors.last())
            .clip(shape)
            .background(Brush.linearGradient(colors)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(size * 0.5f))
    }
}

/** The big card at the top of each use case: what you can do here, in one line. */
@Composable
internal fun HeroCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: List<Color> = DlSunset,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(26.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(accent.map { it.copy(alpha = 0.30f) }))
            .border(1.dp, Brush.linearGradient(accent.map { it.copy(alpha = 0.60f) }), shape)
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GradientTile(icon, 56.dp, accent)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    subtitle,
                    color = DlSoftText.copy(alpha = 0.88f), fontSize = 13.sp, lineHeight = 18.sp,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
    }
}

/** "1 Copy the link · 2 Paste it here · 3 Save": the whole flow at a glance. */
@Composable
internal fun StepsRow(steps: List<String>, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        steps.forEachIndexed { index, text ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0x12FFFFFF))
                    .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(18.dp))
                    .padding(vertical = 12.dp, horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(26.dp).clip(CircleShape).background(Brush.linearGradient(DlSunset)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("${index + 1}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
                }
                Text(
                    text, color = DlSoftText, fontSize = 12.sp, lineHeight = 15.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }
    }
}

@Composable
internal fun SectionTitle(title: String, subtitle: String? = null, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        if (subtitle != null) Text(subtitle, color = DlMuted, fontSize = 12.sp, lineHeight = 16.sp)
    }
}

/** A small status pill: green when [ok], amber otherwise. Tap it to fix the problem when [onClick] is given. */
@Composable
internal fun StatusChip(label: String, ok: Boolean, icon: ImageVector, modifier: Modifier = Modifier, onClick: (() -> Unit)? = null) {
    val tint = if (ok) DlGood else DlWarn
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .clip(shape)
            .background(tint.copy(alpha = 0.14f))
            .border(1.dp, tint.copy(alpha = 0.45f), shape)
            .then(if (onClick != null && !ok) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
        Spacer(modifier = Modifier.width(5.dp))
        Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/**
 * The three use cases as a floating bar. The gradient pill slides to the chosen one with a soft spring.
 * Labels say what you DO there, not what the feature is called.
 */
@Composable
internal fun UseCaseTabs(
    selected: DownloaderTab,
    icons: Map<DownloaderTab, ImageVector>,
    onSelect: (DownloaderTab) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = listOf(DownloaderTab.STORIES, DownloaderTab.REELS, DownloaderTab.ANY)
    val shape = RoundedCornerShape(26.dp)
    val density = LocalDensity.current
    val innerPad = 5.dp
    var outerWidthPx by remember { mutableIntStateOf(0) }
    val tabWidthPx = if (outerWidthPx > 0) (outerWidthPx - with(density) { (innerPad * 2).toPx() }) / tabs.size else 0f
    val index = tabs.indexOf(selected).coerceAtLeast(0)
    val offsetX by animateFloatAsState(
        targetValue = index * tabWidthPx,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 380f),
        label = "usecase_tab_x"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0x14FFFFFF))
            .border(1.dp, GlassBorder, shape)
            .onSizeChanged { outerWidthPx = it.width }
            .padding(innerPad)
    ) {
        if (tabWidthPx > 0f) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(offsetX.roundToInt(), 0) }
                    .width(with(density) { tabWidthPx.toDp() })
                    .height(60.dp)
                    .clip(RoundedCornerShape(21.dp))
                    .background(Brush.horizontalGradient(DlSunset))
            )
        }
        Row {
            tabs.forEach { tab ->
                val active = tab == selected
                val tint by animateColorAsState(if (active) Color.White else DlMuted, label = "usecase_tab_tint")
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .height(60.dp)
                        .clip(RoundedCornerShape(21.dp))
                        .clickable { onSelect(tab) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(icons.getValue(tab), contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                    Text(
                        text = tab.label, color = tint, fontSize = 11.5.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, modifier = Modifier.padding(top = 3.dp)
                    )
                }
            }
        }
    }
}

/** A progress bar in the signature gradient. [progress] null means "working, unknown how long": a light sweeps across. */
@Composable
internal fun GradientProgress(progress: Float?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(CircleShape)
            .background(Color(0x26FFFFFF))
    ) {
        if (progress != null) {
            val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(300), label = "dl_progress")
            Box(
                modifier = Modifier
                    .fillMaxWidth(animated)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(Brush.horizontalGradient(DlSunset))
            )
        } else {
            val transition = rememberInfiniteTransition(label = "dl_indeterminate")
            val x by transition.animateFloat(
                initialValue = -0.4f, targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
                label = "dl_indeterminate_x"
            )
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .offset(x = maxWidth * x)
                        .width(maxWidth * 0.4f)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(Brush.horizontalGradient(DlSunset))
                )
            }
        }
    }
}
