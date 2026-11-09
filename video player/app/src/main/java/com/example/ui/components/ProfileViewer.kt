package com.example.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.download.InstagramParser
import com.example.ui.model.StoryItemDownload
import com.example.ui.viewmodel.ProfileController
import com.example.ui.viewmodel.ProfileUiState
import com.example.ui.viewmodel.ProfileViewerState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/*
 * The full-screen viewer for stories, highlights, posts and reels on a profile.
 *
 * Stories and highlights behave like Instagram's: a row of thin bars on top, the current one fills with white over
 * the length of the story (a photo lasts 5 s, a video plays once), tap the left third to go back and the rest to go
 * on, press and hold to pause. The header shows the exact time the story was posted, down to the second, and a tag
 * says whether it is a story, a reel, a plain video, a photo or a carousel. The three dots open a panel with
 * everything Instagram told us about the item, grouped by topic.
 */

private val VText = Color(0xFFF5F5F5)
private val VMuted = Color(0xFFA8A8A8)
private val VLink = Color(0xFF7CC4FF)
private val VPanel = Color(0xFF121212)
private val VChip = Color(0xFF262626)
private val VCyan = Color(0xFF22D3EE)

/** How long a photo story stays on screen, like Instagram's. */
private const val PHOTO_STORY_MS = 5_000

// ------------------------------------------------------------------------------------------------
// Time and tag helpers (plain functions, unit tested)
// ------------------------------------------------------------------------------------------------

/** 8076 → "2 h 14 min 36 s": every unit that matters, down to the second. */
internal fun elapsedText(totalSeconds: Long): String {
    val s = totalSeconds.coerceAtLeast(0)
    val d = s / 86_400
    val h = (s % 86_400) / 3_600
    val m = (s % 3_600) / 60
    val sec = s % 60
    return buildList {
        if (d > 0) add("$d d")
        if (d > 0 || h > 0) add("$h h")
        if (d > 0 || h > 0 || m > 0) add("$m min")
        add("$sec s")
    }.joinToString(" ")
}

/** "Today 14:32:07", "Yesterday 09:05:41" or "05 Nov 2026 14:32:07". */
internal fun postedClock(epochSec: Long, nowMillis: Long = System.currentTimeMillis()): String {
    if (epochSec <= 0) return ""
    val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(epochSec * 1000))
    val posted = Calendar.getInstance().apply { timeInMillis = epochSec * 1000 }
    val today = Calendar.getInstance().apply { timeInMillis = nowMillis }
    val yesterday = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    fun sameDay(a: Calendar, b: Calendar) = a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    return when {
        sameDay(posted, today) -> "Today $time"
        sameDay(posted, yesterday) -> "Yesterday $time"
        else -> SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(epochSec * 1000)) + " " + time
    }
}

/** The labels of an item's tag: ["STORY", "VIDEO"], ["REEL"], ["PHOTO"], ["CAROUSEL 2/5"]. */
internal fun typeTags(
    item: InstagramParser.StoryItem,
    inStories: Boolean,
    slideIndex: Int,
    slideCount: Int,
    reposts: Boolean = false
): List<String> {
    val product = item.extras.productType
    val media = if (item.isVideo) "VIDEO" else "PHOTO"
    return when {
        reposts || item.repostedAtSec != null -> listOf("REPOST", if (product == "clips") "REEL" else media)
        inStories || product == "story" -> listOf("STORY", media)
        product == "clips" -> listOf("REEL")
        slideCount > 1 || product == "carousel_container" -> listOf("CAROUSEL ${slideIndex + 1}/${slideCount.coerceAtLeast(1)}", media)
        else -> listOf(media)
    }
}

private fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

/** 1080 × 1920 → "1080 × 1920 (9:16, portrait)". */
internal fun resolutionText(width: Int, height: Int): String {
    val g = gcd(width, height).coerceAtLeast(1)
    val shape = when {
        width == height -> "square"
        height > width -> "portrait"
        else -> "landscape"
    }
    return "$width × $height (${width / g}:${height / g}, $shape)"
}

