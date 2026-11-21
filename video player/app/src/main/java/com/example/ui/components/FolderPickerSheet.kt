package com.example.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.local.entity.PlaylistEntity
import com.example.data.model.LocalVideo
import com.example.ui.model.FeedFilter
import com.example.ui.theme.Ds
import com.example.ui.theme.GlassBorderSubtle
import com.example.ui.theme.Slate950
import kotlinx.coroutines.delay

/** One folder on the phone, with how many videos it holds and a video to use as its cover. */
data class FolderInfo(val name: String, val videoCount: Int, val cover: LocalVideo?)

/** Groups the library into folders (A to Z). The cover is the newest real video in each folder. */
fun buildFolderInfos(videos: List<LocalVideo>): List<FolderInfo> =
    videos.groupBy { it.bucketDisplayName }
        .map { (name, list) ->
            FolderInfo(name, list.size, list.filter { !it.isSample }.maxByOrNull { it.dateAddedSec })
        }
        .sortedBy { it.name.lowercase() }

private val SunsetBrush = Brush.linearGradient(listOf(Ds.Palette.Orange, Ds.Palette.Pink, Ds.Palette.Violet))
private val TileFill = Color(0x14FFFFFF)

/**
 * The "where do I watch from" picker: quick views, playlists and every folder as a cover card. Replaces the plain
 * drop-down list. One scrolling grid, a search box for long folder lists, and a gentle staggered entrance (a still
 * version when animations are switched off).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderPickerSheet(
    currentFilter: FeedFilter,
    folders: List<FolderInfo>,
    playlists: List<PlaylistEntity>,
    playlistCounts: Map<Long, Int>,
    onSelectFilter: (FeedFilter) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val motion = com.example.ui.theme.rememberMotionEnabled()
    val focusManager = LocalFocusManager.current
    var query by remember { mutableStateOf("") }
    val shown = remember(folders, query) {
        val q = query.trim()
        if (q.isEmpty()) folders else folders.filter { it.name.contains(q, ignoreCase = true) }
    }
    val totalVideos = remember(folders) { folders.sumOf { it.videoCount } }
    val allLabel = stringResource(R.string.all_videos)
    val favoritesLabel = stringResource(R.string.favorites)
    val recentlyAddedLabel = stringResource(R.string.recently_added)
    val recentlyPlayedLabel = stringResource(R.string.recently_played)
    val quick = listOf(
        Triple<FeedFilter, String, Pair<ImageVector, Color>>(FeedFilter.All, allLabel, Icons.Default.Videocam to Ds.Palette.VioletSoft),
        Triple<FeedFilter, String, Pair<ImageVector, Color>>(FeedFilter.Favorites, favoritesLabel, Icons.Default.Favorite to Ds.Palette.PinkSoft),
        Triple<FeedFilter, String, Pair<ImageVector, Color>>(FeedFilter.RecentlyAdded, recentlyAddedLabel, Icons.Default.Schedule to Color(0xFF38BDF8)),
        Triple<FeedFilter, String, Pair<ImageVector, Color>>(FeedFilter.RecentlyPlayed, recentlyPlayedLabel, Icons.Default.History to Ds.Palette.Good)
    )

    fun pick(filter: FeedFilter) {
        onSelectFilter(filter)
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Slate950,
        contentColor = Color.White,
        shape = RoundedCornerShape(topStart = Ds.Radius.Sheet, topEnd = Ds.Radius.Sheet),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .width(40.dp)
                    .height(4.dp)
                    .background(Color(0x66FFFFFF), CircleShape)
            )
        },
        modifier = Modifier.testTag("folder_picker_sheet")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .background(Brush.verticalGradient(listOf(Color(0x228B5CF6), Color.Transparent), endY = 600f))
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(44.dp).background(SunsetBrush, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Folder, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text("Browse videos", color = Color.White, fontSize = Ds.Type.Title, fontWeight = FontWeight.ExtraBold)
                    if (totalVideos > 0) {
                        Text(
                            "$totalVideos videos · ${folders.size} folders",
                            color = Ds.Palette.Muted,
                            fontSize = Ds.Type.Small
                        )
                    }
                }
                Box(
                    modifier = Modifier.size(Ds.MinTouch).clip(CircleShape).clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color(0xCCFFFFFF), modifier = Modifier.size(20.dp))
                }
            }

            // Search (only worth showing once the list is long)
            if (folders.size >= 6) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(CircleShape)
                        .background(TileFill)
                        .border(1.dp, GlassBorderSubtle, CircleShape)
                        .padding(start = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, tint = Ds.Palette.Muted, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        if (query.isEmpty()) {
                            Text("Search folders", color = Color(0x80FFFFFF), fontSize = Ds.Type.Body)
                        }
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = Ds.Type.Body),
                            cursorBrush = SolidColor(Ds.Palette.Cyan),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                            modifier = Modifier.fillMaxWidth().testTag("folder_search_field")
                        )
                    }
                    if (query.isNotEmpty()) {
                        Box(
                            modifier = Modifier.size(Ds.MinTouch).clip(CircleShape).clickable { query = "" },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Clear", tint = Ds.Palette.Muted, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                if (query.isBlank()) {
                    // Quick views
                    quick.forEachIndexed { i, (filter, label, iconAndTint) ->
                        item(key = "quick_$i") {
                            Staggered(index = i, motion = motion) {
                                RowTile(
                                    title = label,
                                    subtitle = if (filter is FeedFilter.All && totalVideos > 0) "$totalVideos" else null,
                                    selected = currentFilter == filter,
                                    onClick = { pick(filter) },
                                    leading = {
                                        Icon(iconAndTint.first, contentDescription = null, tint = iconAndTint.second, modifier = Modifier.size(18.dp))
                                    },
                                    leadingTint = iconAndTint.second
                                )
                            }
                        }
                    }

                    // Playlists
                    item(key = "h_playlists", span = { GridItemSpan(maxLineSpan) }) {
                        SectionLabel(stringResource(R.string.playlists_section), if (playlists.isEmpty()) null else "${playlists.size}")
                    }
                    if (playlists.isEmpty()) {
                        item(key = "playlists_empty", span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = stringResource(R.string.no_playlists_hint),
                                color = Ds.Palette.Muted,
                                fontSize = Ds.Type.Small,
                                lineHeight = 16.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(Ds.Radius.Md))
                                    .background(TileFill)
                                    .padding(14.dp)
                            )
                        }
                    } else {
                        playlists.forEachIndexed { i, playlist ->
                            item(key = "playlist_${playlist.id}") {
                                Staggered(index = 4 + i, motion = motion) {
                                    RowTile(
                                        title = playlist.name,
                                        subtitle = playlistCounts[playlist.id]?.let { "$it" },
                                        selected = currentFilter is FeedFilter.Playlist && currentFilter.playlistId == playlist.id,
                                        onClick = { pick(FeedFilter.Playlist(playlist.id, playlist.name)) },
                                        leading = { Text(playlist.emojiOrIcon ?: "📁", fontSize = Ds.Type.Title) },
                                        leadingTint = Ds.Palette.Violet
                                    )
                                }
                            }
                        }
                    }
                }

                // Folders
                if (folders.isNotEmpty()) {
                    item(key = "h_folders", span = { GridItemSpan(maxLineSpan) }) {
                        SectionLabel(stringResource(R.string.folders), "${shown.size}")
                    }
                    if (shown.isEmpty()) {
                        item(key = "folders_empty", span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = "No folder matches \"${query.trim()}\"",
                                color = Ds.Palette.Muted,
                                fontSize = Ds.Type.Body,
                                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp)
                            )
                        }
                    }
                    shown.forEachIndexed { i, info ->
                        item(key = "folder_${info.name}") {
                            Staggered(index = i, motion = motion) {
                                FolderCard(
                                    info = info,
                                    selected = currentFilter is FeedFilter.ByFolder &&
                                        currentFilter.folderName.equals(info.name, ignoreCase = true),
                                    onClick = { pick(FeedFilter.ByFolder(info.name)) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Fades and floats a card in, one after another. Only the first dozen animate; later ones just appear. */
