package com.example.engine

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoFormat
import com.yausername.youtubedl_android.mapper.VideoInfo
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A failure with text that is safe to show. [raw] is yt-dlp's own message when there is one. */
class EngineException(message: String, val raw: String? = null, val suggestUpdate: Boolean = false) : Exception(message)

/**
 * Runs yt-dlp (with ffmpeg and a JavaScript runtime). This is the part of OffScroll that used to live
 * inside the main app. Everything here blocks, so the service calls it from a background thread.
 */
class EngineCore(context: Context) {

    private val appContext = context.applicationContext
    private val initLock = Any()

    @Volatile
    private var ready = false

    /** First start unpacks Python and ffmpeg (a few seconds); later starts are instant. */
    fun prepare() {
        if (ready) return
        synchronized(initLock) {
            if (ready) return
            try {
                YoutubeDL.init(appContext)
                FFmpeg.init(appContext)
                ready = true
            } catch (e: Exception) {
                throw EngineException("The download engine couldn't start on this phone (${e.message ?: e.javaClass.simpleName}).")
            }
        }
    }

    /** "built-in" until yt-dlp has been updated at least once. */
    fun version(): String = try {
        YoutubeDL.versionName(appContext) ?: "built-in"
    } catch (e: Exception) {
        "built-in"
    }

    /** Looks the link up and returns the facts the app needs, as JSON (the app builds the quality list from it). */
    fun fetchInfoJson(url: String): String {
        prepare()
        val request = YoutubeDLRequest(url).apply {
            addOption("--no-playlist")
            addOption("--socket-timeout", 20)
            addOption("--retries", 3)
        }
        val info: VideoInfo = try {
            YoutubeDL.getInfo(request)
        } catch (e: YoutubeDL.CanceledException) {
            throw CancellationException("cancelled")
        } catch (e: YoutubeDLException) {
            throw EngineException(e.message ?: "yt-dlp failed", raw = e.message, suggestUpdate = false)
        }
        return infoToJson(info).toString()
    }

    /**
     * Downloads [selector] of [url] into a private folder and returns the finished file. The caller hands it
     * to the main app and later calls [cleanup]. [onProgress] gets yt-dlp's percent, ETA in seconds and raw line.
     */
    fun download(
        url: String,
        selector: String,
        processId: String,
        onProgress: (percent: Float, etaSeconds: Long, line: String) -> Unit
    ): File {
        prepare()
        val workDir = workDirFor(processId)
        workDir.deleteRecursively()
        workDir.mkdirs()
        try {
            val request = YoutubeDLRequest(url).apply {
                addOption("--no-playlist")
                addOption("-f", selector)
                // MP4 when the streams allow it (H.264 + AAC), otherwise MKV (VP9/AV1/Opus in 4K and up)
                addOption("--merge-output-format", "mp4/mkv")
                addOption("-o", "${workDir.absolutePath}/video.%(ext)s")
                addOption("--no-mtime")
                addOption("--newline")
                addOption("--concurrent-fragments", 4)
                addOption("--retries", 5)
                addOption("--fragment-retries", 5)
                addOption("--socket-timeout", 20)
            }
            try {
                YoutubeDL.execute(request, processId, false) { percent, eta, line -> onProgress(percent, eta, line) }
            } catch (e: YoutubeDL.CanceledException) {
                throw CancellationException("cancelled")
            } catch (e: YoutubeDLException) {
                throw EngineException(e.message ?: "yt-dlp failed", raw = e.message)
            }
            return findOutput(workDir)
                ?: throw EngineException("The download finished but no video file came out of it.", suggestUpdate = true)
        } catch (e: Throwable) {
            workDir.deleteRecursively()
            throw e
        }
    }

    fun cancel(processId: String) {
        YoutubeDL.destroyProcessById(processId)
    }

    /** Removes a finished download once the main app has copied it. */
    fun cleanup(processId: String) {
        workDirFor(processId).deleteRecursively()
    }

    /** Removes anything left behind by a crash or an app that never said it was done (older than an hour). */
    fun purgeStale() {
        val root = File(appContext.cacheDir, HANDOFF)
        val cutoff = System.currentTimeMillis() - 60 * 60 * 1000L
        root.listFiles()?.filter { it.lastModified() < cutoff }?.forEach { it.deleteRecursively() }
    }

    /**
     * Downloads the newest yt-dlp. Sites change their pages all the time and yt-dlp's fixes arrive
     * within days, so this is how downloads keep working without a new app version.
     */
    fun update(): String {
        prepare()
        return try {
            when (YoutubeDL.updateYoutubeDL(appContext, YoutubeDL.UpdateChannel.NIGHTLY)) {
                YoutubeDL.UpdateStatus.DONE -> "Updated to ${version()}"
                YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> "Already on the newest version (${version()})"
                null -> "Checked for updates"
            }
        } catch (e: Exception) {
            throw EngineException("Couldn't update the engine: ${e.message ?: "check your internet connection"}")
        }
    }

    fun workDirFor(processId: String): File =
        File(appContext.cacheDir, "$HANDOFF/${processId.filter { it.isLetterOrDigit() || it == '-' || it == '_' }}")

    // --------------------------------------------------------------------------------------------

    private fun infoToJson(info: VideoInfo): JSONObject {
        val formats = JSONArray()
        info.formats.orEmpty().forEach { formats.put(formatToJson(it)) }
        return JSONObject()
            .put("title", info.title ?: "")
            .put("fulltitle", info.fulltitle ?: "")
            .put("uploader", info.uploader ?: "")
            .put("thumbnail", info.thumbnail ?: "")
            .put("duration", info.duration)
            .put("extractor", info.extractor ?: "")
            .put("extractorKey", info.extractorKey ?: "")
            // Sites that serve one single file describe it at the top level instead of in "formats"
            .put("formatId", info.formatId ?: "")
            .put("ext", info.ext ?: "")
            .put("width", info.width)
            .put("height", info.height)
            .put("fileSize", info.fileSize)
            .put("fileSizeApproximate", info.fileSizeApproximate)
            .put("formats", formats)
    }

    private fun formatToJson(f: VideoFormat): JSONObject = JSONObject()
        .put("formatId", f.formatId ?: "")
        .put("width", f.width)
        .put("height", f.height)
        .put("fps", f.fps)
        .put("vcodec", f.vcodec ?: JSONObject.NULL)
        .put("acodec", f.acodec ?: JSONObject.NULL)
        .put("ext", f.ext ?: JSONObject.NULL)
        .put("tbr", f.tbr)
        .put("fileSize", f.fileSize)
        .put("fileSizeApproximate", f.fileSizeApproximate)
        .put("formatNote", f.formatNote ?: JSONObject.NULL)

    /** The merged file. yt-dlp's leftovers (video.f137.mp4, video.f140.m4a) are skipped. */
    private fun findOutput(dir: File): File? {
        val videos = dir.listFiles()?.filter { it.isFile && it.extension.lowercase() in VIDEO_EXTENSIONS }.orEmpty()
        return videos.filter { !PARTIAL_STREAM.matches(it.name) }.maxByOrNull { it.length() }
            ?: videos.maxByOrNull { it.length() }
    }

    private companion object {
        const val HANDOFF = "handoff"
        val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "webm", "mov", "m4v", "3gp", "flv", "avi")

        /** yt-dlp's intermediate per-stream files: video.f137.mp4, video.fhls-720.mp4 */
        val PARTIAL_STREAM = Regex(""".*\.f[\w-]+\.[A-Za-z0-9]+""")
    }
}
