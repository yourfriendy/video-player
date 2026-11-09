package com.example.ui.components

import com.example.ui.theme.Ds
import com.example.ui.viewmodel.EngineStatus
import com.example.data.universal.engine.EngineManager
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.Lifecycle
import androidx.compose.material3.Icon
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.runtime.DisposableEffect
import android.net.Uri
import android.content.Intent
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

private val UCyan = Ds.Palette.Cyan
private val UMuted = Ds.Palette.Muted
private val URose = Ds.Palette.Bad
private val UBorder = Color(0x26FFFFFF)

/** Sites worth naming. yt-dlp handles roughly a thousand more. */
private val SupportedApps = listOf(
    "YouTube", "Facebook", "Snapchat", "TikTok", "X", "Reddit", "Pinterest", "Vimeo", "+1000 more"
)

/**
 * The "Any link" tab of the downloader sheet. The yt-dlp engine behind it is a separate, optional app
 * (about 50 MB), so until it is installed this tab shows a one-tap install card instead.
 */
@Composable
fun UniversalContent(feedViewModel: VideoFeedViewModel) {
    val vm = rememberUniversalViewModel()
    val status by vm.engineStatus.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Coming back from Android's installer: pick up that the engine is there now
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) vm.refreshEngineStatus()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (status is EngineStatus.Installed) {
        UniversalBody(feedViewModel, vm)
    } else {
        EngineInstallCard(
            status = status,
            onInstall = { vm.installEngine() },
            onOpenGitHub = {
                runCatching {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, Uri.parse(EngineManager.RELEASE_PAGE)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }
        )
    }
}

/** Shown instead of the downloader until the optional engine app is installed (or after it needs an update). */
@Composable
private fun EngineInstallCard(status: EngineStatus, onInstall: () -> Unit, onOpenGitHub: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0x24FFFFFF), Color(0x0DFFFFFF))))
            .border(1.dp, UBorder, shape)
            .padding(20.dp)
            .testTag("engine_install_card"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFFF59E0B), Color(0xFFEC4899), Color(0xFF8B5CF6)))),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Extension, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp))
        }
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = if (status is EngineStatus.Outdated) "Update the download engine" else "Add the download engine",
            color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = when (status) {
                is EngineStatus.Outdated -> status.message
                else -> "Saving from YouTube, Facebook, TikTok, Snapchat, X, Reddit and 1000+ more sites needs a small " +
                    "add-on app (about 50 MB). It keeps OffScroll itself light, and Instagram reels and stories work without it."
            },
            color = UMuted, fontSize = 13.sp, lineHeight = 19.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(modifier = Modifier.height(16.dp))
        SupportedAppsRow()
        Spacer(modifier = Modifier.height(18.dp))

        when (status) {
            is EngineStatus.Downloading -> BusyCard(
                label = "Downloading engine… ${(status.progress * 100).roundToInt()}%",
                progress = status.progress,
                onCancel = null
            )

            is EngineStatus.Installing -> MessageCard(
                icon = Icons.Default.CheckCircle,
                tint = UCyan,
                text = if (status.needsPermission) {
                    "Allow installs from OffScroll on the screen that just opened, come back, and tap Install again."
                } else {
                    "Tap Install in the Android installer, then come back here. If Play Protect says it hasn't seen this app before, tap More details, then Install anyway (OffScroll isn't on the Play Store)."
                }
            )

            is EngineStatus.Failed -> Column {
                MessageCard(icon = Icons.Default.ErrorOutline, tint = URose, text = status.message)
                Spacer(modifier = Modifier.height(12.dp))
                GradientButton(text = "Try again", icon = Icons.Default.Refresh, onClick = onInstall)
            }

            else -> GradientButton(
                text = if (status is EngineStatus.Outdated) "Update engine" else "Download and install engine",
                icon = Icons.Default.Download,
                onClick = onInstall,
                modifier = Modifier.testTag("engine_install_button")
            )
        }
        if (status !is EngineStatus.Downloading) {
            Spacer(modifier = Modifier.height(10.dp))
            TextButton(onClick = onOpenGitHub) {
                Text("Open GitHub page", color = UMuted, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun UniversalBody(feedViewModel: VideoFeedViewModel, vm: UniversalDownloadViewModel) {
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
                    .clip(RoundedCornerShape(12.dp))
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
    val shape = RoundedCornerShape(16.dp)
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
                Text(text = option.label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                if (isBest) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "BEST",
                        color = Color(0xFF020617),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
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
