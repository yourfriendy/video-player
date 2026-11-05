package com.example.ui.components

import androidx.compose.ui.platform.testTag
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Switch
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Notifications
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.provider.Settings
import android.os.Build
import android.content.pm.PackageManager
import android.Manifest
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.download.PersonRow
import com.example.data.download.PostItem
import com.example.data.download.PostKind
import com.example.data.download.ProfileDetails
import com.example.data.download.SavedPerson
import com.example.ui.model.StoryItemDownload
import com.example.ui.theme.bouncyClickable
import com.example.ui.theme.staggeredEntrance
import com.example.ui.viewmodel.PeopleKind
import com.example.ui.viewmodel.ProfileController
import com.example.ui.viewmodel.ProfilePeopleState
import com.example.ui.viewmodel.ProfileTab
import com.example.ui.viewmodel.ProfileTabState
import com.example.ui.viewmodel.ProfileUiState
import com.example.ui.viewmodel.ProfileViewerState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Instagram's dark-mode palette
private val IgBlack = Color(0xFF000000)
private val IgText = Color(0xFFF5F5F5)
private val IgMuted = Color(0xFFA8A8A8)
private val IgButton = Color(0xFF262626)
private val IgDivider = Color(0xFF262626)
private val IgLink = Color(0xFFE0F1FF)
private val IgBlue = Color(0xFF0095F6)
private val IgVerified = Color(0xFF3797EF)
private val StoryRing = Brush.linearGradient(
    listOf(Color(0xFFFEDA75), Color(0xFFFA7E1E), Color(0xFFD62976), Color(0xFF962FBF), Color(0xFF4F5BD5))
)

/**
 * A full Instagram-style profile for any account: photo with story ring, counts, bio, links, highlights,
 * and the Posts / Reels / Reposts / Tagged grids, plus followers and following lists. It only appears
 * while [ProfileController] has a profile open, and sits above whichever screen opened it.
 */
@Composable
fun ProfileOverlay(
    controller: ProfileController,
    savedPeople: List<SavedPerson>,
    onSavePerson: (String) -> Unit,
    onRemovePerson: (String) -> Unit,
    onSetAlerts: (String, Boolean) -> Unit,
    onSignIn: () -> Unit
) {
    val state by controller.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(state.toast) {
        val message = state.toast
        if (message != null) {
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            controller.consumeToast()
        }
    }
    if (!state.visible) return

    val shownName = state.details?.username ?: state.username
    val saved = savedPeople.any { it.username.equals(shownName, ignoreCase = true) }
    val alertsOn = savedPeople.firstOrNull { it.username.equals(shownName, ignoreCase = true) }?.alerts == true

    Dialog(
        onDismissRequest = controller::back,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            dismissOnClickOutside = false
        )
    ) {
        ProfileContent(
            state = state,
            controller = controller,
            saved = saved,
            onToggleSave = { if (saved) onRemovePerson(shownName) else onSavePerson(shownName) },
            alertsOn = alertsOn,
            onSetAlerts = { on -> onSetAlerts(shownName, on) },
            onSignIn = onSignIn
        )
    }

    state.people?.let { people -> PeopleDialog(state, people, controller) }
    state.viewer?.let { viewer -> ProfileViewerDialog(viewer, state, controller) }
}

// ------------------------------------------------------------------------------------------------
// Page
// ------------------------------------------------------------------------------------------------

@Composable
private fun ProfileContent(
    state: ProfileUiState,
    controller: ProfileController,
    saved: Boolean,
    onToggleSave: () -> Unit,
    alertsOn: Boolean,
    onSetAlerts: (Boolean) -> Unit,
    onSignIn: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val name = state.details?.username ?: state.username
    var menuOpen by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(IgBlack)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(52.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = controller::back) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(
                text = name,
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (state.details?.isVerified == true) {
                Icon(
                    Icons.Default.Verified,
                    contentDescription = "Verified",
                    tint = IgVerified,
                    modifier = Modifier.padding(start = 5.dp).size(18.dp)
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Open in Instagram") },
                        onClick = {
                            menuOpen = false
                            openLink(context, "https://www.instagram.com/$name/")
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Copy profile link") },
                        onClick = {
                            menuOpen = false
                            clipboard.setText(AnnotatedString("https://www.instagram.com/$name/"))
                            Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Refresh") },
                        onClick = {
                            menuOpen = false
                            controller.retry()
                        }
                    )
                }
            }
        }

        when {
            state.needsLogin -> LoginPrompt(state.error, onSignIn)
            state.details == null && state.error != null -> MessageBlock(
                icon = Icons.Default.Person,
                title = "Couldn't load @$name",
                body = state.error,
                action = "Try again",
                onAction = controller::retry
            )
            state.details == null -> ProfileSkeleton()
            else -> ProfileGrid(state, controller, saved, onToggleSave, alertsOn, onSetAlerts)
        }
    }
}

