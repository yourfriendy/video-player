package com.example.data.universal.engine

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import com.example.data.universal.ErrorMapper
import com.example.data.universal.UniversalException
import com.example.data.universal.engine.EngineProtocol as P
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/** The engine app isn't installed (or can't be reached), so the "Any link" downloader can't run. */
open class EngineMissingException(message: String) : UniversalException(message, false)

/** The installed engine speaks an older (or newer) protocol than this OffScroll: it needs updating. */
class EngineOutdatedException(message: String) : EngineMissingException(message)

/**
 * Talks to the separate OffScroll Engine app over a bound Messenger. Requests are matched to answers by id,
 * so several can be in flight. The connection is opened on first use and dropped after a minute of quiet,
 * so the engine's process (Python, ffmpeg) isn't kept alive for nothing.
 */
class EngineClient(context: Context) {

    private class Pending(val result: CompletableDeferred<Bundle>, val onProgress: ((Bundle) -> Unit)?)

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val pending = ConcurrentHashMap<Int, Pending>()
    private val nextId = AtomicInteger(1)
    private val connectLock = Mutex()

    @Volatile
    private var service: Messenger? = null
    private var connection: ServiceConnection? = null

    private val replies = Messenger(Handler(Looper.getMainLooper()) { message ->
        onReply(message)
        true
    })

    private val dropIdleConnection = Runnable {
        if (pending.isEmpty()) disconnect()
    }

    fun isInstalled(): Boolean = try {
        appContext.packageManager.getPackageInfo(P.ENGINE_PACKAGE, 0)
        true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    /** Sends [what] and waits for the engine's answer. [onProgress] is called (on the main thread) for progress messages. */
    suspend fun request(what: Int, data: Bundle = Bundle(), onProgress: ((Bundle) -> Unit)? = null): Bundle {
        val target = connect()
        main.removeCallbacks(dropIdleConnection)
        val id = nextId.getAndIncrement()
        val entry = Pending(CompletableDeferred(), onProgress)
        pending[id] = entry
        try {
            target.send(Message.obtain(null, what, id, 0).apply {
                this.data = data
                replyTo = replies
            })
        } catch (e: RemoteException) {
            pending.remove(id)
            service = null
            throw UniversalException("The download engine stopped. Try again.")
        }
        try {
            return entry.result.await()
        } catch (e: CancellationException) {
            pending.remove(id)
            throw e
        } finally {
            if (pending.isEmpty()) main.postDelayed(dropIdleConnection, IDLE_MS)
        }
    }

    /** A message that needs no answer (cancel, cleanup). */
    suspend fun tell(what: Int, data: Bundle) {
        val target = try {
            connect()
        } catch (e: UniversalException) {
            return
        }
        try {
            target.send(Message.obtain(null, what, 0, 0).apply { this.data = data; replyTo = replies })
        } catch (e: RemoteException) {
            service = null
        }
    }

    /** Fire-and-forget for callers that aren't coroutines (cancel button). Only if already connected. */
    fun tellNow(what: Int, data: Bundle) {
        val target = service ?: return
        try {
            target.send(Message.obtain(null, what, 0, 0).apply { this.data = data; replyTo = replies })
        } catch (e: RemoteException) {
            service = null
        }
    }

    private suspend fun connect(): Messenger = connectLock.withLock {
        service?.let { return@withLock it }
        if (!isInstalled()) {
            throw EngineMissingException("The download engine isn't installed. Install OffScroll Engine to save from YouTube, TikTok, Facebook and other sites.")
        }
        val ready = CompletableDeferred<Messenger>()
        val conn = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                val messenger = Messenger(binder)
                service = messenger
                ready.complete(messenger)
            }

            override fun onServiceDisconnected(name: ComponentName) {
                service = null
                failAll("The download engine stopped. Try again.")
            }

            override fun onBindingDied(name: ComponentName) {
                service = null
                failAll("The download engine stopped. Try again.")
            }

            override fun onNullBinding(name: ComponentName) {
                ready.completeExceptionally(UniversalException("The download engine didn't answer."))
            }
        }
        val intent = Intent().setComponent(ComponentName(P.ENGINE_PACKAGE, P.SERVICE_CLASS))
        val bound = try {
            appContext.bindService(intent, conn, Context.BIND_AUTO_CREATE)
        } catch (e: SecurityException) {
            false
        }
        if (!bound) {
            runCatching { appContext.unbindService(conn) }
            throw EngineMissingException(
                "OffScroll can't use the installed engine. Both apps must come from the same release " +
                    "(same signing key). Reinstall OffScroll Engine from the latest release."
            )
        }
        connection = conn
        try {
            withTimeout(CONNECT_TIMEOUT_MS) { ready.await() }
        } catch (e: Exception) {
            disconnect()
            if (e is CancellationException && e !is kotlinx.coroutines.TimeoutCancellationException) throw e
            throw UniversalException("The download engine didn't start. Try again.")
        }
    }

    fun disconnect() {
        connection?.let { runCatching { appContext.unbindService(it) } }
        connection = null
        service = null
    }

    private fun failAll(message: String) {
        val all = pending.entries.toList()
        all.forEach { (id, entry) ->
            pending.remove(id)
            entry.result.completeExceptionally(UniversalException(message))
        }
    }

    private fun onReply(message: Message) {
        val entry = pending[message.arg1] ?: return
        val data = message.data ?: Bundle()
        when (message.what) {
            P.REP_PROGRESS -> entry.onProgress?.invoke(data)
            P.REP_RESULT -> {
                pending.remove(message.arg1)
                entry.result.complete(data)
            }
            P.REP_ERROR -> {
                pending.remove(message.arg1)
                entry.result.completeExceptionally(errorFrom(data))
            }
        }
    }

    private fun errorFrom(data: Bundle): Throwable {
        if (data.getBoolean(P.KEY_CANCELLED, false)) return CancellationException("cancelled")
        val raw = data.getString(P.KEY_RAW_ERROR)
        if (raw != null) {
            // yt-dlp's own text: turn it into a sentence people can act on
            val problem = ErrorMapper.explain(raw)
            return UniversalException(problem.message, problem.suggestUpdate)
        }
        return UniversalException(
            data.getString(P.KEY_MESSAGE) ?: "The download engine failed.",
            data.getBoolean(P.KEY_SUGGEST_UPDATE, false)
        )
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000L
        const val IDLE_MS = 60_000L
    }
}
