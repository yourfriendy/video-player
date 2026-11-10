package com.example.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.pm.PackageInfoCompat
import com.example.data.download.InstagramNotificationListener
import com.example.data.download.PeopleStore
import com.example.data.download.StoryAlertSettings
import com.example.data.download.StoryAlertStatus
import com.example.data.download.StoryAlertWorker
import com.example.data.universal.engine.EngineProtocol
import com.example.ui.theme.motionEnabled
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Builds the text behind Settings > Copy diagnostics. Every lookup is guarded: a report must never crash. */
object DiagnosticsReport {

    fun build(context: Context): String {
        fun <T> safe(block: () -> T): T? = try { block() } catch (e: Exception) { null }
        val time = SimpleDateFormat("HH:mm:ss", Locale.US)

        val app = buildList {
            safe {
                val info = context.packageManager.getPackageInfo(context.packageName, 0)
                add("Version ${info.versionName} (build ${PackageInfoCompat.getLongVersionCode(info)})")
            }
            add("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}")
            safe { add("Animations ${if (motionEnabled(context)) "on" else "off or low-memory phone"}") }
        }

        val permissions = buildList {
            safe {
                val videos = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE
                add("Video access: " + if (ContextCompat.checkSelfPermission(context, videos) == PackageManager.PERMISSION_GRANTED) "allowed" else "not allowed")
            }
            safe { add("Notifications: " + if (StoryAlertWorker.notificationsBlocked(context)) "blocked" else "allowed") }
        }

        val alerts = buildList {
            safe {
                val people = PeopleStore(context).all()
                add("${people.count { it.alerts }} of ${people.size} saved people have alerts on")
            }
            safe {
                val minutes = StoryAlertSettings.intervalMinutes(context)
                add("Timer: every $minutes min (${if (StoryAlertSettings.isLive(context)) "live mode" else "normal mode"})")
            }
            safe { add("Instant alerts access: " + if (InstagramNotificationListener.isEnabled(context)) "allowed" else "off") }
        }

        val checks = safe { StoryAlertStatus.history(context).map { (at, line) -> time.format(Date(at)) + "  " + line } }.orEmpty()

        val engine = buildList {
            safe {
                val info = context.packageManager.getPackageInfo(EngineProtocol.ENGINE_PACKAGE, 0)
                add("Installed (build ${PackageInfoCompat.getLongVersionCode(info)})")
            } ?: add("Not installed")
        }

        return Diagnostics.format(
            listOf(
                "App and phone" to app,
                "Permissions" to permissions,
                "Alerts" to alerts,
                "Recent alert checks (newest first)" to checks,
                "Engine (Save videos)" to engine,
                "Recent problems" to Diagnostics.recent().reversed()
            )
        )
    }
}
