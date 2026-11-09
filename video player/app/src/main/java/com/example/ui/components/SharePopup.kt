package com.example.ui.components

import com.example.ui.theme.Ds
import android.app.Application
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.universal.QualityOption
import com.example.service.ShareDownloads
import com.example.ui.theme.FrostedCyan
import com.example.ui.theme.FrostedEmerald
import com.example.ui.theme.FrostedRose
import com.example.ui.theme.FrostedViolet
import com.example.ui.theme.bouncyClickable
import java.util.Locale
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
private val Muted = Ds.Palette.Muted

/**
 * The whole pop-up: a dimmed see-through backdrop and a card that springs up from the bottom.
 * Tap outside (or Back) to hide it; a running download keeps going in the background.
 */
@Composable
fun SharePopupHost(
    onClose: () -> Unit,
    onOpenApp: () -> Unit,
    onOpenAppWithShare: () -> Unit
) {
    val app = LocalContext.current.applicationContext as Application
    val state by ShareDownloads.state.collectAsStateWithLifecycle()

    // Keep showing the last real content while the card slides away (state goes back to Idle on close)
    val lastShown = remember { mutableStateOf(state) }
    if (state !is ShareDownloads.State.Idle) lastShown.value = state
    val display = lastShown.value

    var visible by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun dismiss() {
        if (closing) return
        closing = true
        scope.launch {
            visible = false
            delay(280)
            onClose()
        }
    }

    LaunchedEffect(Unit) { visible = true }
    LaunchedEffect(state is ShareDownloads.State.Idle) {
        if (state is ShareDownloads.State.Idle) dismiss()
    }

    val hide: () -> Unit = {
        when (state) {
            // Nothing is downloading yet, so hiding means "never mind"
            is ShareDownloads.State.Looking -> ShareDownloads.cancel(app)
            is ShareDownloads.State.Choose,
            is ShareDownloads.State.Saved,
            is ShareDownloads.State.Failed -> ShareDownloads.clear()
            else -> Unit // a running download carries on in the background
        }
        dismiss()
    }
    BackHandler(onBack = hide)

    val scrim by animateFloatAsState(
        targetValue = if (visible) 0.55f else 0f,
        animationSpec = tween(260),
        label = "share_scrim"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = scrim))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = hide
                )
        )
        AnimatedVisibility(
            visible = visible,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(animationSpec = spring(dampingRatio = 0.82f, stiffness = 320f)) { it } +
                fadeIn(tween(180)),
            exit = slideOutVertically(animationSpec = tween(240, easing = FastOutSlowInEasing)) { it } +
                fadeOut(tween(200))
        ) {
            PopupCard(
                state = display,
                onHide = hide,
                onSelect = { ShareDownloads.select(it) },
                onDownload = { ShareDownloads.startDownload(app) },
                onCancel = { ShareDownloads.cancel(app) },
                onOpenApp = onOpenApp,
                onFailAction = { action ->
                    when (action) {
                        ShareDownloads.FailAction.Retry -> ShareDownloads.retry(app)
                        ShareDownloads.FailAction.UpdateEngine -> ShareDownloads.updateEngineAndRetry(app)
                        ShareDownloads.FailAction.OpenApp -> onOpenAppWithShare()
                        ShareDownloads.FailAction.None -> hide()
                    }
                }
            )
        }
    }
}

