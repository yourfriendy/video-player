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
    private val fetcher: IgWebFetcher
) {
    /** An HTTP answer we couldn't use. */
    private class HttpFailure(val code: Int, message: String) : Exception(message)

    companion object {
        private const val WEB_BASE = "https://www.instagram.com"

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
     * Asks Instagram who the saved session belongs to. This is the proof that requests really reach
     * Instagram AND are treated as signed in. If the first kind of request is treated as signed out,
     * the other kind is tried before giving up.
     */
    suspend fun whoAmI(): InstagramParser.ProfileInfo = withContext(Dispatchers.IO) {
        val cookies = sessionCookies()
        if (cookies == null || !cookies.contains("sessionid=")) {
            throw LoginRequiredException("Sign in to Instagram to view stories.")
        }
        val csrf = Regex("""csrftoken=([^;]+)""").find(cookies)?.groupValues?.get(1).orEmpty()
        verifySession(cookies, csrf)
    }

    private suspend fun verifySession(cookies: String, csrf: String): InstagramParser.ProfileInfo {
        val url = "$WEB_BASE/api/v1/accounts/current_user/?edit=true"
        var (code, text) = rawGet(url, cookies, csrf)
        fun signedOut() = code == 401 || text.contains("login_required")
        if (signedOut()) {
            fetcher.switchOrigin()
            val retry = rawGet(url, cookies, csrf)
            code = retry.first
            text = retry.second
        }
        if (code == -1) throw DownloadException("Couldn't reach Instagram. Check your internet connection and try again.")
        if (text.contains("checkpoint_required") || text.contains("challenge_required")) {
            throw DownloadException("Instagram wants to verify your account. Open the Instagram app, confirm it's you, then try again.")
        }
        if (signedOut()) {
            throw LoginRequiredException("Instagram says this session is signed out. Please sign in again.")
        }
        return InstagramParser.parseCurrentUser(text)
            ?: throw DownloadException(
                "Instagram answered (HTTP $code) but didn't say who is signed in: ${InstagramParser.describeReply(text)}."
            )
    }

    suspend fun load(target: InstagramParser.StoryTarget): StoryResult = withContext(Dispatchers.IO) {
        val cookies = sessionCookies()
        if (cookies == null || !cookies.contains("sessionid=")) {
            throw LoginRequiredException("Sign in to Instagram to view stories.")
        }
        val csrf = Regex("""csrftoken=([^;]+)""").find(cookies)?.groupValues?.get(1).orEmpty()

        try {
            val me = verifySession(cookies, csrf)
            when (target) {
                is InstagramParser.StoryTarget.Highlight -> {
                    val items = fetchItems("highlight:${target.id}", cookies, csrf)
                    if (items.isEmpty()) throw DownloadException("This highlight is empty or unavailable.")
                    StoryResult("Highlight", items, null, me)
                }

                is InstagramParser.StoryTarget.User -> {
                    val profile = lookupProfile(target.username, cookies, csrf)
                    val items = fetchItems(profile.id, cookies, csrf)
                    if (items.isEmpty()) throw DownloadException(noStoriesMessage(profile, target.username))
                    StoryResult(profile.username.ifBlank { target.username }, items, profile, me)
                }

                is InstagramParser.StoryTarget.Story -> {
                    val profile = lookupProfile(target.username, cookies, csrf)
                    val items = fetchItems(profile.id, cookies, csrf)
                    val match = items.filter { it.id == target.mediaId }
                    if (match.isEmpty()) {
                        throw DownloadException(
                            if (items.isEmpty()) noStoriesMessage(profile, target.username)
                            else "That story is no longer available. Try the username to see all current stories."
                        )
                    }
                    StoryResult(profile.username.ifBlank { target.username }, match, profile, me)
                }
            }
        } catch (e: IOException) {
            throw DownloadException("Couldn't reach Instagram. Check your internet connection and try again.")
        }
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
                throw DownloadException("Instagram wants to verify your account. Open the Instagram app, confirm it's you, then try again.")
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

    private suspend fun fetchItems(reelId: String, cookies: String, csrf: String): List<InstagramParser.StoryItem> {
        val encoded = java.net.URLEncoder.encode(reelId, "UTF-8")
        val (code, text) = rawGet("$WEB_BASE/api/v1/feed/reels_media/?reel_ids=$encoded", cookies, csrf)
        when {
            code == -1 -> throw DownloadException("Couldn't reach Instagram. Check your internet connection and try again.")
            text.contains("checkpoint_required") || text.contains("challenge_required") ->
                throw DownloadException("Instagram wants to verify your account. Open the Instagram app, confirm it's you, then try again.")
            code == 401 || text.contains("login_required") ->
                throw LoginRequiredException("Your Instagram session has expired. Please sign in again.")
            code == 429 -> throw DownloadException(
                "Instagram refused the story list request (error 429). Switching between Wi-Fi and mobile data often fixes this."
            )
            code !in 200..299 -> throw DownloadException(
                "Instagram refused the story list request (error $code): ${InstagramParser.describeReply(text)}."
            )
        }
        return InstagramParser.parseStoryItems(text)
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
