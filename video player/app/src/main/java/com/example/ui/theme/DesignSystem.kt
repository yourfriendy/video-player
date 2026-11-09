package com.example.ui.theme

import android.app.ActivityManager
import android.content.Context
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The one place that defines the app's look. Screens take colours, text sizes and corner radii from here, so a
 * change here changes the whole app and nothing drifts. [DesignTokensLintTest] fails the build if a screen uses a
 * text size or corner radius that is not on these scales.
 *
 * The Instagram-style profile viewer keeps its own near-black `Ig*` colours on purpose: people expect it to look
 * like Instagram. Everything else uses [Palette].
 */
object Ds {

    object Palette {
        // The signature sunset gradient
        val Orange = Color(0xFFF59E0B)
        val Pink = Color(0xFFEC4899)
        val Violet = Color(0xFF8B5CF6)

        // Accents and text on dark surfaces
        val Cyan = Color(0xFF5EEAD4)
        val VioletSoft = Color(0xFFA78BFA)
        val PinkSoft = Color(0xFFF472B6)
        val Muted = Color(0xFF94A3B8)
        val SoftText = Color(0xFFE2E8F0)

        // Meaning
        val Good = Color(0xFF34D399)
        val Warn = Color(0xFFFBBF24)
        val Bad = Color(0xFFFB7185)
    }

    /** Text sizes. Nothing smaller than 11 sp. */
    object Type {
        val Caption = 11.sp
        val Small = 12.sp
        val Label = 13.sp
        val Body = 14.sp
        val BodyLarge = 16.sp
        val Title = 18.sp
        val TitleLarge = 20.sp
        val Headline = 24.sp
        val Display = 28.sp
        val Hero = 34.sp
    }

    /** Corner radii (a circle or full pill is always allowed). */
    object Radius {
        val Xs = 4.dp
        val Sm = 8.dp
        val Md = 12.dp
        val Lg = 16.dp
        val Xl = 20.dp
        val Xxl = 24.dp
        val Sheet = 28.dp
    }

    /** The same scales as plain numbers, used by the lint test. */
    val FontScaleSp = listOf(11f, 12f, 13f, 14f, 16f, 18f, 20f, 24f, 28f, 34f)
    val RadiusScaleDp = listOf(4, 8, 12, 16, 20, 24, 28)

    /** Smallest comfortable tap target. */
    val MinTouch = 44.dp
}

/**
 * False when the person has switched animations off (system "Remove animations" / animation scale 0) or the phone
 * is a low-memory model. Decorative motion (aurora background, shimmer, spinning rings, bouncing dots) should draw
 * a still version instead.
 */
fun motionEnabled(context: Context): Boolean {
    val scale = try {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    } catch (e: Exception) {
        1f
    }
    val lowRam = (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)?.isLowRamDevice == true
    return scale > 0f && !lowRam
}

@Composable
fun rememberMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember { motionEnabled(context) }
}