@Composable
private fun PopupCard(
    state: ShareDownloads.State,
    onHide: () -> Unit,
    onSelect: (String) -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onOpenApp: () -> Unit,
    onFailAction: (ShareDownloads.FailAction) -> Unit
) {
    val (headline, sub) = when (state) {
        is ShareDownloads.State.Looking -> "Checking your link" to state.platform
        is ShareDownloads.State.Choose -> "Ready to download" to state.info.platform
        is ShareDownloads.State.Downloading -> "Downloading" to state.platform
        is ShareDownloads.State.Saved -> "Saved" to state.platform
        is ShareDownloads.State.Failed -> "Couldn't download" to state.platform
        is ShareDownloads.State.Idle -> "" to ""
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(SheetShape)
            .background(Brush.verticalGradient(listOf(Color(0xFF1B1530), Color(0xFF0B0B14))))
            .border(
                1.dp,
                Brush.verticalGradient(listOf(Color(0x55C4B5FD), Color(0x11FFFFFF))),
                SheetShape
            )
            // Swallow taps so touching the card never counts as "tap outside"
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { }
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Grab handle
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(width = 40.dp, height = 4.dp)
                .clip(CircleShape)
                .background(Color(0x33FFFFFF))
        )

        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(FrostedViolet, FrostedRose))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = headline,
                    transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) },
                    label = "share_headline"
                ) { text ->
                    Text(
                        text = text,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (sub.isNotBlank()) {
                    Text(text = sub, color = Muted, fontSize = 12.sp)
                }
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .bouncyClickable(pressedScale = 0.88f, onClick = onHide)
                    .clip(CircleShape)
                    .background(Color(0x1FFFFFFF)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close",
                    tint = Color(0xFFE2E8F0),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        // Body: slides and fades between steps, but not on every progress tick (same kind = no transition)
        AnimatedContent(
            targetState = state,
            transitionSpec = {
                (fadeIn(tween(240, delayMillis = 60)) + slideInVertically(tween(300)) { it / 6 }) togetherWith
                    fadeOut(tween(120))
            },
            contentKey = { it::class },
            label = "share_body"
        ) { s ->
            when (s) {
                is ShareDownloads.State.Looking -> LookingBody(s.message)
                is ShareDownloads.State.Choose -> ChooseBody(s, onSelect, onDownload)
                is ShareDownloads.State.Downloading -> DownloadingBody(s, onHide, onCancel)
                is ShareDownloads.State.Saved -> SavedBody(s, onOpenApp, onHide)
                is ShareDownloads.State.Failed -> FailedBody(s, onFailAction, onHide)
                is ShareDownloads.State.Idle -> Spacer(modifier = Modifier.height(1.dp))
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// Steps
// ------------------------------------------------------------------------------------------------

@Composable
private fun LookingBody(message: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        ShimmerBar()
        Text(text = message, color = Muted, fontSize = 13.sp)
    }
}

@Composable
private fun ChooseBody(
    s: ShareDownloads.State.Choose,
    onSelect: (String) -> Unit,
    onDownload: () -> Unit
) {
    val info = s.info
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(width = 112.dp, height = 72.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF312E81), Color(0xFF831843))))
            ) {
                if (!info.thumbnail.isNullOrBlank()) {
                    AsyncImage(
                        model = info.thumbnail,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = null,
                        tint = Color(0x99FFFFFF),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(26.dp)
                    )
                }
                val duration = formatDuration(info.durationSec)
                if (duration.isNotEmpty()) {
                    Text(
                        text = duration,
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(4.dp)
                            .background(Color(0xAA000000), RoundedCornerShape(8.dp))
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = info.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (!info.uploader.isNullOrBlank()) {
                    Text(
                        text = info.uploader,
                        color = Muted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        if (info.options.size > 1) {
            Text(text = "Quality", color = Muted, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(info.options, key = { it.id }) { option ->
                    QualityChip(
                        option = option,
                        selected = option.id == s.selectedId,
                        onClick = { onSelect(option.id) }
                    )
                }
            }
        }

        GradientButton(
            text = "Download",
            icon = Icons.Default.Download,
            modifier = Modifier.fillMaxWidth(),
            onClick = onDownload
        )
    }
}

@Composable
private fun DownloadingBody(
    s: ShareDownloads.State.Downloading,
    onHide: () -> Unit,
    onCancel: () -> Unit
) {
    val indeterminate = s.progress < 0f
    val animated by animateFloatAsState(
        targetValue = s.progress.coerceIn(0f, 1f),
        animationSpec = tween(250),
        label = "share_progress"
    )

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = s.title,
            color = Color(0xFFE2E8F0),
            fontSize = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = if (indeterminate) "…" else "${(s.progress * 100).toInt()}%",
                color = Color.White,
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(modifier = Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(text = s.phase, color = Color(0xFFC4B5FD), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                if (s.detail.isNotBlank()) {
                    Text(text = s.detail, color = Muted, fontSize = 11.sp)
                }
            }
        }
        if (indeterminate) ShimmerBar() else GradientProgressBar(animated)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton(text = "Cancel", modifier = Modifier.weight(1f), onClick = onCancel)
            GradientButton(text = "Hide, keep going", icon = null, modifier = Modifier.weight(1f), onClick = onHide)
        }
        Text(
            text = "Hiding doesn't stop it. You'll get a notification when it's saved.",
            color = Muted,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun SavedBody(
    s: ShareDownloads.State.Saved,
    onOpenApp: () -> Unit,
    onDone: () -> Unit
) {
    val pop = remember { Animatable(0f) }
    val ring = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        launch { ring.animateTo(1f, tween(700, easing = FastOutSlowInEasing)) }
        pop.animateTo(1f, spring(dampingRatio = 0.45f, stiffness = 300f))
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .drawBehind {
                    val t = ring.value
                    if (t > 0f && t < 1f) {
                        drawCircle(
                            color = FrostedEmerald.copy(alpha = (1f - t) * 0.6f),
                            radius = (size.minDimension / 2f) * (1f + t * 0.8f),
                            style = Stroke(width = 3.dp.toPx() * (1f - t) + 0.5f)
                        )
                    }
                }
                .graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                }
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(FrostedEmerald, FrostedCyan))),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(40.dp)
            )
        }
        Text(
            text = "Saved to your library",
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = s.title,
            color = Muted,
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton(text = "Done", modifier = Modifier.weight(1f), onClick = onDone)
            GradientButton(text = "Open OffScroll", icon = null, modifier = Modifier.weight(1f), onClick = onOpenApp)
        }
    }
}

