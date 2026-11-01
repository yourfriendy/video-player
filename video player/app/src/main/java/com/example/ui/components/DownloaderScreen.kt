package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import android.content.pm.PackageManager
import android.os.Build
import android.view.LayoutInflater
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.R
import com.example.data.download.InstagramParser
import com.example.data.download.SavedPerson
import com.example.ui.model.DownloaderTab
import com.example.ui.model.StoryItemDownload
import com.example.ui.model.StoryKind
import com.example.ui.model.StoryUiState
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.Slate950
import com.example.ui.viewmodel.VideoFeedUiState
import com.example.ui.viewmodel.VideoFeedViewModel

private val Cyan = Color(0xFF5EEAD4)
private val Violet = Color(0xFF8B5CF6)
private val Pink = Color(0xFFEC4899)
private val Orange = Color(0xFFF59E0B)
private val Muted = Color(0xFF94A3B8)

/**
 * The downloader as a full page: saved people with one-tap access and new-story alerts, stories and
 * reposts you can WATCH first (saving is optional), plus the reel and any-link downloaders.
 */
@Composable
fun DownloaderScreen(
    uiState: VideoFeedUiState,
    viewModel: VideoFeedViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Leaving the page by any route (back button, swipe) clears the "open" flag
    DisposableEffect(Unit) { onDispose { viewModel.closeDownloader() } }

    // Instagram's own sign-in runs on its own screen (a plain web view renders reliably there)
    val loginLauncher = rememberLauncherForActivityResult(
        contract = com.example.IgLoginActivity.Contract()
    ) { viewModel.onIgLoginClosed() }
    LaunchedEffect(uiState.isIgLoginOpen) {
        if (uiState.isIgLoginOpen) loginLauncher.launch(viewModel.webUserAgent)
    }
    LaunchedEffect(uiState.downloaderTab) {
        if (uiState.downloaderTab == DownloaderTab.STORIES) viewModel.verifyIgAccount()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Slate950)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .testTag("downloader_screen")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("downloader_back")) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Column {
                Text("Downloader", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Text("Watch first, save only what you want", color = Muted, fontSize = 12.sp)
            }
        }

        // ---- Tabs ----
        val tabShape = RoundedCornerShape(14.dp)
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
                .clip(tabShape)
                .background(Color(0x14FFFFFF))
                .border(1.dp, GlassBorder, tabShape)
                .padding(4.dp)
        ) {
            listOf(DownloaderTab.STORIES, DownloaderTab.REELS, DownloaderTab.ANY).forEach { tab ->
                val selected = tab == uiState.downloaderTab
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(
                            if (selected) Brush.horizontalGradient(listOf(Orange, Pink, Violet))
                            else Brush.horizontalGradient(listOf(Color.Transparent, Color.Transparent))
                        )
                        .clickable { viewModel.setDownloaderTab(tab) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (tab == DownloaderTab.STORIES) "People" else tab.label,
                        color = if (selected) Color.White else Muted,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            when (uiState.downloaderTab) {
                DownloaderTab.STORIES -> PeoplePage(uiState, viewModel)
                DownloaderTab.REELS -> ReelsContent(uiState, viewModel)
                DownloaderTab.ANY -> UniversalContent(viewModel)
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                text = "Only save videos and photos you have permission to keep, and respect the creator's rights.",
                color = Color(0xFF64748B),
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
    }
}

// ----------------------------------------------------------------------------------------------
// People: saved people, stories, reposts
// ----------------------------------------------------------------------------------------------

@Composable
private fun PeoplePage(uiState: VideoFeedUiState, viewModel: VideoFeedViewModel) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val signedIn = uiState.isIgSignedIn
    var viewer by remember { mutableStateOf<Pair<List<InstagramParser.StoryItem>, Int>?>(null) }
    var pendingAlertFor by remember { mutableStateOf<String?>(null) }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        pendingAlertFor?.let { viewModel.setPersonAlerts(it, true) }
        pendingAlertFor = null
        if (!granted) {
            Toast.makeText(context, "Notifications are off for OffScroll, so alerts can't be shown.", Toast.LENGTH_LONG).show()
        }
    }
    // Phones often pause background work to save battery; this lets the user exempt OffScroll
    val power = remember { context.getSystemService(Context.POWER_SERVICE) as PowerManager }
    var batteryExempt by remember { mutableStateOf(power.isIgnoringBatteryOptimizations(context.packageName)) }
    val batteryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        batteryExempt = power.isIgnoringBatteryOptimizations(context.packageName)
    }
    fun enableAlerts(name: String) {
        val needsAsk = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsAsk) {
            pendingAlertFor = name
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.setPersonAlerts(name, true)
        }
    }

    Column {
        if (signedIn) {
            AccountStrip(
                account = uiState.igAccount,
                checking = uiState.isCheckingIgAccount,
                error = uiState.igAccountError,
                onSignOut = { viewModel.igSignOut() }
            )
        } else {
            LoginCard(onSignIn = { viewModel.openIgLogin() })
        }

        // ---- Saved people ----
        if (uiState.people.isNotEmpty()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text("Your people", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(uiState.people, key = { it.username }) { person ->
                    PersonChip(person, onClick = { viewModel.openPerson(person.username) })
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        LinkField(
            value = uiState.storyInput,
            placeholder = "Username or story link, like @x_user",
            enabled = uiState.storyState !is StoryUiState.Loading,
            keyboardType = KeyboardType.Uri,
            onChange = { viewModel.setStoryInput(it) },
            onPaste = { clipboard.getText()?.text?.let { viewModel.setStoryInput(it.trim()) } },
            onGo = {
                keyboard?.hide()
                if (signedIn) viewModel.findStories() else viewModel.openIgLogin()
            },
            testTag = "stories_input"
        )
        Spacer(modifier = Modifier.height(12.dp))
        GradientButton(
            text = if (signedIn) "Find stories" else "Sign in to continue",
            icon = if (signedIn) Icons.Default.Search else Icons.Default.Lock,
            enabled = !signedIn || uiState.storyInput.isNotBlank(),
            onClick = {
                keyboard?.hide()
                if (signedIn) viewModel.findStories() else viewModel.openIgLogin()
            }
        )

        // ---- Results ----
        val stories = uiState.storyState
        val owner = (stories as? StoryUiState.Loaded)?.owner
        if (stories is StoryUiState.Loaded) {
            Spacer(modifier = Modifier.height(16.dp))
            stories.profile?.let { ProfileCard(it) }
            if (owner != null && owner != "Highlight") {
                Spacer(modifier = Modifier.height(10.dp))
                val saved = uiState.people.firstOrNull { it.username.equals(owner, ignoreCase = true) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill(
                        text = if (saved != null) "Saved" else "Save person",
                        icon = if (saved != null) Icons.Default.Star else Icons.Default.StarBorder,
                        highlighted = saved != null,
                        onClick = { if (saved != null) viewModel.removePerson(owner) else viewModel.savePerson(owner) }
                    )
                    Pill(
                        text = if (saved?.alerts == true) "Alerts on" else "Alert me",
                        icon = if (saved?.alerts == true) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                        highlighted = saved?.alerts == true,
                        onClick = { if (saved?.alerts == true) viewModel.setPersonAlerts(owner, false) else enableAlerts(owner) }
                    )
                }
                if (saved?.alerts == true) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "You'll get a notification about every 30 minutes at most when @$owner posts a new story. " +
                            "It works even when OffScroll is closed (but not after \"Force stop\"). Nobody is told you looked.",
                        color = Muted, fontSize = 11.sp, lineHeight = 15.sp
                    )
                    if (!batteryExempt) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Pill(
                            text = "Keep alerts reliable",
                            icon = Icons.Default.Notifications,
                            onClick = {
                                try {
                                    batteryLauncher.launch(
                                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))
                                    )
                                } catch (e: Exception) {
                                    batteryLauncher.launch(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                                }
                            }
                        )
                        Text(
                            "Your phone may pause background checks to save battery. Tap to let OffScroll check while closed.",
                            color = Muted, fontSize = 11.sp, lineHeight = 15.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        if (stories is StoryUiState.Loaded || uiState.repostsState !is StoryUiState.Idle || uiState.storyKind == StoryKind.REPOSTS) {
            Spacer(modifier = Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StoryKind.values().forEach { kind ->
                    Pill(
                        text = if (kind == StoryKind.REPOSTS) "Reposts (beta)" else kind.label,
                        highlighted = uiState.storyKind == kind,
                        onClick = { viewModel.setStoryKind(kind) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        val kind = uiState.storyKind
        val state = if (kind == StoryKind.STORIES) uiState.storyState else uiState.repostsState
        ResultsSection(
            state = state,
            kind = kind,
            uiState = uiState,
            viewModel = viewModel,
            onOpen = { list, index -> viewer = list to index }
        )

        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Tap a story to watch it here. Watching never tells the owner. Saving is optional.",
            color = Muted, fontSize = 12.sp, lineHeight = 17.sp
        )
    }

    viewer?.let { (list, index) ->
        MediaViewerDialog(
            items = list,
            startIndex = index,
            downloads = uiState.storyDownloads,
            onSave = { viewModel.saveMediaItem(it) },
            onClose = { viewer = null }
        )
    }
}

@Composable
private fun ResultsSection(
    state: StoryUiState,
    kind: StoryKind,
    uiState: VideoFeedUiState,
    viewModel: VideoFeedViewModel,
    onOpen: (List<InstagramParser.StoryItem>, Int) -> Unit
) {
    val retry = { if (kind == StoryKind.STORIES) viewModel.findStories() else viewModel.findReposts() }
    when (state) {
        is StoryUiState.Idle -> {
            if (kind == StoryKind.REPOSTS) {
                OutlineButton(text = "Load reposts", onClick = { viewModel.findReposts() }, modifier = Modifier.fillMaxWidth())
            }
        }
        is StoryUiState.Loading -> BusyCard(
            label = if (kind == StoryKind.STORIES) "Looking for stories…" else "Opening their Reposts tab…",
            progress = null,
            onCancel = null
        )
        is StoryUiState.NeedsLogin -> {
            MessageCard(Icons.Default.Lock, Orange, state.message)
            Spacer(modifier = Modifier.height(10.dp))
            GradientButton(text = "Sign in with Instagram", icon = Icons.Default.Lock, onClick = { viewModel.openIgLogin() })
        }
        is StoryUiState.Error -> {
            MessageCard(Icons.Default.ErrorOutline, Color(0xFFFB7185), state.message)
            Spacer(modifier = Modifier.height(10.dp))
            GradientButton(text = "Try again", icon = Icons.Default.Search, onClick = { retry() })
        }
        is StoryUiState.Loaded -> {
            val downloads = uiState.storyDownloads
            val remaining = state.items.count { downloads[it.id] !is StoryItemDownload.Done }
            Text(
                text = "${state.items.size} ${if (kind == StoryKind.STORIES) "stories" else "reposts"}",
                color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(8.dp))
            if (remaining > 0) {
                OutlineButton(
                    text = "Save all ($remaining)",
                    onClick = { if (kind == StoryKind.STORIES) viewModel.downloadAllStories() else viewModel.saveAllReposts() },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                MessageCard(Icons.Default.CheckCircle, Cyan, "All saved to your phone.")
            }
            Spacer(modifier = Modifier.height(10.dp))
            MediaGrid(
                items = state.items,
                downloads = downloads,
                onOpen = { index -> onOpen(state.items, index) },
                onSave = { viewModel.saveMediaItem(it) }
            )
        }
    }
}

@Composable
private fun PersonChip(person: SavedPerson, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(Color(0x1AFFFFFF))
            .border(1.dp, if (person.alerts) Cyan.copy(alpha = 0.6f) else GlassBorder, shape)
            .clickable(onClick = onClick)
            .padding(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(Orange, Pink, Violet))),
            contentAlignment = Alignment.Center
        ) {
            Text(person.username.take(1).uppercase(), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text("@${person.username}", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (person.alerts) {
            Spacer(modifier = Modifier.width(6.dp))
            Icon(Icons.Default.Notifications, contentDescription = "Alerts on", tint = Cyan, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
private fun Pill(
    text: String,
    onClick: () -> Unit,
    highlighted: Boolean = false,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .height(36.dp)
            .clip(shape)
            .background(
                if (highlighted) Brush.horizontalGradient(listOf(Violet, Color(0xFF6366F1)))
                else Brush.horizontalGradient(listOf(Color(0x1AFFFFFF), Color(0x1AFFFFFF)))
            )
            .border(1.dp, if (highlighted) Color.Transparent else GlassBorder, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = if (highlighted) Color.White else Muted, modifier = Modifier.size(15.dp))
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(text, color = if (highlighted) Color.White else Color(0xFFE2E8F0), fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

// ----------------------------------------------------------------------------------------------
// Grid + viewer
// ----------------------------------------------------------------------------------------------

@Composable
private fun MediaGrid(
    items: List<InstagramParser.StoryItem>,
    downloads: Map<String, StoryItemDownload>,
    onOpen: (Int) -> Unit,
    onSave: (InstagramParser.StoryItem) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(3).forEachIndexed { rowIndex, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEachIndexed { col, item ->
                    MediaTile(
                        item = item,
                        download = downloads[item.id],
                        modifier = Modifier.weight(1f),
                        onOpen = { onOpen(rowIndex * 3 + col) },
                        onSave = { onSave(item) }
                    )
                }
                repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun MediaTile(
    item: InstagramParser.StoryItem,
    download: StoryItemDownload?,
    modifier: Modifier,
    onOpen: () -> Unit,
    onSave: () -> Unit
) {
    Box(
        modifier = modifier
            .aspectRatio(0.62f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E293B))
            .clickable(onClick = onOpen)
    ) {
        AsyncImage(
            model = item.thumbnailUrl ?: item.mediaUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.6f to Color.Transparent, 1f to Color(0xCC000000)))
        )
        if (item.isVideo) {
            Icon(
                Icons.Default.PlayArrow, contentDescription = "Video", tint = Color.White,
                modifier = Modifier.align(Alignment.Center).size(34.dp).background(Color(0x66000000), CircleShape)
            )
        }
        Text(
            text = item.author?.let { "@$it" } ?: timeAgo(item.takenAtSec),
            color = Color.White, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
        )
        IconButton(onClick = onSave, modifier = Modifier.align(Alignment.TopEnd).size(34.dp)) {
            Icon(
                imageVector = if (download is StoryItemDownload.Done) Icons.Default.CheckCircle else Icons.Default.Download,
                contentDescription = "Save",
                tint = if (download is StoryItemDownload.Done) Cyan else Color.White,
                modifier = Modifier.size(18.dp).background(Color(0x66000000), CircleShape).padding(3.dp)
            )
        }
    }
}

/** Watch stories/reposts full screen without saving anything. Saving is one optional tap. */
@Composable
private fun MediaViewerDialog(
    items: List<InstagramParser.StoryItem>,
    startIndex: Int,
    downloads: Map<String, StoryItemDownload>,
    onSave: (InstagramParser.StoryItem) -> Unit,
    onClose: () -> Unit
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, items.lastIndex), pageCount = { items.size })
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                val item = items[page]
                if (item.isVideo && pager.currentPage == page) {
                    ViewerVideo(item.mediaUrl)
                } else {
                    AsyncImage(
                        model = if (item.isVideo) (item.thumbnailUrl ?: item.mediaUrl) else item.mediaUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            val current = items[pager.currentPage.coerceIn(0, items.lastIndex)]
            Column(modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.fillMaxWidth()) {
                    items.take(40).forEachIndexed { i, _ ->
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(3.dp)
                                .clip(CircleShape)
                                .background(if (i <= pager.currentPage) Color.White else Color(0x55FFFFFF))
                        )
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
                    Text(
                        text = (current.author?.let { "@$it · " } ?: "") + timeAgo(current.takenAtSec),
                        color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            }

            val done = downloads[current.id] is StoryItemDownload.Done
            Row(
                modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Color(0x99000000))
                    .clickable(enabled = !done) { onSave(current) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (done) Icons.Default.CheckCircle else Icons.Default.Download,
                    contentDescription = null, tint = if (done) Cyan else Color.White, modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(if (done) "Saved" else "Save", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ViewerVideo(url: String) {
    val context = LocalContext.current
    val player = remember(url) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            repeatMode = Player.REPEAT_MODE_ONE
            playWhenReady = true
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        factory = { ctx ->
            (LayoutInflater.from(ctx).inflate(R.layout.reel_player_view, null, false) as PlayerView).apply { this.player = player }
        },
        update = { it.player = player },
        onRelease = { it.player = null },
        modifier = Modifier.fillMaxSize()
    )
}
