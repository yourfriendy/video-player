package com.example.ui.components

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.universal.LinkUtils
import com.example.data.universal.MediaInfo
import com.example.data.universal.QualityOption
import com.example.data.universal.formatDuration
import com.example.ui.viewmodel.UniversalDownloadViewModel
import com.example.ui.viewmodel.UniversalState
import com.example.ui.viewmodel.VideoFeedViewModel
import kotlin.math.roundToInt

private val UCyan = Color(0xFF5EEAD4)
private val UMuted = Color(0xFF94A3B8)
private val URose = Color(0xFFFB7185)
private val UBorder = Color(0x26FFFFFF)

/** Sites worth naming. yt-dlp handles roughly a thousand more. */
private val SupportedApps = listOf(
    "YouTube", "Facebook", "Snapchat", "TikTok", "X", "Reddit", "Pinterest", "Vimeo", "+1000 more"
)

/** The "Any link" tab of the downloader sheet. */
@Composable
fun UniversalContent(feedViewModel: VideoFeedViewModel) {
    val vm = rememberUniversalViewModel()
    val state by vm.state.collectAsStateWithLifecycle()
    val input by vm.input.collectAsStateWithLifecycle()
    val engine by vm.engineVersion.collectAsStateWithLifecycle()
    val engineNote by vm.engineNote.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val busy = state is UniversalState.Working || state is UniversalState.Downloading

    // Android 13+ needs this for the progress notification. The download starts either way.
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(Unit) {
        vm.prepareEngine()
        // Convenience: a link already on the clipboard drops into the field
        if (vm.input.value.isBlank()) {
            LinkUtils.extractUrl(clipboard.getText()?.text)?.let { vm.setInput(it) }
        }
    }

    // A finished download: reload the library in place so the new video is in the feed
    val isSaved = state is UniversalState.Saved
    LaunchedEffect(isSaved) {
        if (isSaved) feedViewModel.onExternalVideoSaved()
    }

    Column {
        LinkField(
            value = input,
            placeholder = "Paste a YouTube, Facebook, TikTok… link",
            enabled = !busy,
            keyboardType = KeyboardType.Uri,
            onChange = { vm.setInput(it) },
            onPaste = { clipboard.getText()?.text?.let { vm.setInput(it.trim()) } },
            onGo = {
                keyboard?.hide()
                vm.lookUp()
            },
            testTag = "universal_input"
        )

        Spacer(modifier = Modifier.height(12.dp))
        SupportedAppsRow()
        Spacer(modifier = Modifier.height(16.dp))

        when (val s = state) {
            is UniversalState.Idle -> GradientButton(
                text = "Find video",
                icon = Icons.Default.Search,
                enabled = input.isNotBlank(),
                onClick = {
                    keyboard?.hide()
                    vm.lookUp()
                },
                modifier = Modifier.testTag("universal_find")
            )

            is UniversalState.Working -> BusyCard(
                label = s.message,
                progress = null,
                onCancel = { vm.cancelDownload() }
            )

            is UniversalState.Failed -> Column {
                MessageCard(icon = Icons.Default.ErrorOutline, tint = URose, text = s.message)
                Spacer(modifier = Modifier.height(12.dp))
                if (s.canUpdateEngine) {
                    GradientButton(
                        text = "Update engine and retry",
                        icon = Icons.Default.Refresh,
                        onClick = { vm.updateEngine(thenRetry = true) },
                        modifier = Modifier.testTag("universal_update_retry")
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlineButton(
                        text = "Try again",
                        onClick = { vm.lookUp() },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    GradientButton(
                        text = "Try again",
                        icon = Icons.Default.Refresh,
                        onClick = { vm.lookUp() }
                    )
                }
            }

            is UniversalState.Ready -> ReadyCard(
                info = s.info,
                selectedId = s.selectedId,
                onSelect = { vm.select(it) },
                onDownload = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    vm.startDownload()
                }
            )

            is UniversalState.Downloading -> Column {
                val percent = (s.progress * 100).roundToInt()
                BusyCard(
                    label = "${s.phase}… $percent%",
                    progress = s.progress,
                    onCancel = { vm.cancelDownload() }
                )
                if (s.detail.isNotBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(text = s.detail, color = UMuted, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${s.option.label} · you can switch to another app, the download keeps going.",
                    color = UMuted,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
            }

            is UniversalState.Saved -> Column {
                MessageCard(
                    icon = Icons.Default.CheckCircle,
                    tint = UCyan,
                    text = "Saved! ${s.option.label} is in your library (Movies/OffScroll)."
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    GradientButton(
                        text = "Play now",
                        icon = Icons.Default.PlayArrow,
                        onClick = { feedViewModel.playSavedVideo(s.uri) },
                        modifier = Modifier.weight(1f)
                    )
                    OutlineButton(
                        text = "Download another",
                        onClick = { vm.reset() },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))
        EngineRow(
            version = engine,
            note = engineNote,
            enabled = !busy,
            onUpdate = { vm.updateEngine() }
        )
    }
}

@Composable
private fun SupportedAppsRow() {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SupportedApps.forEach { name ->
            val chip = RoundedCornerShape(50)
            Text(
                text = name,
                color = Color(0xFFCBD5E1),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .clip(chip)
                    .background(Color(0x14FFFFFF))
                    .border(1.dp, UBorder, chip)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

@Composable
private fun ReadyCard(
    info: MediaInfo,
    selectedId: String,
    onSelect: (String) -> Unit,
    onDownload: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    val chosen = info.options.firstOrNull { it.id == selectedId } ?: info.options.first()

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(Color(0x14FFFFFF))
                .border(1.dp, UBorder, shape)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = info.thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(112.dp)
                    .height(68.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0x22FFFFFF))
            )
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
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = listOfNotNull(
                        info.platform,
                        info.uploader,
                        formatDuration(info.durationSec).ifEmpty { null }
                    ).joinToString(" · "),
                    color = UMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Choose quality", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        info.options.forEachIndexed { index, option ->
            QualityRow(
                option = option,
                selected = option.id == selectedId,
                isBest = index == 0 && option.quality > 0 && info.options.size > 1,
                onClick = { onSelect(option.id) }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(6.dp))
        GradientButton(
            text = "Download ${chosen.label}",
            onClick = onDownload,
            modifier = Modifier.testTag("universal_download")
        )
    }
}

@Composable
private fun QualityRow(option: QualityOption, selected: Boolean, isBest: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) UCyan.copy(alpha = 0.12f) else Color(0x10FFFFFF))
            .border(1.dp, if (selected) UCyan.copy(alpha = 0.7f) else UBorder, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .testTag("quality_${option.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .border(2.dp, if (selected) UCyan else UMuted, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(UCyan)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = option.label, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                if (isBest) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "BEST",
                        color = Color(0xFF020617),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(UCyan)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            if (option.detail.isNotEmpty()) {
                Text(text = option.detail, color = UMuted, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun EngineRow(version: String, note: String, enabled: Boolean, onUpdate: () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Download engine: yt-dlp ${version.ifBlank { "built-in" }}",
                color = UMuted,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onUpdate, enabled = enabled) {
                Text(text = "Update", color = if (enabled) UCyan else UMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (note.isNotBlank()) {
            Text(text = note, color = UMuted, fontSize = 11.sp, lineHeight = 15.sp)
        }
    }
}

/** The activity-scoped instance, shared with MainActivity (which routes links shared from other apps). */
@Composable
private fun rememberUniversalViewModel(): UniversalDownloadViewModel {
    val context = LocalContext.current
    return remember(context) {
        var c: Context = context
        while (c is ContextWrapper && c !is ComponentActivity) c = c.baseContext
        val owner = c as? ComponentActivity ?: error("The downloader needs to be shown inside an activity")
        ViewModelProvider(owner)[UniversalDownloadViewModel::class.java]
    }
}
