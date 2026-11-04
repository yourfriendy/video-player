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

    /** Username of the account that posted the reel, read from the public page. */
    fun extractUsername(html: String): String? {
        val patterns = listOf(
            Regex("""\\?"username\\?"\s*:\s*\\?"([A-Za-z0-9._]{1,30})"""),
            Regex("""UsernameText[^>]*>([A-Za-z0-9._]{1,30})<"""),
            Regex("""instagram\.com/([A-Za-z0-9._]{1,30})/(?:reel|p)/""")
        )
        for (p in patterns) {
            val name = p.find(html)?.groupValues?.get(1)
            if (!name.isNullOrBlank() && name !in setOf("reel", "p", "explore", "accounts")) return name
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
        val takenAtSec: Long,
        /** Original poster, for reposts. */
        val author: String? = null,
        val authorName: String? = null,
        val authorPic: String? = null,
        val caption: String? = null,
        /** Short code of a post/reel (instagram.com/reel/<code>). */
        val code: String? = null,
        val likes: Long? = null,
        val comments: Long? = null,
        val plays: Long? = null,
        val durationSec: Double? = null,
        /** When it was reposted, if Instagram says so (otherwise only [takenAtSec], the original post time, is known). */
        val repostedAtSec: Long? = null,
        val expiresAtSec: Long? = null,
        /** People tagged in a story (@mentions). */
        val mentions: List<ProfileInfo> = emptyList(),
        /** A reel whose video address isn't in the list reply yet: it must be looked up before it can play or save. */
        val needsResolve: Boolean = false,
        /** The short note the reposter typed when they tapped Repost (NOT the reel's own caption). */
        val repostNote: String? = null,
        /** What Instagram sent about the repost itself, for troubleshooting ("Copy details"). */
        val debug: String? = null
    )

    data class HighlightInfo(val id: String, val title: String, val coverUrl: String?)

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
        val followers: Long?,
        val bio: String = "",
        val following: Long? = null,
        val posts: Long? = null,
        val externalUrl: String? = null
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
            followers = followers,
            bio = text(user, "biography"),
            following = user.optJSONObject("edge_follow")?.optLong("count", -1L)?.takeIf { it >= 0 }
                ?: user.optLong("following_count", -1L).takeIf { it >= 0 },
            posts = user.optJSONObject("edge_owner_to_timeline_media")?.optLong("count", -1L)?.takeIf { it >= 0 }
                ?: user.optLong("media_count", -1L).takeIf { it >= 0 },
            externalUrl = text(user, "external_url").takeIf { it.startsWith("http") }
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

    private fun itemToStory(item: org.json.JSONObject): StoryItem? {
        val id = text(item, "pk").ifBlank { text(item, "id").substringBefore('_') }
        if (id.isBlank()) return null
        val videoUrl = item.optJSONArray("video_versions")?.optJSONObject(0)?.let { text(it, "url") }.orEmpty()
        val candidates = item.optJSONObject("image_versions2")?.optJSONArray("candidates")
        val imageUrl = candidates?.optJSONObject(0)?.let { text(it, "url") }.orEmpty()
        val thumbUrl = candidates?.optJSONObject(candidates.length() - 1)?.let { text(it, "url") }.orEmpty()
        val isVideo = videoUrl.startsWith("http")
        val media = if (isVideo) videoUrl else imageUrl
        if (!media.startsWith("http")) return null
        // A reel from a list reply carries only its picture: mark it so the real video gets looked up
        val reelLike = item.optInt("media_type", 0) == 2 || text(item, "product_type") == "clips" ||
            item.has("video_duration") || item.optBoolean("is_video", false)
        val needsResolve = !isVideo && reelLike
        val owner = item.optJSONObject("user") ?: item.optJSONObject("owner")
        return StoryItem(
            id = id,
            isVideo = isVideo || needsResolve,
            mediaUrl = media,
            thumbnailUrl = (thumbUrl.ifBlank { imageUrl }).takeIf { it.startsWith("http") },
            takenAtSec = item.optLong("taken_at", 0L),
            authorName = owner?.let { text(it, "full_name") }?.ifBlank { null },
            authorPic = owner?.let { text(it, "profile_pic_url") }?.takeIf { it.startsWith("http") },
            caption = captionOf(item),
            code = text(item, "code").ifBlank { text(item, "shortcode") }.ifBlank { null },
            likes = item.optLong("like_count", -1L).takeIf { it >= 0 },
            comments = item.optLong("comment_count", -1L).takeIf { it >= 0 },
            plays = (item.optLong("play_count", -1L).takeIf { it >= 0 } ?: item.optLong("view_count", -1L).takeIf { it >= 0 }),
            durationSec = item.optDouble("video_duration", -1.0).takeIf { it >= 0 },
            repostedAtSec = scanRepostTime(item, 0),
            expiresAtSec = item.optLong("expiring_at", -1L).takeIf { it > 0 },
            mentions = mentionsOf(item),
            needsResolve = needsResolve
        )
    }

    private fun captionOf(item: org.json.JSONObject): String? {
        val c = item.opt("caption")
        val direct = when (c) {
            is org.json.JSONObject -> text(c, "text")
            is String -> c.trim()
            else -> ""
        }
        if (direct.isNotBlank()) return direct
        return item.optJSONObject("edge_media_to_caption")?.optJSONArray("edges")?.optJSONObject(0)
            ?.optJSONObject("node")?.let { text(it, "text") }?.takeIf { it.isNotBlank() }
    }

    private fun mentionsOf(item: org.json.JSONObject): List<ProfileInfo> {
        val array = item.optJSONArray("reel_mentions") ?: return emptyList()
        return (0 until array.length()).mapNotNull { i -> array.optJSONObject(i)?.optJSONObject("user")?.let { profileFrom(it) } }
            .distinctBy { it.username }
    }

    /** Looks for a "reposted at" time inside an item: any number under a key that mentions repost/reshare. */
    private fun scanRepostTime(obj: org.json.JSONObject, depth: Int): Long? {
        val keys = obj.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = obj.opt(key)
            if (Regex("(repost|reshare)", RegexOption.IGNORE_CASE).containsMatchIn(key)) {
                val n = (value as? Number)?.toLong() ?: (value as? String)?.toLongOrNull()
                if (n != null && n in 1_000_000_000L..4_000_000_000L) return n
                if (n != null && n in 1_000_000_000_000L..4_000_000_000_000L) return n / 1000
            }
            if (depth < 3 && value is org.json.JSONObject) scanRepostTime(value, depth + 1)?.let { return it }
            if (depth < 3 && value is org.json.JSONArray) {
                // arrays can hold wrapper objects too; look at the first few entries only
                for (i in 0 until minOf(value.length(), 10)) {
                    value.optJSONObject(i)?.let { child -> scanRepostTime(child, depth + 1)?.let { return it } }
                }
            }
        }
        return null
    }

    /** Reply of `media/<id>/info/`: the full post/reel (video address, caption, counts, times). */
    fun parseMediaInfo(json: String): StoryItem? = try {
        org.json.JSONObject(json).optJSONArray("items")?.optJSONObject(0)?.let { itemToStory(it) }
    } catch (e: Exception) {
        null
    }

    /** Fills a list entry in with what the full lookup found. */
    fun mergeResolved(old: StoryItem, full: StoryItem): StoryItem = old.copy(
        isVideo = full.isVideo || old.isVideo,
        mediaUrl = if (full.isVideo) full.mediaUrl else old.mediaUrl,
        needsResolve = !full.isVideo && old.needsResolve,
        takenAtSec = if (full.takenAtSec > 0) full.takenAtSec else old.takenAtSec,
        caption = full.caption ?: old.caption,
        code = full.code ?: old.code,
        likes = full.likes ?: old.likes,
        comments = full.comments ?: old.comments,
        plays = full.plays ?: old.plays,
        durationSec = full.durationSec ?: old.durationSec,
        repostedAtSec = old.repostedAtSec ?: full.repostedAtSec,
        authorName = full.authorName ?: old.authorName,
        authorPic = full.authorPic ?: old.authorPic
    )

    /** Reply of `highlights/<id>/highlights_tray/`. */
    fun parseHighlightsTray(json: String): List<HighlightInfo> = try {
        val tray = org.json.JSONObject(json).optJSONArray("tray")
        (0 until (tray?.length() ?: 0)).mapNotNull { i ->
            val h = tray?.optJSONObject(i) ?: return@mapNotNull null
            val id = text(h, "id")
            if (id.isBlank()) return@mapNotNull null
            val cover = h.optJSONObject("cover_media")
            val url = cover?.optJSONObject("cropped_image_version")?.let { text(it, "url") }
                ?: cover?.optJSONObject("full_image_version")?.let { text(it, "url") }
            HighlightInfo(id, text(h, "title"), url?.takeIf { it.startsWith("http") })
        }
    } catch (e: Exception) {
        emptyList()
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
                array.optJSONObject(i)?.let { itemToStory(it) }?.let { result.add(it) }
            }
        }
        return result.distinctBy { it.id }.sortedBy { it.takenAtSec }
    }

    private fun walk(node: Any?, visit: (org.json.JSONObject) -> Unit) {
        when (node) {
            is org.json.JSONObject -> {
                visit(node)
                val keys = node.keys()
                while (keys.hasNext()) walk(node.opt(keys.next()), visit)
            }
            is org.json.JSONArray -> for (i in 0 until node.length()) walk(node.opt(i), visit)
        }
    }

    private fun walkWithParents(
        node: Any?,
        parents: List<org.json.JSONObject>,
        visit: (org.json.JSONObject, List<org.json.JSONObject>) -> Unit
    ) {
        when (node) {
            is org.json.JSONObject -> {
                visit(node, parents)
                val next = parents + node
                val keys = node.keys()
                while (keys.hasNext()) walkWithParents(node.opt(keys.next()), next, visit)
            }
            is org.json.JSONArray -> for (i in 0 until node.length()) walkWithParents(node.opt(i), parents, visit)
        }
    }

    private class RepostMeta(var time: Long? = null, var note: String? = null, val lines: MutableList<String> = ArrayList())

    private val REPOST_KEY = Regex("(repost|reshare)", RegexOption.IGNORE_CASE)
    private val TIME_KEY = Regex("(time|date|created|_at$|^at$)", RegexOption.IGNORE_CASE)
    private val NOTE_KEYS = setOf("note", "text", "message", "comment", "repost_note", "repost_caption", "reshare_comment")

    private fun epochSeconds(v: Any?): Long? {
        val n = (v as? Number)?.toLong() ?: (v as? String)?.toLongOrNull() ?: return null
        return when {
            n in 1_000_000_000L..4_000_000_000L -> n
            n in 1_000_000_000_000L..4_000_000_000_000L -> n / 1000
            n in 1_000_000_000_000_000L..4_000_000_000_000_000L -> n / 1_000_000
            else -> null
        }
    }

    private fun looksLikeMedia(o: org.json.JSONObject) =
        o.has("video_versions") || o.has("image_versions2") || o.has("carousel_media") || o.has("display_url")

    /**
     * Looks inside an object (never into arrays or into the reel itself) for fields that describe the REPOST:
     * a time (when the reposter tapped Repost) and a note (what they typed). A field only counts if it sits under
     * a key that mentions repost/reshare, so the reel's own caption and post time are never mistaken for them.
     */
    private fun scanRepost(o: org.json.JSONObject, inRepost: Boolean, depth: Int, media: org.json.JSONObject, meta: RepostMeta, path: String) {
        val keys = o.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val value = o.opt(key)
            val ctx = inRepost || REPOST_KEY.containsMatchIn(key)
            when (value) {
                is org.json.JSONObject -> {
                    if (value === media || looksLikeMedia(value) || depth >= 4) continue
                    if (key == "caption" && !ctx) continue
                    scanRepost(value, ctx, depth + 1, media, meta, "$path$key.")
                }
                is org.json.JSONArray -> {
                    // Look into arrays of wrapper objects (never into the reel itself); first few entries only
                    if (depth < 4) {
                        for (i in 0 until minOf(value.length(), 10)) {
                            val child = value.optJSONObject(i) ?: continue
                            if (child === media || looksLikeMedia(child)) continue
                            scanRepost(child, ctx, depth + 1, media, meta, "$path$key[$i].")
                        }
                    }
                }
                else -> if (ctx && meta.lines.size < 40) {
                    val shown = value?.toString()?.take(120)
                    meta.lines.add("$path$key = $shown")
                    if (meta.time == null && TIME_KEY.containsMatchIn(key)) meta.time = epochSeconds(value)
                    if (meta.note == null && value is String && value.isNotBlank() && epochSeconds(value) == null &&
                        (key.lowercase() in NOTE_KEYS || (key == "caption" && REPOST_KEY.containsMatchIn(path + key)))
                    ) meta.note = value.trim()
                }
            }
        }
    }

    private fun repostMeta(media: org.json.JSONObject, parents: List<org.json.JSONObject>): RepostMeta {
        val meta = RepostMeta()
        // nearest wrapper first, then the reel's own top level
        for (candidate in parents.takeLast(3).reversed() + media) {
            if (candidate !== media && looksLikeMedia(candidate)) continue
            scanRepost(candidate, false, 0, media, meta, "")
        }
        return meta
    }

    private fun parseAny(json: String): Any? = try {
        org.json.JSONTokener(json).nextValue()
    } catch (e: Exception) {
        null
    }

    /**
     * Finds story items ANYWHERE in a reply, whatever the wrapper looks like. Instagram's own web
     * page receives the same item objects (with `video_versions` / `image_versions2`) inside
     * differently named wrappers, so this keeps working when only the wrapper changes.
     */
    fun parseStoryItemsDeep(json: String): List<StoryItem> {
        val root = parseAny(json) ?: return emptyList()
        val found = ArrayList<StoryItem>()
        walk(root) { obj ->
            val looksLikeItem = obj.has("video_versions") || obj.has("image_versions2")
            if (looksLikeItem && (obj.has("taken_at") || obj.has("media_type") || obj.has("pk"))) {
                itemToStory(obj)?.let { found.add(it) }
            }
        }
        return found.distinctBy { it.id }.sortedBy { it.takenAtSec }
    }

    private fun ownerMatches(owner: org.json.JSONObject, username: String, userId: String?): Boolean {
        if (username.isNotBlank() && text(owner, "username").equals(username, ignoreCase = true)) return true
        val id = text(owner, "pk").ifBlank { text(owner, "id") }
        return userId != null && id.isNotBlank() && id == userId
    }

    /**
     * Story items that belong to ONE account (or one highlight), wherever they sit in a reply.
     *
     * Instagram's own story page preloads other people's stories as well (the next accounts in the
     * tray), and ordinary posts and reels carry the same `video_versions` / `image_versions2` fields.
     * Accepting every such object returned strangers' content, so each item is kept only if its owner
     * is the requested account. An item that names a different owner is always rejected.
     */
    fun parseStoryItemsForOwner(
        json: String,
        username: String,
        userId: String? = null,
        highlightId: String? = null
    ): List<StoryItem> {
        val root = parseAny(json) ?: return emptyList()
        val found = ArrayList<StoryItem>()
        walk(root) { container ->
            val items = container.optJSONArray("items") ?: return@walk
            val containerOwner = container.optJSONObject("user") ?: container.optJSONObject("owner")
            val containerId = text(container, "id")
            val containerOk = when {
                highlightId != null ->
                    containerId == highlightId || containerId == "highlight:$highlightId"
                containerOwner != null -> ownerMatches(containerOwner, username, userId)
                else -> userId != null && containerId == userId
            }
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val itemOwner = item.optJSONObject("user") ?: item.optJSONObject("owner")
                val accepted = if (highlightId != null) containerOk
                else if (itemOwner != null) ownerMatches(itemOwner, username, userId)
                else containerOk
                if (accepted) itemToStory(item)?.let { found.add(it) }
            }
        }
        return found.distinctBy { it.id }.sortedBy { it.takenAtSec }
    }

    /**
     * The older `graphql/query` story reply (`data.reels_media[].items[]` with `display_url` / `video_url`
     * and `*_resources` lists). Used as one more fallback. Items are kept only if they belong to [username]/[userId].
     */
    fun parseLegacyStoryItems(json: String, username: String, userId: String?): List<StoryItem> {
        val root = try {
            org.json.JSONObject(json)
        } catch (e: Exception) {
            return emptyList()
        }
        val reels = root.optJSONObject("data")?.optJSONArray("reels_media") ?: return emptyList()
        val found = ArrayList<StoryItem>()
        for (r in 0 until reels.length()) {
            val items = reels.optJSONObject(r)?.optJSONArray("items") ?: continue
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                val owner = item.optJSONObject("owner") ?: item.optJSONObject("user")
                if (owner != null && !ownerMatches(owner, username, userId)) continue
                val id = text(item, "id").ifBlank { text(item, "pk") }
                if (id.isBlank()) continue
                val isVideo = item.optBoolean("is_video", false)
                val videoResources = item.optJSONArray("video_resources")
                val displayResources = item.optJSONArray("display_resources")
                fun lastSrc(a: org.json.JSONArray?): String =
                    if (a == null || a.length() == 0) "" else text(a.optJSONObject(a.length() - 1) ?: org.json.JSONObject(), "src")
                fun firstSrc(a: org.json.JSONArray?): String =
                    if (a == null || a.length() == 0) "" else text(a.optJSONObject(0) ?: org.json.JSONObject(), "src")
                val video = text(item, "video_url").ifBlank { lastSrc(videoResources) }
                val image = text(item, "display_url").ifBlank { lastSrc(displayResources) }
                val media = if (isVideo) video else image
                if (!media.startsWith("http")) continue
                found.add(
                    StoryItem(
                        id = id,
                        isVideo = isVideo,
                        mediaUrl = media,
                        thumbnailUrl = firstSrc(displayResources).ifBlank { image }.takeIf { it.startsWith("http") },
                        takenAtSec = item.optLong("taken_at_timestamp", 0L)
                    )
                )
            }
        }
        return found.distinctBy { it.id }.sortedBy { it.takenAtSec }
    }

    /** Pulls Instagram's two page tokens (needed to send its own GraphQL queries) out of any instagram.com page. */
    fun parsePageTokens(html: String): Pair<String, String>? {
        val lsd = Regex(""""LSD",\[\],\{"token":"([^"]+)"""").find(html)?.groupValues?.get(1)
        val dtsg = Regex(""""DTSGInitialData",\[\],\{"token":"([^"]+)"""").find(html)?.groupValues?.get(1)
        return if (lsd != null && dtsg != null) lsd to dtsg else null
    }

    /**
     * Reposts: media made by OTHER accounts, found anywhere in a reply. The profile owner's own posts are
     * skipped (they are not reposts), and carousels contribute their first picture or video.
     */
    fun parseRepostItems(json: String, profileUsername: String): List<StoryItem> {
        val root = parseAny(json) ?: return emptyList()
        val found = ArrayList<StoryItem>()
        walkWithParents(root, emptyList()) { obj, parents ->
            val mobileShape = obj.has("video_versions") || obj.has("image_versions2") || obj.has("carousel_media")
            val webShape = obj.has("shortcode") && obj.has("display_url")
            if (!(mobileShape || webShape) || !(obj.has("code") || obj.has("pk") || obj.has("shortcode"))) return@walkWithParents
            val ownerObj = obj.optJSONObject("user") ?: obj.optJSONObject("owner")
            val author = ownerObj?.let { text(it, "username") }.orEmpty()
            if (author.isBlank() || author.equals(profileUsername, ignoreCase = true)) return@walkWithParents
            var source = obj
            if (webShape && !obj.has("image_versions2")) {
                // The website's shape: translate to the common one
                source = org.json.JSONObject(obj.toString()).apply {
                    put("pk", text(obj, "id"))
                    put("code", text(obj, "shortcode"))
                    put("taken_at", obj.optLong("taken_at_timestamp", 0L))
                    put("image_versions2", org.json.JSONObject().put("candidates", org.json.JSONArray().put(org.json.JSONObject().put("url", text(obj, "display_url")))))
                    if (text(obj, "video_url").startsWith("http")) put("video_versions", org.json.JSONArray().put(org.json.JSONObject().put("url", text(obj, "video_url"))))
                    put("media_type", if (obj.optBoolean("is_video", false)) 2 else 1)
                    put("like_count", obj.optJSONObject("edge_liked_by")?.optLong("count", -1L) ?: -1L)
                    put("comment_count", obj.optJSONObject("edge_media_to_comment")?.optLong("count", -1L) ?: -1L)
                }
            } else if (!obj.has("video_versions") && !obj.has("image_versions2")) {
                val child = obj.optJSONArray("carousel_media")?.optJSONObject(0) ?: return@walkWithParents
                source = org.json.JSONObject(obj.toString()).apply {
                    child.opt("video_versions")?.let { put("video_versions", it) }
                    child.opt("image_versions2")?.let { put("image_versions2", it) }
                }
            }
            itemToStory(source)?.let {
                val meta = repostMeta(obj, parents)
                found.add(
                    it.copy(
                        author = author,
                        authorName = it.authorName ?: ownerObj?.let { o -> text(o, "full_name") }?.ifBlank { null },
                        repostedAtSec = meta.time ?: it.repostedAtSec,
                        repostNote = meta.note,
                        debug = "parents: " + parents.takeLast(3).joinToString(" > ") { p -> p.keys().asSequence().take(8).joinToString(",") } +
                            "\nreel keys: " + obj.keys().asSequence().take(25).joinToString(",") +
                            "\nrepost fields:\n" + meta.lines.joinToString("\n")
                    )
                )
            }
        }
        return found.distinctBy { it.id }
    }

    /** Finds the account with this username anywhere in a reply (photo, name, id...). */
    fun parseProfileDeep(json: String, username: String): ProfileInfo? {
        val root = parseAny(json) ?: return null
        var best: ProfileInfo? = null
        walk(root) { obj ->
            if (text(obj, "username").equals(username, ignoreCase = true)) {
                profileFrom(obj)?.let { candidate ->
                    val current = best
                    if (current == null || (current.picUrl == null && candidate.picUrl != null) ||
                        (current.fullName.isBlank() && candidate.fullName.isNotBlank())
                    ) best = candidate
                }
            }
        }
        return best
    }

    /** `accounts/edit/web_form_data` reply -> who the signed-in user is (no photo in this one). */
    fun parseEditForm(json: String, accountId: String): ProfileInfo? = try {
        val form = org.json.JSONObject(json).optJSONObject("form_data")
        val username = form?.let { text(it, "username") }.orEmpty()
        if (form == null || username.isBlank()) null
        else ProfileInfo(
            id = accountId.ifBlank { "0" },
            username = username,
            fullName = text(form, "first_name"),
            picUrl = null,
            isPrivate = form.optBoolean("is_private", false),
            isVerified = false,
            followers = null
        )
    } catch (e: Exception) {
        null
    }

    // ------------------------------------------------------------------------------------------
    // Full profile: details, posts, reels, tagged, followers and following
    // ------------------------------------------------------------------------------------------

    private fun profileDetailsFrom(user: org.json.JSONObject): ProfileDetails? {
        val base = profileFrom(user) ?: return null
        val hd = user.optJSONObject("hd_profile_pic_url_info")?.let { text(it, "url") }?.takeIf { it.startsWith("http") }
        val links = ArrayList<BioLink>()
        user.optJSONArray("bio_links")?.let { arr ->
            for (i in 0 until arr.length()) {
                val link = arr.optJSONObject(i) ?: continue
                val url = text(link, "url")
                if (url.startsWith("http")) links.add(BioLink(text(link, "title"), url))
            }
        }
        if (links.isEmpty() && base.externalUrl != null) links.add(BioLink("", base.externalUrl))
        val pronouns = ArrayList<String>()
        user.optJSONArray("pronouns")?.let { arr ->
            for (i in 0 until arr.length()) arr.optString(i).trim().takeIf { it.isNotEmpty() }?.let { pronouns.add(it) }
        }
        return ProfileDetails(
            id = base.id,
            username = base.username,
            fullName = base.fullName,
            picUrl = hd ?: base.picUrl,
            isPrivate = base.isPrivate,
            isVerified = base.isVerified,
            followers = base.followers,
            following = base.following,
            posts = base.posts,
            bio = base.bio,
            externalUrl = base.externalUrl,
            bioLinks = links,
            category = text(user, "category").ifBlank { text(user, "category_name") }.ifBlank { null },
            pronouns = pronouns,
            isBusiness = user.optBoolean("is_business", false) || user.optBoolean("is_business_account", false)
        )
    }

    /** Reply of `users/<id>/info/`. */
    fun parseProfileDetails(json: String): ProfileDetails? = try {
        org.json.JSONObject(json).optJSONObject("user")?.let { profileDetailsFrom(it) }
    } catch (e: Exception) {
        null
    }

    /** Reply of `users/web_profile_info/`. */
    fun parseProfileDetailsWeb(json: String): ProfileDetails? = try {
        org.json.JSONObject(json).optJSONObject("data")?.optJSONObject("user")?.let { profileDetailsFrom(it) }
    } catch (e: Exception) {
        null
    }

    /** When Instagram refuses the rich lookups, the basic account still gives a usable profile. */
    fun detailsFromBasic(p: ProfileInfo): ProfileDetails = ProfileDetails(
        id = p.id,
        username = p.username,
        fullName = p.fullName,
        picUrl = p.picUrl,
        isPrivate = p.isPrivate,
        isVerified = p.isVerified,
        followers = p.followers,
        following = p.following,
        posts = p.posts,
        bio = p.bio,
        externalUrl = p.externalUrl,
        bioLinks = if (p.externalUrl != null) listOf(BioLink("", p.externalUrl)) else emptyList(),
        category = null,
        pronouns = emptyList(),
        isBusiness = false
    )

    /** Instagram's own explanation inside an error reply (`{"message": "...", "status": "fail"}`), if it gave one. */
    fun parseErrorMessage(json: String): String? = try {
        org.json.JSONObject(json).let { o -> text(o, "message").ifBlank { text(o, "error_title") } }.ifBlank { null }
    } catch (e: Exception) {
        null
    }

    /** A grid thumbnail: the smallest picture that is still sharp at roughly a third of the screen. */
    private fun gridThumb(media: org.json.JSONObject): String? {
        val candidates = media.optJSONObject("image_versions2")?.optJSONArray("candidates") ?: return null
        var best: String? = null
        var bestWidth = Int.MAX_VALUE
        var widest: String? = null
        var widestWidth = -1
        for (i in 0 until candidates.length()) {
            val c = candidates.optJSONObject(i) ?: continue
            val url = text(c, "url").takeIf { it.startsWith("http") } ?: continue
            val w = c.optInt("width", 0)
            if (w >= 320 && w < bestWidth) { best = url; bestWidth = w }
            if (w > widestWidth) { widest = url; widestWidth = w }
        }
        return best ?: widest
    }

    private fun postFrom(m: org.json.JSONObject): PostItem? {
        val id = text(m, "pk").ifBlank { text(m, "id").substringBefore('_') }
        if (id.isBlank()) return null
        val code = text(m, "code").ifBlank { text(m, "shortcode") }.ifBlank { null }
        val caption = captionOf(m)
        val likes = m.optLong("like_count", -1L).takeIf { it >= 0 }
        val comments = m.optLong("comment_count", -1L).takeIf { it >= 0 }
        val plays = m.optLong("play_count", -1L).takeIf { it >= 0 } ?: m.optLong("view_count", -1L).takeIf { it >= 0 }
        val taken = m.optLong("taken_at", 0L)

        val slides = ArrayList<StoryItem>()
        val carousel = m.optJSONArray("carousel_media")
        if (carousel != null && carousel.length() > 0) {
            for (i in 0 until carousel.length()) {
                val child = carousel.optJSONObject(i) ?: continue
                itemToStory(child)?.let { slides.add(it) }
            }
        } else {
            itemToStory(m)?.let { slides.add(it) }
        }
        if (slides.isEmpty()) return null
        // Slides carry the post's own details so the viewer can show them
        val merged = slides.map {
            it.copy(
                code = code ?: it.code,
                caption = caption ?: it.caption,
                takenAtSec = if (it.takenAtSec > 0) it.takenAtSec else taken,
                likes = likes ?: it.likes,
                comments = comments ?: it.comments,
                plays = plays ?: it.plays
            )
        }
        val product = text(m, "product_type")
        val kind = when {
            merged.size > 1 || m.optInt("media_type", 0) == 8 || product == "carousel_container" -> PostKind.CAROUSEL
            product == "clips" -> PostKind.REEL
            merged[0].isVideo -> PostKind.VIDEO
            else -> PostKind.PHOTO
        }
        return PostItem(
            id = id,
            code = code,
            kind = kind,
            thumbUrl = gridThumb(m) ?: merged[0].thumbnailUrl,
            caption = caption,
            likes = likes,
            comments = comments,
            plays = plays,
            takenAtSec = taken,
            isPinned = m.optJSONObject("timeline_pin_info") != null,
            media = merged
        )
    }

    /** Reply of `feed/user/<id>/` or `usertags/<id>/feed/`: a page of posts. */
    fun parsePostsFeed(json: String): PostPage = try {
        val o = org.json.JSONObject(json)
        val arr = o.optJSONArray("items")
        val items = ArrayList<PostItem>()
        for (i in 0 until (arr?.length() ?: 0)) {
            arr?.optJSONObject(i)?.let { postFrom(it) }?.let { items.add(it) }
        }
        val next = text(o, "next_max_id").ifBlank { null }
        PostPage(items, if (o.optBoolean("more_available", false)) next else null)
    } catch (e: Exception) {
        PostPage(emptyList(), null)
    }

    /** Reply of `clips/user/`: a page of reels (each wrapped in a `media` object). */
    fun parseClipsFeed(json: String): PostPage = try {
        val o = org.json.JSONObject(json)
        val arr = o.optJSONArray("items")
        val items = ArrayList<PostItem>()
        for (i in 0 until (arr?.length() ?: 0)) {
            val entry = arr?.optJSONObject(i) ?: continue
            (entry.optJSONObject("media") ?: entry).let { postFrom(it) }?.let { items.add(it) }
        }
        val paging = o.optJSONObject("paging_info")
        val next = paging?.let { text(it, "max_id") }?.ifBlank { null }
        val more = paging?.optBoolean("more_available", false) ?: false
        PostPage(items, if (more) next else null)
    } catch (e: Exception) {
        PostPage(emptyList(), null)
    }

    /** Reply of `friendships/<id>/followers/` or `.../following/`. */
    fun parsePeople(json: String): PersonPage = try {
        val o = org.json.JSONObject(json)
        val arr = o.optJSONArray("users")
        val people = ArrayList<PersonRow>()
        for (i in 0 until (arr?.length() ?: 0)) {
            val u = arr?.optJSONObject(i) ?: continue
            val id = text(u, "pk").ifBlank { text(u, "pk_id") }.ifBlank { text(u, "id") }
            val name = text(u, "username")
            if (id.isBlank() || name.isBlank()) continue
            people.add(
                PersonRow(
                    id = id,
                    username = name,
                    fullName = text(u, "full_name"),
                    picUrl = text(u, "profile_pic_url").takeIf { it.startsWith("http") },
                    isVerified = u.optBoolean("is_verified", false),
                    isPrivate = u.optBoolean("is_private", false)
                )
            )
        }
        PersonPage(people, text(o, "next_max_id").ifBlank { null })
    } catch (e: Exception) {
        PersonPage(emptyList(), null)
    }

    // ------------------------------------------------------------------------------------------
    // Reading whatever the profile pages send: accounts and posts "anywhere" in a reply
    // ------------------------------------------------------------------------------------------

    private fun detailsScore(d: ProfileDetails): Int =
        (if (d.followers != null) 4 else 0) + (if (d.following != null) 1 else 0) + (if (d.posts != null) 1 else 0) +
            (if (d.picUrl != null) 2 else 0) + (if (d.bio.isNotBlank()) 2 else 0) + (if (d.fullName.isNotBlank()) 1 else 0) +
            d.bioLinks.size

    /** The one with more information. */
    fun betterDetails(a: ProfileDetails?, b: ProfileDetails?): ProfileDetails? = when {
        a == null -> b
        b == null -> a
        detailsScore(b) > detailsScore(a) -> b
        else -> a
    }

    /** The fullest account object for [username] found anywhere in a reply (any shape Instagram uses). */
    fun parseDetailsDeep(json: String, username: String): ProfileDetails? {
        val root = parseAny(json) ?: return null
        var best: ProfileDetails? = null
        walk(root) { obj ->
            if (text(obj, "username").equals(username, ignoreCase = true)) {
                best = betterDetails(best, profileDetailsFrom(obj))
            }
        }
        return best
    }

    /** A post in the website's older GraphQL shape (`shortcode`, `display_url`, `edge_*`). */
    private fun postFromWebNode(n: org.json.JSONObject): PostItem? {
        val id = text(n, "id")
        val code = text(n, "shortcode")
        if (id.isBlank() || code.isBlank()) return null
        val caption = n.optJSONObject("edge_media_to_caption")?.optJSONArray("edges")?.optJSONObject(0)
            ?.optJSONObject("node")?.let { text(it, "text") }?.ifBlank { null }
        val likes = (n.optJSONObject("edge_media_preview_like") ?: n.optJSONObject("edge_liked_by"))
            ?.optLong("count", -1L)?.takeIf { it >= 0 }
        val comments = (n.optJSONObject("edge_media_to_comment") ?: n.optJSONObject("edge_media_preview_comment"))
            ?.optLong("count", -1L)?.takeIf { it >= 0 }
        val plays = n.optLong("video_view_count", -1L).takeIf { it >= 0 }
        val taken = n.optLong("taken_at_timestamp", 0L)

        fun slide(node: org.json.JSONObject): StoryItem? {
            val display = text(node, "display_url")
            if (!display.startsWith("http")) return null
            val video = node.optBoolean("is_video", false)
            val videoUrl = text(node, "video_url")
            val sid = text(node, "id").ifBlank { id }
            return StoryItem(
                id = sid,
                isVideo = video,
                mediaUrl = if (video && videoUrl.startsWith("http")) videoUrl else display,
                thumbnailUrl = display,
                takenAtSec = taken,
                caption = caption,
                code = code,
                likes = likes,
                comments = comments,
                plays = plays,
                // A video whose address wasn't in the list reply has to be looked up before it can play or save
                needsResolve = video && !videoUrl.startsWith("http")
            )
        }

        val children = n.optJSONObject("edge_sidecar_to_children")?.optJSONArray("edges")
        val slides = ArrayList<StoryItem>()
        if (children != null && children.length() > 0) {
            for (i in 0 until children.length()) children.optJSONObject(i)?.optJSONObject("node")?.let { slide(it) }?.let { slides.add(it) }
        } else {
            slide(n)?.let { slides.add(it) }
        }
        if (slides.isEmpty()) return null
        val kind = when {
            slides.size > 1 -> PostKind.CAROUSEL
            text(n, "product_type") == "clips" -> PostKind.REEL
            slides[0].isVideo -> PostKind.VIDEO
            else -> PostKind.PHOTO
        }
        return PostItem(
            id = id, code = code, kind = kind, thumbUrl = text(n, "display_url").takeIf { it.startsWith("http") },
            caption = caption, likes = likes, comments = comments, plays = plays, takenAtSec = taken,
            isPinned = n.optJSONArray("pinned_for_users")?.let { it.length() > 0 } ?: false, media = slides
        )
    }

    /**
     * Every post found anywhere in a reply. [owner], when given, keeps only that account's own posts
     * (so "suggested" content on the same page is left out).
     */
    fun parsePostsDeep(json: String, owner: String?): List<PostItem> {
        val root = parseAny(json) ?: return emptyList()
        val out = ArrayList<PostItem>()
        walk(root) { obj ->
            val hasCode = text(obj, "code").isNotBlank() || text(obj, "shortcode").isNotBlank()
            if (hasCode) {
                val looksV1 = obj.has("image_versions2") || obj.has("video_versions") || obj.has("carousel_media")
                val looksWeb = obj.has("display_url") && obj.has("shortcode")
                if (looksV1 || looksWeb) {
                    val by = (obj.optJSONObject("user") ?: obj.optJSONObject("owner"))?.let { text(it, "username") }.orEmpty()
                    if (owner == null || by.isBlank() || by.equals(owner, ignoreCase = true)) {
                        val post = if (looksV1) postFrom(obj) else postFromWebNode(obj)
                        if (post != null) out.add(post)
                    }
                }
            }
        }
        return out
    }
}
