package com.example.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Keeps the app's process alive and shows a progress notification while a video downloads.
 *
 * Without a foreground service Android freezes or kills a backgrounded app within minutes, which
 * would stop a long download as soon as the user switches to another app. The download itself runs
 * in the ViewModel; this service only keeps it alive and shows the notification.
 */
class DownloadKeepAliveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val title = intent?.getStringExtra(EXTRA_TITLE) ?: "Downloading video"
        ensureChannel(this)
        val notification = buildNotification(this, title, "Starting", -1)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            // Android refused to start it (e.g. app not in the foreground): the download still runs
            stopSelf()
        }
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL_ID = "offscroll_downloads"
        private const val NOTIFICATION_ID = 4107
        private const val EXTRA_TITLE = "title"

        fun start(context: Context, title: String) {
            try {
                val intent = Intent(context, DownloadKeepAliveService::class.java).putExtra(EXTRA_TITLE, title)
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                // Not allowed right now: the download keeps going while the app is open
            }
        }

        fun stop(context: Context) {
            try {
                context.stopService(Intent(context, DownloadKeepAliveService::class.java))
            } catch (e: Exception) {
                // Already stopped
            }
        }

        /** [percent] < 0 shows an indeterminate bar. */
        @SuppressLint("MissingPermission")
        fun update(context: Context, title: String, text: String, percent: Int) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return
            }
            try {
                NotificationManagerCompat.from(context)
                    .notify(NOTIFICATION_ID, buildNotification(context, title, text, percent))
            } catch (e: SecurityException) {
                // Notifications are switched off: nothing to update
            }
        }

        private fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Progress of videos being downloaded"
                setShowBadge(false)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        private fun buildNotification(context: Context, title: String, text: String, percent: Int): Notification {
            val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            val open = launch?.let {
                PendingIntent.getActivity(
                    context, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                )
            }
            return NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(title)
                .setContentText(text)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setContentIntent(open)
                .setProgress(100, percent.coerceIn(0, 100), percent < 0)
                .build()
        }
    }
}
