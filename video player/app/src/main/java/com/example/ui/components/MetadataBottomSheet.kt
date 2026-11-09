package com.example.ui.components

import com.example.R
import androidx.compose.ui.res.stringResource
import com.example.ui.theme.Ds
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DataUsage
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.VideoMetadataDetails
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassBorderSubtle
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Violet = Ds.Palette.VioletSoft
private val Teal = Ds.Palette.Cyan
private val Sky = Color(0xFF60A5FA)
private val Amber = Ds.Palette.Warn
private val Rose = Ds.Palette.Bad
private val CardFill = Color(0x66020617)
private val Muted = Ds.Palette.Muted

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetadataBottomSheet(
    details: VideoMetadataDetails,
    onDismiss: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
) {
    val context = LocalContext.current
    val video = details.video

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFA0B1020),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .background(Color(0x44FFFFFF), RoundedCornerShape(4.dp))
            )
        },
        modifier = Modifier
            .border(1.dp, GlassBorder, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .testTag("metadata_bottom_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp)
                .padding(bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            FeedbackPill(Modifier.align(Alignment.CenterHorizontally))
            Reveal(0) { TopBar(onDismiss) }

            Reveal(1) {
                HeroCard(
                    name = video.displayName,
                    extension = video.fileExtension,
                    folder = video.relativePath.ifEmpty { video.bucketDisplayName },
                    onCopy = { copyToClipboard(context, "File name", video.displayName) }
                )
            }

            Reveal(2) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile(Icons.Default.Timer, video.formattedDuration, "Duration", Violet, Modifier.weight(1f))
                    StatTile(Icons.Default.AspectRatio, video.formattedResolution, "Resolution", Sky, Modifier.weight(1f))
                    StatTile(Icons.Default.DataUsage, video.formattedSize, "Size", Teal, Modifier.weight(1f))
                }
            }

            Reveal(3) { StorageCard(video.sizeBytes) }

            Reveal(4) {
                SectionCard("Video", Icons.Default.Videocam, Sky) {
                    DetailRow("Codec", details.videoCodec)
                    RowDivider()
                    DetailRow("Format", video.fileExtension)
                    RowDivider()
                    DetailRow("Frame rate", details.frameRate)
                    if (details.captureFps.isNotBlank()) {
                        RowDivider()
                        DetailRow("Capture FPS", details.captureFps)
                    }
                    RowDivider()
                    DetailRow("Bitrate", details.bitrate)
                    RowDivider()
                    DetailRow("Rotation", details.rotation)
                    RowDivider()
                    DetailRow("Color", details.colorStandard)
                }
            }

            Reveal(5) {
                SectionCard("Audio", Icons.Default.MusicNote, Teal) {
                    DetailRow("Track", details.audioCodec)
                }
            }

            Reveal(6) {
                SectionCard("File", Icons.Default.Folder, Amber) {
                    DetailRow("Added", video.formattedDateAdded)
                    RowDivider()
                    DetailRow("Modified", formatSeconds(video.dateModifiedSec))
                    RowDivider()
                    DetailRow("Type", video.mimeType.ifBlank { "Unknown" })
                    RowDivider()
                    DetailRow(
                        label = "Folder",
                        value = video.relativePath.ifEmpty { video.bucketDisplayName },
                        onCopy = { copyToClipboard(context, "Folder", video.relativePath.ifEmpty { video.bucketDisplayName }) }
                    )
                    if (video.filePath.isNotBlank()) {
                        RowDivider()
                        DetailRow(
                            label = "Full path",
                            value = video.filePath,
                            onCopy = { copyToClipboard(context, "Path", video.filePath) }
                        )
                    }
                }
            }
        }
    }
}

/** Fades and slides a section in, one after another, so the sheet feels alive instead of snapping open. */
@Composable
private fun Reveal(index: Int, content: @Composable () -> Unit) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(index * 55L)
        shown = true
    }
    AnimatedVisibility(
        visible = shown,
        enter = fadeIn(tween(380)) + slideInVertically(tween(380, easing = FastOutSlowInEasing)) { it / 5 }
    ) {
        content()
    }
}

