package com.example.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.example.data.model.LocalVideo
import com.example.ui.model.DurationBucket
import com.example.ui.model.FeedFilter
import com.example.ui.model.SearchOptions
import com.example.ui.model.SearchSort
import com.example.ui.theme.FrostedRose
import com.example.ui.theme.FrostedVioletLight
import com.example.ui.theme.GlassBorder
import com.example.ui.theme.GlassBorderSubtle
import com.example.ui.theme.Slate800
import com.example.ui.viewmodel.VideoFeedUiState
import com.example.ui.viewmodel.VideoFeedViewModel

private val Violet = Color(0xFF8B5CF6)
private val Indigo = Color(0xFF6366F1)
private val Cyan = Color(0xFF5EEAD4)
private val Muted = Color(0xFF94A3B8)

/**
 * Full-screen search page.
 *
 * - Type to filter instantly (matching ignores case, punctuation and file extensions).
 * - Narrow by length, favorites, folder and sort order.
 * - With nothing typed it shows recent searches, quick picks, recently added videos and
 *   videos you can continue watching.
 * - Tapping a result opens that exact video and turns the feed into the search results.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(
    uiState: VideoFeedUiState,
    viewModel: VideoFeedViewModel,
    modifier: Modifier = Modifier
) {
    BackHandler { viewModel.closeSearch() }

    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    var isFocused by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        try {
            focusRequester.requestFocus()
        } catch (_: Exception) {
            // not attached yet; the user can tap the field
        }
    }
    // Scrolling the list means "I'm done typing": get the keyboard out of the way
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress) keyboard?.hide()
    }

    val draft = uiState.searchDraft
    val options = uiState.searchOptions
    val hasQuery = draft.isNotBlank()
    val showSuggestions = !hasQuery && options.isDefault
    val tokens = remember(draft) { searchTokens(draft) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(listOf(Color(0xFF160B38), Color(0xFF0B0A16), Color(0xFF05060B)))
            )
            // Swallow touches so nothing behind this page can be scrolled or tapped
            .pointerInput(Unit) { detectTapGestures { } }
            .testTag("search_screen")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
        ) {
            // ---- Search field ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 6.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.closeSearch() }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White
                    )
                }
                val shape = RoundedCornerShape(18.dp)
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .height(52.dp)
                        .clip(shape)
                        .background(Color(0x1FFFFFFF))
                        .border(
                            width = if (isFocused) 1.5.dp else 1.dp,
                            brush = if (isFocused) {
                                Brush.horizontalGradient(listOf(Cyan, Violet))
                            } else {
                                SolidColor(GlassBorder)
                            },
                            shape = shape
                        )
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = if (isFocused) Cyan else Muted,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    BasicTextField(
                        value = draft,
                        onValueChange = { viewModel.setSearchDraft(it) },
                        singleLine = true,
                        textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                        cursorBrush = SolidColor(Cyan),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester)
                            .onFocusChanged { isFocused = it.isFocused }
                            .testTag("search_input"),
                        decorationBox = { inner ->
                            Box(contentAlignment = Alignment.CenterStart) {
                                if (draft.isEmpty()) {
                                    Text(
                                        text = "Search your videos…",
                                        color = Color(0xFF64748B),
                                        fontSize = 16.sp
                                    )
                                }
                                inner()
                            }
                        }
                    )
                    if (draft.isNotEmpty()) {
                        IconButton(
                            onClick = { viewModel.setSearchDraft("") },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear text",
                                tint = Muted,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // ---- Filter chips ----
            FilterChipsRow(
                options = options,
                folders = uiState.folders,
                onChange = { viewModel.setSearchOptions(it) }
            )

            // ---- Body ----
            if (showSuggestions) {
                SuggestionsList(
                    uiState = uiState,
                    listState = listState,
                    onPickRecent = { viewModel.setSearchDraft(it) },
                    onRemoveRecent = { viewModel.removeRecentSearch(it) },
                    onClearRecents = { viewModel.clearRecentSearches() },
                    onSetOptions = { viewModel.setSearchOptions(it) },
                    onPlay = { viewModel.playVideo(it, FeedFilter.All) }
                )
            } else if (uiState.searchResults.isEmpty() && !uiState.isSearchRunning) {
                NoResults(
                    onReset = {
                        viewModel.setSearchDraft("")
                        viewModel.setSearchOptions(SearchOptions())
                    }
                )
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item(key = "header") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${uiState.searchResults.size} " +
                                    if (uiState.searchResults.size == 1) "result" else "results",
                                color = Color.White,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = "Tap one to play it",
                                color = Muted,
                                fontSize = 12.sp
                            )
                        }
                    }
                    items(uiState.searchResults, key = { it.id }) { video ->
                        val saved = uiState.watchHistoryMap[video.uri.toString()]?.lastPositionMs ?: 0L
                        val progress = if (video.durationMs > 0) {
                            (saved.toFloat() / video.durationMs).coerceIn(0f, 1f)
                        } else 0f
                        SearchResultRow(
                            video = video,
                            tokens = tokens,
                            progress = progress,
                            onClick = { viewModel.openSearchResult(video) }
                        )
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------------------------------------------------
// Filter chips
// ----------------------------------------------------------------------------------------------

@Composable
private fun FilterChipsRow(
    options: SearchOptions,
    folders: List<String>,
    onChange: (SearchOptions) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "duration") {
            DropdownChip(
                label = options.duration.label,
                selected = options.duration != DurationBucket.ANY,
                icon = Icons.Default.Timer,
                entries = DurationBucket.values().map { bucket ->
                    bucket.label to { onChange(options.copy(duration = bucket)) }
                }
            )
        }
        item(key = "favorites") {
            OptionChip(
                label = "Favorites",
                selected = options.favoritesOnly,
                icon = Icons.Default.Favorite,
                onClick = { onChange(options.copy(favoritesOnly = !options.favoritesOnly)) }
            )
        }
        if (folders.isNotEmpty()) {
            item(key = "folder") {
                DropdownChip(
                    label = options.folder ?: "All folders",
                    selected = options.folder != null,
                    icon = Icons.Default.Folder,
                    entries = listOf("All folders" to { onChange(options.copy(folder = null)) }) +
                        folders.map { name -> name to { onChange(options.copy(folder = name)) } }
                )
            }
        }
        item(key = "sort") {
            DropdownChip(
                label = options.sort.label,
                selected = options.sort != SearchSort.BEST_MATCH,
                icon = Icons.Default.Sort,
                entries = SearchSort.values().map { sort ->
                    sort.label to { onChange(options.copy(sort = sort)) }
                }
            )
        }
        if (!options.isDefault || options.sort != SearchSort.BEST_MATCH) {
            item(key = "reset") {
                OptionChip(
                    label = "Reset",
                    selected = false,
                    icon = Icons.Default.Refresh,
                    onClick = { onChange(SearchOptions()) }
                )
            }
        }
    }
}

@Composable
private fun DropdownChip(
    label: String,
    selected: Boolean,
    icon: ImageVector,
    entries: List<Pair<String, () -> Unit>>
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OptionChip(label = label, selected = selected, icon = icon, onClick = { expanded = true })
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            entries.forEach { (text, action) ->
                DropdownMenuItem(
                    text = { Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    onClick = {
                        action()
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun OptionChip(
    label: String,
    selected: Boolean,
    icon: ImageVector? = null,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .height(36.dp)
            .clip(shape)
            .background(
                if (selected) Brush.horizontalGradient(listOf(Violet, Indigo))
                else SolidColor(Color(0x1AFFFFFF))
            )
            .border(1.dp, if (selected) Color.Transparent else GlassBorder, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) Color.White else Muted,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
        }
        Text(
            text = label,
            color = if (selected) Color.White else Color(0xFFE2E8F0),
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// ----------------------------------------------------------------------------------------------
// Suggestions (nothing typed yet)
// ----------------------------------------------------------------------------------------------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SuggestionsList(
    uiState: VideoFeedUiState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onPickRecent: (String) -> Unit,
    onRemoveRecent: (String) -> Unit,
    onClearRecents: () -> Unit,
    onSetOptions: (SearchOptions) -> Unit,
    onPlay: (LocalVideo) -> Unit
) {
    val recentlyAdded = remember(uiState.allVideos) {
        uiState.allVideos.sortedByDescending { it.dateAddedSec }.take(12)
    }
    val continueWatching = remember(uiState.allVideos, uiState.watchHistoryMap) {
        uiState.allVideos
            .filter { video ->
                val saved = uiState.watchHistoryMap[video.uri.toString()]?.lastPositionMs ?: 0L
                saved > 5_000L && video.durationMs > 60_000L && saved < video.durationMs - 5_000L
            }
            .sortedByDescending { uiState.watchHistoryMap[it.uri.toString()]?.lastPlayedAt ?: 0L }
            .take(10)
    }
    val topFolders = remember(uiState.allVideos) {
        uiState.allVideos
            .groupingBy { it.bucketDisplayName }
            .eachCount()
            .entries
            .sortedByDescending { it.value }
            .take(6)
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp)
    ) {
        if (uiState.recentSearches.isNotEmpty()) {
            item(key = "recent") {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionTitle("Recent searches")
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            text = "Clear all",
                            color = FrostedVioletLight,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(onClick = onClearRecents)
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        uiState.recentSearches.forEach { query ->
                            RecentChip(
                                text = query,
                                onClick = { onPickRecent(query) },
                                onRemove = { onRemoveRecent(query) }
                            )
                        }
                    }
                }
            }
        }

        item(key = "quick") {
            Column {
                SectionTitle("Quick picks", Modifier.padding(horizontal = 16.dp))
                Spacer(modifier = Modifier.height(10.dp))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        OptionChip("Favorites", false, Icons.Default.Favorite) {
                            onSetOptions(SearchOptions(favoritesOnly = true))
                        }
                    }
                    item {
                        OptionChip("Under 1 min", false, Icons.Default.Timer) {
                            onSetOptions(SearchOptions(duration = DurationBucket.SHORT))
                        }
                    }
                    item {
                        OptionChip("1 – 10 min", false, Icons.Default.Timer) {
                            onSetOptions(SearchOptions(duration = DurationBucket.MEDIUM))
                        }
                    }
                    item {
                        OptionChip("10 min +", false, Icons.Default.Timer) {
                            onSetOptions(SearchOptions(duration = DurationBucket.LONG))
                        }
                    }
                    items(topFolders, key = { "folder_" + it.key }) { entry ->
                        OptionChip("${entry.key} · ${entry.value}", false, Icons.Default.Folder) {
                            onSetOptions(SearchOptions(folder = entry.key))
                        }
                    }
                }
            }
        }

        if (continueWatching.isNotEmpty()) {
            item(key = "continue") {
                PosterSection("Continue watching", continueWatching, onPlay)
            }
        }

        if (recentlyAdded.isNotEmpty()) {
            item(key = "added") {
                PosterSection("Recently added", recentlyAdded, onPlay)
            }
        }

        item(key = "footer") {
            Text(
                text = "${uiState.totalCount} videos on this phone · ${uiState.totalStorageFormatted}",
                color = Color(0xFF64748B),
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Brush.verticalGradient(listOf(Cyan, Violet)))
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun RecentChip(text: String, onClick: () -> Unit, onRemove: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .height(36.dp)
            .clip(shape)
            .background(Color(0x1AFFFFFF))
            .border(1.dp, GlassBorderSubtle, shape)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.History,
            contentDescription = null,
            tint = Muted,
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            color = Color(0xFFE2E8F0),
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = 2.dp)
        )
        IconButton(onClick = onRemove, modifier = Modifier.size(28.dp)) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove",
                tint = Muted,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
private fun PosterSection(title: String, videos: List<LocalVideo>, onPlay: (LocalVideo) -> Unit) {
    Column {
        SectionTitle(title, Modifier.padding(horizontal = 16.dp))
        Spacer(modifier = Modifier.height(10.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(videos, key = { it.id }) { video ->
                PosterCard(video = video, onClick = { onPlay(video) })
            }
        }
    }
}

@Composable
private fun PosterCard(video: LocalVideo, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .width(112.dp)
            .height(172.dp)
            .clip(shape)
            .background(Slate800)
            .border(1.dp, GlassBorderSubtle, shape)
            .clickable(onClick = onClick)
    ) {
        VideoThumbnail(video = video, modifier = Modifier.fillMaxSize())
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.55f to Color.Transparent,
                        1f to Color(0xDD000000)
                    )
                )
        )
        DurationBadge(
            text = video.formattedDuration,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
        )
        Text(
            text = video.displayName.substringBeforeLast('.'),
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(8.dp)
        )
    }
}

// ----------------------------------------------------------------------------------------------
// Results
// ----------------------------------------------------------------------------------------------

@Composable
private fun SearchResultRow(
    video: LocalVideo,
    tokens: List<String>,
    progress: Float,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Color(0x14FFFFFF))
            .border(1.dp, GlassBorderSubtle, shape)
            .clickable(onClick = onClick)
            .padding(8.dp)
            .testTag("search_result_${video.id}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(132.dp)
                .height(76.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Slate800)
        ) {
            VideoThumbnail(video = video, modifier = Modifier.fillMaxSize())
            DurationBadge(
                text = video.formattedDuration,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(5.dp)
            )
            if (progress > 0.02f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(progress)
                        .height(3.dp)
                        .background(Brush.horizontalGradient(listOf(Cyan, Violet)))
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = highlightedTitle(video.displayName.substringBeforeLast('.', video.displayName), tokens),
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = null,
                    tint = Muted,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = video.bucketDisplayName,
                    color = Muted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "${video.formattedSize} · ${video.formattedResolution} · ${video.formattedDateAdded}",
                color = Color(0xFF64748B),
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (video.isFavorite) {
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = "Favorite",
                tint = FrostedRose,
                modifier = Modifier
                    .padding(end = 6.dp)
                    .size(18.dp)
            )
        }
    }
}

@Composable
private fun VideoThumbnail(video: LocalVideo, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    if (video.isSample) {
        Box(
            modifier = modifier.background(
                Brush.linearGradient(listOf(Color(0xFF312E81), Color(0xFF4C1D95), Color(0xFF0F172A)))
            )
        )
    } else {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(video.uri)
                .videoFrameMillis((video.durationMs / 2).coerceIn(0L, 1_000L))
                .build(),
            contentDescription = video.displayName,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    }
}

@Composable
private fun DurationBadge(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = Color.White,
        fontSize = 10.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .background(Color(0xCC000000), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
private fun NoResults(onReset: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(Color(0x1AFFFFFF))
                .border(1.dp, GlassBorder, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.SearchOff,
                contentDescription = null,
                tint = FrostedVioletLight,
                modifier = Modifier.size(40.dp)
            )
        }
        Spacer(modifier = Modifier.height(18.dp))
        Text(
            text = "No matches",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Try fewer words, or loosen the filters above.",
            color = Muted,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(20.dp))
        OptionChip(label = "Clear search & filters", selected = true, icon = Icons.Default.Refresh, onClick = onReset)
    }
}

// ----------------------------------------------------------------------------------------------
// Helpers
// ----------------------------------------------------------------------------------------------

private fun searchTokens(query: String): List<String> =
    query.lowercase()
        .map { if (it.isLetterOrDigit()) it else ' ' }
        .joinToString("")
        .split(' ')
        .filter { it.isNotEmpty() }

/** Marks the parts of a file name that matched what the user typed. */
private fun highlightedTitle(name: String, tokens: List<String>): AnnotatedString {
    if (tokens.isEmpty()) return AnnotatedString(name)
    val lower = name.lowercase()
    if (lower.length != name.length) return AnnotatedString(name) // odd Unicode: skip highlighting
    val marked = BooleanArray(name.length)
    for (token in tokens) {
        var from = lower.indexOf(token)
        while (from >= 0) {
            for (k in from until from + token.length) marked[k] = true
            from = lower.indexOf(token, from + token.length)
        }
    }
    return buildAnnotatedString {
        var i = 0
        while (i < name.length) {
            val isMatch = marked[i]
            var j = i
            while (j < name.length && marked[j] == isMatch) j++
            val piece = name.substring(i, j)
            if (isMatch) {
                withStyle(
                    SpanStyle(
                        color = Cyan,
                        fontWeight = FontWeight.Bold,
                        background = Cyan.copy(alpha = 0.16f)
                    )
                ) { append(piece) }
            } else {
                append(piece)
            }
            i = j
        }
    }
}