@Composable
private fun FailedBody(
    s: ShareDownloads.State.Failed,
    onFailAction: (ShareDownloads.FailAction) -> Unit,
    onClose: () -> Unit
) {
    val primary = when (s.action) {
        ShareDownloads.FailAction.Retry -> "Try again"
        ShareDownloads.FailAction.UpdateEngine -> "Update and retry"
        ShareDownloads.FailAction.OpenApp -> "Open OffScroll"
        ShareDownloads.FailAction.None -> null
    }
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = Color(0xFFFBBF24),
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(text = s.message, color = Color(0xFFE2E8F0), fontSize = 14.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassButton(text = "Close", modifier = Modifier.weight(1f), onClick = onClose)
            if (primary != null) {
                GradientButton(
                    text = primary,
                    icon = null,
                    modifier = Modifier.weight(1f),
                    onClick = { onFailAction(s.action) }
                )
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// Pieces
// ------------------------------------------------------------------------------------------------

@Composable
private fun QualityChip(option: QualityOption, selected: Boolean, onClick: () -> Unit) {
    val background by animateColorAsState(
        targetValue = if (selected) Color(0x338B5CF6) else Color(0x14FFFFFF),
        animationSpec = tween(200),
        label = "chip_bg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) Color(0xFFA78BFA) else Color(0x22FFFFFF),
        animationSpec = tween(200),
        label = "chip_border"
    )
    Column(
        modifier = Modifier
            .bouncyClickable(pressedScale = 0.94f, onClick = onClick)
            .clip(RoundedCornerShape(16.dp))
            .background(background)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Text(
            text = option.label,
            color = Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
        if (option.detail.isNotBlank()) {
            Text(text = option.detail, color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
private fun GradientButton(
    text: String,
    icon: ImageVector?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Row(
        modifier = modifier
            .height(52.dp)
            .bouncyClickable(pressedScale = 0.97f, onClick = onClick)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.horizontalGradient(listOf(FrostedViolet, FrostedRose))),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = text,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun GlassButton(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(52.dp)
            .bouncyClickable(pressedScale = 0.97f, onClick = onClick)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0x14FFFFFF))
            .border(1.dp, Color(0x22FFFFFF), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color(0xFFE2E8F0),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
private fun GradientProgressBar(fraction: Float) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(CircleShape)
            .background(Color(0x22FFFFFF))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction.coerceIn(0.02f, 1f))
                .clip(CircleShape)
                .background(Brush.horizontalGradient(listOf(FrostedViolet, FrostedRose)))
        )
    }
}

/** A light that sweeps across the track while we don't know how long something will take. */
@Composable
private fun ShimmerBar() {
    val transition = rememberInfiniteTransition(label = "share_shimmer")
    val x by transition.animateFloat(
        initialValue = -0.4f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "share_shimmer_x"
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(CircleShape)
            .background(Color(0x22FFFFFF))
            .drawBehind {
                val w = size.width
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, FrostedViolet, FrostedRose, Color.Transparent),
                        startX = w * (x - 0.4f),
                        endX = w * x
                    )
                )
            }
    )
}

private fun formatDuration(totalSeconds: Int): String {
    if (totalSeconds <= 0) return ""
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}