@Composable
private fun TopBar(onDismiss: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(stringResource(R.string.details), color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Text("Everything about this video, 100% on your phone", color = Muted, fontSize = 12.sp)
        }
        IconButton(
            onClick = onDismiss,
            modifier = Modifier
                .size(34.dp)
                .background(Color(0x22FFFFFF), CircleShape)
                .border(1.dp, GlassBorderSubtle, CircleShape)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xCCFFFFFF), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun HeroCard(name: String, extension: String, folder: String, onCopy: () -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF3B2A7A), Color(0xFF1E2A5E), Color(0xFF0F3A47))))
            .border(1.dp, Brush.verticalGradient(listOf(Color(0x66FFFFFF), Color(0x14FFFFFF))), shape)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .background(Brush.linearGradient(listOf(Color(0x66A78BFA), Color(0x335EEAD4))), RoundedCornerShape(20.dp))
                    .border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Movie, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Chip(extension.uppercase(Locale.getDefault()), Teal)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = folder,
                        color = Color(0xB3FFFFFF),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = onCopy,
                modifier = Modifier
                    .size(38.dp)
                    .background(Color(0x33FFFFFF), CircleShape)
                    .border(1.dp, Color(0x44FFFFFF), CircleShape)
            ) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy file name", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun Chip(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp,
        modifier = Modifier
            .background(color.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

@Composable
private fun StatTile(icon: ImageVector, value: String, label: String, accent: Color, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(CardFill)
            .border(1.dp, GlassBorderSubtle, shape)
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(34.dp).background(accent.copy(alpha = 0.16f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = value,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(text = label, color = Muted, fontSize = 11.sp)
    }
}

/** Shows how much of the phone's storage this one file takes, with a smooth animated bar. */
@Composable
private fun StorageCard(fileBytes: Long) {
    val (freeBytes, totalBytes) = remember {
        try {
            val stat = StatFs(Environment.getDataDirectory().path)
            stat.availableBytes to stat.blockCountLong * stat.blockSizeLong
        } catch (e: Exception) {
            0L to 0L
        }
    }
    val usedShare = if (totalBytes > 0) (fileBytes.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
    val phoneUsed = if (totalBytes > 0) ((totalBytes - freeBytes).toFloat() / totalBytes).coerceIn(0f, 1f) else 0f
    val animatedUsed by animateFloatAsState(phoneUsed, tween(900, easing = FastOutSlowInEasing), label = "phone_used")
    val animatedFile by animateFloatAsState(usedShare, tween(1100, easing = FastOutSlowInEasing), label = "file_share")

    SectionCard("Storage", Icons.Default.Storage, Violet) {
        if (totalBytes <= 0) {
            Text(stringResource(R.string.storage_info_isn_t_available_on), color = Muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 10.dp))
            return@SectionCard
        }
        Spacer(Modifier.height(10.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0x22FFFFFF))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedUsed.coerceAtLeast(0.02f))
                    .background(Brush.horizontalGradient(listOf(Color(0xFF6366F1), Violet)))
            )
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedFile.coerceAtLeast(0.01f))
                    .background(Rose)
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.this_video) + formatBytes(fileBytes), color = Rose, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(formatBytes(freeBytes) + " free of " + formatBytes(totalBytes), color = Muted, fontSize = 12.sp)
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun SectionCard(
    title: String,
    icon: ImageVector,
    accent: Color,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(CardFill)
            .border(1.dp, GlassBorderSubtle, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(26.dp).background(accent.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(15.dp))
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = title.uppercase(Locale.getDefault()),
                color = accent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
        content()
    }
}

@Composable
private fun RowDivider() = HorizontalDivider(color = Color(0x14FFFFFF))

@Composable
private fun DetailRow(label: String, value: String, onCopy: (() -> Unit)? = null) {
    val clickable = if (onCopy != null) Modifier.clickable { onCopy() } else Modifier
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(clickable)
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(text = label, color = Muted, fontSize = 13.sp, modifier = Modifier.width(96.dp))
        Text(
            text = value,
            color = if (value == "Unknown") Color(0x66FFFFFF) else Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f)
        )
    }
}

private fun formatBytes(bytes: Long): String {
    val gb = bytes / 1_073_741_824.0
    val mb = bytes / 1_048_576.0
    return when {
        gb >= 1 -> String.format(Locale.getDefault(), "%.1f GB", gb)
        mb >= 1 -> String.format(Locale.getDefault(), "%.1f MB", mb)
        else -> String.format(Locale.getDefault(), "%d KB", bytes / 1024)
    }
}

private fun formatSeconds(sec: Long): String =
    if (sec <= 0) "Unknown"
    else SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()).format(Date(sec * 1000))

private fun copyToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText(label, text))
    Feedback.show("Copied $label")
}
