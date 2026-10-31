package com.example.data.universal

import java.util.Locale

// ------------------------------------------------------------------------------------------------
// Everything in this file is plain Kotlin (no Android, no yt-dlp classes) so it can be unit tested.
// ------------------------------------------------------------------------------------------------

/** One stream a site offers: video-only, audio-only, or both together ("muxed"). */
data class FormatInfo(
    val formatId: String,
    val width: Int,
    val height: Int,
    val fps: Int,
    val vcodec: String?,
    val acodec: String?,
    val ext: String?,
    val bitrateKbps: Int,
    val sizeBytes: Long,
    val note: String? = null
) {
    /** Real picture streams only: storyboards ("mhtml") and audio-only streams don't count. */
    val hasVideo: Boolean
        get() = (width > 0 || height > 0) &&
            vcodec != "none" &&
            ext != "mhtml" &&
            note?.contains("storyboard", ignoreCase = true) != true

    /** Unknown audio codec is treated as "has audio" (most single-file sites don't say). */
    val hasAudio: Boolean get() = acodec != "none"

    val isAudioOnly: Boolean get() = vcodec == "none" && acodec != "none"

    /** What people call the quality: the SHORT side, so a 1080x1920 reel is "1080p", like a 1920x1080 clip. */
    val quality: Int
        get() = if (width > 0 && height > 0) minOf(width, height) else maxOf(width, height)
}

/** A row in the "Choose quality" list. */
data class QualityOption(
    val id: String,
    /** Short side in pixels. 0 means "best available, picked automatically". */
    val quality: Int,
    val label: String,
    val detail: String,
    /** The yt-dlp `-f` expression that downloads exactly this option. */
    val selector: String,
    val approxBytes: Long,
    /** How much of the total download is the video stream (the rest is the separate audio stream). */
    val videoShare: Float
)

/** What the app knows about a link after looking it up. */
data class MediaInfo(
    val pageUrl: String,
    val title: String,
    val uploader: String?,
    val thumbnail: String?,
    val durationSec: Int,
    val platform: String,
    val options: List<QualityOption>
)

object QualityOptions {
    /** Used when a site doesn't tell us its resolutions. */
    const val BEST_SELECTOR = "bv*+ba[ext=m4a]/bv*+ba/b"
    const val DEFAULT_VIDEO_SHARE = 0.85f

    /**
     * One option per available resolution, best first.
     *
     * Within a resolution it prefers the highest frame rate, then the most compatible codec
     * (H.264 plays everywhere, AV1 stutters on many phones), then the highest bitrate. The video is
     * addressed by its exact format id, so the user gets precisely what the row says.
     */
    fun build(formats: List<FormatInfo>, durationSec: Int): List<QualityOption> {
        val videos = formats.filter { it.hasVideo }
        if (videos.isEmpty()) return listOf(bestAvailable())

        val bestAudio = formats.filter { it.isAudioOnly }
            .maxWithOrNull(compareBy<FormatInfo>({ it.bitrateKbps }, { it.sizeBytes }))
        val audioBytes = bestAudio?.let { estimateBytes(it, durationSec) } ?: 0L

        return videos.groupBy { it.quality }
            .entries
            .sortedByDescending { it.key }
            .map { (quality, group) ->
                val best = group.maxWithOrNull(
                    compareBy<FormatInfo>({ it.fps }, { codecRank(it.vcodec) }, { it.bitrateKbps }, { it.sizeBytes })
                )!!
                val muxed = best.hasAudio
                val videoBytes = estimateBytes(best, durationSec)
                val total = when {
                    muxed -> videoBytes
                    videoBytes > 0 -> videoBytes + audioBytes
                    else -> 0L
                }
                val share = when {
                    muxed -> 1f
                    videoBytes > 0 && audioBytes > 0 -> videoBytes.toFloat() / (videoBytes + audioBytes)
                    else -> DEFAULT_VIDEO_SHARE
                }
                val detail = listOfNotNull(
                    codecName(best.vcodec),
                    if (best.fps >= 50) "${best.fps} fps" else null,
                    formatBytes(total)?.let { "~$it" }
                ).joinToString(" · ")
                val tag = qualityTag(quality)
                QualityOption(
                    id = "q$quality",
                    quality = quality,
                    label = if (tag != null) "${quality}p · $tag" else "${quality}p",
                    detail = detail,
                    selector = selectorFor(best, quality, muxed),
                    approxBytes = total,
                    videoShare = share
                )
            }
    }

