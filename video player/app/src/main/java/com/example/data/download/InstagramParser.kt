package com.example.data.download

/**
 * Pure string helpers for reading a public Instagram reel page. No Android classes on purpose,
 * so they are easy to unit-test and easy to adjust when Instagram changes its markup.
 */
object InstagramParser {

    private val URL_REGEX =
        Regex("""https?://(?:www\.|m\.)?(?:instagram\.com|instagr\.am)/[^\s"'<>]+""", RegexOption.IGNORE_CASE)

    private val SHORTCODE_REGEX = Regex("""(?:reels?|p|tv)/([A-Za-z0-9_-]{5,})""")

    /** Finds the first Instagram link in arbitrary text (a pasted link or a "Share" message). */
    fun extractInstagramUrl(text: String?): String? {
        if (text.isNullOrBlank()) return null
        return URL_REGEX.find(text)?.value?.trimEnd('.', ',', ')', ']', '!', '?')
    }

    /** `https://www.instagram.com/reel/AbC123xyz/?igsh=...` -> `AbC123xyz` */
    fun findShortcode(url: String): String? {
        val path = url.substringBefore('?').substringBefore('#')
        return SHORTCODE_REGEX.find(path)?.groupValues?.get(1)
    }

    /** "Share" links (`/share/reel/...`) use a temporary code and redirect to the real reel. */
    fun isShareLink(url: String): Boolean = url.substringBefore('?').contains("/share/")

    /**
     * Looks for the direct video file inside a reel/embed page. Tries the structured JSON that
     * Instagram embeds in the page first, then the Open Graph tags used for link previews.
     */
    fun extractVideoUrl(html: String): String? {
        for (key in listOf("video_url", "contentUrl", "playable_url_quality_hd", "playable_url")) {
            scanForUrl(html, key)?.let { return it }
        }
        val versions = html.indexOf("video_versions")
        if (versions >= 0) {
            val window = html.substring(versions, minOf(html.length, versions + 1500))
            scanForUrl(window, "url")?.let { return it }
        }
        metaContent(html, "og:video:secure_url")?.let { if (it.startsWith("http")) return it }
        metaContent(html, "og:video")?.let { if (it.startsWith("http")) return it }
        Regex("""<video[^>]*\ssrc="([^"]+)"""").find(html)?.groupValues?.get(1)?.let {
            val decoded = decodeHtml(it)
            if (decoded.startsWith("http")) return decoded
        }
        return null
    }

    fun extractTitle(html: String): String? =
        metaContent(html, "og:title")?.take(80)?.takeIf { it.isNotBlank() }

    /** Does the page look like Instagram's login wall rather than the reel itself? */
    fun looksLikeLoginWall(html: String): Boolean =
        html.contains("loginForm", ignoreCase = true) ||
            (html.contains("Log in", ignoreCase = true) && !html.contains("og:video", ignoreCase = true) &&
                !html.contains("video_url", ignoreCase = true))

    // --------------------------------------------------------------------------------------

    /**
     * Finds `key` followed (possibly through JSON/JS escaping like `\"key\":\"https:\/\/...`) by
     * a URL, and returns that URL cleaned of escape sequences.
     */
    private fun scanForUrl(text: String, key: String): String? {
        var from = 0
        while (true) {
            val k = text.indexOf(key, from)
            if (k < 0) return null
            var i = k + key.length
            while (i < text.length && (text[i] == '\\' || text[i] == '"' || text[i] == ':' || text[i].isWhitespace())) i++
            if (text.startsWith("http", i)) {
                val end = text.indexOf('"', i)
                if (end > i) return cleanEscapes(text.substring(i, end))
            }
            from = k + key.length
        }
    }

    private fun cleanEscapes(raw: String): String =
        raw.replace(Regex("""\\+u([0-9a-fA-F]{4})""")) { it.groupValues[1].toInt(16).toChar().toString() }
            .replace("\\", "")
            .let { decodeHtml(it) }
            .trimEnd('\\')

    private fun metaContent(html: String, property: String): String? {
        val p = Regex.escape(property)
        val forward = Regex("""<meta[^>]+property=["']$p["'][^>]+content=["']([^"']*)["']""", RegexOption.IGNORE_CASE)
        val reverse = Regex("""<meta[^>]+content=["']([^"']*)["'][^>]+property=["']$p["']""", RegexOption.IGNORE_CASE)
        val value = forward.find(html)?.groupValues?.get(1) ?: reverse.find(html)?.groupValues?.get(1)
        return value?.let { decodeHtml(it) }
    }

    private fun decodeHtml(s: String): String =
        s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#039;", "'").replace("&#x27;", "'")
            .replace("&lt;", "<").replace("&gt;", ">")
}
