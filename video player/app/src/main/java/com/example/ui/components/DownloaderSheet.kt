package com.example.ui.components

import com.example.ui.theme.Ds
import androidx.compose.material.icons.filled.Link
import com.example.ui.theme.bouncyClickable
import androidx.compose.ui.draw.shadow
import androidx.compose.material.icons.filled.Verified
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
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

private val Cyan = Ds.Palette.Cyan
private val Violet = Ds.Palette.Violet
private val Pink = Ds.Palette.Pink
private val Orange = Ds.Palette.Orange
private val Muted = Ds.Palette.Muted

// ----------------------------------------------------------------------------------------------
// Reels tab
// ----------------------------------------------------------------------------------------------

@Composable
internal fun ReelsContent(uiState: VideoFeedUiState, viewModel: VideoFeedViewModel) {
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

internal fun timeAgo(takenAtSec: Long): String {
    if (takenAtSec <= 0L) return "Posted recently"
    val minutes = ((System.currentTimeMillis() / 1000 - takenAtSec) / 60).coerceAtLeast(0)
    return when {
        minutes < 1 -> "Just now"
        minutes < 60 -> "$minutes min ago"
        else -> "${minutes / 60} h ago"
    }
}

@Composable
internal fun Avatar(
    url: String?,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    /** Shown while the photo loads, or if there is none / it can't be loaded (a username works). */
    initial: String? = null
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Orange, Pink, Violet))),
        contentAlignment = Alignment.Center
    ) {
        val letter = initial?.trim()?.removePrefix("@")?.take(1)?.uppercase().orEmpty()
        if (url == null && letter.isEmpty()) {
            Icon(Icons.Default.Person, contentDescription = null, tint = Color.White, modifier = Modifier.size(size / 2))
        } else {
            if (letter.isNotEmpty()) {
                Text(letter, color = Color.White, fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.Bold)
            }
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
            }
        }
    }
}

internal fun formatCount(n: Long): String = when {
    n >= 1_000_000 -> String.format("%.1fM", n / 1_000_000.0).replace(".0M", "M")
    n >= 10_000 -> String.format("%.1fK", n / 1_000.0).replace(".0K", "K")
    else -> n.toString()
}

/** Shows exactly who Instagram says we are signed in as, so it's visible the connection is real. */
@Composable
internal fun AccountStrip(
    account: InstagramParser.ProfileInfo?,
    checking: Boolean,
    error: String?,
    onSignOut: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
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

/** The person you searched for: a big photo in a story ring, name, badges, stats and bio. */
@Composable
internal fun ProfileCard(profile: InstagramParser.ProfileInfo, onAvatarClick: (() -> Unit)? = null) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(Color(0x24FFFFFF), Color(0x0DFFFFFF))))
            .border(1.dp, GlassBorder, shape)
            .padding(16.dp)
            .testTag("story_profile_card")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .then(if (onAvatarClick != null) Modifier.clip(CircleShape).clickable(onClick = onAvatarClick) else Modifier)
                    .testTag("story_profile_avatar"),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .border(3.dp, Brush.sweepGradient(listOf(Orange, Pink, Violet, Pink, Orange)), CircleShape)
                )
                Avatar(profile.picUrl, 80.dp, initial = profile.username, modifier = Modifier.border(3.dp, Color(0xFF0B1226), CircleShape))
                if (onAvatarClick != null) {
                    Icon(
                        Icons.Default.PlayArrow, contentDescription = "Watch story", tint = Color.White,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(26.dp)
                            .background(Brush.linearGradient(listOf(Orange, Pink, Violet)), CircleShape)
                            .border(2.dp, Color(0xFF0B1226), CircleShape)
                            .padding(3.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (profile.fullName.isNotBlank()) profile.fullName else "@${profile.username}",
                        color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
                    )
                    if (profile.isVerified) {
                        Spacer(modifier = Modifier.width(5.dp))
                        Icon(Icons.Default.Verified, contentDescription = "Verified", tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                    }
                }
                Text("@${profile.username}", color = Cyan, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (profile.isPrivate) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clip(CircleShape).background(Color(0x1FFFFFFF)).padding(horizontal = 9.dp, vertical = 3.dp)
                    ) {
                        Icon(Icons.Default.Lock, contentDescription = null, tint = Muted, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Private account", color = Color(0xFFE2E8F0), fontSize = 11.sp)
                    }
                }
            }
        }
        val stats = listOf(
            "Posts" to profile.posts,
            "Followers" to profile.followers,
            "Following" to profile.following
        ).filter { it.second != null }
        if (stats.isNotEmpty()) {
            Spacer(modifier = Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0x1A000000))
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                stats.forEach { (label, value) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(formatCount(value ?: 0L), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(label, color = Muted, fontSize = 11.sp)
                    }
                }
            }
        }
        if (profile.bio.isNotBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(profile.bio, color = Color(0xFFE2E8F0), fontSize = 13.sp, lineHeight = 18.sp, maxLines = 6, overflow = TextOverflow.Ellipsis)
        }
        profile.externalUrl?.let {
            Spacer(modifier = Modifier.height(6.dp))
            Text(it.removePrefix("https://").removePrefix("http://").take(40), color = Cyan, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onAvatarClick != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Text("Tap the photo to watch their story", color = Muted, fontSize = 11.sp)
        }
    }
}

