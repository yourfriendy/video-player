package com.example.data.music

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.ui.model.RepeatKind
import com.example.ui.model.Song
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** One song in the play queue, as the music screens need it. */
data class QueueEntry(val songId: Long, val title: String, val artist: String, val albumId: Long)

data class MusicPlayerState(
    val queue: List<QueueEntry> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val shuffle: Boolean = false,
    val repeat: RepeatKind = RepeatKind.OFF,
    val durationMs: Long = 0L,
    /** Wall-clock time (ms) when the sleep timer will pause the music, or 0 when it is off. */
    val sleepEndsAtMs: Long = 0L
) {
    val current: QueueEntry? get() = queue.getOrNull(currentIndex)
    val hasQueue: Boolean get() = queue.isNotEmpty() && currentIndex >= 0
}

/**
 * The app's one connection to the music player service. The screens only talk to this: play a list, pause, skip,
 * seek, shuffle, repeat, sleep timer. The service does the real playing, so it carries on in the background.
 */
object MusicPlayerManager {
    private var appContext: Context? = null
    private var controller: MediaController? = null
    private var pending: ListenableFuture<MediaController>? = null
    private var cachedQueue: List<QueueEntry> = emptyList()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var sleepJob: Job? = null

    private val _state = MutableStateFlow(MusicPlayerState())
    val state: StateFlow<MusicPlayerState> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (events.contains(Player.EVENT_TIMELINE_CHANGED)) cachedQueue = readQueue(player)
            publish()
        }
    }

    fun init(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    /** Connects to the service (starting it if needed) so the state reflects what is already playing. */
    fun connect() = withController { }

    private fun withController(action: (MediaController) -> Unit) {
        controller?.let {
            action(it)
            return
        }
        val ctx = appContext ?: return
        val future = pending ?: MediaController.Builder(
            ctx, SessionToken(ctx, ComponentName(ctx, com.example.service.MusicPlaybackService::class.java))
        ).buildAsync().also { pending = it }
        future.addListener({
            val connected = runCatching { future.get() }.getOrNull() ?: return@addListener
            if (controller == null) {
                controller = connected
                connected.addListener(listener)
                cachedQueue = readQueue(connected)
                publish()
            }
            controller?.let(action)
        }, ContextCompat.getMainExecutor(ctx))
    }

    private fun readQueue(player: Player): List<QueueEntry> =
        (0 until player.mediaItemCount).map { i ->
            val item = player.getMediaItemAt(i)
            QueueEntry(
                songId = item.mediaId.toLongOrNull() ?: -1L,
                title = item.mediaMetadata.title?.toString() ?: "",
                artist = item.mediaMetadata.artist?.toString() ?: "",
                albumId = item.mediaMetadata.artworkUri?.lastPathSegment?.toLongOrNull() ?: -1L
            )
        }

    private fun publish() {
        val c = controller ?: return
        _state.value = _state.value.copy(
            queue = cachedQueue,
            currentIndex = if (c.mediaItemCount == 0) -1 else c.currentMediaItemIndex,
            isPlaying = c.isPlaying,
            shuffle = c.shuffleModeEnabled,
            repeat = when (c.repeatMode) {
                Player.REPEAT_MODE_ALL -> RepeatKind.ALL
                Player.REPEAT_MODE_ONE -> RepeatKind.ONE
                else -> RepeatKind.OFF
            },
            durationMs = c.duration.coerceAtLeast(0L)
        )
    }

    /** Plays [songs] from [startIndex]. With [shuffle] the rest comes in random order. */
    fun playSongs(songs: List<Song>, startIndex: Int, shuffle: Boolean = false) {
        if (songs.isEmpty()) return
        withController { c ->
            c.shuffleModeEnabled = shuffle
            c.setMediaItems(songs.map { MusicRepository.mediaItem(it) }, startIndex.coerceIn(0, songs.lastIndex), 0L)
            c.prepare()
            c.play()
        }
    }

    fun togglePlayPause() = withController { c ->
        if (c.isPlaying) {
            c.pause()
        } else {
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition()
            c.play()
        }
    }

    fun next() = withController { it.seekToNextMediaItem() }

    /** Restarts the song if it has played for a few seconds; otherwise goes to the one before. */
    fun previous() = withController { c ->
        if (c.currentPosition > 3_000L) c.seekTo(0L) else c.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) = withController { it.seekTo(positionMs.coerceAtLeast(0L)) }

    fun toggleShuffle() = withController { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    fun cycleRepeat() = withController { c ->
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun jumpTo(index: Int) = withController { c ->
        if (index in 0 until c.mediaItemCount) {
            c.seekTo(index, 0L)
            c.play()
        }
    }

    fun removeAt(index: Int) = withController { c ->
        if (index in 0 until c.mediaItemCount) c.removeMediaItem(index)
    }

    /** Pauses without connecting (nothing to pause if we never connected). */
    fun pauseNow() {
        controller?.pause()
    }

    fun positionMs(): Long = controller?.currentPosition ?: 0L

    /** Pauses the music after [minutes] minutes. Pass null to switch the timer off. */
    fun setSleepTimer(minutes: Int?) {
        sleepJob?.cancel()
        if (minutes == null) {
            _state.value = _state.value.copy(sleepEndsAtMs = 0L)
            return
        }
        val endsAt = System.currentTimeMillis() + minutes * 60_000L
        _state.value = _state.value.copy(sleepEndsAtMs = endsAt)
        sleepJob = scope.launch {
            delay(minutes * 60_000L)
            controller?.pause()
            _state.value = _state.value.copy(sleepEndsAtMs = 0L)
        }
    }
}
