package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
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
import com.example.data.download.InstagramParser
import com.example.ui.model.DownloadStatus
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.Slate950
import kotlin.math.roundToInt

private val Cyan = Color(0xFF5EEAD4)
private val Violet = Color(0xFF8B5CF6)
private val Pink = Color(0xFFEC4899)
private val Orange = Color(0xFFF59E0B)
private val Muted = Color(0xFF94A3B8)

/**
 * "Save from Instagram": paste a public reel link (or share a reel straight to OffScroll) and
 * it is saved to Movies/OffScroll, where it shows up in the feed like any other video.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloaderSheet(
    url: String,
    status: DownloadStatus,
    onUrlChange: (String) -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onPlay: () -> Unit,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isBusy = status is DownloadStatus.Fetching || status is DownloadStatus.Downloading

    // Convenience: if an Instagram link is already on the clipboard, drop it into the field
    LaunchedEffect(Unit) {
        if (url.isBlank()) {
            val copied = clipboard.getText()?.text
            InstagramParser.extractInstagramUrl(copied)?.let { onUrlChange(it) }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
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
                        text = "Paste a reel link, or share a reel to OffScroll",
                        fontSize = 12.sp,
                        color = Muted
                    )
                }
                IconButton(onClick = onDismiss, modifier = Modifier.testTag("downloader_close")) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // ---- Link field ----
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
                    value = url,
                    onValueChange = onUrlChange,
                    enabled = !isBusy,
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                    cursorBrush = SolidColor(Cyan),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        keyboard?.hide()
                        onDownload()
                    }),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("downloader_input"),
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (url.isEmpty()) {
                                Text(
                                    text = "https://www.instagram.com/reel/…",
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
                if (url.isNotEmpty() && !isBusy) {
                    IconButton(onClick = { onUrlChange("") }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Clear", tint = Muted, modifier = Modifier.size(18.dp))
                    }
                }
                if (!isBusy) {
                    IconButton(
                        onClick = {
                            clipboard.getText()?.text?.let { onUrlChange(it.trim()) }
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .testTag("downloader_paste")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentPaste,
                            contentDescription = "Paste",
                            tint = Cyan,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ---- Status / action area ----
            Column {
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
                                    onDownload()
                                }
                            )
                        }
                    }

                    is DownloadStatus.Fetching -> {
                        BusyCard(label = "Finding the video…", progress = null, onCancel = onCancel)
                    }

                    is DownloadStatus.Downloading -> {
                        val p = status.progress
                        BusyCard(
                            label = if (p >= 0f) "Downloading… ${(p * 100).roundToInt()}%" else "Downloading…",
                            progress = if (p >= 0f) p else null,
                            onCancel = onCancel
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
                                    onClick = onPlay,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlineButton(
                                    text = "Download another",
                                    onClick = { onUrlChange("") },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // ---- Help + fair-use note ----
            Text(
                text = "Tip: in Instagram tap Share → Copy link, or share the reel straight to OffScroll and it downloads automatically.",
                color = Muted,
                fontSize = 12.sp,
                lineHeight = 17.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Works with public reels only. Please only save videos you have permission to keep, and respect the creator's rights.",
                color = Color(0xFF64748B),
                fontSize = 11.sp,
                lineHeight = 15.sp
            )
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
private fun BusyCard(label: String, progress: Float?, onCancel: () -> Unit) {
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
