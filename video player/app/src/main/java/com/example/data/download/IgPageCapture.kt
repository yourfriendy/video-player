package com.example.data.download

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Opens one of Instagram's own pages in a hidden browser and records the data replies that the
 * PAGE ITSELF requests.
 *
 * Instagram can refuse calls that look automated, but it can't tell its own page's requests from
 * a person's: they are made by Instagram's own code, with all of its own headers. We never make
 * those requests ourselves. We only read what comes back (like the browser's "network" tab does).
 * Nothing is clicked, so opening a page this way does not mark any story as "seen".
 */
class IgPageCapture(context: Context, private val userAgent: String) {

    data class Captured(val url: String, val status: Int, val body: String)

    private val appContext = context.applicationContext

    /**
     * Loads [pageUrl] and returns every API reply seen, stopping early once [enough] says we have
     * what we need, or after [timeoutMs]. Always returns what was captured so far (maybe nothing).
     */
    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface", "RequiresFeature")
    suspend fun capture(
        pageUrl: String,
        timeoutMs: Long = 25_000L,
        enough: (List<Captured>) -> Boolean
    ): CaptureResult = withContext(Dispatchers.Main) {
        val captured = CopyOnWriteArrayList<Captured>()
        val done = CompletableDeferred<Unit>()
        var pageNote: String? = null
        val view = WebView(appContext)
        try {
            view.settings.javaScriptEnabled = true
            view.settings.domStorageEnabled = true
            view.settings.userAgentString = userAgent
            CookieManager.getInstance().setAcceptCookie(true)

            // Give the hidden page a normal phone-sized screen so Instagram lays it out like a real visit
            view.measure(
                View.MeasureSpec.makeMeasureSpec(1080, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(1920, View.MeasureSpec.EXACTLY)
            )
            view.layout(0, 0, 1080, 1920)

            view.addJavascriptInterface(object {
                @JavascriptInterface
                fun onCapture(url: String, status: Int, body: String) {
                    captured.add(Captured(url, status, body))
                    if (enough(captured.toList())) done.complete(Unit)
                }
            }, "OffScrollCapture")

            if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
                WebViewCompat.addDocumentStartJavaScript(view, HOOK_SCRIPT, setOf("https://www.instagram.com"))
            }
            view.webViewClient = object : WebViewClient() {
                override fun onPageStarted(v: WebView?, url: String?, favicon: Bitmap?) {
                    v?.evaluateJavascript(HOOK_SCRIPT, null) // harmless if already installed
                }

                override fun onPageFinished(v: WebView?, url: String?) {
                    // The story page ships its reel inside the page as JSON: read it now and again once scripts settle
                    v?.evaluateJavascript(HARVEST_SCRIPT, null)
                    Handler(Looper.getMainLooper()).postDelayed({ v?.evaluateJavascript(HARVEST_SCRIPT, null) }, 2500L)
                }

                override fun shouldOverrideUrlLoading(v: WebView?, request: WebResourceRequest?): Boolean =
                    request?.url?.host?.endsWith("instagram.com") != true

                /**
                 * First line of defence, in the network layer:
                 *  - anything that looks like a "story seen" report is answered with an empty reply and never sent;
                 *  - photos and videos (Instagram's media servers) are not downloaded at all. We only need the data.
                 */
                override fun shouldInterceptRequest(v: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                    val uri = request?.url ?: return null
                    val host = uri.host.orEmpty()
                    val url = uri.toString().lowercase()
                    val onMediaHost = host.endsWith("cdninstagram.com") || host.endsWith("fbcdn.net")
                    // "static.*" serves Instagram's own scripts and styles: blocking it stops the page from starting at all
                    val isMedia = onMediaHost && !host.startsWith("static.") &&
                        (host.contains("scontent") || MEDIA_FILE.containsMatchIn(url))
                    val isSeenReport = SEEN_URL.containsMatchIn(url)
                    if (!isMedia && !isSeenReport) return null
                    return WebResourceResponse(
                        "application/json", "utf-8", 200, "OK", emptyMap(),
                        java.io.ByteArrayInputStream(if (isSeenReport) "{\"status\":\"ok\"}".toByteArray() else ByteArray(0))
                    )
                }

                override fun onReceivedError(v: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    if (request?.isForMainFrame == true) pageNote = "the page didn't load (${error?.description})"
                }

                override fun onReceivedHttpError(v: WebView?, request: WebResourceRequest?, response: WebResourceResponse?) {
                    if (request?.isForMainFrame == true) pageNote = "the page answered HTTP ${response?.statusCode}"
                }
            }
            view.loadUrl(pageUrl)
            withTimeoutOrNull(timeoutMs) { done.await() }
            CaptureResult(captured.toList(), pageNote)
        } finally {
            view.stopLoading()
            view.destroy()
        }
    }

    data class CaptureResult(val replies: List<Captured>, val pageNote: String?)

    private companion object {
        /** URLs that report a story as seen (or mark anything as seen). Never let these leave the phone. */
        val MEDIA_FILE = Regex("""\.(mp4|m4s|webm|m4a|jpg|jpeg|webp|heic)(\?|$)""")

        val SEEN_URL = Regex("""(/seen\b|/seen/|seen_|_seen|mark_as_seen|markasseen|stories/reel/seen|media/seen)""")

        /** Hands over the JSON blocks embedded in the page that look like they carry stories, profiles or reels. */
        const val HARVEST_SCRIPT = """
            (function () {
              try {
                var blocks = document.querySelectorAll('script[type="application/json"]');
                for (var i = 0; i < blocks.length; i++) {
                  var t = blocks[i].textContent || '';
                  if (t.length > 20 && t.length < 3000000 &&
                      (t.indexOf('image_versions2') >= 0 || t.indexOf('video_versions') >= 0 || t.indexOf('reels_media') >= 0)) {
                    OffScrollCapture.onCapture('embedded-page-data', 200, t);
                  }
                }
              } catch (e) {}
            })();
        """

        /**
         * Wraps fetch() and XMLHttpRequest so a copy of each API reply is handed to the app, and so that
         * NOTHING that reports a story as viewed can be sent (second line of defence, in the page itself:
         * it also catches GraphQL "mutations", which share one URL with harmless reads).
         * Everything else about the page is unchanged.
         */
        const val HOOK_SCRIPT = """
            (function () {
              if (window.__offscrollHooked) return;
              window.__offscrollHooked = true;
              function want(u) { return typeof u === 'string' && /(api\/v1\/|graphql)/.test(u); }
              // Anything that could tell Instagram "this story was viewed" (or change anything at all) is dropped
              function blocked(u, body) {
                u = String(u || '');
                if (/seen/i.test(u)) return true;
                if (/graphql/i.test(u) && body && /(Seen|Mutation)/.test(String(body))) return true;
                return false;
              }
              function nameOf(b) {
                var m = /fb_api_req_friendly_name=([^&]+)/.exec(String(b || ''));
                return m ? '#' + m[1] : '';
              }
              function bodyOf(b) {
                try { if (typeof b === 'string') return b; if (b && typeof b.toString === 'function') return b.toString(); } catch (e) {}
                return '';
              }
              if (navigator.sendBeacon) {
                var origBeacon = navigator.sendBeacon.bind(navigator);
                navigator.sendBeacon = function (u, d) { return blocked(u, bodyOf(d)) ? true : origBeacon(u, d); };
              }
              function send(u, s, t) {
                try { if (t && t.length < 3000000) OffScrollCapture.onCapture(String(u), s | 0, String(t)); } catch (e) {}
              }
              var origFetch = window.fetch;
              if (origFetch) {
                window.fetch = function (input, init) {
                  var url = (typeof input === 'string') ? input : ((input && input.url) || '');
                  if (blocked(url, bodyOf(init && init.body))) {
                    return Promise.resolve(new Response('{"status":"ok"}', { status: 200, headers: { 'Content-Type': 'application/json' } }));
                  }
                  var tag = nameOf(bodyOf(init && init.body));
                  var p = origFetch.apply(this, arguments);
                  if (want(url)) {
                    p.then(function (r) { r.clone().text().then(function (t) { send(url + tag, r.status, t); }); }).catch(function () {});
                  }
                  return p;
                };
              }
              var open = XMLHttpRequest.prototype.open, sendX = XMLHttpRequest.prototype.send;
              XMLHttpRequest.prototype.open = function (m, u) { this.__osUrl = u; return open.apply(this, arguments); };
              XMLHttpRequest.prototype.send = function (body) {
                var x = this;
                if (blocked(x.__osUrl, bodyOf(body))) { try { x.abort(); } catch (e) {} return; }
                x.__osTag = nameOf(bodyOf(body));
                x.addEventListener('load', function () {
                  var u = x.__osUrl || '';
                  if (!want(u)) return;
                  var t = '';
                  try { t = (x.responseType === '' || x.responseType === 'text') ? x.responseText : JSON.stringify(x.response); } catch (e) {}
                  send(u + (x.__osTag || ''), x.status, t);
                });
                return sendX.apply(this, arguments);
              };
            })();
        """
    }
}