private fun durationText(seconds: Double): String {
    val total = seconds.toLong()
    val tenths = ((seconds - total) * 10).toInt()
    return String.format(Locale.US, "%d:%02d.%d", total / 60, total % 60, tenths)
}

private fun fullCount(n: Long): String = String.format(Locale.US, "%,d", n)

// ------------------------------------------------------------------------------------------------
// The viewer
// ------------------------------------------------------------------------------------------------

/** Everything the viewer needs from whoever opened it (the profile, or the Downloader's stories and reposts). */
internal class ViewerHost(
    val title: String,
    val items: List<InstagramParser.StoryItem>,
    val startIndex: Int = 0,
    /** Stories and highlights run on a timer; posts and reels are browsed by hand. */
    val timed: Boolean,
    val inStories: Boolean,
    val reposts: Boolean = false,
    /** Whose name and photo to show when an item doesn't carry its own author. */
    val fallbackOwner: String,
    val fallbackOwnerPic: String?,
    val saveStatus: (String) -> StoryItemDownload?,
    val onSave: (InstagramParser.StoryItem) -> Unit,
    val onSaveAll: (List<InstagramParser.StoryItem>) -> Unit,
    val onOpenUser: (String) -> Unit,
    val onResolve: (InstagramParser.StoryItem) -> Unit,
    val onClose: () -> Unit
)