    fun bestAvailable() = QualityOption(
        id = "best",
        quality = 0,
        label = "Best available",
        detail = "Chosen automatically",
        selector = BEST_SELECTOR,
        approxBytes = 0L,
        videoShare = 1f
    )

    private fun selectorFor(video: FormatInfo, quality: Int, muxed: Boolean): String {
        val id = video.formatId
        if (id.isBlank()) {
            // Should not happen, but never build a broken expression
            val h = "[height<=?$quality]"
            return "bv*$h+ba[ext=m4a]/bv*$h+ba/b$h/b"
        }
        return if (muxed) "$id/b" else "$id+ba[ext=m4a]/$id+ba/b"
    }

    private fun estimateBytes(format: FormatInfo, durationSec: Int): Long = when {
        format.sizeBytes > 0 -> format.sizeBytes
        format.bitrateKbps > 0 && durationSec > 0 -> format.bitrateKbps.toLong() * 125L * durationSec
        else -> 0L
    }

    private fun codecRank(vcodec: String?): Int = when {
        vcodec == null -> 0
        vcodec.startsWith("avc") || vcodec.startsWith("h264") -> 3
        vcodec.startsWith("hev") || vcodec.startsWith("hvc") || vcodec.startsWith("h265") -> 2
        vcodec.startsWith("vp09") || vcodec.startsWith("vp9") -> 2
        vcodec.startsWith("av01") || vcodec.startsWith("av1") -> 1
        else -> 0
    }

    fun codecName(vcodec: String?): String? = when {
        vcodec == null || vcodec == "none" -> null
        vcodec.startsWith("avc") || vcodec.startsWith("h264") -> "H.264"
        vcodec.startsWith("hev") || vcodec.startsWith("hvc") || vcodec.startsWith("h265") -> "H.265"
        vcodec.startsWith("vp09") || vcodec.startsWith("vp9") -> "VP9"
        vcodec.startsWith("vp8") -> "VP8"
        vcodec.startsWith("av01") || vcodec.startsWith("av1") -> "AV1"
        else -> null
    }

    fun qualityTag(shortSide: Int): String? = when {
        shortSide >= 4000 -> "8K"
        shortSide >= 2000 -> "4K"
        shortSide >= 1400 -> "2K"
        shortSide >= 1000 -> "Full HD"
        shortSide >= 700 -> "HD"
        else -> null
    }
}

// ------------------------------------------------------------------------------------------------
// Links
// ------------------------------------------------------------------------------------------------

object LinkUtils {
    private val URL = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
    private val BARE_LINK = Regex("""^(?:www\.)?[a-z0-9][a-z0-9-]*(?:\.[a-z0-9-]+)+/\S*$""", RegexOption.IGNORE_CASE)
    private val TRAILING = charArrayOf('.', ',', ';', ')', ']', '}', '!', '?', '"', '\'')

    /**
     * Pulls the link out of whatever was pasted or shared ("Look at this https://youtu.be/x, wow").
     * Also accepts a bare "youtu.be/abc" or "www.tiktok.com/@a/video/1".
     */
    fun extractUrl(text: String?): String? {
        val trimmed = text?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        URL.find(trimmed)?.let { return it.value.trimEnd(*TRAILING) }
        val bare = trimmed.split(Regex("\\s+")).firstOrNull { BARE_LINK.matches(it) } ?: return null
        return "https://" + bare.trimEnd(*TRAILING)
    }
}

