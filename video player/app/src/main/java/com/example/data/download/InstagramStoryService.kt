package com.example.data.download

import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** The saved Instagram session is missing or no longer valid: the user has to sign in again. */
class LoginRequiredException(message: String) : Exception(message)

data class StoryResult(
    val owner: String,
    val items: List<InstagramParser.StoryItem>,
    /** The account that was looked up (photo, name, username), or null for highlights. */
    val profile: InstagramParser.ProfileInfo?,
    /** The signed-in account, proving the session works. */
    val signedInAs: InstagramParser.ProfileInfo?
)

/**
 * Loads someone's active stories using the signed-in user's OWN Instagram session.
 *
 * Stories are never public, so Instagram only returns them to a logged-in account. The user signs
 * in on Instagram's own page (in a WebView); this class only reuses the resulting session cookie
 * and sees exactly what that account could already see in the Instagram app. The password is never
 * visible to OffScroll.
 */
class InstagramStoryService(
    private val client: OkHttpClient,
    private val userAgent: String,
    private val fetcher: IgWebFetcher,
    private val pageCapture: IgPageCapture
) {
    /** An HTTP answer we couldn't use. */
    private class HttpFailure(val code: Int, message: String) : Exception(message)

    companion object {
        private const val WEB_BASE = "https://www.instagram.com"

        /** The mobile-app API host. Since Oct 2026 the www host answers story-list requests with HTTP 500. */
        private const val MOBILE_BASE = "https://i.instagram.com"

        /** The persisted query Instagram's own story page uses (PolarisStoriesV3ReelPageStandaloneQuery). It can rotate. */
        private const val STORY_DOC_ID = "28449850404637481"
        private const val STORY_QUERY_NAME = "PolarisStoriesV3ReelPageStandaloneQuery"

        /** The older story query (also used by Instaloader). */
        private const val LEGACY_STORY_HASH = "303a4ae99711322310f25250d988f3b7"

        /** Instagram's public web-app id (sent by the website itself on every request). */
        private const val IG_APP_ID = "936619743392459"

        fun sessionCookies(): String? = try {
            CookieManager.getInstance().getCookie(WEB_BASE)
        } catch (e: Exception) {
            null
        }

        fun isSignedIn(): Boolean = sessionCookies()?.contains("sessionid=") == true

        fun signOut() {
            try {
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Asks Instagram who the saved session belongs to, trying three different endpoints (Instagram
     * refuses some of them from some connections). This is a nicety for the UI: the real work never
     * depends on it.
     */
    suspend fun whoAmI(): InstagramParser.ProfileInfo = withContext(Dispatchers.IO) {
        val cookies = sessionCookies()
        if (cookies == null || !cookies.contains("sessionid=")) {
            throw LoginRequiredException("Sign in to Instagram to view stories.")
        }
        val csrf = Regex("""csrftoken=([^;]+)""").find(cookies)?.groupValues?.get(1).orEmpty()
        identify(cookies, csrf)
    }

    private suspend fun identify(cookies: String, csrf: String): InstagramParser.ProfileInfo {
        val accountId = Regex("""ds_user_id=(\d+)""").find(cookies)?.groupValues?.get(1).orEmpty()
        val notes = ArrayList<String>()
        var signedOutAnswers = 0

        suspend fun tryEndpoint(label: String, url: String, parse: (String) -> InstagramParser.ProfileInfo?): InstagramParser.ProfileInfo? {
            val (code, text) = rawGet(url, cookies, csrf)
            if (code == 401 || text.contains("login_required")) signedOutAnswers++
            if (code == -1) throw DownloadException("Couldn't reach Instagram. Check your internet connection and try again.")
            if (text.contains("checkpoint_required") || text.contains("challenge_required")) {
                throw VerificationRequiredException("Instagram wants to verify your account. Open the Instagram app, confirm it's you, then try again.")
            }
            val parsed = if (code in 200..299) parse(text) else null
            if (parsed == null) notes.add("$label: HTTP $code, ${InstagramParser.describeReply(text)}")
            return parsed
        }

        val endpoints = ArrayList<Triple<String, String, (String) -> InstagramParser.ProfileInfo?>>()
        if (accountId.isNotBlank()) {
            endpoints.add(
                Triple("profile", "$WEB_BASE/api/v1/users/$accountId/info/", { body: String -> InstagramParser.parseCurrentUser(body) })
            )
        }
        endpoints.add(
            Triple("edit form", "$WEB_BASE/api/v1/accounts/edit/web_form_data/", { body: String -> InstagramParser.parseEditForm(body, accountId) })
        )
        endpoints.add(
            Triple("session", "$WEB_BASE/api/v1/accounts/current_user/?edit=true", { body: String -> InstagramParser.parseCurrentUser(body) })
        )

        for ((label, url, parse) in endpoints) {
            tryEndpoint(label, url, parse)?.let { return it }
        }
        if (signedOutAnswers == endpoints.size) {
            throw LoginRequiredException("Instagram says this session is signed out. Please sign in again.")
        }
        throw DownloadException("Connected, but Instagram wouldn't show your profile (" + notes.joinToString("; ") + "). Stories may still work.")
    }

    suspend fun load(target: InstagramParser.StoryTarget): StoryResult = withContext(Dispatchers.IO) {
        val cookies = sessionCookies()
        if (cookies == null || !cookies.contains("sessionid=")) {
            throw LoginRequiredException("Sign in to Instagram to view stories.")
        }
        val csrf = Regex("""csrftoken=([^;]+)""").find(cookies)?.groupValues?.get(1).orEmpty()

        // Method 1: ask Instagram's API directly.
        val directFailure: DownloadException = try {
            return@withContext loadDirect(target, cookies, csrf)
        } catch (e: LoginRequiredException) {
            throw e
        } catch (e: VerificationRequiredException) {
            throw e
        } catch (e: DownloadException) {
            e
        } catch (e: IOException) {
            DownloadException("Couldn't reach Instagram. Check your internet connection and try again.")
        }

        // Method 2: let Instagram's own page ask, and read what it receives.
        try {
            loadViaPage(target)
        } catch (e: NoStoriesException) {
            throw e
        } catch (e: DownloadException) {
            if (directFailure is NoStoriesException) throw directFailure
            throw DownloadException(
                "Couldn't load stories.\n\n" +
                    "Asking Instagram directly: ${directFailure.message}\n\n" +
                    "Reading Instagram's own page: ${e.message}"
            )
        }
    }

    private suspend fun loadDirect(
        target: InstagramParser.StoryTarget,
        cookies: String,
        csrf: String
    ): StoryResult {
        try {
            return when (target) {
                is InstagramParser.StoryTarget.Highlight -> {
                    val items = fetchStoryReel("highlight:${target.id}", null, null, null, target.id)
                    if (items.isEmpty()) throw NoStoriesException("This highlight is empty or unavailable.")
                    StoryResult("Highlight", items, null, null)
                }

                is InstagramParser.StoryTarget.User -> {
                    val profile = lookupProfile(target.username, cookies, csrf)
                    val items = fetchStoryReel(profile.id, null, target.username, profile.id, null)
                    if (items.isEmpty()) throw NoStoriesException(noStoriesMessage(profile, target.username))
                    StoryResult(profile.username.ifBlank { target.username }, items, profile, null)
                }

                is InstagramParser.StoryTarget.Story -> {
                    val profile = lookupProfile(target.username, cookies, csrf)
                    var items = fetchStoryReel(profile.id, target.mediaId, target.username, profile.id, null)
                    if (items.none { it.id == target.mediaId }) {
                        // Not in the list: ask for that one story directly
                        items = items + fetchSingleStory(target.mediaId, target.username, profile.id)
                    }
                    val match = items.filter { it.id == target.mediaId }
                    if (match.isEmpty()) {
                        throw NoStoriesException(
                            if (items.isEmpty()) noStoriesMessage(profile, target.username)
                            else "That story is no longer available. Try the username to see all current stories."
                        )
                    }
                    StoryResult(profile.username.ifBlank { target.username }, match, profile, null)
                }
            }
        } catch (e: IOException) {
            throw DownloadException("Couldn't reach Instagram. Check your internet connection and try again.")
        }
    }

    /** Opens the story page like a visitor would and picks the stories out of the replies the page receives. */
    private suspend fun loadViaPage(target: InstagramParser.StoryTarget): StoryResult {
        val (pageUrl, username) = when (target) {
            is InstagramParser.StoryTarget.User -> "$WEB_BASE/stories/${target.username}/" to target.username
            is InstagramParser.StoryTarget.Story -> "$WEB_BASE/stories/${target.username}/${target.mediaId}/" to target.username
            is InstagramParser.StoryTarget.Highlight -> "$WEB_BASE/stories/highlights/${target.id}/" to ""
        }
        val highlightId = (target as? InstagramParser.StoryTarget.Highlight)?.id
        val result = pageCapture.capture(pageUrl) { replies ->
            replies.any {
                it.status in 200..299 &&
                    InstagramParser.parseStoryItemsForOwner(it.body, username, null, highlightId).isNotEmpty()
            }
        }
        val profile = if (username.isNotBlank()) {
            result.replies.firstNotNullOfOrNull { InstagramParser.parseProfileDeep(it.body, username) }
        } else null
        // Only THIS account's stories: the page also preloads other people's, which must never show up here
        var items = result.replies
            .filter { it.status in 200..299 }
            .flatMap { InstagramParser.parseStoryItemsForOwner(it.body, username, profile?.id, highlightId) }
            .distinctBy { it.id }
            .sortedBy { it.takenAtSec }

        if (target is InstagramParser.StoryTarget.Story && items.isNotEmpty()) {
            val match = items.filter { it.id == target.mediaId }
            items = match
            if (items.isEmpty()) throw NoStoriesException("That story is no longer available. Try the username to see all current stories.")
        }
        if (items.isEmpty()) {
            val seen = result.replies.take(4).joinToString("; ") {
                it.url.substringBefore('?').substringAfter("instagram.com").take(40) +
                    " → HTTP ${it.status}, ${InstagramParser.describeReply(it.body)}"
            }
            throw DownloadException(
                (result.pageNote ?: "the page loaded") + ", but no stories came with it" +
                    (if (seen.isNotBlank()) " (it received: $seen)." else " (it received no data at all).")
            )
        }
        return StoryResult(profile?.username?.ifBlank { username } ?: username.ifBlank { "Highlight" }, items, profile, null)
    }

    /**
     * (Beta) A person's Reposts tab. There is no documented address for it, so, like the stories fallback,
     * their real Reposts page is opened in the hidden browser and what that page receives is read.
     * Only other people's media is kept (the owner's own posts are not reposts).
     */
    suspend fun loadReposts(username: String): StoryResult = withContext(Dispatchers.IO) {
        val result = pageCapture.capture("$WEB_BASE/${java.net.URLEncoder.encode(username, "UTF-8")}/reposts/") { replies ->
            replies.any {
                it.status in 200..299 && it.url.contains("repost", ignoreCase = true) &&
                    InstagramParser.parseRepostItems(it.body, username).isNotEmpty()
            }
        }
        val ok = result.replies.filter { it.status in 200..299 }
        var items = ok.filter { it.url.contains("repost", ignoreCase = true) }
            .flatMap { InstagramParser.parseRepostItems(it.body, username) }
        if (items.isEmpty()) {
            // Some pages ship the first batch inside the page itself
            items = ok.filter { it.url.startsWith("embedded-page-data") }
                .flatMap { InstagramParser.parseRepostItems(it.body, username) }
        }
        items = items.distinctBy { it.id }
        val profile = result.replies.firstNotNullOfOrNull { InstagramParser.parseProfileDeep(it.body, username) }
        if (items.isEmpty()) {
            val seen = result.replies.take(5).joinToString("; ") {
                it.url.substringBefore('?').substringAfter("instagram.com").take(34) + " → HTTP ${it.status}"
            }
            throw NoStoriesException(
                "No reposts found for @$username. They may have none, or the account may be private." +
                    (if (seen.isNotBlank()) "\n\n(The page received: $seen)" else "")
            )
        }
        StoryResult(username, items, profile, null)
    }

    private fun noStoriesMessage(profile: InstagramParser.ProfileInfo, typed: String): String {
        val name = profile.username.ifBlank { typed }
        return if (profile.isPrivate) {
            "@$name is a private account. Their stories are only visible to people who follow them."
        } else {
            "@$name has no active stories right now."
        }
    }

    /**
     * Finds the account. Instagram sometimes refuses one lookup method but not another, so three are
     * tried, and if all fail the message says exactly what Instagram answered at each step.
     */
    private suspend fun lookupProfile(username: String, cookies: String, csrf: String): InstagramParser.ProfileInfo {
        val encoded = java.net.URLEncoder.encode(username, "UTF-8")
        val notes = ArrayList<String>()

        suspend fun step(
            label: String,
            url: String,
            parse: (String) -> InstagramParser.ProfileInfo?
        ): InstagramParser.ProfileInfo? {
            val (code, text) = rawGet(url, cookies, csrf)
            if (code == -1) throw DownloadException("Couldn't reach Instagram. Check your internet connection and try again.")
            if (text.contains("checkpoint_required") || text.contains("challenge_required")) {
                throw VerificationRequiredException("Instagram wants to verify your account. Open the Instagram app, confirm it's you, then try again.")
            }
            if (code == 401 || text.contains("login_required")) {
                throw LoginRequiredException("Your Instagram session has expired. Please sign in again.")
            }
            val parsed = if (code in 200..299) parse(text) else null
            if (parsed == null) notes.add("$label: HTTP $code, ${InstagramParser.describeReply(text)}")
            return parsed
        }

        step("1) account lookup", "$WEB_BASE/api/v1/users/web_profile_info/?username=$encoded") {
            InstagramParser.parseProfile(it)
        }?.let { return it }
        step("2) search", "$WEB_BASE/api/v1/fbsearch/web/top_search/?context=blended&query=$encoded&include_reel=true") {
            InstagramParser.parseProfileFromSearch(it, username)
        }?.let { return it }
        step("3) profile page", "$WEB_BASE/$encoded/") { html ->
            InstagramParser.parseUserIdFromProfileHtml(html, username)?.let {
                InstagramParser.ProfileInfo(it, username, "", null, false, false, null)
            }
        }?.let { return it }

        throw DownloadException(
            "Couldn't find @$username. Check the spelling.\n\nIf it is spelled right, this is what Instagram answered:\n" +
                notes.joinToString("\n")
        )
    }

    // --- story list: four doors, in the order the maintained open-source tools use them -----------------

    @Volatile
    private var pageTokens: Triple<String, String, Long>? = null

    /** Instagram's two page tokens (needed to send its own GraphQL queries), read from its home page. */
    private suspend fun tokens(forceRefresh: Boolean = false): Pair<String, String>? {
        val cached = pageTokens
        if (!forceRefresh && cached != null && System.currentTimeMillis() - cached.third < 20 * 60_000L) {
            return cached.first to cached.second
        }
        val answer = try {
            fetcher.request("$WEB_BASE/", headers = mapOf("Accept" to "text/html"))
        } catch (e: Exception) {
            return null
        }
        if (answer.status !in 200..299) return null
        val found = InstagramParser.parsePageTokens(answer.body) ?: return null
        pageTokens = Triple(found.first, found.second, System.currentTimeMillis())
        return found
    }

    private fun enc(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")

    private fun hasDataReply(text: String): Boolean = try {
        val o = org.json.JSONObject(text)
        !o.isNull("data") && !o.has("errors")
    } catch (e: Exception) {
        false
    }

    /**
     * Loads one account's (or highlight's) story list.
     *  1. the mobile API   2. Instagram's own story-page query   3. the older Instaloader query
     * Instagram answers each of them differently for different sessions, so the next is tried whenever a
     * door is shut or gives an empty list. The list is only declared empty if the second door answered properly.
     */
    private suspend fun fetchStoryReel(
        reelId: String,
        mediaId: String?,
        owner: String?,
        ownerId: String?,
        highlightId: String?
    ): List<InstagramParser.StoryItem> {
        val notes = ArrayList<String>()
        val minimal = mapOf("Accept" to "*/*", "X-IG-App-ID" to IG_APP_ID)
        var properlyEmpty = false
        var loginAnswers = 0
        var attempts = 0

        fun parse(text: String, fallbackToPlain: Boolean): List<InstagramParser.StoryItem> {
            val scoped = InstagramParser.parseStoryItemsForOwner(text, owner.orEmpty(), ownerId, highlightId)
            return if (scoped.isNotEmpty() || !fallbackToPlain) scoped else InstagramParser.parseStoryItems(text)
        }

        suspend fun send(label: String, url: String, method: String, headers: Map<String, String>, body: String?): Pair<Int, String> {
            attempts++
            val (code, text) = rawRequest(url, method, headers, body)
            if (code == -1) throw DownloadException("Couldn't reach Instagram. Check your internet connection and try again.")
            if (text.contains("checkpoint_required") || text.contains("challenge_required")) {
                throw VerificationRequiredException("Instagram wants to verify your account. Open the Instagram app, confirm it's you, then try again.")
            }
            if (code == 401 || text.contains("login_required")) loginAnswers++
            return code to text
        }

        // 1) the mobile API
        run {
            val (code, text) = send("mobile", "$MOBILE_BASE/api/v1/feed/reels_media/?reel_ids=${enc(reelId)}", "GET", minimal, null)
            val items = if (code in 200..299) parse(text, true) else emptyList()
            if (items.isNotEmpty()) return items
            notes.add("1) mobile list: HTTP $code, ${InstagramParser.describeReply(text)}")
        }

        // 2) Instagram's own story-page query
        run {
            val t = tokens()
            if (t == null) {
                notes.add("2) story query: couldn't read Instagram's page tokens")
                return@run
            }
            val variables = org.json.JSONObject()
                .put("media_id", mediaId ?: org.json.JSONObject.NULL)
                .put("reel_ids_arr", org.json.JSONArray().put(reelId))
                .put("__relay_internal__pv__PolarisCommunityNoteStoriesLabelEnabledrelayprovider", true)
                .toString()
            val body = listOf(
                "doc_id" to STORY_DOC_ID,
                "variables" to variables,
                "lsd" to t.first,
                "fb_dtsg" to t.second,
                "server_timestamps" to "true",
                "fb_api_caller_class" to "RelayModern",
                "fb_api_req_friendly_name" to STORY_QUERY_NAME
            ).joinToString("&") { enc(it.first) + "=" + enc(it.second) }
            val headers = mapOf(
                "Accept" to "*/*",
                "X-IG-App-ID" to IG_APP_ID,
                "Content-Type" to "application/x-www-form-urlencoded",
                "X-CSRFToken" to "{csrf}",
                "X-FB-Friendly-Name" to STORY_QUERY_NAME,
                "X-FB-LSD" to t.first
            )
            val (code, text) = send("story query", "$WEB_BASE/api/graphql", "POST", headers, body)
            val items = if (code in 200..299) parse(text, false) else emptyList()
            if (items.isNotEmpty()) return items
            if (code in 200..299 && hasDataReply(text)) properlyEmpty = true
            if (code !in 200..299) pageTokens = null // tokens may have gone stale: read them again next time
            notes.add("2) story query: HTTP $code, ${InstagramParser.describeReply(text)}")
        }

        // 3) the older query
        if (highlightId == null) {
            val variables = org.json.JSONObject()
                .put("reel_ids", org.json.JSONArray().put(reelId))
                .put("precomposed_overlay", false)
                .toString()
            val (code, text) = send(
                "legacy", "$WEB_BASE/graphql/query/?query_hash=$LEGACY_STORY_HASH&variables=${enc(variables)}",
                "GET", minimal, null
            )
            val items = if (code in 200..299) InstagramParser.parseLegacyStoryItems(text, owner.orEmpty(), ownerId) else emptyList()
            if (items.isNotEmpty()) return items
            notes.add("3) older query: HTTP $code, ${InstagramParser.describeReply(text)}")
        }

        if (properlyEmpty) return emptyList()
        if (attempts > 0 && loginAnswers == attempts) {
            throw LoginRequiredException("Your Instagram session has expired. Please sign in again.")
        }
        throw DownloadException("Instagram wouldn't list the stories:\n" + notes.joinToString("\n"))
    }

    /** Last door, for a story link: ask for that one story by its id. */
    private suspend fun fetchSingleStory(mediaId: String, owner: String, ownerId: String): List<InstagramParser.StoryItem> {
        val minimal = mapOf("Accept" to "*/*", "X-IG-App-ID" to IG_APP_ID)
        val (code, text) = try {
            rawRequest("$MOBILE_BASE/api/v1/media/${enc(mediaId)}/info/", "GET", minimal, null)
        } catch (e: DownloadException) {
            return emptyList()
        }
        if (code !in 200..299) return emptyList()
        return InstagramParser.parseStoryItemsForOwner(text, owner, ownerId)
    }

    /** One request as the signed-in user via the hidden browser; plain HTTP only if the browser itself is unavailable. */
    private suspend fun rawRequest(url: String, method: String, headers: Map<String, String>, body: String?): Pair<Int, String> {
        val answer = try {
            fetcher.request(url, method, headers, body)
        } catch (e: DownloadException) {
            throw e
        } catch (e: Exception) {
            null
        }
        return if (answer != null) answer.status to answer.body else -1 to ""
    }

    /** One GET as the signed-in user: via the hidden browser, or plain HTTP only if the browser itself is unavailable. */
    private suspend fun rawGet(url: String, cookies: String, csrf: String): Pair<Int, String> {
        val answer = try {
            fetcher.get(url)
        } catch (e: DownloadException) {
            throw e
        } catch (e: Exception) {
            null
        }
        return if (answer != null) answer.status to answer.body else plainGet(url, cookies, csrf)
    }

    private fun plainGet(url: String, cookies: String, csrf: String): Pair<Int, String> {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Cookie", cookies)
            .header("X-CSRFToken", csrf)
            .header("X-IG-App-ID", IG_APP_ID)
            .header("X-Requested-With", "XMLHttpRequest")
            .header("Referer", "$WEB_BASE/")
            .header("Accept", "*/*")
            .build()
        return try {
            client.newCall(request).execute().use { it.code to (it.body?.string().orEmpty()) }
        } catch (e: IOException) {
            -1 to ""
        }
    }
}
