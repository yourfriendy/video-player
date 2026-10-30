package com.example.data.download

import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/** The saved Instagram session is missing or no longer valid: the user has to sign in again. */
class LoginRequiredException(message: String) : Exception(message)

data class StoryResult(val owner: String, val items: List<InstagramParser.StoryItem>)

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
    private val userAgent: String
) {
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

    suspend fun load(target: InstagramParser.StoryTarget): StoryResult = withContext(Dispatchers.IO) {
        val cookies = sessionCookies()
        if (cookies == null || !cookies.contains("sessionid=")) {
            throw LoginRequiredException("Sign in to Instagram to view stories.")
        }
        val csrf = Regex("""csrftoken=([^;]+)""").find(cookies)?.groupValues?.get(1).orEmpty()

        try {
            when (target) {
                is InstagramParser.StoryTarget.Highlight -> {
                    val items = fetchItems("highlight:${target.id}", cookies, csrf)
                    if (items.isEmpty()) throw DownloadException("This highlight is empty or unavailable.")
                    StoryResult("Highlight", items)
                }

                is InstagramParser.StoryTarget.User -> {
                    val items = fetchItems(userId(target.username, cookies, csrf), cookies, csrf)
                    if (items.isEmpty()) {
                        throw DownloadException("@${target.username} has no active stories right now.")
                    }
                    StoryResult(target.username, items)
                }

                is InstagramParser.StoryTarget.Story -> {
                    val items = fetchItems(userId(target.username, cookies, csrf), cookies, csrf)
                    val match = items.filter { it.id == target.mediaId }
                    if (match.isEmpty()) {
                        throw DownloadException(
                            if (items.isEmpty()) "That story has expired (stories disappear after 24 hours)."
                            else "That story is no longer available. Try the username to see all current stories."
                        )
                    }
                    StoryResult(target.username, match)
                }
            }
        } catch (e: IOException) {
            throw DownloadException("Couldn't reach Instagram. Check your internet connection and try again.")
        }
    }

    private fun userId(username: String, cookies: String, csrf: String): String {
        val body = get("$WEB_BASE/api/v1/users/web_profile_info/?username=$username", cookies, csrf, notFoundMessage = "No Instagram account called @$username.")
        return InstagramParser.parseUserId(body)
            ?: throw DownloadException("Couldn't find @$username. Check the spelling, or the account may be unavailable.")
    }

    private fun fetchItems(reelId: String, cookies: String, csrf: String): List<InstagramParser.StoryItem> {
        val encoded = java.net.URLEncoder.encode(reelId, "UTF-8")
        val body = get("$WEB_BASE/api/v1/feed/reels_media/?reel_ids=$encoded", cookies, csrf)
        return InstagramParser.parseStoryItems(body)
    }

    private fun get(url: String, cookies: String, csrf: String, notFoundMessage: String? = null): String {
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
        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            when {
                response.code == 404 -> throw DownloadException(notFoundMessage ?: "Instagram couldn't find that.")
                response.code == 429 -> throw DownloadException(
                    "Instagram is limiting requests right now. Wait a few minutes and try again."
                )
                text.contains("login_required") || response.code == 401 ->
                    throw LoginRequiredException("Your Instagram session has expired. Please sign in again.")
                text.contains("checkpoint_required") || text.contains("challenge_required") ->
                    throw DownloadException(
                        "Instagram wants to verify your account. Open the Instagram app, confirm it's you, then try again."
                    )
                !response.isSuccessful -> throw DownloadException(
                    "Instagram refused the request (error ${response.code}). Try again later."
                )
            }
            return text
        }
    }
}
