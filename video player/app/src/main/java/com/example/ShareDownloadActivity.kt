package com.example

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.data.download.InstagramParser
import com.example.service.ShareDownloads
import com.example.ui.components.SharePopupHost
import com.example.ui.theme.MyApplicationTheme

/**
 * What the Share sheet opens. It's see-through, so the app you shared from (Instagram, YouTube...)
 * stays visible behind a small download card: no app switch. The work itself runs in
 * [ShareDownloads], so hiding the card doesn't stop a download.
 */
class ShareDownloadActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Only on a fresh start; rotation must not start the same download again
        if (savedInstanceState == null) route(intent)

        setContent {
            MyApplicationTheme {
                SharePopupHost(
                    onClose = { finish() },
                    onOpenApp = { openOffScroll(null) },
                    onOpenAppWithShare = { openOffScroll(ShareDownloads.originalText) }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        route(intent)
    }

    override fun onStart() {
        super.onStart()
        ShareDownloads.popupVisible = true
    }

    override fun onStop() {
        super.onStop()
        ShareDownloads.popupVisible = false
    }

    private fun route(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND) return
        val shared = intent.getStringExtra(Intent.EXTRA_TEXT)
        // Consume it so the same share is never handled twice
        intent.action = null
        intent.removeExtra(Intent.EXTRA_TEXT)

        val instagramUrl = InstagramParser.extractInstagramUrl(shared)
        if (instagramUrl != null && InstagramParser.isStoryLink(instagramUrl)) {
            // Stories need your Instagram sign-in and a choice of which ones to save: that's the full app's job
            openOffScroll(shared)
            return
        }
        if (!ShareDownloads.begin(application, shared)) {
            Toast.makeText(this, "A download is already running", Toast.LENGTH_SHORT).show()
        }
    }

    /** Brings the full app forward; [shared] (if any) is replayed there exactly like a normal share. */
    private fun openOffScroll(shared: String?) {
        val launch = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (shared != null) {
            launch.action = Intent.ACTION_SEND
            launch.type = "text/plain"
            launch.putExtra(Intent.EXTRA_TEXT, shared)
        }
        startActivity(launch)
        finish()
    }
}
