package com.example.data.download

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Makes Instagram API calls from inside a hidden, genuine browser engine (the system WebView)
 * that shares the user's sign-in cookies.
 *
 * Instagram is far stricter with requests that come from an app's own HTTP stack than with ones
 * from a real browser (different TLS fingerprint, headers and behaviour), and answers the former
 * with errors such as 429. Running `fetch()` inside a page whose origin is instagram.com makes
 * the calls indistinguishable from ordinary use of the website by the signed-in user.
 */
class IgWebFetcher(context: Context, private val userAgent: String) {

    data class Result(val status: Int, val body: String)

    private val appContext = context.applicationContext
    private val pending = ConcurrentHashMap<String, CompletableDeferred<Result>>()
    private val counter = AtomicInteger()
    private var webView: WebView? = null
    private var ready: CompletableDeferred<Unit>? = null

    /** Which kind of page the hidden browser sits on. Both have instagram.com as their origin. */
    private enum class Origin { ROBOTS_TXT, BLANK_PAGE }

    private var origin = Origin.ROBOTS_TXT

    private inner class Bridge {
        @JavascriptInterface
        fun onResult(id: String, status: Int, body: String) {
            pending.remove(id)?.complete(Result(status, body))
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    private suspend fun ensureReady(): WebView = withContext(Dispatchers.Main) {
        val existing = webView
        if (existing != null) {
            ready?.await()
            return@withContext existing
        }
        val loaded = CompletableDeferred<Unit>()
        ready = loaded
        val view = WebView(appContext).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString = userAgent
            CookieManager.getInstance().setAcceptCookie(true)
            addJavascriptInterface(Bridge(), "OffScrollBridge")
            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    loaded.complete(Unit)
                }

                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    if (request?.isForMainFrame == true) {
                        loaded.completeExceptionally(
                            DownloadException("Couldn't reach Instagram. Check your internet connection and try again.")
                        )
                    }
                }

                // Only ever stay on instagram.com (a redirect within it is fine)
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
                    request?.url?.host?.endsWith("instagram.com") != true
            }
            when (origin) {
                // A real first-party page: requests from it are exactly what the website itself would send
                Origin.ROBOTS_TXT -> loadUrl("https://www.instagram.com/robots.txt")
                // Fallback: a blank page that merely claims instagram.com as its origin
                Origin.BLANK_PAGE -> loadDataWithBaseURL(
                    "https://www.instagram.com/",
                    "<!doctype html><html><head><meta charset=\"utf-8\"></head><body></body></html>",
                    "text/html",
                    "utf-8",
                    null
                )
            }
        }
        webView = view
        try {
            withTimeout(10_000) { loaded.await() }
        } catch (e: TimeoutCancellationException) {
            throw DownloadException("The built-in browser didn't start. Please try again.")
        }
        view
    }

    /** Rebuilds the hidden browser on the other kind of page. Used when Instagram treats this one as signed out. */
    suspend fun switchOrigin() {
        withContext(Dispatchers.Main) {
            webView?.destroy()
            webView = null
            ready = null
            origin = if (origin == Origin.ROBOTS_TXT) Origin.BLANK_PAGE else Origin.ROBOTS_TXT
        }
    }

    /** GETs [url] as the signed-in user. [Result.status] is -1 when the request failed before getting any answer. */
    suspend fun get(url: String): Result {
        val first = attempt(url)
        if (first.status != -1) return first
        // The request couldn't even be made (e.g. the page blocked it): try the other kind of page once
        switchOrigin()
        return attempt(url)
    }

    private suspend fun attempt(url: String): Result {
        val view = ensureReady()
        val id = counter.incrementAndGet().toString()
        val deferred = CompletableDeferred<Result>()
        pending[id] = deferred

        val js = """
            (function () {
              var csrf = (document.cookie.match(/csrftoken=([^;]+)/) || [])[1] || '';
              var headers = {
                'X-IG-App-ID': '936619743392459',
                'X-ASBD-ID': '129477',
                'X-IG-WWW-Claim': '0',
                'X-Requested-With': 'XMLHttpRequest',
                'X-CSRFToken': csrf,
                'Accept': '*/*'
              };
              fetch(${JSONObject.quote(url)}, { credentials: 'include', headers: headers })
                .then(function (r) {
                  return r.text().then(function (t) { OffScrollBridge.onResult(${JSONObject.quote(id)}, r.status, t); });
                })
                .catch(function (e) { OffScrollBridge.onResult(${JSONObject.quote(id)}, -1, String(e)); });
            })();
        """.trimIndent()

        withContext(Dispatchers.Main) {
            view.resumeTimers()
            view.evaluateJavascript(js, null)
        }
        return try {
            withTimeout(25_000) { deferred.await() }
        } catch (e: TimeoutCancellationException) {
            throw DownloadException("Instagram took too long to answer. Please try again.")
        } finally {
            pending.remove(id)
        }
    }

    fun release() {
        Handler(Looper.getMainLooper()).post {
            webView?.destroy()
            webView = null
            ready = null
        }
    }
}