object Platforms {
    private val known = listOf(
        "youtube" to "YouTube",
        "youtu.be" to "YouTube",
        "facebook" to "Facebook",
        "fb.watch" to "Facebook",
        "instagram" to "Instagram",
        "snapchat" to "Snapchat",
        "tiktok" to "TikTok",
        "twitter" to "X (Twitter)",
        "x.com" to "X (Twitter)",
        "reddit" to "Reddit",
        "redd.it" to "Reddit",
        "pinterest" to "Pinterest",
        "pin.it" to "Pinterest",
        "vimeo" to "Vimeo",
        "dailymotion" to "Dailymotion",
        "threads" to "Threads",
        "twitch" to "Twitch",
        "linkedin" to "LinkedIn",
        "likee" to "Likee"
    )

    /** A friendly site name from yt-dlp's extractor name (preferred) or the link's host. */
    fun nameFor(extractorKey: String?, url: String): String {
        val key = extractorKey?.lowercase(Locale.ROOT).orEmpty()
        val host = url.substringAfter("://").substringBefore('/').lowercase(Locale.ROOT)
        for ((needle, name) in known) {
            if (key.contains(needle) || host.contains(needle)) return name
        }
        val fromHost = host.removePrefix("www.").removePrefix("m.")
        return if (fromHost.isNotEmpty()) fromHost else "Video"
    }
}

// ------------------------------------------------------------------------------------------------
// Errors
// ------------------------------------------------------------------------------------------------

/** [suggestUpdate]: the failure looks like "the site changed", which updating yt-dlp usually fixes. */
data class DownloadProblem(val message: String, val suggestUpdate: Boolean)

object ErrorMapper {
    fun explain(raw: String?): DownloadProblem {
        val text = raw.orEmpty()
        val l = text.lowercase(Locale.ROOT)
        fun has(vararg needles: String) = needles.any { l.contains(it) }

        return when {
            has("no space left") ->
                DownloadProblem("Your phone is out of storage space.", false)
            has("not a bot") ->
                DownloadProblem(
                    "YouTube wants proof you're not a bot on this connection. Try again later, or on a different network.",
                    false
                )
            has("private video", "this video is private", "private account", "is private") ->
                DownloadProblem("That video is private.", false)
            has("confirm your age", "age-restricted", "age restricted", "inappropriate for some users") ->
                DownloadProblem("That video is age-restricted and needs a signed-in account.", false)
            has("login required", "log in", "logged in", "login to", "sign in to", "cookies") ->
                DownloadProblem("That video needs a logged-in account, so it can't be saved.", false)
            has("not available in your country", "geo restrict", "geo-restrict", "blocked it in your country") ->
                DownloadProblem("That video isn't available in your country.", false)
            has(
                "video unavailable", "has been removed", "no longer available", "does not exist",
                "this video is not available", "has been terminated", "been deleted", "content isn't available"
            ) ->
                DownloadProblem("That video isn't available any more.", false)
            has("unsupported url") ->
                DownloadProblem("That link isn't a video this app can download.", false)
            has("http error 404", "404: not found") ->
                DownloadProblem("That page wasn't found. Check the link.", false)
            has("http error 429", "too many requests") ->
                DownloadProblem("The site is limiting requests. Wait a few minutes and try again.", false)
            has("http error 403", "forbidden") ->
                DownloadProblem("The site refused the download (HTTP 403). Updating the engine usually fixes this.", true)
            has(
                "unable to download webpage", "urlopen error", "name or service not known",
                "temporary failure in name resolution", "network is unreachable", "timed out",
                "connection reset", "connection refused", "ssl:"
            ) ->
                DownloadProblem("Couldn't reach the site. Check your internet connection.", false)
            has(
                "unable to extract", "nsig", "no video formats found", "requested format is not available",
                "extractorerror", "unable to find", "signature"
            ) ->
                DownloadProblem("This site changed and the download engine is out of date.", true)
            has("ffmpeg", "conversion failed", "postprocessing", "merger") ->
                DownloadProblem("Joining the video and audio failed. Try a lower quality.", false)
            else -> {
                val last = text.lines().map { it.trim() }.lastOrNull { it.startsWith("ERROR:", ignoreCase = true) }
                val short = last
                    ?.substringAfter(':')?.trim()
                    ?.replace(Regex("""^\[[^\]]+]\s*[^:]*:\s*"""), "")
                    ?.take(140)
                if (short.isNullOrBlank()) {
                    DownloadProblem("The download failed.", true)
                } else {
                    DownloadProblem(short, true)
                }
            }
        }
    }
}

