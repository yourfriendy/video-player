package com.example

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView

/**
 * What Android opens when another app says "play this video with..." (Files, Gallery, WhatsApp, a browser...).
 * It is declared for ACTION_VIEW + video/* in the manifest, which is what makes OffScroll show up in those lists.
 *
 * It is deliberately separate from [MainActivity]: the video you were handed is played as-is, straight away, even if
 * it is not in your library or the app has no permission to read your videos (the sender grants access to that one
 * file). Your feed, filters and playback position are left alone.
 */
@OptIn(UnstableApi::class)
class ExternalVideoActivity : ComponentActivity() {

    private lateinit var playerView: PlayerView
    private lateinit var titleView: TextView
    private lateinit var messageView: TextView

    private var player: ExoPlayer? = null
    private var videoUri: Uri? = null

    // Kept while the player is released (screen off, another app on top) so playback continues where it was
    private var resumeMs = 0L
    private var playWhenReady = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(buildViews())

        videoUri = uriFrom(intent)
        savedInstanceState?.let {
            resumeMs = it.getLong(KEY_POSITION, 0L)
            playWhenReady = it.getBoolean(KEY_PLAYING, true)
        }
        showTitle()
    }

    /** Another video was opened while this screen is already up: switch to it. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        releasePlayer()
        videoUri = uriFrom(intent)
        resumeMs = 0L
        playWhenReady = true
        showTitle()
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) startPlayback()
    }

    override fun onStart() {
        super.onStart()
        startPlayback()
    }

    override fun onStop() {
        super.onStop()
        releasePlayer()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(KEY_POSITION, player?.currentPosition ?: resumeMs)
        outState.putBoolean(KEY_PLAYING, player?.playWhenReady ?: playWhenReady)
    }

    private fun buildViews(): View {
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        playerView = PlayerView(this).apply {
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            // The file name follows the controls: shown when they are, hidden when they fade out
            setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { visibility ->
                titleView.visibility = visibility
            })
        }
        titleView = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 16f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            setBackgroundColor(Color.argb(120, 0, 0, 0))
            setPadding(dp(16), dp(10), dp(16), dp(10))
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP)
        }
        messageView = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            visibility = View.GONE
            setPadding(dp(32), dp(32), dp(32), dp(32))
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER)
        }
        root.addView(playerView)
        root.addView(titleView)
        root.addView(messageView)

        // Edge-to-edge: keep the controls clear of the status bar, navigation bar and camera cut-out
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        return root
    }

    private fun startPlayback() {
        if (player != null) return
        val uri = videoUri
        if (uri == null) {
            showMessage("There is no video to play.")
            return
        }
        messageView.visibility = View.GONE
        val exo = ExoPlayer.Builder(this).build()
        exo.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                showMessage(friendlyError(error))
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) messageView.visibility = View.GONE
            }
        })
        exo.setMediaItem(MediaItem.fromUri(uri))
        if (resumeMs > 0L) exo.seekTo(resumeMs)
        exo.playWhenReady = playWhenReady
        exo.prepare()
        playerView.player = exo
        player = exo
    }

    private fun releasePlayer() {
        player?.let {
            resumeMs = it.currentPosition
            playWhenReady = it.playWhenReady
            it.release()
        }
        playerView.player = null
        player = null
    }

    private fun showMessage(text: String) {
        messageView.text = text
        messageView.visibility = View.VISIBLE
    }

    private fun friendlyError(error: PlaybackException): String = when (error.errorCode) {
        PlaybackException.ERROR_CODE_IO_NO_PERMISSION ->
            "OffScroll isn't allowed to read this file. Allow video access in the app's settings, or open the video from your Gallery or Files."
        PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND ->
            "This video couldn't be found. It may have been moved or deleted."
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
        PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
            "This video couldn't be loaded. Check your internet connection and try again."
        PlaybackException.ERROR_CODE_DECODING_FAILED,
        PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
        PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
        PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ->
            "This phone can't play this kind of video."
        else -> "This video can't be played."
    }

    private fun showTitle() {
        titleView.text = videoUri?.let { displayName(it) }.orEmpty()
    }

    /** A readable file name: the sender's own display name for content links, otherwise the last part of the path. */
    private fun displayName(uri: Uri): String {
        if (uri.scheme == "content") {
            try {
                contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0)?.takeIf { it.isNotBlank() }?.let { return it }
                }
            } catch (e: Exception) {
                com.example.data.Diagnostics.log("ignored", "ExternalVideoActivity: no display name for the video")
            }
        }
        return uri.lastPathSegment?.substringAfterLast('/').orEmpty()
    }

    private fun uriFrom(intent: Intent?): Uri? =
        intent?.data ?: intent?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private companion object {
        const val KEY_POSITION = "position_ms"
        const val KEY_PLAYING = "play_when_ready"
    }
}