@Composable
private fun Staggered(index: Int, motion: Boolean, content: @Composable () -> Unit) {
    val animate = motion && index < 12
    val progress = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (animate) {
            delay(index * 35L)
            progress.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
        }
    }
    Box(
        modifier = Modifier.graphicsLayer {
            alpha = progress.value
            translationY = (1f - progress.value) * 24.dp.toPx()
        }
    ) { content() }
}

/** Taps shrink the card a touch and spring back. */
@Composable
private fun Modifier.pressScale(onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 500f),
        label = "folder_press"
    )
    return this
        .graphicsLayer { scaleX = scale; scaleY = scale }
        .clickable(interactionSource = source, indication = null, onClick = onClick)
}

@Composable
private fun SectionLabel(text: String, trailing: String?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text.uppercase(),
            color = Ds.Palette.Muted,
            fontSize = Ds.Type.Caption,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) {
            Text(trailing, color = Color(0x99FFFFFF), fontSize = Ds.Type.Caption, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** A compact tile: icon on the left, name, optional count, a check when it is the current view. */
@Composable
private fun RowTile(
    title: String,
    subtitle: String?,
    selected: Boolean,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
    leadingTint: Color
) {
    val shape = RoundedCornerShape(Ds.Radius.Md)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .pressScale(onClick)
            .clip(shape)
            .background(if (selected) Color(0x268B5CF6) else TileFill)
            .border(if (selected) 1.5.dp else 1.dp, if (selected) SunsetBrush else SolidColor(GlassBorderSubtle), shape)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(34.dp).background(leadingTint.copy(alpha = 0.16f), CircleShape),
            contentAlignment = Alignment.Center
        ) { leading() }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                color = Color.White,
                fontSize = Ds.Type.Label,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(subtitle, color = Ds.Palette.Muted, fontSize = Ds.Type.Caption, maxLines = 1)
            }
        }
        if (selected) {
            Icon(Icons.Default.Check, contentDescription = "Selected", tint = Ds.Palette.Cyan, modifier = Modifier.size(18.dp))
        }
    }
}

