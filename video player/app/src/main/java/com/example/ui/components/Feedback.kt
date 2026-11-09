package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.Ds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The one way the app tells you something passing ("Link copied", "Saved", "Couldn't open that link"). Replaces the
 * mix of Toasts and Snackbars. A message can carry one action ("View", "Undo", "Open settings"), is announced to
 * screen readers, and stays up long enough to read: about 2 s plus 55 ms per character, between 3 and 7 s.
 *
 * Several windows can be on screen at once (the profile and story viewers are their own windows), so each of those
 * windows hosts its own [FeedbackLayer]; they all show the same message and the top one is the one you see.
 */
object Feedback {
    data class Message(val id: Long, val text: String, val actionLabel: String?, val onAction: (() -> Unit)?)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _current = MutableStateFlow<Message?>(null)
    val current: StateFlow<Message?> = _current.asStateFlow()
    private var counter = 0L
    private var timer: Job? = null

    fun show(text: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
        val message = Message(++counter, text, actionLabel, onAction)
        _current.value = message
        timer?.cancel()
        val hold = (2_200L + text.length * 55L + if (actionLabel != null) 1_500L else 0L).coerceIn(3_000L, 7_000L)
        timer = scope.launch {
            delay(hold)
            if (_current.value?.id == message.id) _current.value = null
        }
    }

    fun dismiss() {
        timer?.cancel()
        _current.value = null
    }
}

/** Just the pill (wraps its content), for places that lay it out themselves such as a bottom sheet. */
@Composable
fun FeedbackPill(modifier: Modifier = Modifier) {
    val message by Feedback.current.collectAsState()
    var last by remember { mutableStateOf<Feedback.Message?>(null) }
    if (message != null) last = message
    AnimatedVisibility(
        visible = message != null,
        modifier = modifier,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 }
    ) {
        val shown = message ?: last
        if (shown != null) {
            val shape = RoundedCornerShape(Ds.Radius.Lg)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .widthIn(max = 420.dp)
                    .clip(shape)
                    .background(Color(0xF21E293B))
                    .border(1.dp, Color(0x33FFFFFF), shape)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
                    .semantics { liveRegion = LiveRegionMode.Polite }
            ) {
                Text(
                    text = shown.text, color = Color.White, fontSize = Ds.Type.Body,
                    maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
                )
                if (shown.actionLabel != null && shown.onAction != null) {
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = shown.actionLabel, color = Ds.Palette.Cyan, fontSize = Ds.Type.Body, fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(Ds.Radius.Sm))
                            .clickable { shown.onAction.invoke(); Feedback.dismiss() }
                            .padding(horizontal = 8.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

/** The pill pinned near the bottom of a window. Drop one inside the root Box of every window that can show messages. */
@Composable
fun FeedbackLayer(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 84.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        FeedbackPill()
    }
}
