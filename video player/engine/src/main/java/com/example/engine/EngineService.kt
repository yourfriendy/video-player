package com.example.engine

import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import androidx.core.content.FileProvider
import com.example.engine.EngineProtocol as P
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * What the main OffScroll app talks to. Only apps signed with the same key can bind (see the manifest).
 * Each request carries an id in Message.arg1 and the client's Messenger in replyTo; answers echo the id.
 */
class EngineService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = ConcurrentHashMap<String, Job>()
    private lateinit var core: EngineCore
    private lateinit var messenger: Messenger

    override fun onCreate() {
        super.onCreate()
        core = EngineCore(applicationContext)
        scope.launch { core.purgeStale() }
        messenger = Messenger(Handler(Looper.getMainLooper()) { message ->
            handle(message)
            true
        })
    }

    override fun onBind(intent: Intent?): IBinder = messenger.binder

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun handle(msg: Message) {
        val client = msg.replyTo ?: return
        val id = msg.arg1
        val data = msg.data ?: Bundle()
        val clientPackage = packageManager.getPackagesForUid(msg.sendingUid)?.firstOrNull()

        when (msg.what) {
            P.REQ_HELLO -> run(client, id) {
                core.prepare()
                reply(client, P.REP_RESULT, id, Bundle().apply {
                    putString(P.KEY_VERSION, core.version())
                    putInt(P.KEY_PROTOCOL, P.PROTOCOL_VERSION)
                    putLong(P.KEY_VERSION_CODE, versionCode())
                })
            }

            P.REQ_FETCH_INFO -> run(client, id) {
                val json = core.fetchInfoJson(data.getString(P.KEY_URL).orEmpty())
                reply(client, P.REP_RESULT, id, Bundle().apply {
                    putString(P.KEY_INFO_JSON, json)
                    putString(P.KEY_VERSION, core.version())
                })
            }

            P.REQ_DOWNLOAD -> {
                val processId = data.getString(P.KEY_PROCESS_ID).orEmpty()
                run(client, id, processId) {
                    val file = core.download(
                        url = data.getString(P.KEY_URL).orEmpty(),
                        selector = data.getString(P.KEY_SELECTOR).orEmpty(),
                        processId = processId
                    ) { percent, eta, line ->
                        reply(client, P.REP_PROGRESS, id, Bundle().apply {
                            putFloat(P.KEY_PERCENT, percent)
                            putLong(P.KEY_ETA, eta)
                            putString(P.KEY_LINE, line)
                        })
                    }
                    val uri = FileProvider.getUriForFile(this, "$packageName.handoff", file)
                    // Read access to this one file, for the app that asked and nobody else
                    if (clientPackage != null) {
                        grantUriPermission(clientPackage, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    reply(client, P.REP_RESULT, id, Bundle().apply {
                        putString(P.KEY_FILE_URI, uri.toString())
                        putString(P.KEY_FILE_EXT, file.extension.lowercase())
                    })
                }
            }

            P.REQ_CANCEL -> {
                val processId = data.getString(P.KEY_PROCESS_ID).orEmpty()
                scope.launch { core.cancel(processId) }
                jobs[processId]?.cancel()
            }

            P.REQ_UPDATE -> run(client, id) {
                val note = core.update()
                reply(client, P.REP_RESULT, id, Bundle().apply {
                    putString(P.KEY_MESSAGE, note)
                    putString(P.KEY_VERSION, core.version())
                })
            }

            P.REQ_CLEANUP -> {
                val processId = data.getString(P.KEY_PROCESS_ID).orEmpty()
                scope.launch { core.cleanup(processId) }
            }
        }
    }

    /** Runs [block] off the main thread and turns any failure into an error reply. */
    private fun run(client: Messenger, id: Int, processId: String? = null, block: () -> Unit) {
        val job = scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                reply(client, P.REP_ERROR, id, Bundle().apply { putBoolean(P.KEY_CANCELLED, true) })
            } catch (e: EngineException) {
                reply(client, P.REP_ERROR, id, Bundle().apply {
                    putString(P.KEY_MESSAGE, e.message)
                    e.raw?.let { putString(P.KEY_RAW_ERROR, it) }
                    putBoolean(P.KEY_SUGGEST_UPDATE, e.suggestUpdate)
                })
            } catch (e: Throwable) {
                reply(client, P.REP_ERROR, id, Bundle().apply {
                    putString(P.KEY_MESSAGE, "The engine failed (${e.javaClass.simpleName}).")
                    putBoolean(P.KEY_SUGGEST_UPDATE, true)
                })
            } finally {
                if (processId != null) jobs.remove(processId)
            }
        }
        if (processId != null) jobs[processId] = job
    }

    private fun reply(to: Messenger, what: Int, id: Int, data: Bundle) {
        try {
            to.send(Message.obtain(null, what, id, 0).apply { this.data = data })
        } catch (e: RemoteException) {
            // The main app went away; nothing to tell
        }
    }

    @Suppress("DEPRECATION")
    private fun versionCode(): Long = try {
        val info = packageManager.getPackageInfo(packageName, PackageManager.GET_META_DATA)
        if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    } catch (e: Exception) {
        0L
    }
}
