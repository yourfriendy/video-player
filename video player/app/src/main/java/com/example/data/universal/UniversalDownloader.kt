package com.example.data.universal

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoFormat
import com.yausername.youtubedl_android.mapper.VideoInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** An error whose message is safe to show as-is. [suggestUpdate]: updating the engine may fix it. */
class UniversalException(message: String, val suggestUpdate: Boolean = false) : Exception(message)

/**
 * Downloads videos from YouTube, Facebook, Snapchat, TikTok, X, Reddit, Pinterest, Vimeo and
 * around a thousand other sites by running yt-dlp (with ffmpeg and a JavaScript runtime) inside the app.
 *
 * Everything here is blocking work, so every public function hops to [Dispatchers.IO].
 */
class UniversalDownloader(context: Context) {

    private val appContext = context.applicationContext
    private val initLock = Any()

    @Volatile
    private var ready = false

    /** First start unpacks Python and ffmpeg (a few seconds); later starts are instant. */
    suspend fun prepare() = withContext(Dispatchers.IO) { ensureReady() }

    private fun ensureReady() {
        if (ready) return
        synchronized(initLock) {
            if (ready) return
            try {
                YoutubeDL.init(appContext)
                FFmpeg.init(appContext)
                ready = true
            } catch (e: Exception) {
                throw UniversalException(
                    "The download engine couldn't start on this phone (${e.message ?: e.javaClass.simpleName})."
                )
            }
        }
    }

    /** "built-in" until the engine has been updated at least once. */
    fun engineVersion(): String = try {
        YoutubeDL.versionName(appContext) ?: "built-in"
    } catch (e: Exception) {
        "built-in"
    }

    /** Looks the link up and lists every quality the site offers. */
    suspend fun fetchInfo(url: String): MediaInfo = withContext(Dispatchers.IO) {
        ensureReady()
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
            val problem = ErrorMapper.explain(e.message)
            throw UniversalException(problem.message, problem.suggestUpdate)
        }
        toMediaInfo(url, info)
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
    ): Uri = withContext(Dispatchers.IO) {
        ensureReady()
        val workDir = File(appContext.cacheDir, "universal/$processId")
        workDir.deleteRecursively()
        workDir.mkdirs()
        try {
            val request = YoutubeDLRequest(info.pageUrl).apply {
                addOption("--no-playlist")
                addOption("-f", option.selector)
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
                YoutubeDL.execute(request, processId, false) { percent, eta, line ->
                    onProgress(percent, eta, line)
                }
            } catch (e: YoutubeDL.CanceledException) {
                throw CancellationException("cancelled")
            } catch (e: YoutubeDLException) {
                val problem = ErrorMapper.explain(e.message)
                throw UniversalException(problem.message, problem.suggestUpdate)
            }

            val output = findOutput(workDir)
                ?: throw UniversalException("The download finished but no video file came out of it.", true)
            val suffix = if (option.quality > 0) " [${option.quality}p]" else ""
            saveToLibrary(output, "${safeFileName(info.title)}$suffix.${output.extension.lowercase()}")
        } finally {
            workDir.deleteRecursively()
        }
    }

    /** Stops a running download (the coroutine then ends with a [CancellationException]). */
    fun cancel(processId: String) {
        YoutubeDL.destroyProcessById(processId)
    }

    /**
     * Downloads the newest yt-dlp. Sites change their pages all the time and yt-dlp's fixes arrive
     * within days, so this is how downloads keep working without a new app version.
     */
    suspend fun updateEngine(): String = withContext(Dispatchers.IO) {
        ensureReady()
        try {
            when (YoutubeDL.updateYoutubeDL(appContext, YoutubeDL.UpdateChannel.NIGHTLY)) {
                YoutubeDL.UpdateStatus.DONE -> "Updated to ${engineVersion()}"
                YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> "Already on the newest version (${engineVersion()})"
                null -> "Checked for updates"
            }
        } catch (e: Exception) {
            throw UniversalException("Couldn't update the engine: ${e.message ?: "check your internet connection"}")
        }
    }

    // --------------------------------------------------------------------------------------------

    private fun toMediaInfo(url: String, info: VideoInfo): MediaInfo {
        val formats = info.formats.orEmpty().map { it.toFormatInfo() }.ifEmpty {
            // Sites that serve one single file report it at the top level instead
            listOf(
                FormatInfo(
                    formatId = info.formatId.orEmpty(),
                    width = info.width,
                    height = info.height,
                    fps = 0,
                    vcodec = null,
                    acodec = null,
                    ext = info.ext,
                    bitrateKbps = 0,
                    sizeBytes = if (info.fileSize > 0) info.fileSize else info.fileSizeApproximate
                )
            )
        }
        val title = listOfNotNull(info.title, info.fulltitle)
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() } ?: "Video"
        return MediaInfo(
            pageUrl = url,
            title = title,
            uploader = info.uploader?.takeIf { it.isNotBlank() },
            thumbnail = info.thumbnail,
            durationSec = info.duration,
            platform = Platforms.nameFor(info.extractorKey ?: info.extractor, url),
            options = QualityOptions.build(formats, info.duration)
        )
    }

    private fun VideoFormat.toFormatInfo() = FormatInfo(
        formatId = formatId.orEmpty(),
        width = width,
        height = height,
        fps = fps,
        vcodec = vcodec,
        acodec = acodec,
        ext = ext,
        bitrateKbps = tbr,
        sizeBytes = if (fileSize > 0) fileSize else fileSizeApproximate,
        note = formatNote
    )

    /** The merged file. yt-dlp's leftovers (video.f137.mp4, video.f140.m4a) are skipped. */
    private fun findOutput(dir: File): File? {
        val videos = dir.listFiles()?.filter { it.isFile && it.extension.lowercase() in VIDEO_EXTENSIONS }.orEmpty()
        return videos.filter { !PARTIAL_STREAM.matches(it.name) }.maxByOrNull { it.length() }
            ?: videos.maxByOrNull { it.length() }
    }

    /** Copies the file into Movies/OffScroll via MediaStore (no storage permission needed on Android 10+). */
    private fun saveToLibrary(source: File, displayName: String): Uri {
        val mime = mimeFor(source.extension)
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
                resolver.openOutputStream(uri)?.use { out -> source.inputStream().use { it.copyTo(out) } }
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
                source.inputStream().use { input -> target.outputStream().use { input.copyTo(it) } }
            } catch (e: Throwable) {
                target.delete()
                throw e
            }
            MediaScannerConnection.scanFile(appContext, arrayOf(target.absolutePath), arrayOf(mime), null)
            return Uri.fromFile(target)
        }
    }

    private fun mimeFor(extension: String): String = when (extension.lowercase()) {
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "mov" -> "video/quicktime"
        "3gp" -> "video/3gpp"
        else -> "video/mp4"
    }

    private companion object {
        val VIDEO_EXTENSIONS = setOf("mp4", "mkv", "webm", "mov", "m4v", "3gp", "flv", "avi")

        /** yt-dlp's intermediate per-stream files: video.f137.mp4, video.fhls-720.mp4 */
        val PARTIAL_STREAM = Regex(""".*\.f[\w-]+\.[A-Za-z0-9]+""")
    }
}
