package com.example.data.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The timer for story and repost alerts, chosen by the user (1 to 60 minutes). */
object StoryAlertSettings {
    const val MIN_MINUTES = 1
    const val MAX_MINUTES = 60
    const val DEFAULT_MINUTES = 5

    /** Up to this many minutes the checks run in live mode (see [StoryWatchService]). */
    const val LIVE_MAX_MINUTES = 10

    private const val PREFS = "story_alert_settings"
    private const val KEY_MINUTES = "interval_minutes"

    fun intervalMinutes(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_MINUTES, DEFAULT_MINUTES)
            .coerceIn(MIN_MINUTES, MAX_MINUTES)

    fun setIntervalMinutes(context: Context, minutes: Int) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt(KEY_MINUTES, minutes.coerceIn(MIN_MINUTES, MAX_MINUTES))
            .apply()
    }

    /** Short timers need live mode: Android won't run ordinary background work every minute or two. */
    fun isLive(context: Context): Boolean = intervalMinutes(context) <= LIVE_MAX_MINUTES
}

/**
 * Live mode for short timers. Android pauses ordinary background work whenever the phone is idle, so a 1 or 2
 * minute timer can't be kept that way. A foreground service (it shows one small ongoing notification) is what
 * Android allows to keep running, so this one checks the people you set alerts for every N minutes, measured from
 * the start of one round to the start of the next, and alerts the moment something new shows up.
 *
 * It stops itself when no one has alerts on or when the timer is set above [StoryAlertSettings.LIVE_MAX_MINUTES].
 * The normal 5-minute background chain keeps running next to it as a safety net.
 */
class StoryWatchService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loop: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel(this)
        val notification = buildNotification(this, "Watching for new stories…")
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            // Android refused (app not allowed to start it right now): the 5-minute background chain still runs
            stopSelf()
            return START_NOT_STICKY
        }
        // Already looping and nothing changed (e.g. the app just came back to the front): leave the round alone.
        // A changed timer passes EXTRA_RESTART so the new interval applies at once.
        val restart = intent?.getBooleanExtra(EXTRA_RESTART, false) == true
        if (loop?.isActive == true && !restart) return START_STICKY
        loop?.cancel()
        loop = scope.launch { runLoop() }
        return START_STICKY
    }

    private suspend fun runLoop() {
        while (true) {
            val minutes = StoryAlertSettings.intervalMinutes(this)
            val watching = try {
                PeopleStore(this).all().count { it.alerts }
            } catch (e: Exception) {
                0
            }
            if (watching == 0 || minutes > StoryAlertSettings.LIVE_MAX_MINUTES) {
                stopSelf()
                return
            }
            val startedAt = SystemClock.elapsedRealtime()
            try {
                StoryAlertRun(applicationContext).check(null)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
            com.example.data.Diagnostics.log("ignored", "StoryWatchService.kt:113 One bad round must not end live mode")
        }
            update(this, watching, minutes)
            // Start-to-start spacing; never less than 15 s between rounds so a slow round can't turn into a request storm
            val wait = (minutes * 60_000L - (SystemClock.elapsedRealtime() - startedAt)).coerceAtLeast(15_000L)
            delay(wait)
        }
    }

    // Android 15 can time out foreground services of some types: fall back to the normal background chain
    override fun onTimeout(startId: Int, fgsType: Int) {
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "story_watch"
        private const val NOTIFICATION_ID = 4109

        private const val EXTRA_RESTART = "restart"

        fun start(context: Context, restart: Boolean = false) {
            try {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, StoryWatchService::class.java).putExtra(EXTRA_RESTART, restart)
                )
            } catch (e: Exception) {
            com.example.data.Diagnostics.log("ignored", "StoryWatchService.kt:145 Not allowed to start right now (app in the background): the background chain sti")
        }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, StoryWatchService::class.java))
            } catch (e: Exception) {
            com.example.data.Diagnostics.log("ignored", "StoryWatchService.kt:153 Already stopped")
        }
        }

        private fun update(context: Context, watching: Int, minutes: Int) {
            val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            val text = "Checking " + watching + (if (watching == 1) " person" else " people") +
                " every " + minutes + " min. Last check " + time
            try {
                if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                    @Suppress("MissingPermission")
                    NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, buildNotification(context, text))
                }
            } catch (e: SecurityException) {
            com.example.data.Diagnostics.log("ignored", "StoryWatchService.kt:167 Notifications switched off: the loop keeps running anyway")
        }
        }

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(CHANNEL_ID, "Live story alerts", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shown while OffScroll checks for new stories on your timer"
                setShowBadge(false)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        private fun buildNotification(context: Context, text: String): Notification {
            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_offscroll)
                .setContentTitle("OffScroll is watching for new stories")
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text + "\nChange the timer in a person's profile, or turn their alerts off to stop."))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(open)
                .build()
        }
    }
}
