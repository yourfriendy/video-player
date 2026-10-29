package com.example.ui.components

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer

/**
 * A fixed set of long-lived players shared by the feed pages.
 *
 * Building an ExoPlayer is expensive (codec and renderer setup), so creating one per page made
 * every swipe hitch. Pages now borrow a player by index instead: four consecutive pages never
 * share one, so the current page and both neighbours always have their own.
 */
@OptIn(UnstableApi::class)
class PlayerPool(context: Context, private val size: Int = 4) {
    private val players: List<ExoPlayer> = List(size) { build(context.applicationContext) }

    fun playerFor(page: Int): ExoPlayer = players[Math.floorMod(page, size)]

    fun release() {
        players.forEach { it.release() }
    }

    private fun build(context: Context): ExoPlayer {
        // Local files need very little buffering: small buffers = faster start and less memory
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(1_500, 8_000, 250, 500)
            .build()
        return ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setHandleAudioBecomingNoisy(true)
            .build()
            .apply {
                videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
            }
    }
}