@Composable
internal fun ProfileViewerDialog(viewer: ProfileViewerState, state: ProfileUiState, controller: ProfileController) {
    val timed = viewer.title == "Stories" || state.highlights.any { it.title.ifBlank { "Highlight" } == viewer.title }
    InstaViewer(
        ViewerHost(
            title = viewer.title,
            items = viewer.items,
            startIndex = viewer.startIndex,
            timed = timed,
            inStories = timed && viewer.title == "Stories",
            fallbackOwner = state.details?.username ?: state.username,
            fallbackOwnerPic = state.details?.picUrl,
            saveStatus = { state.saves[it] },
            onSave = controller::save,
            onSaveAll = controller::saveAll,
            onOpenUser = controller::open,
            onResolve = controller::resolveNow,
            onClose = controller::closeViewer
        )
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun InstaViewer(host: ViewerHost) {
    val items = host.items
    if (items.isEmpty()) {
        LaunchedEffect(Unit) { host.onClose() }
        return
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val timed = host.timed
    val inStories = host.inStories
    var detailsOpen by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { if (detailsOpen) detailsOpen = false else host.onClose() },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        val pager = rememberPagerState(
            initialPage = host.startIndex.coerceIn(0, items.lastIndex),
            pageCount = { items.size }
        )
        val page = pager.currentPage.coerceIn(0, items.lastIndex)
        val current = items[page]
        val currentIsVideo = current.isVideo && !current.needsResolve

        var holding by remember { mutableStateOf(false) }
        val paused = holding || detailsOpen
        val photoProgress = remember { Animatable(0f) }
        var videoProgress by remember { mutableFloatStateOf(0f) }

        fun next() {
            scope.launch {
                if (page < items.lastIndex) pager.animateScrollToPage(page + 1) else host.onClose()
            }
        }
        fun previous() {
            scope.launch {
                if (page > 0) pager.animateScrollToPage(page - 1) else photoProgress.snapTo(0f)
            }
        }

        // A new story starts from zero
        LaunchedEffect(page) {
            photoProgress.snapTo(0f)
            videoProgress = 0f
        }
        // Photos fill their bar over 5 s (holding pauses it); videos drive their own bar while they play
        LaunchedEffect(page, paused, timed, current.needsResolve, current.isVideo) {
            if (!timed || paused || current.isVideo || current.needsResolve) return@LaunchedEffect
            val remaining = (PHOTO_STORY_MS * (1f - photoProgress.value)).toInt().coerceAtLeast(1)
            photoProgress.animateTo(1f, tween(remaining, easing = LinearEasing))
            next()
        }
        val barProgress: () -> Float = { if (currentIsVideo) videoProgress else photoProgress.value }

        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(
                state = pager,
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (!timed) Modifier else Modifier.pointerInput(page, items.size) {
                            // Watches touches without taking them (so swipes and the video still work):
                            // finger down = pause, quick tap = left third back / elsewhere forward
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                holding = true
                                var moved = false
                                var releasedAt: Long? = null
                                while (true) {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                                    if (!change.pressed) {
                                        releasedAt = change.uptimeMillis
                                        break
                                    }
                                }
                                holding = false
                                val up = releasedAt
                                if (up != null && !moved && up - down.uptimeMillis < 350) {
                                    if (down.position.x < size.width / 3f) previous() else next()
                                }
                            }
                        }
                    )
            ) { p ->
                val item = items[p]
                val isCurrent = pager.currentPage == p
                when {
                    item.needsResolve -> {
                        // A reel that only came as a picture: look the real video up, then it plays
                        if (isCurrent) LaunchedEffect(item.id) { host.onResolve(item) }
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            AsyncImage(
                                model = item.thumbnailUrl ?: item.mediaUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(modifier = Modifier.fillMaxSize().background(Color(0x99000000)))
                            FetchingLoader(
                                steps = listOf("Fetching info…", "Finding the video…", "Getting it ready to play…"),
                                icon = Icons.Default.Videocam
                            )
                        }
                    }
                    item.isVideo && isCurrent -> ViewerVideo(
                        url = item.mediaUrl,
                        playing = !paused,
                        loop = !timed,
                        onProgress = if (timed) { value -> videoProgress = value } else null,
                        onEnded = if (timed) { { next() } } else null
                    )
                    else -> AsyncImage(
                        model = if (item.isVideo) (item.thumbnailUrl ?: item.mediaUrl) else item.mediaUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            // Soft shade so white text stays readable on bright stories
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(150.dp)
                    .background(Brush.verticalGradient(listOf(Color(0x99000000), Color.Transparent)))
            )

            val status = host.saveStatus(current.id)
            val owner = current.author ?: host.fallbackOwner
            val ownerPic = current.authorPic ?: host.fallbackOwnerPic
            val nowSec by produceState(System.currentTimeMillis() / 1000, page) {
                while (true) {
                    value = System.currentTimeMillis() / 1000
                    delay(1000)
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            ) {
                if (items.size > 1) {
                    StorySegmentBars(count = items.size, current = page, timed = timed, progress = barProgress)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 2.dp)
                ) {
                    ProfileAvatar(ownerPic, Modifier.size(36.dp))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = owner.takeIf { it.isNotBlank() }?.let { "@$it" } ?: host.title,
                                color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            typeTags(current, inStories, page, items.size, host.reposts).forEach { label ->
                                TypeTag(label)
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                        }
                        // A repost shows when it was reposted; everything else shows when it was posted
                        val stamp = current.repostedAtSec ?: current.takenAtSec
                        if (stamp > 0) {
                            Text(
                                text = (if (current.repostedAtSec != null) "Reposted " else "") + postedClock(stamp) +
                                    "  ·  " + elapsedText(nowSec - stamp) + " ago",
                                color = Color(0xE6FFFFFF), fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(onClick = { if (status !is StoryItemDownload.Done) host.onSave(current) }) {
                        when (status) {
                            is StoryItemDownload.Downloading -> CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                            is StoryItemDownload.Done -> Icon(Icons.Default.CheckCircle, contentDescription = "Saved", tint = VCyan)
                            else -> Icon(Icons.Default.Download, contentDescription = "Save", tint = Color.White)
                        }
                    }
                    IconButton(onClick = { detailsOpen = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Details", tint = Color.White)
                    }
                    IconButton(onClick = host.onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            }

            // Caption, counts and @mentions along the bottom
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD000000))))
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                current.repostNote?.takeIf { it.isNotBlank() }?.let {
                    Text("Reposted with: $it", color = Color(0xFFA7F3D0), fontSize = 13.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, lineHeight = 18.sp)
                }
                current.caption?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = Color.White, fontSize = 14.sp, maxLines = 4, overflow = TextOverflow.Ellipsis, lineHeight = 19.sp)
                }
                val counts = listOfNotNull(
                    current.likes?.let { "${compactCount(it)} likes" },
                    current.comments?.let { "${compactCount(it)} comments" },
                    current.plays?.let { "${compactCount(it)} plays" }
                ).joinToString("  ·  ")
                if (counts.isNotBlank()) Text(counts, color = VMuted, fontSize = 12.sp)
                if (current.mentions.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                        itemsIndexed(current.mentions, key = { _, who -> who.username }) { _, who ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color(0x33FFFFFF))
                                    .clickable { host.onOpenUser(who.username) }
                                    .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp)
                            ) {
                                ProfileAvatar(who.picUrl, Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("@${who.username}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // ---- The three-dots panel ----
            AnimatedVisibility(visible = detailsOpen, enter = fadeIn(), exit = fadeOut()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x99000000))
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { detailsOpen = false }
                )
            }
            AnimatedVisibility(
                visible = detailsOpen,
                modifier = Modifier.align(Alignment.BottomCenter),
                enter = slideInVertically(tween(260)) { it } + fadeIn(),
                exit = slideOutVertically(tween(200)) { it } + fadeOut()
            ) {
                DetailsPanel(
                    item = current,
                    viewerTitle = host.title,
                    inStories = inStories,
                    reposts = host.reposts,
                    slideIndex = page,
                    slideCount = items.size,
                    owner = owner,
                    nowSec = nowSec,
                    allItems = items,
                    host = host,
                    onClose = { detailsOpen = false }
                )
            }
        }
    }
}