@Composable
internal fun LoginCard(onSignIn: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
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
internal fun LinkField(
    value: String,
    placeholder: String,
    enabled: Boolean,
    keyboardType: KeyboardType,
    onChange: (String) -> Unit,
    onPaste: () -> Unit,
    onGo: () -> Unit,
    testTag: String
) {
    val fieldShape = RoundedCornerShape(20.dp)
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .clip(fieldShape)
            .background(if (focused) Color(0x2BFFFFFF) else Color(0x1FFFFFFF))
            .border(
                width = if (focused) 1.5.dp else 1.dp,
                brush = if (focused) Brush.horizontalGradient(listOf(Orange, Pink, Violet)) else SolidColor(GlassBorder),
                shape = fieldShape
            )
            .padding(start = 14.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Link, contentDescription = null,
            tint = if (focused) Cyan else Muted, modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        BasicTextField(
            value = value,
            onValueChange = onChange,
            enabled = enabled,
            singleLine = true,
            textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
            cursorBrush = SolidColor(Cyan),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onGo() }),
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { focused = it.isFocused }
                .testTag(testTag),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = Color(0xFF64748B),
                            fontSize = 14.sp,
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
internal fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: androidx.compose.ui.graphics.vector.ImageVector = Icons.Default.Download
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .then(if (enabled) Modifier.shadow(12.dp, shape, ambientColor = Pink, spotColor = Pink) else Modifier)
            .clip(shape)
            .background(
                if (enabled) Brush.horizontalGradient(listOf(Orange, Pink, Violet))
                else SolidColor(Color(0x33FFFFFF))
            )
            .bouncyClickable(pressedScale = 0.97f, enabled = enabled, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(21.dp))
        Spacer(modifier = Modifier.width(9.dp))
        Text(
            text = text,
            color = Color.White.copy(alpha = if (enabled) 1f else 0.5f),
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
internal fun OutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier
            .height(54.dp)
            .clip(shape)
            .background(Color(0x1AFFFFFF))
            .border(1.dp, GlassBorder, shape)
            .bouncyClickable(pressedScale = 0.97f, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text = text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun MessageCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    text: String
) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(tint.copy(alpha = 0.12f))
            .border(1.dp, tint.copy(alpha = 0.40f), shape)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Text(text = text, color = Color(0xFFE2E8F0), fontSize = 13.sp, lineHeight = 18.sp)
    }
}

@Composable
internal fun BusyCard(label: String, progress: Float?, onCancel: (() -> Unit)?) {
    val shape = RoundedCornerShape(20.dp)
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
        GradientProgress(progress)
    }
}