// ------------------------------------------------------------------------------------------------
// Progress
// ------------------------------------------------------------------------------------------------

/**
 * Turns yt-dlp's per-file progress into one bar for the whole job.
 *
 * yt-dlp downloads the video stream and the audio stream one after the other, each from 0 to 100,
 * then merges them. Here the bar covers both (weighted by [videoShare]), never goes backwards,
 * and stays under 100% until the file is really saved.
 */
class ProgressMapper(private val videoShare: Float) {
    enum class Phase { Starting, Video, Audio, Merging }

    var phase: Phase = Phase.Starting
        private set

    private var streams = 0
    private var last = 0f

    /** [percent] is yt-dlp's 0..100 value for the file currently downloading. */
    fun onLine(line: String, percent: Float): Float {
        if (line.startsWith("[download] Destination:")) {
            streams++
            phase = if (isAudioFile(line)) Phase.Audio else Phase.Video
        } else if (line.startsWith("[Merger]") || line.startsWith("[VideoRemuxer]") || line.contains("Merging formats")) {
            phase = Phase.Merging
        }
        val fraction = (percent / 100f).coerceIn(0f, 1f)
        val overall = when {
            phase == Phase.Merging -> MAX_BEFORE_SAVED
            streams <= 1 -> fraction * (if (videoShare < 1f) videoShare else 1f)
            else -> {
                val share = if (videoShare < 1f) videoShare else QualityOptions.DEFAULT_VIDEO_SHARE
                share + (1f - share) * fraction
            }
        }
        last = maxOf(last, overall.coerceAtMost(MAX_BEFORE_SAVED))
        return last
    }

    fun phaseLabel(): String = when (phase) {
        Phase.Starting -> "Starting"
        Phase.Video -> "Downloading video"
        Phase.Audio -> "Downloading audio"
        Phase.Merging -> "Joining video and audio"
    }

    private fun isAudioFile(line: String): Boolean =
        line.substringAfterLast('.').trim().lowercase(Locale.ROOT) in AUDIO_EXTENSIONS

    companion object {
        private const val MAX_BEFORE_SAVED = 0.99f
        private val AUDIO_EXTENSIONS = setOf("m4a", "mp3", "opus", "aac", "ogg", "weba", "wav", "flac")
        private val SPEED = Regex("""\bat\s+([0-9.]+\s?[KMGT]?i?B/s)""")

        /** "1.2MiB/s · 0:30 left" from a yt-dlp progress line and the ETA the library parsed. */
        fun detail(line: String, etaSeconds: Long): String {
            val speed = SPEED.find(line)?.groupValues?.get(1)?.replace(" ", "")
            val eta = if (etaSeconds in 1..359_999) formatDuration(etaSeconds.toInt()) + " left" else null
            return listOfNotNull(speed, eta).joinToString(" · ")
        }
    }
}

// ------------------------------------------------------------------------------------------------
// Formatting
// ------------------------------------------------------------------------------------------------

fun formatBytes(bytes: Long): String? {
    if (bytes <= 0) return null
    val mb = bytes / 1_000_000.0
    return when {
        mb >= 1000 -> String.format(Locale.US, "%.1f GB", mb / 1000.0)
        mb >= 10 -> String.format(Locale.US, "%.0f MB", mb)
        mb >= 1 -> String.format(Locale.US, "%.1f MB", mb)
        else -> "<1 MB"
    }
}

fun formatDuration(totalSeconds: Int): String {
    if (totalSeconds <= 0) return ""
    val h = totalSeconds / 3600
    val m = totalSeconds % 3600 / 60
    val s = totalSeconds % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%d:%02d", m, s)
}

/** A title made safe for a file name. */
fun safeFileName(title: String, maxLength: Int = 90): String {
    val cleaned = title
        .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .trim('.')
    val cut = if (cleaned.length > maxLength) cleaned.take(maxLength).trim() else cleaned
    return cut.ifBlank { "video" }
}