/** Instagram's row of thin bars: finished ones white, the current one filling, the rest dim. */
@Composable
private fun StorySegmentBars(count: Int, current: Int, timed: Boolean, progress: () -> Float) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
        repeat(minOf(count, 60)) { i ->
            Canvas(modifier = Modifier.weight(1f).height(2.5.dp)) {
                val radius = CornerRadius(size.height / 2, size.height / 2)
                drawRoundRect(Color(0x66FFFFFF), cornerRadius = radius)
                val fraction = when {
                    i < current -> 1f
                    i == current -> if (timed) progress().coerceIn(0f, 1f) else 1f
                    else -> 0f
                }
                if (fraction > 0f) drawRoundRect(Color.White, size = Size(size.width * fraction, size.height), cornerRadius = radius)
            }
        }
    }
}

@Composable
private fun TypeTag(label: String) {
    val colors = when {
        label.startsWith("STORY") -> listOf(Color(0xFFF59E0B), Color(0xFFEC4899))
        label == "REPOST" -> listOf(Color(0xFF10B981), Color(0xFF22D3EE))
        label.startsWith("REEL") -> listOf(Color(0xFFEC4899), Color(0xFF8B5CF6))
        label.startsWith("CAROUSEL") -> listOf(Color(0xFF8B5CF6), Color(0xFF22D3EE))
        label == "VIDEO" -> listOf(Color(0xFF0EA5E9), Color(0xFF22D3EE))
        else -> listOf(Color(0x66FFFFFF), Color(0x66FFFFFF))
    }
    Text(
        text = label,
        color = Color.White,
        fontSize = 11.sp,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = 0.6.sp,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.horizontalGradient(colors))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

// ------------------------------------------------------------------------------------------------
// The details panel
// ------------------------------------------------------------------------------------------------

private data class DetailChip(val text: String, val onClick: (() -> Unit)? = null)

private sealed interface DetailRow {
    data class Line(val label: String, val value: String, val onClick: (() -> Unit)? = null) : DetailRow
    data class Chips(val label: String, val chips: List<DetailChip>) : DetailRow
    data class Block(val label: String, val text: String) : DetailRow
}

private data class DetailSection(val title: String, val icon: ImageVector, val rows: List<DetailRow>)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsPanel(
    item: InstagramParser.StoryItem,
    viewerTitle: String,
    inStories: Boolean,
    reposts: Boolean,
    slideIndex: Int,
    slideCount: Int,
    owner: String,
    nowSec: Long,
    allItems: List<InstagramParser.StoryItem>,
    host: ViewerHost,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    fun copy(label: String, text: String) {
        clipboard.setText(AnnotatedString(text))
        Toast.makeText(context, "$label copied", Toast.LENGTH_SHORT).show()
    }
    fun open(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) {
            Toast.makeText(context, "Couldn't open that link", Toast.LENGTH_SHORT).show()
        }
    }
    fun person(username: String) = DetailChip("@$username") { onClose(); host.onOpenUser(username) }

    val extras = item.extras
    val postLink = item.code?.let { "https://www.instagram.com/${if (extras.productType == "clips") "reel" else "p"}/$it/" }

    val sections = buildList {
        // ---- When ----
        val when_ = buildList<DetailRow> {
            if (item.takenAtSec > 0) {
                add(DetailRow.Line("Posted", SimpleDateFormat("EEEE, dd MMM yyyy", Locale.getDefault()).format(Date(item.takenAtSec * 1000))))
                add(DetailRow.Line("Exact time", SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(item.takenAtSec * 1000))))
                add(DetailRow.Line("Time ago", elapsedText(nowSec - item.takenAtSec)))
            }
            item.expiresAtSec?.let { exp ->
                add(
                    DetailRow.Line(
                        "Expires",
                        if (exp > nowSec) "in ${elapsedText(exp - nowSec)}" else "expired " + elapsedText(nowSec - exp) + " ago"
                    )
                )
            }
            if (item.takenAtSec > 0) add(DetailRow.Line("Unix time", item.takenAtSec.toString()))
        }
        if (when_.isNotEmpty()) add(DetailSection("When", Icons.Default.Schedule, when_))

        // ---- Repost ----
        if (reposts || item.repostedAtSec != null) {
            val repost = buildList<DetailRow> {
                item.repostedAtSec?.let { at ->
                    add(DetailRow.Line("Reposted at", SimpleDateFormat("EEEE, dd MMM yyyy  HH:mm:ss", Locale.getDefault()).format(Date(at * 1000))))
                    add(DetailRow.Line("Reposted", elapsedText(nowSec - at) + " ago"))
                }
                if (owner.isNotBlank()) add(DetailRow.Chips("Original post by", listOf(person(owner))))
                item.repostNote?.takeIf { it.isNotBlank() }?.let { add(DetailRow.Block("Repost note", it)) }
            }
            if (repost.isNotEmpty()) add(DetailSection("Repost", Icons.Default.Repeat, repost))
        }

        // ---- Type and media ----
        val type = buildList<DetailRow> {
            add(DetailRow.Line("Type", typeTags(item, inStories, slideIndex, slideCount, reposts).joinToString(" · ")))
            add(DetailRow.Line("Shown in", viewerTitle))
            if (slideCount > 1) add(DetailRow.Line("Item", "${slideIndex + 1} of $slideCount"))
            item.durationSec?.takeIf { it > 0 }?.let { add(DetailRow.Line("Duration", durationText(it))) }
            if (extras.width != null && extras.height != null) add(DetailRow.Line("Resolution", resolutionText(extras.width, extras.height)))
            extras.hasAudio?.let { add(DetailRow.Line("Sound", if (it) "Has audio" else "No audio")) }
            extras.productType?.let { add(DetailRow.Line("Instagram label", it)) }
            add(DetailRow.Line("Media ID", item.id) { copy("Media ID", item.id) })
            item.code?.let { add(DetailRow.Line("Short code", it) { copy("Short code", it) }) }
        }
        add(DetailSection("Type and media", Icons.Default.Videocam, type))

        // ---- People ----
        val people = buildList<DetailRow> {
            if (owner.isNotBlank()) add(DetailRow.Chips("Posted by", listOf(person(owner))))
            if (item.mentions.isNotEmpty()) add(DetailRow.Chips("Mentions", item.mentions.map { person(it.username) }))
            if (extras.taggedUsers.isNotEmpty()) add(DetailRow.Chips("Tagged people", extras.taggedUsers.map { person(it.username) }))
            if (extras.coauthors.isNotEmpty()) add(DetailRow.Chips("Co-authors", extras.coauthors.map { person(it) }))
            if (extras.paidPartnership) add(DetailRow.Line("Paid partnership", "Yes"))
            item.authorName?.takeIf { it.isNotBlank() }?.let { add(DetailRow.Line("Name", it)) }
        }
        if (people.isNotEmpty()) add(DetailSection("People", Icons.Default.Person, people))

        // ---- Place and sound ----
        val placeAndSound = buildList<DetailRow> {
            extras.location?.let { place ->
                add(DetailRow.Line("Location", place) { open("https://www.google.com/maps/search/?api=1&query=" + Uri.encode(place)) })
            }
            extras.music?.let { add(DetailRow.Line("Music", it) { copy("Music", it) }) }
            extras.originalAudio?.let { add(DetailRow.Line("Original audio", it)) }
        }
        if (placeAndSound.isNotEmpty()) add(DetailSection("Place and sound", Icons.Default.Place, placeAndSound))

        // ---- Text ----
        val text = buildList<DetailRow> {
            item.caption?.takeIf { it.isNotBlank() }?.let { add(DetailRow.Block("Caption", it)) }
            if (extras.hashtags.isNotEmpty()) {
                add(DetailRow.Chips("Hashtags", extras.hashtags.map { tag -> DetailChip("#$tag") { open("https://www.instagram.com/explore/tags/$tag/") } }))
            }
            if (extras.links.isNotEmpty()) {
                add(DetailRow.Chips("Links", extras.links.map { url -> DetailChip(url.removePrefix("https://").removePrefix("http://").take(34)) { open(url) } }))
            }
            extras.altText?.let { add(DetailRow.Block("Picture description (alt text)", it)) }
        }
        if (text.isNotEmpty()) add(DetailSection("Text and links", Icons.Default.Tag, text))

        // ---- Stickers and interaction ----
        val stickers = buildList<DetailRow> {
            if (extras.stickers.isNotEmpty()) add(DetailRow.Chips("Stickers", extras.stickers.map { DetailChip(it) }))
            extras.canReply?.let { add(DetailRow.Line("Replies", if (it) "Allowed" else "Off")) }
            extras.canReshare?.let { add(DetailRow.Line("Sharing", if (it) "Allowed" else "Off")) }
        }
        if (stickers.isNotEmpty()) add(DetailSection("Stickers and interaction", Icons.Default.EmojiEmotions, stickers))

        // ---- Numbers ----
        val numbers = buildList<DetailRow> {
            item.likes?.let { add(DetailRow.Line("Likes", fullCount(it) + "  (" + compactCount(it) + ")")) }
            item.comments?.let { add(DetailRow.Line("Comments", fullCount(it) + "  (" + compactCount(it) + ")")) }
            item.plays?.let { add(DetailRow.Line("Plays / views", fullCount(it) + "  (" + compactCount(it) + ")")) }
        }
        if (numbers.isNotEmpty()) add(DetailSection("Numbers", Icons.Default.Favorite, numbers))
    }

    fun everything(): String = buildString {
        sections.forEach { section ->
            appendLine("== ${section.title} ==")
            section.rows.forEach { row ->
                when (row) {
                    is DetailRow.Line -> appendLine("${row.label}: ${row.value}")
                    is DetailRow.Chips -> appendLine("${row.label}: ${row.chips.joinToString(", ") { it.text }}")
                    is DetailRow.Block -> appendLine("${row.label}: ${row.text}")
                }
            }
            appendLine()
        }
        postLink?.let { appendLine("Link: $it") }
    }.trim()

    val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.8f)
            .clip(shape)
            .background(VPanel)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { /* keep taps inside the panel */ }
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 10.dp)
                .size(width = 38.dp, height = 4.dp)
                .clip(CircleShape)
                .background(Color(0x55FFFFFF))
        )
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 6.dp)) {
            Icon(Icons.Default.Info, contentDescription = null, tint = VCyan, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text("Details", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White) }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
        ) {
            sections.forEach { section ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp)) {
                    Box(
                        modifier = Modifier.size(26.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFEC4899), Color(0xFF8B5CF6)))),
                        contentAlignment = Alignment.Center
                    ) { Icon(section.icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp)) }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(section.title.uppercase(), color = VMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                }
                section.rows.forEach { row ->
                    when (row) {
                        is DetailRow.Line -> Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (row.onClick != null) Modifier.clickable(onClick = row.onClick) else Modifier)
                                .padding(vertical = 7.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(row.label, color = VMuted, fontSize = 13.sp, modifier = Modifier.width(118.dp))
                            Text(
                                row.value,
                                color = if (row.onClick != null) VLink else VText,
                                fontSize = 13.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f)
                            )
                        }
                        is DetailRow.Chips -> Column(modifier = Modifier.padding(vertical = 7.dp)) {
                            Text(row.label, color = VMuted, fontSize = 13.sp)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(top = 6.dp)
                            ) {
                                row.chips.forEach { chip ->
                                    Text(
                                        chip.text, color = if (chip.onClick != null) VLink else VText, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(VChip)
                                            .then(if (chip.onClick != null) Modifier.clickable(onClick = chip.onClick) else Modifier)
                                            .padding(horizontal = 12.dp, vertical = 7.dp)
                                    )
                                }
                            }
                        }
                        is DetailRow.Block -> Column(modifier = Modifier.padding(vertical = 7.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(row.label, color = VMuted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                                Text(
                                    "Copy", color = VLink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.clickable { copy(row.label, row.text) }.padding(4.dp)
                                )
                            }
                            Text(row.text, color = VText, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }

            // ---- Actions ----
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 22.dp, bottom = 8.dp)) {
                Text("ACTIONS", color = VMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                postLink?.let { link ->
                    ActionPill("Copy link", Icons.Default.ContentCopy) { copy("Link", link) }
                    ActionPill("Open on Instagram", Icons.Default.OpenInNew) { open(link) }
                }
                ActionPill("Copy video link", Icons.Default.Link) { copy("Media link", item.mediaUrl) }
                ActionPill("Copy all details", Icons.Default.ContentCopy) { copy("Details", everything()) }
                item.debug?.let { raw -> ActionPill("Copy raw data", Icons.Default.ContentCopy) { copy("Raw data", raw) } }
                ActionPill("Save this", Icons.Default.Download) { host.onSave(item); onClose() }
                if (allItems.size > 1) ActionPill("Save all ${allItems.size}", Icons.Default.Download) { host.onSaveAll(allItems); onClose() }
            }
            Spacer(modifier = Modifier.navigationBarsPadding().height(28.dp))
        }
    }
}

@Composable
private fun ActionPill(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(VChip)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    ) {
        Icon(icon, contentDescription = null, tint = VCyan, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(7.dp))
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
