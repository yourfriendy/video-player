package com.example.ui.components

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.ui.theme.Ds

/** The five gestures the player understands, shown once on first use and again from the three-dots hub. */
@Composable
fun GestureTourDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        GlassCard(modifier = Modifier.heightIn(max = 640.dp)) {
            Text("Quick tour", color = Color.White, fontSize = Ds.Type.TitleLarge, fontWeight = FontWeight.ExtraBold)
            Text(
                "Everything works without buttons. You can read this again any time from the three dots.",
                color = Ds.Palette.Muted, fontSize = Ds.Type.Small, modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
            )
            Column(
                modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GestureHelpCard(Icons.Default.Swipe, "Swipe up or down", "Go to the next or previous video")
                GestureHelpCard(Icons.Default.TouchApp, "Tap, and double tap", "Tap to show the controls, tap again for full screen; double tap anywhere to pause or play")
                GestureHelpCard(Icons.Default.Gesture, "Drag left or right", "Seek forward or back, with a live time readout")
                GestureHelpCard(Icons.Default.Swipe, "Left edge", "Slide up or down along the left edge to change brightness")
                GestureHelpCard(Icons.Default.Swipe, "Right edge", "Slide up or down along the right edge to change volume")
            }
            Spacer(modifier = Modifier.height(14.dp))
            DsButton(text = "Got it", onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}

/** Remembers that the tour was shown, so it appears once per install. */
object GestureTourState {
    private const val PREFS = "offscroll_prefs"
    private const val KEY = "gesture_tour_seen"

    fun shouldShow(context: Context): Boolean = !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun markSeen(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, true).apply()
    }
}
