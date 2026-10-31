package com.example.ui.components

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.download.InstagramParser
import com.example.data.download.InstagramStoryService
import com.example.ui.model.DownloadStatus
import com.example.ui.model.DownloaderTab
import com.example.ui.model.StoryItemDownload
import com.example.ui.model.StoryUiState
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.Slate950
import com.example.ui.viewmodel.VideoFeedUiState
import com.example.ui.viewmodel.VideoFeedViewModel
import kotlin.math.roundToInt

private val Cyan = Color(0xFF5EEAD4)
private val Violet = Color(0xFF8B5CF6)
private val Pink = Color(0xFFEC4899)
private val Orange = Color(0xFFF59E0B)
private val Muted = Color(0xFF94A3B8)

/**
 * "Save from Instagram" with two tabs:
 *  - Reels: paste a public reel link (or share a reel straight to OffScroll).
 *  - Stories: type a username or paste a story link. Stories are never public, so this tab
 *    needs the user to sign in to their own Instagram account first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloaderSheet(
    uiState: VideoFeedUiState,
    viewModel: VideoFeedViewModel
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = { viewModel.closeDownloader() },
        sheetState = sheetState,
        containerColor = Slate950,
        contentColor = Color.White,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .background(Color(0x66FFFFFF), CircleShape)
            )
        },
        modifier = Modifier.testTag("downloader_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
        ) {
            // ---- Header ----
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(listOf(Orange, Pink, Violet))),
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
                    Text(
                        text = "Save from Instagram",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = if (uiState.downloaderTab == DownloaderTab.REELS) {
                            "Paste a reel link, or share a reel to OffScroll"
                        } else {
                            "Username or story link"
                        },
                        fontSize = 12.sp,
                        color = Muted
                    )
                }
                IconButton(onClick = { viewModel.closeDownloader() }, modifier = Modifier.testTag("downloader_close")) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ---- Tabs ----
            TabSwitcher(
                selected = uiState.downloaderTab,
                onSelect = { viewModel.setDownloaderTab(it) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            when (uiState.downloaderTab) {
                DownloaderTab.REELS -> ReelsContent(uiState, viewModel)
                DownloaderTab.STORIES -> StoriesContent(uiState, viewModel)
            }

            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "Only save videos and photos you have permission to keep, and respect the creator's rights.",
                color = Color(0xFF64748B),
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
        }
    }

    // Instagram's sign-in runs on its own screen (a plain web view renders reliably there)
    val loginLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = com.example.IgLoginActivity.Contract()
    ) { viewModel.onIgLoginClosed() }
    LaunchedEffect(uiState.isIgLoginOpen) {
        if (uiState.isIgLoginOpen) loginLauncher.launch(viewModel.webUserAgent)
    }
}

@Composable
private fun TabSwitcher(selected: DownloaderTab, onSelect: (DownloaderTab) -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0x14FFFFFF))
            .border(1.dp, GlassBorder, shape)
            .padding(4.dp)
    ) {
        DownloaderTab.values().forEach { tab ->
            val isSelected = tab == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(
                        if (isSelected) Brush.horizontalGradient(listOf(Orange, Pink, Violet))
                        else SolidColor(Color.Transparent)
                    )
                    .clickable { onSelect(tab) }
                    .testTag("downloader_tab_${tab.name.lowercase()}"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tab.label,
                    color = if (isSelected) Color.White else Muted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ----------------------------------------------------------------------------------------------
// Reels tab
// ----------------------------------------------------------------------------------------------

@Composable
private fun ReelsContent(uiState: VideoFeedUiState, viewModel: VideoFeedViewModel) {
    val url = uiState.downloadUrl
    val status = uiState.downloadStatus
    val clipboard = LocalClipboardManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val isBusy = status is DownloadStatus.Fetching || status is DownloadStatus.Downloading

    // Convenience: if an Instagram link is already on the clipboard, drop it into the field
    LaunchedEffect(Unit) {
        if (url.isBlank()) {
            val copied = clipboard.getText()?.text
            InstagramParser.extractInstagramUrl(copied)?.let { viewModel.setDownloadUrl(it) }
        }
    }

    Column {
        LinkField(
            value = url,
            placeholder = "https://www.instagram.com/reel/…",
            enabled = !isBusy,
            keyboardType = KeyboardType.Uri,
            onChange = { viewModel.setDownloadUrl(it) },
            onPaste = { clipboard.getText()?.text?.let { viewModel.setDownloadUrl(it.trim()) } },
            onGo = {
                keyboard?.hide()
                viewModel.startDownload()
            },
            testTag = "downloader_input"
        )

        Spacer(modifier = Modifier.height(16.dp))

        when (status) {
            is DownloadStatus.Idle, is DownloadStatus.Failed -> {
                Column {
                    if (status is DownloadStatus.Failed) {
                        MessageCard(
                            icon = Icons.Default.ErrorOutline,
                            tint = Color(0xFFFB7185),
                            text = status.message
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    GradientButton(
                        text = if (status is DownloadStatus.Failed) "Try again" else "Download",
                        enabled = url.isNotBlank(),
                        onClick = {
                            keyboard?.hide()
                            viewModel.startDownload()
                        }
                    )
                }
            }

            is DownloadStatus.Fetching -> {
                BusyCard(label = "Finding the video…", progress = null, onCancel = { viewModel.cancelDownload() })
            }

            is DownloadStatus.Downloading -> {
                val p = status.progress
                BusyCard(
                    label = if (p >= 0f) "Downloading… ${(p * 100).roundToInt()}%" else "Downloading…",
                    progress = if (p >= 0f) p else null,
                    onCancel = { viewModel.cancelDownload() }
                )
            }

            is DownloadStatus.Success -> {
                Column {
                    MessageCard(
                        icon = Icons.Default.CheckCircle,
                        tint = Cyan,
                        text = "Saved! It's in your library (Movies/OffScroll)."
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GradientButton(
                            text = "Play now",
                            icon = Icons.Default.PlayArrow,
                            onClick = { viewModel.playDownloaded() },
                            modifier = Modifier.weight(1f)
                        )
                        OutlineButton(
                            text = "Download another",
                            onClick = { viewModel.setDownloadUrl("") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Tip: in Instagram tap Share → Copy link, or share the reel straight to OffScroll and it downloads automatically. Works with public reels only.",
            color = Muted,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
    }
}

// ----------------------------------------------------------------------------------------------
// Stories tab
// ----------------------------------------------------------------------------------------------

@Composable
private fun StoriesContent(uiState: VideoFeedUiState, viewModel: VideoFeedViewModel) {
    val clipboard = LocalClipboardManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val state = uiState.storyState
    val signedIn = uiState.isIgSignedIn
    val isLoading = state is StoryUiState.Loading

    Column {
        if (signedIn) {
            AccountStrip(
                account = uiState.igAccount,
                checking = uiState.isCheckingIgAccount,
                error = uiState.igAccountError,
                onSignOut = { viewModel.igSignOut() }
            )
        } else {
            if (uiState.igAccountError != null) {
                MessageCard(Icons.Default.ErrorOutline, Color(0xFFFB7185), uiState.igAccountError)
                Spacer(modifier = Modifier.height(10.dp))
            }
            LoginCard(onSignIn = { viewModel.openIgLogin() })
        }

        Spacer(modifier = Modifier.height(14.dp))

        LinkField(
            value = uiState.storyInput,
            placeholder = "Username or story link",
            enabled = !isLoading,
            keyboardType = KeyboardType.Uri,
            onChange = { viewModel.setStoryInput(it) },
            onPaste = { clipboard.getText()?.text?.let { viewModel.setStoryInput(it.trim()) } },
            onGo = {
                keyboard?.hide()
                if (signedIn) viewModel.findStories() else viewModel.openIgLogin()
            },
            testTag = "stories_input"
        )

        Spacer(modifier = Modifier.height(14.dp))

        when (state) {
            is StoryUiState.Loading -> {
                BusyCard(label = "Looking for stories…", progress = null, onCancel = null)
            }

            is StoryUiState.Loaded -> {
                StoryResults(uiState = uiState, state = state, viewModel = viewModel)
            }

            else -> {
                Column {
                    when (state) {
                        is StoryUiState.Error -> {
                            MessageCard(Icons.Default.ErrorOutline, Color(0xFFFB7185), state.message)
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        is StoryUiState.NeedsLogin -> {
                            MessageCard(Icons.Default.Lock, Orange, state.message)
                            Spacer(modifier = Modifier.height(12.dp))
                        }
                        else -> Unit
                    }
                    GradientButton(
                        text = if (signedIn) {
                            if (state is StoryUiState.Error) "Try again" else "Find stories"
                        } else "Sign in to continue",
                        icon = if (signedIn) Icons.Default.Search else Icons.Default.Lock,
                        enabled = !signedIn || uiState.storyInput.isNotBlank(),
                        onClick = {
                            keyboard?.hide()
                            if (signedIn) viewModel.findStories() else viewModel.openIgLogin()
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = "Enter @username to see all current stories, or paste a story link for just that one. " +
                "You can only see stories your own account can see. Videos save to Movies/OffScroll, photos to Pictures/OffScroll.",
            color = Muted,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
    }
}

@Composable
private fun StoryResults(
    uiState: VideoFeedUiState,
    state: StoryUiState.Loaded,
    viewModel: VideoFeedViewModel
) {
    val downloads = uiState.storyDownloads
    val remaining = state.items.count { downloads[it.id] !is StoryItemDownload.Done }
    val anyRunning = downloads.values.any { it is StoryItemDownload.Downloading }

    Column {
        state.profile?.let {
            ProfileCard(it)
            Spacer(modifier = Modifier.height(12.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = (if (state.owner == "Highlight") "Highlight" else "@${state.owner}") +
                    " · ${state.items.size} ${if (state.items.size == 1) "story" else "stories"}",
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))

        if (anyRunning) {
            OutlineButton(text = "Stop", onClick = { viewModel.cancelStoryDownloads() }, modifier = Modifier.fillMaxWidth())
        } else if (remaining > 0) {
            GradientButton(
                text = if (remaining == state.items.size) "Download all (${state.items.size})" else "Download remaining ($remaining)",
                onClick = { viewModel.downloadAllStories() }
            )
        } else {
            MessageCard(Icons.Default.CheckCircle, Cyan, "All saved to your phone.")
        }

        Spacer(modifier = Modifier.height(12.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            state.items.forEach { item ->
                StoryRow(
                    item = item,
                    download = downloads[item.id],
                    onDownload = { viewModel.downloadStoryItem(item) }
                )
            }
        }
    }
}

@Composable
private fun StoryRow(
    item: InstagramParser.StoryItem,
    download: StoryItemDownload?,
    onDownload: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0x14FFFFFF))
            .border(1.dp, GlassBorder, shape)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(52.dp)
                .height(78.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF1E293B))
        ) {
            if (item.thumbnailUrl != null) {
                AsyncImage(
                    model = item.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Icon(
                imageVector = if (item.isVideo) Icons.Default.Videocam else Icons.Default.Image,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(4.dp)
                    .size(16.dp)
                    .background(Color(0x99000000), CircleShape)
                    .padding(2.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (item.isVideo) "Video story" else "Photo story",
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = when (download) {
                    is StoryItemDownload.Downloading ->
                        if (download.progress >= 0f) "Downloading… ${(download.progress * 100).roundToInt()}%" else "Downloading…"
                    is StoryItemDownload.Done -> "Saved ✓"
                    is StoryItemDownload.Failed -> download.message
                    null -> timeAgo(item.takenAtSec)
                },
                color = when (download) {
                    is StoryItemDownload.Done -> Cyan
                    is StoryItemDownload.Failed -> Color(0xFFFB7185)
                    else -> Muted
                },
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        when (download) {
            is StoryItemDownload.Downloading -> {
                if (download.progress >= 0f) {
                    LinearProgressIndicator(
                        progress = { download.progress },
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(CircleShape),
                        color = Cyan,
                        trackColor = Color(0x33FFFFFF)
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .width(40.dp)
                            .height(4.dp)
                            .clip(CircleShape),
                        color = Cyan,
                        trackColor = Color(0x33FFFFFF)
                    )
                }
            }
            is StoryItemDownload.Done -> {
                Icon(Icons.Default.CheckCircle, contentDescription = "Saved", tint = Cyan, modifier = Modifier.size(24.dp))
            }
            else -> {
                IconButton(onClick = onDownload) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = "Download",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

private fun timeAgo(takenAtSec: Long): String {
    if (takenAtSec <= 0L) return "Posted recently"
    val minutes = ((System.currentTimeMillis() / 1000 - takenAtSec) / 60).coerceAtLeast(0)
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "$minutes min ago"
        else -> "${minutes / 60} h ago"
    }
}

@Composable
private fun Avatar(url: String?, size: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Orange, Pink, Violet))),
        contentAlignment = Alignment.Center
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(2.dp)
                    .clip(CircleShape)
            )
        } else {
            Icon(Icons.Default.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(size / 2))
        }
    }
}

private fun formatCount(n: Long): String = when {
    n >= 1_000_000 -> String.format("%.1fM", n / 1_000_000.0).replace(".0M", "M")
    n >= 10_000 -> String.format("%.1fK", n / 1_000.0).replace(".0K", "K")
    else -> n.toString()
}

/** Shows exactly who Instagram says we are signed in as, so it's visible the connection is real. */
@Composable
private fun AccountStrip(
    account: InstagramParser.ProfileInfo?,
    checking: Boolean,
    error: String?,
    onSignOut: () -> Unit
) {
    val shape = RoundedCornerShape(14.dp)
    val ok = account != null
    val tint = if (error != null) Orange else Cyan
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(tint.copy(alpha = 0.10f))
            .border(1.dp, tint.copy(alpha = 0.35f), shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (ok) {
            Avatar(account?.picUrl, 40.dp)
        } else {
            Icon(
                imageVector = if (error != null) Icons.Default.ErrorOutline else Icons.Default.CheckCircle,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            when {
                account != null -> {
                    Text("Connected to Instagram as", color = Muted, fontSize = 11.sp)
                    Text(
                        text = "@${account.username}",
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (account.fullName.isNotBlank()) {
                        Text(account.fullName, color = Color(0xFFE2E8F0), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                checking -> Text("Checking the connection to Instagram…", color = Color(0xFFE2E8F0), fontSize = 13.sp)
                error != null -> Text(error, color = Color(0xFFE2E8F0), fontSize = 12.sp, lineHeight = 17.sp)
                else -> Text("Signed in to Instagram", color = Color(0xFFE2E8F0), fontSize = 13.sp)
            }
        }
        Text(
            text = "Sign out",
            color = Color(0xFFFB7185),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onSignOut)
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .testTag("ig_sign_out")
        )
    }
}

/** The person you searched for: photo, name, @username, badges and followers. */
@Composable
private fun ProfileCard(profile: InstagramParser.ProfileInfo) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0x14FFFFFF))
            .border(1.dp, GlassBorder, shape)
            .padding(12.dp)
            .testTag("story_profile_card"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(profile.picUrl, 64.dp)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (profile.fullName.isNotBlank()) profile.fullName else "@${profile.username}",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "@${profile.username}",
                color = Cyan,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val facts = buildList {
                if (profile.isVerified) add("Verified ✓")
                if (profile.isPrivate) add("Private 🔒")
                profile.followers?.let { add("${formatCount(it)} followers") }
            }
            if (facts.isNotEmpty()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(facts.joinToString("  ·  "), color = Muted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun LoginCard(onSignIn: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Orange.copy(alpha = 0.10f))
            .border(1.dp, Orange.copy(alpha = 0.35f), shape)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = Orange, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Stories need your Instagram login", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Instagram only shows stories to signed-in accounts. You sign in on Instagram's own page, " +
                "so OffScroll never sees your password, and you can sign out here any time.",
            color = Color(0xFFE2E8F0),
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Heads-up: Instagram doesn't officially allow third-party tools, and it may limit accounts that use them. " +
                "A spare account is the safer choice.",
            color = Orange,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        GradientButton(text = "Sign in with Instagram", icon = Icons.Default.Lock, onClick = onSignIn)
    }
}

// ----------------------------------------------------------------------------------------------
// Instagram sign-in (Instagram's own page, shown in a WebView)
// ----------------------------------------------------------------------------------------------

// ----------------------------------------------------------------------------------------------
// Shared pieces
// ----------------------------------------------------------------------------------------------

@Composable
private fun LinkField(
    value: String,
    placeholder: String,
    enabled: Boolean,
    keyboardType: KeyboardType,
    onChange: (String) -> Unit,
    onPaste: () -> Unit,
    onGo: () -> Unit,
    testTag: String
) {
    val fieldShape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(fieldShape)
            .background(Color(0x1FFFFFFF))
            .border(1.dp, GlassBorder, fieldShape)
            .padding(start = 14.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        BasicTextField(
            value = value,
            onValueChange = onChange,
            enabled = enabled,
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
            cursorBrush = SolidColor(Cyan),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onGo() }),
            modifier = Modifier
                .weight(1f)
                .testTag(testTag),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = Color(0xFF64748B),
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    inner()
                }
            }
        )
        if (value.isNotEmpty() && enabled) {
            IconButton(onClick = { onChange("") }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Clear", tint = Muted, modifier = Modifier.size(18.dp))
            }
        }
        if (enabled) {
            IconButton(onClick = onPaste, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = Icons.Default.ContentPaste,
                    contentDescription = "Paste",
                    tint = Cyan,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.Download
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .background(
                if (enabled) Brush.horizontalGradient(listOf(Orange, Pink, Violet))
                else SolidColor(Color(0x33FFFFFF))
            )
            .clickable(enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            color = Color.White.copy(alpha = if (enabled) 1f else 0.5f),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun OutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .height(52.dp)
            .clip(shape)
            .background(Color(0x1AFFFFFF))
            .border(1.dp, GlassBorder, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun MessageCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    text: String
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(tint.copy(alpha = 0.10f))
            .border(1.dp, tint.copy(alpha = 0.35f), shape)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = text, color = Color(0xFFE2E8F0), fontSize = 13.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun BusyCard(label: String, progress: Float?, onCancel: (() -> Unit)?) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0x14FFFFFF))
            .border(1.dp, GlassBorder, shape)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (onCancel != null) {
                Text(
                    text = "Cancel",
                    color = Color(0xFFFB7185),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onCancel)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                        .testTag("downloader_cancel")
                )
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = Cyan,
                trackColor = Color(0x33FFFFFF)
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = Cyan,
                trackColor = Color(0x33FFFFFF)
            )
        }
    }
}