@Composable
private fun ProfileGrid(
    state: ProfileUiState,
    controller: ProfileController,
    saved: Boolean,
    onToggleSave: () -> Unit,
    alertsOn: Boolean,
    onSetAlerts: (Boolean) -> Unit
) {
    val details = state.details ?: return
    val gridState = rememberLazyGridState()
    val tab = state.tab
    val tabState = when (tab) {
        ProfileTab.POSTS -> state.posts
        ProfileTab.REELS -> state.reels
        ProfileTab.REPOSTS -> state.reposts
        ProfileTab.TAGGED -> state.tagged
    }

    // Keep loading pages while the end of the grid is within a few rows
    val nearEnd by remember {
        derivedStateOf {
            val info = gridState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 7
        }
    }
    LaunchedEffect(nearEnd, tab, tabState.items.size, tabState.loaded) {
        if (nearEnd) controller.loadMore()
    }

    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        state = gridState,
        horizontalArrangement = Arrangement.spacedBy(1.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
        modifier = Modifier.fillMaxSize().navigationBarsPadding()
    ) {
        item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
            ProfileHeader(details, state, controller, saved, onToggleSave, alertsOn, onSetAlerts)
        }
        if (state.highlights.isNotEmpty()) {
            item(key = "highlights", span = { GridItemSpan(maxLineSpan) }) {
                HighlightsRow(state, controller)
            }
        }
        item(key = "tabs", span = { GridItemSpan(maxLineSpan) }) {
            ProfileTabs(selected = tab, onSelect = controller::selectTab)
        }
        if (tabState.items.isNotEmpty()) {
            itemsIndexed(tabState.items, key = { _, post -> "${tab.name}-${post.id}" }) { index, post ->
                PostTile(
                    post = post,
                    reelStyle = tab == ProfileTab.REELS,
                    modifier = Modifier.staggeredEntrance(index % 3, stepMs = 40, riseDp = 16f),
                    onClick = { controller.openPost(post) }
                )
            }
        }
        item(key = "footer-${tab.name}", span = { GridItemSpan(maxLineSpan) }) {
            GridFooter(details, tab, tabState, onRetry = controller::retryTab)
        }
    }
}

@Composable
private fun ProfileHeader(
    details: ProfileDetails,
    state: ProfileUiState,
    controller: ProfileController,
    saved: Boolean,
    onToggleSave: () -> Unit,
    alertsOn: Boolean,
    onSetAlerts: (Boolean) -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 6.dp)
            .staggeredEntrance(0)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AvatarWithRing(
                url = details.picUrl,
                hasStories = state.hasStories,
                loading = state.viewerLoading,
                onClick = controller::viewStories
            )
            Spacer(modifier = Modifier.width(22.dp))
            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatBlock(details.posts, "posts", null)
                StatBlock(details.followers, "followers") { controller.openPeople(PeopleKind.FOLLOWERS) }
                StatBlock(details.following, "following") { controller.openPeople(PeopleKind.FOLLOWING) }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        if (details.fullName.isNotBlank() || details.pronouns.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (details.fullName.isNotBlank()) {
                    Text(details.fullName, color = IgText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                if (details.pronouns.isNotEmpty()) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(details.pronouns.joinToString("/"), color = IgMuted, fontSize = 14.sp)
                }
            }
        }
        details.category?.let { Text(it, color = IgMuted, fontSize = 14.sp) }
        if (details.bio.isNotBlank()) {
            BioText(
                bio = details.bio,
                onMention = { controller.open(it) },
                onTag = { openLink(context, "https://www.instagram.com/explore/tags/$it/") },
                onUrl = { openLink(context, if (it.startsWith("http")) it else "https://$it") }
            )
        }
        details.bioLinks.take(3).forEach { link ->
            Row(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .clickable { openLink(context, link.url) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Link, contentDescription = null, tint = IgLink, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = link.title.ifBlank { prettyUrl(link.url) },
                    color = IgLink,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ProfileButton("Stories", Modifier.weight(1f), highlighted = state.hasStories, onClick = controller::viewStories)
            ProfileButton(if (saved) "Saved" else "Save person", Modifier.weight(1f), onClick = onToggleSave)
            ProfileButton("Instagram", Modifier.weight(1f)) {
                openLink(context, "https://www.instagram.com/${details.username}/")
            }
        }
        AlertsRow(username = details.username, on = alertsOn, onSet = onSetAlerts)
    }
}

