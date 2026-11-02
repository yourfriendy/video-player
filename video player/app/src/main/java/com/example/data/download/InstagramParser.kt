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
        val needsResolve: Boolean = false
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
        repostedAtSec = full.repostedAtSec ?: old.repostedAtSec,
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
        walk(root) { obj ->
            val mobileShape = obj.has("video_versions") || obj.has("image_versions2") || obj.has("carousel_media")
            val webShape = obj.has("shortcode") && obj.has("display_url")
            if (!(mobileShape || webShape) || !(obj.has("code") || obj.has("pk") || obj.has("shortcode"))) return@walk
            val ownerObj = obj.optJSONObject("user") ?: obj.optJSONObject("owner")
            val author = ownerObj?.let { text(it, "username") }.orEmpty()
            if (author.isBlank() || author.equals(profileUsername, ignoreCase = true)) return@walk
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
                val child = obj.optJSONArray("carousel_media")?.optJSONObject(0) ?: return@walk
                source = org.json.JSONObject(obj.toString()).apply {
                    child.opt("video_versions")?.let { put("video_versions", it) }
                    child.opt("image_versions2")?.let { put("image_versions2", it) }
                }
            }
            itemToStory(source)?.let {
                found.add(it.copy(author = author, authorName = it.authorName ?: ownerObj?.let { o -> text(o, "full_name") }?.ifBlank { null }))
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
}
