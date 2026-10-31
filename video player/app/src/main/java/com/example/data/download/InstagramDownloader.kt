package com.example.data.download

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

/** An error with a message that is safe to show to the user as-is. */
open class DownloadException(message: String) : Exception(message)

/** Instagram wants the user to confirm their identity first; falling back to another method won't help. */
class VerificationRequiredException(message: String) : DownloadException(message)

/** The account was found but really has nothing to show. */
class NoStoriesException(message: String) : DownloadException(message)

data class ResolvedReel(val shortcode: String, val videoUrl: String, val title: String?)

/**
 * Downloads PUBLIC Instagram reels into the phone's Movies/OffScroll folder.
 *
 * Instagram has no official download API, so this reads the same public page a browser or a
 * link-preview bot sees. That means it can only work for public reels and may need updating
 * if Instagram changes its pages. It never asks for, stores or uses anyone's login.
 */
class InstagramDownloader(private val context: Context) {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    /** Same browser identity for the sign-in page and for API calls, so Instagram sees one consistent client. */
    val webUserAgent: String = try {
        android.webkit.WebSettings.getDefaultUserAgent(context)
            .replace("; wv", "")      // "wv" marks an embedded WebView
            .replace(" Version/4.0", "") // so does the old "Version/4.0" token; real Chrome has neither
    } catch (e: Exception) {
        DESKTOP_UA
    }

    private val webFetcher = IgWebFetcher(context, webUserAgent)
    private val pageCapture = IgPageCapture(context, webUserAgent)

    val stories = InstagramStoryService(client, webUserAgent, webFetcher, pageCapture)

    /** Frees the hidden browser engine. */
    fun release() = webFetcher.release()

    /** Turns a pasted link into a direct video URL. */
    suspend fun resolve(inputUrl: String): ResolvedReel = withContext(Dispatchers.IO) {
        try {
            var url = inputUrl.trim()
            if (InstagramParser.isShareLink(url)) url = finalUrlAfterRedirects(url)

            val code = InstagramParser.findShortcode(url)
                ?: throw DownloadException("That doesn't look like an Instagram reel link.")

            val attempts = listOf(
                "https://www.instagram.com/reel/$code/embed/captioned/" to DESKTOP_UA,
                "https://www.instagram.com/p/$code/embed/" to DESKTOP_UA,
                "https://www.instagram.com/reel/$code/" to CRAWLER_UA
            )
            var sawLoginWall = false
            for ((pageUrl, userAgent) in attempts) {
                coroutineContext.ensureActive()
                val html = fetchText(pageUrl, userAgent) ?: continue
                InstagramParser.extractVideoUrl(html)?.let { video ->
                    return@withContext ResolvedReel(code, video, InstagramParser.extractTitle(html))
                }
                if (InstagramParser.looksLikeLoginWall(html)) sawLoginWall = true
            }
            throw DownloadException(
                if (sawLoginWall) {
                    "Instagram asked for a login, so this reel can't be fetched. Only public reels work."
                } else {
                    "Couldn't find a video at this link. It may be private, removed, a photo post, or Instagram blocked the request."
                }
            )
        } catch (e: IOException) {
            throw DownloadException("Couldn't reach Instagram. Check your internet connection and try again.")
        }
    }

    /**
     * Streams the video into Movies/OffScroll. [onProgress] gets 0..1, or -1 when the size is unknown.
     * Returns the Uri of the saved file.
     */
    suspend fun download(reel: ResolvedReel, onProgress: (Float) -> Unit): Uri =
        downloadMedia(reel.videoUrl, "Instagram_${reel.shortcode}", isVideo = true, onProgress = onProgress)

    /** Saves any video or photo URL into Movies/OffScroll or Pictures/OffScroll. Used by reels and stories. */
    suspend fun downloadMedia(
        url: String,
        baseName: String,
        isVideo: Boolean,
        onProgress: (Float) -> Unit
    ): Uri = withContext(Dispatchers.IO) {
        // Captured here because the writer lambda below is not a suspend lambda
        val ctx = coroutineContext
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", DESKTOP_UA)
            .header("Referer", "https://www.instagram.com/")
            .build()
        val call = client.newCall(request)
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    throw DownloadException("Instagram refused the download (error ${response.code}). Try again in a moment.")
                }
                val body = response.body ?: throw DownloadException("Instagram sent an empty response.")
                val type = body.contentType()?.toString().orEmpty()
                if (type.startsWith("text/") || type.contains("json")) {
                    throw DownloadException("Instagram didn't return a file. The link may have expired.")
                }
                val total = body.contentLength()
                val name = "${baseName}_${System.currentTimeMillis() / 1000}.${if (isVideo) "mp4" else "jpg"}"
                saveStream(name, isVideo) { out ->
                    val buffer = ByteArray(64 * 1024)
                    var copied = 0L
                    var lastReported = -1
                    body.byteStream().use { input ->
                        while (true) {
                            ctx.ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            out.write(buffer, 0, read)
                            copied += read
                            if (total > 0) {
                                val percent = (copied * 100 / total).toInt()
                                if (percent != lastReported) {
                                    lastReported = percent
                                    onProgress(percent / 100f)
                                }
                            } else if (lastReported != -1) {
                                lastReported = -1
                                onProgress(-1f)
                            }
                        }
                    }
                    if (copied == 0L) throw DownloadException("The downloaded file was empty.")
                }
            }
        } catch (e: IOException) {
            throw DownloadException("The download was interrupted. Check your connection and try again.")
        } finally {
            call.cancel()
        }
    }

    // --------------------------------------------------------------------------------------

    private fun finalUrlAfterRedirects(url: String): String {
        val request = Request.Builder().url(url).header("User-Agent", DESKTOP_UA).build()
        client.newCall(request).execute().use { return it.request.url.toString() }
    }

    private fun fetchText(url: String, userAgent: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Accept", "text/html,application/xhtml+xml")
            .build()
        return client.newCall(request).execute().use { response ->
            if (response.isSuccessful) response.body?.string() else null
        }
    }

    /** Writes via MediaStore on Android 10+, so no storage permission is needed. */
    private fun saveStream(fileName: String, isVideo: Boolean, writer: (java.io.OutputStream) -> Unit): Uri {
        val mime = if (isVideo) "video/mp4" else "image/jpeg"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = context.contentResolver
            val collection = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val folder = (if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES) + "/OffScroll"
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, folder)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = resolver.insert(collection, values)
                ?: throw DownloadException("Couldn't create the file on your phone.")
            try {
                resolver.openOutputStream(uri)?.use { writer(it) }
                    ?: throw DownloadException("Couldn't write to your phone's storage.")
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                return uri
            } catch (e: Throwable) {
                resolver.delete(uri, null, null) // never leave a half-written file behind
                throw e
            }
        } else {
            val type = if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
            val dir = File(context.getExternalFilesDir(type), "OffScroll").apply { mkdirs() }
            val file = File(dir, fileName)
            try {
                file.outputStream().use { writer(it) }
            } catch (e: Throwable) {
                file.delete()
                throw e
            }
            MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), arrayOf(mime), null)
            return Uri.fromFile(file)
        }
    }

    private companion object {
        const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) " +
                "Chrome/124.0.0.0 Safari/537.36"
        const val CRAWLER_UA = "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)"
    }
}