/**
 * On/off switch for new-story and repost notifications about this person. Turning it on also saves them.
 * Handles the Android 13+ permission, tells you when the phone has notifications switched off, and shows what
 * the last background check did, so "why didn't I get an alert?" has a visible answer.
 */
@Composable
private fun AlertsRow(username: String, on: Boolean, onSet: (Boolean) -> Unit) {
    val context = LocalContext.current
    var waitingForPermission by remember { mutableStateOf(false) }

    fun openNotificationSettings() {
        try {
            context.startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            // No settings screen to open
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (waitingForPermission) {
            waitingForPermission = false
            onSet(true) // still switch it on: the alert is saved, and works as soon as notifications are allowed
        }
        if (!granted) {
            Toast.makeText(context, "Notifications are off for OffScroll, so alerts can't be shown. Opening settings…", Toast.LENGTH_LONG).show()
            openNotificationSettings()
        }
    }

    fun turnOn() {
        val needsAsk = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsAsk) {
            waitingForPermission = true
            permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onSet(true)
            if (com.example.data.download.StoryAlertWorker.notificationsBlocked(context)) {
                Toast.makeText(context, "Notifications are muted for OffScroll in phone settings. Opening settings…", Toast.LENGTH_LONG).show()
                openNotificationSettings()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(IgButton)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("profile_alerts_row")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (on) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                contentDescription = null,
                tint = if (on) IgBlue else IgMuted,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Story alerts", color = IgText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    text = if (on) "Notifies you about @$username's new stories and reposts, checked about every 5 minutes. Nobody is told you looked."
                    else "Get a notification when @$username posts a new story or reposts a reel.",
                    color = IgMuted, fontSize = 12.sp, lineHeight = 16.sp
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = on,
                onCheckedChange = { if (it) turnOn() else onSet(false) },
                colors = SwitchDefaults.colors(checkedTrackColor = IgBlue)
            )
        }
        if (on) {
            var status by remember { mutableStateOf(com.example.data.download.StoryAlertStatus.read(context)) }
            var instant by remember { mutableStateOf(com.example.data.download.InstagramNotificationListener.isEnabled(context)) }
            LaunchedEffect(Unit) {
                while (true) {
                    status = com.example.data.download.StoryAlertStatus.read(context)
                    instant = com.example.data.download.InstagramNotificationListener.isEnabled(context)
                    kotlinx.coroutines.delay(3_000L)
                }
            }
            Text(
                text = if (instant) {
                    "Instant alerts are on. Turn on the bell for @$username in the Instagram app (Stories, Reels) and OffScroll checks the moment Instagram's own notification arrives."
                } else {
                    "Want it instant instead of every 5 minutes? Let OffScroll see Instagram's notifications: turn on the bell for @$username in the Instagram app, and OffScroll checks the moment that notification arrives. Only Instagram's are read."
                },
                color = IgMuted, fontSize = 12.sp, lineHeight = 16.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
            if (!instant) {
                Text(
                    "Allow instant alerts", color = IgBlue, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .clickable {
                            try {
                                context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            } catch (e: Exception) {
                                Toast.makeText(context, "Open Settings > Notifications > Notification access and allow OffScroll", Toast.LENGTH_LONG).show()
                            }
                        }
                )
                Text(
                    "If Android says \"Restricted setting\": Settings > Apps > OffScroll > ⋮ > Allow restricted settings, then try again.",
                    color = IgMuted, fontSize = 11.sp, lineHeight = 15.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Text(
                text = status?.let { (at, line) ->
                    "Last check " + java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date(at)) + ": " + line
                } ?: "No check has run yet. The first one only notes what is already posted.",
                color = IgMuted, fontSize = 11.sp, lineHeight = 15.sp,
                modifier = Modifier.padding(top = 8.dp)
            )
            Row(modifier = Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Text(
                    "Send test", color = IgBlue, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable {
                        val ok = com.example.data.download.StoryAlertWorker.sendTest(context)
                        Toast.makeText(
                            context,
                            if (ok) "Test alert sent" else "Notifications are off for OffScroll in phone settings",
                            Toast.LENGTH_LONG
                        ).show()
                        if (!ok) openNotificationSettings()
                    }
                )
                Text(
                    "Check now", color = IgBlue, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable {
                        com.example.data.download.StoryAlertScheduler.checkNow(context)
                        Toast.makeText(context, "Checking now. The result shows here in a minute.", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }
}

@Composable
private fun AvatarWithRing(url: String?, hasStories: Boolean, loading: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(92.dp)
            .bouncyClickable(pressedScale = 0.95f, enabled = !loading, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .border(
                    width = if (hasStories) 3.dp else 1.dp,
                    brush = if (hasStories) StoryRing else SolidColor(Color(0xFF363636)),
                    shape = CircleShape
                )
        )
        ProfileAvatar(url, Modifier.size(80.dp))
        if (loading) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun ProfileAvatar(url: String?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.clip(CircleShape).background(IgButton),
        contentAlignment = Alignment.Center
    ) {
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(Icons.Default.Person, contentDescription = null, tint = IgMuted, modifier = Modifier.fillMaxSize(0.55f))
        }
    }
}

@Composable
private fun StatBlock(value: Long?, label: String, onClick: (() -> Unit)?) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        Text(compactCount(value), color = IgText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Text(label, color = IgText, fontSize = 13.sp)
    }
}

@Composable
private fun ProfileButton(
    text: String,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(34.dp)
            .bouncyClickable(pressedScale = 0.96f, onClick = onClick)
            .clip(RoundedCornerShape(8.dp))
            .background(if (highlighted) IgBlue else IgButton),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

@Composable
private fun BioText(
    bio: String,
    onMention: (String) -> Unit,
    onTag: (String) -> Unit,
    onUrl: (String) -> Unit
) {
    val text = remember(bio) { linkify(bio) }
    ClickableText(
        text = text,
        style = TextStyle(color = IgText, fontSize = 14.sp, lineHeight = 19.sp),
        onClick = { offset ->
            text.getStringAnnotations(start = offset, end = offset).firstOrNull()?.let { found ->
                when (found.tag) {
                    "user" -> onMention(found.item)
                    "tag" -> onTag(found.item)
                    "url" -> onUrl(found.item)
                }
            }
        }
    )
}

@Composable
private fun HighlightsRow(state: ProfileUiState, controller: ProfileController) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.padding(top = 16.dp)
    ) {
        itemsIndexed(state.highlights, key = { _, h -> h.id }) { index, highlight ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(70.dp)
                    .staggeredEntrance(index, stepMs = 40, riseDp = 12f)
                    .bouncyClickable(pressedScale = 0.94f) { controller.openHighlight(highlight) }
            ) {
                Box(
                    modifier = Modifier
                        .size(66.dp)
                        .border(1.dp, Color(0xFF363636), CircleShape)
                        .padding(3.dp)
                ) {
                    ProfileAvatar(highlight.coverUrl, Modifier.fillMaxSize())
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = highlight.title.ifBlank { "Highlight" },
                    color = IgText,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ProfileTabs(selected: ProfileTab, onSelect: (ProfileTab) -> Unit) {
    Column(modifier = Modifier.padding(top = 14.dp)) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(IgDivider))
        Row(modifier = Modifier.fillMaxWidth()) {
            ProfileTab.values().forEach { tab ->
                val icon: ImageVector = when (tab) {
                    ProfileTab.POSTS -> Icons.Default.GridOn
                    ProfileTab.REELS -> Icons.Default.Movie
                    ProfileTab.REPOSTS -> Icons.Default.Repeat
                    ProfileTab.TAGGED -> Icons.Default.AccountBox
                }
                val label = when (tab) {
                    ProfileTab.POSTS -> "Posts"
                    ProfileTab.REELS -> "Reels"
                    ProfileTab.REPOSTS -> "Reposts"
                    ProfileTab.TAGGED -> "Tagged"
                }
                val active = tab == selected
                val tint by animateColorAsState(
                    targetValue = if (active) Color.White else Color(0xFF737373),
                    animationSpec = tween(200),
                    label = "profile_tab_tint"
                )
                val line by animateColorAsState(
                    targetValue = if (active) Color.White else Color.Transparent,
                    animationSpec = tween(200),
                    label = "profile_tab_line"
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clickable { onSelect(tab) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(24.dp))
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(line)
                    )
                }
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(IgDivider))
    }
}

@Composable
private fun PostTile(post: PostItem, reelStyle: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .aspectRatio(0.75f)
            .background(Color(0xFF121212))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = post.thumbUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        val badge: ImageVector? = when {
            post.isPinned -> Icons.Default.PushPin
            post.kind == PostKind.CAROUSEL -> Icons.Default.Collections
            post.kind == PostKind.REEL || post.kind == PostKind.VIDEO -> Icons.Default.Movie
            else -> null
        }
        if (badge != null) {
            Icon(
                imageVector = badge,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .background(Color(0x40000000), CircleShape)
                    .padding(3.dp)
                    .size(15.dp)
            )
        }
        if ((reelStyle || post.kind == PostKind.REEL) && post.plays != null) {
            Row(
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                Text(
                    text = compactCount(post.plays),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    style = TextStyle(shadow = Shadow(color = Color.Black, blurRadius = 6f))
                )
            }
        }
    }
}

@Composable
private fun GridFooter(
    details: ProfileDetails,
    tab: ProfileTab,
    tabState: ProfileTabState,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        when {
            tabState.loading || !tabState.loaded && tabState.error == null -> {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(26.dp))
                if (tab == ProfileTab.REPOSTS) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Collecting reposts. This can take a minute.", color = IgMuted, fontSize = 12.sp)
                }
            }
            details.isPrivate && tabState.items.isEmpty() && (tabState.error != null || tabState.loaded) -> MessageBlock(
                icon = Icons.Default.Lock,
                title = "This account is private",
                body = "Follow this account on Instagram to see their photos and videos here.",
                action = null,
                onAction = {}
            )
            tabState.error != null -> MessageBlock(
                icon = Icons.Default.Person,
                title = "Couldn't load this",
                body = tabState.error,
                action = "Try again",
                onAction = onRetry
            )
            tabState.items.isEmpty() -> MessageBlock(
                icon = when (tab) {
                    ProfileTab.POSTS -> Icons.Default.GridOn
                    ProfileTab.REELS -> Icons.Default.Movie
                    ProfileTab.REPOSTS -> Icons.Default.Repeat
                    ProfileTab.TAGGED -> Icons.Default.AccountBox
                },
                title = when (tab) {
                    ProfileTab.POSTS -> "No posts yet"
                    ProfileTab.REELS -> "No reels yet"
                    ProfileTab.REPOSTS -> "No reposts found"
                    ProfileTab.TAGGED -> "No tagged posts"
                },
                body = null,
                action = null,
                onAction = {}
            )
            else -> Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun MessageBlock(
    icon: ImageVector,
    title: String,
    body: String?,
    action: String?,
    onAction: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier.size(64.dp).border(2.dp, Color(0xFF363636), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = IgText, modifier = Modifier.size(28.dp))
        }
        Text(title, color = IgText, fontSize = 17.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        if (body != null) {
            Text(body, color = IgMuted, fontSize = 14.sp, textAlign = TextAlign.Center, lineHeight = 19.sp)
        }
        if (action != null) {
            Text(
                text = action,
                color = IgBlue,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(10.dp)
            )
        }
    }
}

@Composable
private fun LoginPrompt(message: String?, onSignIn: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Lock, contentDescription = null, tint = IgText, modifier = Modifier.size(40.dp))
        Spacer(modifier = Modifier.height(14.dp))
        Text("Sign in to Instagram", color = IgText, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message ?: "Profiles show through your own Instagram sign-in. OffScroll never sees your password.",
            color = IgMuted,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            lineHeight = 19.sp
        )
        Spacer(modifier = Modifier.height(20.dp))
        ProfileButton("Sign in", Modifier.width(200.dp), highlighted = true, onClick = onSignIn)
    }
}

/** Grey placeholders in the shape of a profile, pulsing while the real one loads. */
@Composable
private fun ProfileSkeleton() {
    val transition = rememberInfiniteTransition(label = "profile_skeleton")
    val pulse by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse),
        label = "profile_skeleton_alpha"
    )
    val grey = Color(0xFF262626)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .graphicsLayer { alpha = pulse }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(86.dp).clip(CircleShape).background(grey))
            Spacer(modifier = Modifier.width(22.dp))
            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceEvenly) {
                repeat(3) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(modifier = Modifier.size(width = 36.dp, height = 16.dp).clip(RoundedCornerShape(6.dp)).background(grey))
                        Spacer(modifier = Modifier.height(6.dp))
                        Box(modifier = Modifier.size(width = 54.dp, height = 12.dp).clip(RoundedCornerShape(6.dp)).background(grey))
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(18.dp))
        Box(modifier = Modifier.size(width = 140.dp, height = 14.dp).clip(RoundedCornerShape(6.dp)).background(grey))
        Spacer(modifier = Modifier.height(10.dp))
        Box(modifier = Modifier.fillMaxWidth(0.8f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(grey))
        Spacer(modifier = Modifier.height(8.dp))
        Box(modifier = Modifier.fillMaxWidth(0.55f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(grey))
        Spacer(modifier = Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            repeat(3) {
                Box(modifier = Modifier.weight(1f).height(34.dp).clip(RoundedCornerShape(8.dp)).background(grey))
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
            repeat(3) {
                Box(modifier = Modifier.weight(1f).aspectRatio(0.75f).background(grey))
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// Followers and following
// ------------------------------------------------------------------------------------------------

@Composable
private fun PeopleDialog(state: ProfileUiState, people: ProfilePeopleState, controller: ProfileController) {
    val details = state.details
    val query = people.query.trim()
    val shown = remember(people.people, query) {
        if (query.isEmpty()) people.people
        else people.people.filter {
            it.username.contains(query, ignoreCase = true) || it.fullName.contains(query, ignoreCase = true)
        }
    }
    val total = if (people.kind == PeopleKind.FOLLOWERS) details?.followers else details?.following
    val listState = rememberLazyListState()
    val nearEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - 8
        }
    }
    LaunchedEffect(nearEnd, people.people.size, query) {
        if (nearEnd && query.isEmpty()) controller.loadMorePeople()
    }

    Dialog(
        onDismissRequest = controller::closePeople,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(IgBlack)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = controller::closePeople) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Text(
                    text = details?.username.orEmpty(),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                PeopleTab(
                    text = "${compactCount(details?.followers)} followers",
                    active = people.kind == PeopleKind.FOLLOWERS,
                    modifier = Modifier.weight(1f)
                ) { controller.switchPeople(PeopleKind.FOLLOWERS) }
                PeopleTab(
                    text = "${compactCount(details?.following)} following",
                    active = people.kind == PeopleKind.FOLLOWING,
                    modifier = Modifier.weight(1f)
                ) { controller.switchPeople(PeopleKind.FOLLOWING) }
            }
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(IgDivider))

            BasicTextField(
                value = people.query,
                onValueChange = controller::setPeopleQuery,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                cursorBrush = SolidColor(Color.White),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(IgButton)
                    .padding(horizontal = 12.dp),
                decorationBox = { inner ->
                    Row(modifier = Modifier.fillMaxHeight(), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Search, contentDescription = null, tint = IgMuted, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (people.query.isEmpty()) Text("Search", color = IgMuted, fontSize = 15.sp)
                            inner()
                        }
                    }
                }
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val status = when {
                    people.done -> "All ${people.people.size} loaded"
                    total != null -> "Loaded ${people.people.size} of ${compactCount(total)}"
                    else -> "Loaded ${people.people.size}"
                }
                Text(status, color = IgMuted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                if (!people.done) {
                    Text(
                        text = if (people.loadingAll) "Stop" else "Load all",
                        color = IgBlue,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { controller.toggleLoadAllPeople() }
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }
            if (query.isNotEmpty() && !people.done) {
                Text(
                    text = "Searching the people loaded so far. Tap Load all to search everyone.",
                    color = IgMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }

            LazyColumn(state = listState, modifier = Modifier.weight(1f)) {
                itemsIndexed(shown, key = { _, p -> p.id }) { _, person ->
                    PersonItem(person) { controller.open(person.username) }
                }
                item(key = "people-footer") {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        when {
                            people.loading -> CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(24.dp)
                            )
                            people.error != null -> MessageBlock(
                                icon = Icons.Default.Lock,
                                title = "Couldn't load this list",
                                body = people.error,
                                action = "Try again",
                                onAction = controller::retryPeople
                            )
                            people.done && people.people.isEmpty() -> Text(
                                text = "Nobody to show here.",
                                color = IgMuted,
                                fontSize = 14.sp
                            )
                            query.isNotEmpty() && shown.isEmpty() -> Text(
                                text = "No match in the people loaded so far.",
                                color = IgMuted,
                                fontSize = 14.sp
                            )
                            else -> Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PeopleTab(text: String, active: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val line by animateColorAsState(
        targetValue = if (active) Color.White else Color.Transparent,
        animationSpec = tween(200),
        label = "people_tab_line"
    )
    Box(
        modifier = modifier.height(44.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (active) Color.White else IgMuted,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
        Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(1.dp).background(line))
    }
}

@Composable
private fun PersonItem(person: PersonRow, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ProfileAvatar(person.picUrl, Modifier.size(48.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = person.username,
                    color = IgText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (person.isVerified) {
                    Icon(
                        Icons.Default.Verified,
                        contentDescription = "Verified",
                        tint = IgVerified,
                        modifier = Modifier.padding(start = 4.dp).size(14.dp)
                    )
                }
            }
            if (person.fullName.isNotBlank()) {
                Text(person.fullName, color = IgMuted, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (person.isPrivate) {
            Icon(Icons.Default.Lock, contentDescription = "Private", tint = IgMuted, modifier = Modifier.size(16.dp))
        }
    }
}

// ------------------------------------------------------------------------------------------------
// Viewer: a post's slides, stories, highlights
// ------------------------------------------------------------------------------------------------

@Composable
private fun ProfileViewerDialog(viewer: ProfileViewerState, state: ProfileUiState, controller: ProfileController) {
    val items = viewer.items
    if (items.isEmpty()) {
        LaunchedEffect(Unit) { controller.closeViewer() }
        return
    }
    val context = LocalContext.current

    Dialog(
        onDismissRequest = controller::closeViewer,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)
    ) {
        val pager = rememberPagerState(
            initialPage = viewer.startIndex.coerceIn(0, items.lastIndex),
            pageCount = { items.size }
        )
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
                val item = items[page]
                val isCurrent = pager.currentPage == page
                when {
                    item.needsResolve -> {
                        // A reel that only came as a picture: look the real video up, then it plays
                        if (isCurrent) LaunchedEffect(item.id) { controller.resolveNow(item) }
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            AsyncImage(
                                model = item.thumbnailUrl ?: item.mediaUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                            CircularProgressIndicator(color = Color.White)
                        }
                    }
                    item.isVideo && isCurrent -> ViewerVideo(item.mediaUrl)
                    else -> AsyncImage(
                        model = if (item.isVideo) (item.thumbnailUrl ?: item.mediaUrl) else item.mediaUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }

            val current = items[pager.currentPage.coerceIn(0, items.lastIndex)]
            val status = state.saves[current.id]

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            ) {
                if (items.size > 1) {
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
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                ) {
                    Text(
                        text = if (items.size > 1) "${viewer.title}  ${pager.currentPage + 1}/${items.size}" else viewer.title,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (items.size > 1) {
                        Text(
                            text = "Save all",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0x44FFFFFF))
                                .clickable { controller.saveAll(items) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                    IconButton(onClick = { if (status !is StoryItemDownload.Done) controller.save(current) }) {
                        when (status) {
                            is StoryItemDownload.Downloading -> CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp)
                            )
                            is StoryItemDownload.Done -> Icon(Icons.Default.CheckCircle, contentDescription = "Saved", tint = Color(0xFF22D3EE))
                            else -> Icon(Icons.Default.Download, contentDescription = "Save", tint = Color.White)
                        }
                    }
                    IconButton(onClick = controller::closeViewer) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xDD000000))))
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                current.caption?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = Color.White, fontSize = 14.sp, maxLines = 4, overflow = TextOverflow.Ellipsis, lineHeight = 19.sp)
                }
                val counts = listOfNotNull(
                    current.likes?.let { "${compactCount(it)} likes" },
                    current.comments?.let { "${compactCount(it)} comments" },
                    current.plays?.let { "${compactCount(it)} plays" }
                ).joinToString("  ·  ")
                if (counts.isNotBlank()) Text(counts, color = IgMuted, fontSize = 12.sp)
                if (current.takenAtSec > 0) {
                    Text(
                        text = SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(current.takenAtSec * 1000)),
                        color = IgMuted,
                        fontSize = 12.sp
                    )
                }
                if (current.mentions.isNotEmpty()) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                        itemsIndexed(current.mentions, key = { _, who -> who.username }) { _, who ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(Color(0x33FFFFFF))
                                    .clickable { controller.open(who.username) }
                                    .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp)
                            ) {
                                ProfileAvatar(who.picUrl, Modifier.size(24.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("@${who.username}", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                current.code?.let { code ->
                    Text(
                        text = "Open on Instagram",
                        color = IgLink,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .clickable { openLink(context, "https://www.instagram.com/p/$code/") }
                    )
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// Helpers
// ------------------------------------------------------------------------------------------------

private val BIO_TOKEN = Regex("""(@[A-Za-z0-9._]+)|(#[\p{L}\p{N}_]+)|((?:https?://|www\.)\S+)""")

/** Turns @mentions, #hashtags and links in a bio into tappable, link-coloured text. */
private fun linkify(bio: String): AnnotatedString = buildAnnotatedString {
    var last = 0
    for (match in BIO_TOKEN.findAll(bio)) {
        append(bio.substring(last, match.range.first))
        val raw = match.value
        val clean = if (raw.startsWith("@") || raw.startsWith("#")) raw.trimEnd('.') else raw.trimEnd('.', ',', ')', '!', '?')
        val tag = when {
            clean.startsWith("@") -> "user"
            clean.startsWith("#") -> "tag"
            else -> "url"
        }
        val start = length
        withStyle(SpanStyle(color = IgLink, fontWeight = FontWeight.SemiBold)) { append(clean) }
        addStringAnnotation(tag, if (tag == "url") clean else clean.substring(1), start, length)
        append(raw.substring(clean.length))
        last = match.range.last + 1
    }
    append(bio.substring(last))
}

/** 1,234 → "1,234", 12,345 → "12.3K", 1,200,000 → "1.2M" (the way Instagram abbreviates counts). */
private fun compactCount(n: Long?): String {
    if (n == null) return "–"
    fun short(value: Double, suffix: String): String {
        val text = String.format(Locale.US, "%.1f", value)
        return (if (text.endsWith(".0")) text.dropLast(2) else text) + suffix
    }
    return when {
        n >= 1_000_000L -> short(n / 1_000_000.0, "M")
        n >= 10_000L -> short(n / 1_000.0, "K")
        else -> String.format(Locale.US, "%,d", n)
    }
}

private fun prettyUrl(url: String): String =
    url.removePrefix("https://").removePrefix("http://").removePrefix("www.").trimEnd('/')

private fun openLink(context: android.content.Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(context, "Couldn't open that link", Toast.LENGTH_SHORT).show()
    }
}
