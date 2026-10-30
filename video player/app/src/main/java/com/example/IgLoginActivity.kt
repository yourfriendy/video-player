package com.example

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContract
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.example.data.download.InstagramStoryService

/**
 * Instagram's own sign-in page in a plain web view, on its own screen.
 *
 * (An earlier version hosted the web view inside a Compose dialog and rendered blank on some
 * phones. A dedicated Activity with a classic web view is the reliable way to do this.)
 *
 * OffScroll never sees the password: it only notices that Instagram has set its session cookie,
 * then closes this screen. The cookie stays in the web view's private storage.
 */
class IgLoginActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var loadBar: ProgressBar
    private lateinit var status: TextView
    private val handler = Handler(Looper.getMainLooper())
    private var done = false

    /** Instagram's sign-in uses client-side navigation, so also poll for the cookie once a second. */
    private val poller = object : Runnable {
        override fun run() {
            checkSignedIn()
            if (!done) handler.postDelayed(this, 1000L)
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "RequiresFeature")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val navy = Color.parseColor("#020617")
        window.statusBarColor = navy
        window.navigationBarColor = navy

        fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

        // ---- Header: ✕  title  ↻ ----
        val close = TextView(this).apply {
            text = "✕"; textSize = 20f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setPadding(dp(16), 0, dp(16), 0)
            contentDescription = "Close"
            setOnClickListener { finish() }
        }
        val title = TextView(this).apply {
            text = "Sign in to Instagram"; textSize = 16f; setTextColor(Color.WHITE)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        val subtitle = TextView(this).apply {
            text = "Instagram's own page. OffScroll never sees your password."
            textSize = 11f; setTextColor(Color.parseColor("#94A3B8"))
        }
        val titles = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(title); addView(subtitle)
        }
        val reload = TextView(this).apply {
            text = "↻"; textSize = 22f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setPadding(dp(16), 0, dp(16), 0)
            contentDescription = "Reload"
            setOnClickListener { status.visibility = View.GONE; webView.reload() }
        }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(navy)
            addView(close, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(56)))
            addView(titles, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(reload, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(56)))
        }

        loadBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; visibility = View.GONE
        }
        status = TextView(this).apply {
            setTextColor(Color.parseColor("#F59E0B")); textSize = 13f
            setBackgroundColor(Color.parseColor("#1E293B"))
            setPadding(dp(16), dp(10), dp(16), dp(10))
            visibility = View.GONE
        }

        webView = WebView(this).apply {
            setBackgroundColor(Color.WHITE) // so a blank page is visibly "white", never mistaken for the app
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.useWideViewPort = true
            settings.loadWithOverviewMode = true
            settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            intent.getStringExtra(EXTRA_USER_AGENT)?.let { settings.userAgentString = it }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                @Suppress("DEPRECATION")
                settings.forceDark = WebSettings.FORCE_DARK_OFF // never recolour Instagram's page
            }
            // Don't announce "this is an embedded app web view" to Instagram on every request
            if (WebViewFeature.isFeatureSupported(WebViewFeature.REQUESTED_WITH_HEADER_ALLOW_LIST)) {
                WebSettingsCompat.setRequestedWithHeaderOriginAllowList(settings, emptySet())
            }
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    loadBar.progress = newProgress
                    loadBar.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
                }
            }
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    status.visibility = View.GONE
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    checkSignedIn()
                }

                override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                    checkSignedIn()
                }

                // Instagram sometimes tries to bounce into its app (instagram://, intent://): ignore that
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val scheme = request?.url?.scheme?.lowercase()
                    return scheme != "http" && scheme != "https"
                }

                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    if (request?.isForMainFrame == true) {
                        showStatus("Couldn't load Instagram (${error?.description}). Check your connection, then tap ↻.")
                    }
                }

                override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, errorResponse: WebResourceResponse?) {
                    if (request?.isForMainFrame == true) {
                        showStatus("Instagram answered with error ${errorResponse?.statusCode}. Tap ↻ to try again.")
                    }
                }

                @androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
                override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                    showStatus("The browser engine stopped. Close this screen and open it again.")
                    return true
                }
            }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(navy)
            addView(header)
            addView(loadBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)))
            addView(status, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        setContentView(root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })

        if (savedInstanceState == null) webView.loadUrl(LOGIN_URL) else webView.restoreState(savedInstanceState)
    }

    private fun showStatus(message: String) {
        status.text = message
        status.visibility = View.VISIBLE
    }

    private fun checkSignedIn() {
        if (done || !InstagramStoryService.isSignedIn()) return
        done = true
        CookieManager.getInstance().flush()
        setResult(RESULT_OK)
        finish()
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
        handler.post(poller)
    }

    override fun onPause() {
        handler.removeCallbacks(poller)
        webView.onPause()
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        webView.saveState(outState)
    }

    override fun onDestroy() {
        handler.removeCallbacks(poller)
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }

    /** Launch contract: input = browser identity string to use, result = true if the user is now signed in. */
    class Contract : ActivityResultContract<String, Boolean>() {
        override fun createIntent(context: Context, input: String): Intent =
            Intent(context, IgLoginActivity::class.java).putExtra(EXTRA_USER_AGENT, input)

        override fun parseResult(resultCode: Int, intent: Intent?): Boolean = resultCode == RESULT_OK
    }

    companion object {
        private const val EXTRA_USER_AGENT = "user_agent"
        private const val LOGIN_URL = "https://www.instagram.com/accounts/login/"
    }
}
