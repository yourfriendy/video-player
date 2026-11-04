package com.example.data.universal

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import com.example.data.universal.engine.EngineClient
import com.example.data.universal.engine.EngineInfoParser
import com.example.data.universal.engine.EngineOutdatedException
import com.example.data.universal.engine.EngineProtocol as P
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/** An error whose message is safe to show as-is. [suggestUpdate]: updating the engine may fix it. */
open class UniversalException(message: String, val suggestUpdate: Boolean = false) : Exception(message)

/**
 * Downloads videos from YouTube, Facebook, Snapchat, TikTok, X, Reddit, Pinterest, Vimeo and around a
 * thousand other sites.
 *
 * The heavy part (yt-dlp, Python, ffmpeg, about 50 MB) is NOT inside this app any more: it lives in the
 * optional OffScroll Engine app and is reached through [EngineClient]. This class keeps the same functions
 * the rest of OffScroll always used, so the screens didn't have to change. The finished file is copied into
 * Movies/OffScroll here, so it belongs to this app and shows up in the feed like any other video.
 */
class UniversalDownloader(context: Context) {

    private val appContext = context.applicationContext
    private val client = EngineClient(appContext)

    @Volatile
    private var lastVersion: String = "built-in"

    fun isEngineInstalled(): Boolean = client.isInstalled()

    /** Starts the engine (first time it unpacks Python and ffmpeg, a few seconds) and checks it matches this app. */
    suspend fun prepare() {
        val hello = client.request(P.REQ_HELLO)
        val protocol = hello.getInt(P.KEY_PROTOCOL, 0)
        if (protocol != P.PROTOCOL_VERSION) {
            throw EngineOutdatedException(
                if (protocol < P.PROTOCOL_VERSION) "The installed OffScroll Engine is out of date. Update it to keep downloading."
                else "This OffScroll is older than the installed engine. Update OffScroll."
            )
        }
        lastVersion = hello.getString(P.KEY_VERSION) ?: lastVersion
    }

    /** "built-in" until the engine has been updated at least once. */
    fun engineVersion(): String = lastVersion

    /** Looks the link up and lists every quality the site offers. */
    suspend fun fetchInfo(url: String): MediaInfo {
        prepare()
        val reply = client.request(P.REQ_FETCH_INFO, Bundle().apply { putString(P.KEY_URL, url) })
        reply.getString(P.KEY_VERSION)?.let { lastVersion = it }
        val json = reply.getString(P.KEY_INFO_JSON) ?: throw UniversalException("The engine returned nothing for that link.", true)
        return withContext(Dispatchers.Default) { EngineInfoParser.parse(url, JSONObject(json)) }
    }

    /**
     * Downloads [option] of [info] and saves it to Movies/OffScroll.
     * [onProgress] gets yt-dlp's percent (0..100) for the current file, the ETA in seconds and the raw line.
     */
    suspend fun download(
        info: MediaInfo,
        option: QualityOption,
        processId: String,
        onProgress: (percent: Float, etaSeconds: Long, line: String) -> Unit
    ): Uri {
        prepare()
        val reply = try {
            client.request(
                P.REQ_DOWNLOAD,
                Bundle().apply {
                    putString(P.KEY_URL, info.pageUrl)
                    putString(P.KEY_SELECTOR, option.selector)
                    putString(P.KEY_PROCESS_ID, processId)
                },
                onProgress = { data ->
                    onProgress(data.getFloat(P.KEY_PERCENT), data.getLong(P.KEY_ETA), data.getString(P.KEY_LINE).orEmpty())
                }
            )
        } catch (e: CancellationException) {
            // The coroutine is already cancelled, so the goodbye messages must run in a context that isn't
            withContext(kotlinx.coroutines.NonCancellable) {
                client.tell(P.REQ_CANCEL, Bundle().apply { putString(P.KEY_PROCESS_ID, processId) })
                client.tell(P.REQ_CLEANUP, Bundle().apply { putString(P.KEY_PROCESS_ID, processId) })
            }
            throw e
        }
        val handoff = Uri.parse(reply.getString(P.KEY_FILE_URI) ?: throw UniversalException("The download finished but no video file came out of it.", true))
        val extension = reply.getString(P.KEY_FILE_EXT).orEmpty().ifBlank { "mp4" }
        try {
            val suffix = if (option.quality > 0) " [${option.quality}p]" else ""
            return withContext(Dispatchers.IO) {
                saveToLibrary(
                    handoff,
                    extension,
                    "${safeFileName(info.uploader?.takeIf { it.isNotBlank() } ?: info.title)}_" +
                        java.text.SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", java.util.Locale.US).format(java.util.Date()) +
                        "${suffix}.${extension}"
                )
            }
        } finally {
            // Whether it worked or not, the engine can drop its copy now
            withContext(kotlinx.coroutines.NonCancellable) {
                client.tell(P.REQ_CLEANUP, Bundle().apply { putString(P.KEY_PROCESS_ID, processId) })
            }
        }
    }

    /** Stops a running download (the coroutine then ends with a [CancellationException]). */
    fun cancel(processId: String) {
        client.tellNow(P.REQ_CANCEL, Bundle().apply { putString(P.KEY_PROCESS_ID, processId) })
    }

    /**
     * Downloads the newest yt-dlp. Sites change their pages all the time and yt-dlp's fixes arrive
     * within days, so this is how downloads keep working without a new app version.
     */
    suspend fun updateEngine(): String {
        prepare()
        val reply = client.request(P.REQ_UPDATE)
        reply.getString(P.KEY_VERSION)?.let { lastVersion = it }
        return reply.getString(P.KEY_MESSAGE) ?: "Checked for updates"
    }

    // --------------------------------------------------------------------------------------------

    /** Copies the engine's finished file into Movies/OffScroll via MediaStore (no storage permission needed on Android 10+). */
    private fun saveToLibrary(source: Uri, extension: String, displayName: String): Uri {
        val mime = mimeFor(extension)
        val input = appContext.contentResolver.openInputStream(source)
            ?: throw UniversalException("Couldn't read the finished download from the engine.")
        input.use { stream ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = appContext.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                    put(MediaStore.MediaColumns.MIME_TYPE, mime)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/OffScroll")
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw UniversalException("Couldn't create the file on your phone.")
                try {
                    resolver.openOutputStream(uri)?.use { out -> stream.copyTo(out) }
                        ?: throw UniversalException("Couldn't write to your phone's storage.")
                    values.clear()
                    values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                    resolver.update(uri, values, null, null)
                    return uri
                } catch (e: Throwable) {
                    resolver.delete(uri, null, null) // never leave a half-written file behind
                    throw e
                }
            } else {
                val base = appContext.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: appContext.filesDir
                val dir = File(base, "OffScroll").apply { mkdirs() }
                val target = File(dir, displayName)
                try {
                    target.outputStream().use { stream.copyTo(it) }
                } catch (e: Throwable) {
                    target.delete()
                    throw e
                }
                MediaScannerConnection.scanFile(appContext, arrayOf(target.absolutePath), arrayOf(mime), null)
                return Uri.fromFile(target)
            }
        }
    }

    private fun mimeFor(extension: String): String = when (extension.lowercase()) {
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "mov" -> "video/quicktime"
        "3gp" -> "video/3gpp"
        else -> "video/mp4"
    }
}