/** A folder as a cover card: the newest video's frame, the name and the count over a dark fade. */
@Composable
private fun FolderCard(info: FolderInfo, selected: Boolean, onClick: () -> Unit) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(Ds.Radius.Lg)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.2f)
            .pressScale(onClick)
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF312E81), Color(0xFF4C1D95), Color(0xFF0F172A))))
            .border(if (selected) 2.dp else 1.dp, if (selected) SunsetBrush else SolidColor(GlassBorderSubtle), shape)
            .testTag("folder_card_${info.name}")
    ) {
        if (info.cover != null) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(info.cover.uri).crossfade(true).build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                Icons.Default.Folder,
                contentDescription = null,
                tint = Color(0x33FFFFFF),
                modifier = Modifier.size(48.dp).align(Alignment.Center)
            )
        }
        // Dark fade so the text is readable over any frame
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color(0xE6000000)))
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .size(28.dp)
                .background(Color(0x66000000), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Folder, contentDescription = null, tint = Ds.Palette.Warn, modifier = Modifier.size(15.dp))
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(24.dp)
                    .background(SunsetBrush, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Check, contentDescription = "Selected", tint = Color.White, modifier = Modifier.size(15.dp))
            }
        }
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                info.name,
                color = Color.White,
                fontSize = Ds.Type.Label,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (info.videoCount > 0) {
                Text(
                    if (info.videoCount == 1) "1 video" else "${info.videoCount} videos",
                    color = Color(0xCCFFFFFF),
                    fontSize = Ds.Type.Caption
                )
            }
        }
    }
}
