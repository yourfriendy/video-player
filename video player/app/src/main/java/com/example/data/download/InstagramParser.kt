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

    // ------------------------------------------------------------------------------------------
    // Stories
    // ------------------------------------------------------------------------------------------

    sealed interface StoryTarget {
        /** Every active story of this account. */
        data class User(val username: String) : StoryTarget

        /** One specific story (from a story link); falls back to "not available" if it has expired. */
        data class Story(val username: String, val mediaId: String) : StoryTarget

        /** A saved highlight. */
        data class Highlight(val id: String) : StoryTarget
    }

    data class StoryItem(
        val id: String,
        val isVideo: Boolean,
        val mediaUrl: String,
        val thumbnailUrl: String?,
        val takenAtSec: Long
    )

    private val USERNAME_REGEX = Regex("""^[A-Za-z0-9._]{1,30}$""")
    private val RESERVED_FIRST_SEGMENTS = setOf(
        "reel", "reels", "p", "tv", "explore", "accounts", "direct", "stories", "share",
        "about", "legal", "developer", "challenge", "web", "api", "oauth", "emails", "nametag"
    )

    fun isStoryLink(text: String?): Boolean =
        extractInstagramUrl(text)?.substringBefore('?')?.contains("/stories/") == true

    /**
     * Understands "Username or link": `name`, `@name`, a profile link, a story link
     * (`/stories/name/123/`) or a highlight link (`/stories/highlights/123/`).
     */
    fun parseStoryTarget(input: String?): StoryTarget? {
        val text = input?.trim().orEmpty()
        if (text.isEmpty()) return null

        val url = extractInstagramUrl(text)
        if (url != null) {
            val path = url.substringBefore('?').substringBefore('#')
                .substringAfter("://").substringAfter('/', "")
                .trim('/')
            val parts = path.split('/').filter { it.isNotEmpty() }
            if (parts.isEmpty()) return null
            if (parts[0] == "stories") {
                if (parts.size >= 3 && parts[1] == "highlights" && parts[2].all { it.isDigit() }) {
                    return StoryTarget.Highlight(parts[2])
                }
                if (parts.size >= 3 && parts[2].all { it.isDigit() } && USERNAME_REGEX.matches(parts[1])) {
                    return StoryTarget.Story(parts[1], parts[2])
                }
                if (parts.size >= 2 && USERNAME_REGEX.matches(parts[1])) return StoryTarget.User(parts[1])
                return null
            }
            val first = parts[0]
            if (first in RESERVED_FIRST_SEGMENTS) return null
            return if (USERNAME_REGEX.matches(first)) StoryTarget.User(first) else null
        }

        val name = text.removePrefix("@").trim()
        return if (USERNAME_REGEX.matches(name)) StoryTarget.User(name) else null
    }

    /** `web_profile_info` response -> numeric user id. */
    fun parseUserId(json: String): String? = try {
        org.json.JSONObject(json).optJSONObject("data")?.optJSONObject("user")?.optString("id")
            ?.takeIf { it.isNotBlank() && it != "null" }
    } catch (e: Exception) {
        null
    }

    /** `fbsearch/web/top_search` response -> id of the account whose username matches exactly. */
    fun parseUserIdFromSearch(json: String, username: String): String? = try {
        val users = org.json.JSONObject(json).optJSONArray("users")
        var found: String? = null
        if (users != null) {
            for (i in 0 until users.length()) {
                val user = users.optJSONObject(i)?.optJSONObject("user") ?: continue
                if (user.optString("username").equals(username, ignoreCase = true)) {
                    found = (user.optString("pk").ifBlank { user.optString("id") }).takeIf { it.isNotBlank() }
                    break
                }
            }
        }
        found
    } catch (e: Exception) {
        null
    }

    /** Last resort: dig the account id out of the profile page's embedded data. */
    fun parseUserIdFromProfileHtml(html: String, username: String): String? {
        val name = Regex.escape(username)
        val patterns = listOf(
            Regex("""profilePage_(\d+)"""),
            Regex(""""id"\s*:\s*"(\d+)"\s*,\s*"username"\s*:\s*"$name"""", RegexOption.IGNORE_CASE),
            Regex(""""username"\s*:\s*"$name"\s*,[^{}]{0,200}?"id"\s*:\s*"(\d+)"""", RegexOption.IGNORE_CASE),
            Regex(""""profile_id"\s*:\s*"(\d+)""""),
            Regex(""""target_id"\s*:\s*"(\d+)"""")
        )
        for (pattern in patterns) {
            pattern.find(html)?.groupValues?.get(1)?.let { return it }
        }
        return null
    }

    /** What we show about an account so the user can see the app really is talking to Instagram. */
    data class ProfileInfo(
        val id: String,
        val username: String,
        val fullName: String,
        val picUrl: String?,
        val isPrivate: Boolean,
        val isVerified: Boolean,
        val followers: Long?
    )

    private fun text(obj: org.json.JSONObject, key: String): String {
        if (obj.isNull(key)) return ""
        return obj.optString(key).trim()
    }

    private fun profileFrom(user: org.json.JSONObject): ProfileInfo? {
        val id = text(user, "id").ifBlank { text(user, "pk") }.ifBlank { text(user, "pk_id") }
        if (id.isBlank()) return null
        val pic = text(user, "profile_pic_url_hd").ifBlank { text(user, "profile_pic_url") }
        val followers = user.optJSONObject("edge_followed_by")?.optLong("count", -1L)?.takeIf { it >= 0 }
            ?: user.optLong("follower_count", -1L).takeIf { it >= 0 }
        return ProfileInfo(
            id = id,
            username = text(user, "username"),
            fullName = text(user, "full_name"),
            picUrl = pic.takeIf { it.startsWith("http") },
            isPrivate = user.optBoolean("is_private", false),
            isVerified = user.optBoolean("is_verified", false),
            followers = followers
        )
    }

    /** `web_profile_info` response -> the account. */
    fun parseProfile(json: String): ProfileInfo? = try {
        org.json.JSONObject(json).optJSONObject("data")?.optJSONObject("user")?.let { profileFrom(it) }
    } catch (e: Exception) {
        null
    }

    /** `top_search` response -> the account whose username matches exactly. */
    fun parseProfileFromSearch(json: String, username: String): ProfileInfo? {
        return try {
            val users = org.json.JSONObject(json).optJSONArray("users") ?: return null
            for (i in 0 until users.length()) {
                val user = users.optJSONObject(i)?.optJSONObject("user") ?: continue
                if (text(user, "username").equals(username, ignoreCase = true)) return profileFrom(user)
            }
            null
        } catch (e: Exception) {
            null
        }
    }

    /** `accounts/current_user` response -> the signed-in account itself. */
    fun parseCurrentUser(json: String): ProfileInfo? = try {
        org.json.JSONObject(json).optJSONObject("user")?.let { profileFrom(it) }
    } catch (e: Exception) {
        null
    }

    /** A short, secret-free description of a reply, for error messages ("JSON with keys data,status"). */
    fun describeReply(reply: String): String {
        val t = reply.trim()
        if (t.isEmpty()) return "an empty reply"
        if (t.startsWith("{")) {
            return try {
                val o = org.json.JSONObject(t)
                val keys = o.keys().asSequence().take(6).toList().joinToString(",")
                val message = text(o, "message")
                val user = o.optJSONObject("data")?.let { if (it.has("user") && it.isNull("user")) ", account: none" else "" }.orEmpty()
                "JSON ($keys)" + user + (if (message.isNotBlank()) ", message: ${message.take(60)}" else "")
            } catch (e: Exception) {
                "unreadable data"
            }
        }
        if (t.startsWith("<")) return "a web page with no account data in it"
        return "an unexpected reply"
    }

    /**
     * Reads the story items out of the responses of `feed/reels_media` or `feed/user/<id>/story`.
     * Videos come first-choice from `video_versions`, photos from `image_versions2`.
     */
    fun parseStoryItems(json: String): List<StoryItem> {
        val root = try {
            org.json.JSONObject(json)
        } catch (e: Exception) {
            return emptyList()
        }
        val itemArrays = mutableListOf<org.json.JSONArray>()
        root.optJSONArray("reels_media")?.let { reels ->
            for (i in 0 until reels.length()) reels.optJSONObject(i)?.optJSONArray("items")?.let { itemArrays.add(it) }
        }
        root.optJSONObject("reels")?.let { reels ->
            val keys = reels.keys()
            while (keys.hasNext()) reels.optJSONObject(keys.next())?.optJSONArray("items")?.let { itemArrays.add(it) }
        }
        root.optJSONObject("reel")?.optJSONArray("items")?.let { itemArrays.add(it) }

        val result = ArrayList<StoryItem>()
        for (array in itemArrays) {
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val id = item.optString("pk").ifBlank { item.optString("id").substringBefore('_') }
                if (id.isBlank()) continue

                val videoUrl = item.optJSONArray("video_versions")?.optJSONObject(0)?.optString("url").orEmpty()
                val candidates = item.optJSONObject("image_versions2")?.optJSONArray("candidates")
                val imageUrl = candidates?.optJSONObject(0)?.optString("url").orEmpty()
                val thumbUrl = candidates?.optJSONObject(candidates.length() - 1)?.optString("url").orEmpty()

                val isVideo = videoUrl.startsWith("http")
                val media = if (isVideo) videoUrl else imageUrl
                if (!media.startsWith("http")) continue
                result.add(
                    StoryItem(
                        id = id,
                        isVideo = isVideo,
                        mediaUrl = media,
                        thumbnailUrl = (thumbUrl.ifBlank { imageUrl }).takeIf { it.startsWith("http") },
                        takenAtSec = item.optLong("taken_at", 0L)
                    )
                )
            }
        }
        return result.distinctBy { it.id }.sortedBy { it.takenAtSec }
    }
}
