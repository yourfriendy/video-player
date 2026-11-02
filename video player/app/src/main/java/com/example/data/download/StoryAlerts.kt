package com.example.data.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.MainActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Every ~5 minutes, checks the saved people that have alerts on and notifies about NEW stories and NEW reposts.
 * It only reads (it never opens or "views" a story), exactly like the Stories screen.
 *
 * Android never repeats a periodic job faster than every 15 minutes, so each run books the next one 5 minutes
 * ahead. If the phone is idle or in battery saver, Android may still run a check a little later than that.
 */
class StoryAlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        try {
            runChecks()
        } finally {
            StoryAlertScheduler.scheduleNext(applicationContext)
        }
        return Result.success()
    }

    private suspend fun runChecks() {
        val store = PeopleStore(applicationContext)
        val people = store.all().filter { it.alerts }
        if (people.isEmpty()) return
        if (!withContext(Dispatchers.Main) { InstagramStoryService.isSignedIn() }) return

        val downloader = withContext(Dispatchers.Main) { InstagramDownloader(applicationContext) }
        try {
            for ((index, person) in people.withIndex()) {
                if (index > 0) delay(4_000L) // be gentle: never hammer Instagram
                try {
                    val items = downloader.stories.load(InstagramParser.StoryTarget.User(person.username)).items
                    record(store, person, items.map { it.id }.toSet())
                } catch (e: NoStoriesException) {
                    record(store, person, emptySet())
                } catch (e: LoginRequiredException) {
                    notify("Instagram sign-in expired", "Open OffScroll and sign in again to keep getting story alerts.", "relogin".hashCode(), null)
                    break
                } catch (e: Exception) {
                    // Instagram hiccup: try again at the next run
                }
                try {
                    delay(2_000L)
                    checkReposts(downloader, store, person.username)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // reposts are a beta feature: a failure here must never stop the story alerts
                }
            }
        } finally {
            downloader.release()
        }
    }

    private fun record(store: PeopleStore, person: SavedPerson, nowIds: Set<String>) {
        val fresh = nowIds - person.seenIds
        if (person.baselined && fresh.isNotEmpty()) {
            val n = fresh.size
            notify(
                "@" + person.username + " posted " + (if (n == 1) "a new story" else "$n new stories"),
                "Tap to watch it in OffScroll. Nobody is told you looked.",
                person.username.lowercase().hashCode(),
                person.username
            )
        }
        store.update(person.username) { it.copy(seenIds = it.seenIds + nowIds, baselined = true) }
    }

    /**
     * Newest reposts of one person. Repost ids are stored in the same "seen" list as story ids, with an "r:" prefix,
     * and a marker entry says "the first look already happened" (so the very first check never floods you with alerts).
     */
    private suspend fun checkReposts(downloader: InstagramDownloader, store: PeopleStore, username: String) {
        val items = try {
            downloader.stories.loadReposts(username, null, limit = 12).items
        } catch (e: NoStoriesException) {
            emptyList()
        }
        if (items.isEmpty()) return // nothing readable: don't mark anything as seen
        val person = store.all().firstOrNull { it.username.equals(username, ignoreCase = true) } ?: return
        val nowIds = items.map { REPOST_PREFIX + it.id }.toSet()
        val fresh = nowIds - person.seenIds
        if (REPOST_MARK in person.seenIds && fresh.isNotEmpty()) {
            val n = fresh.size
            val by = items.firstOrNull { (REPOST_PREFIX + it.id) in fresh }?.author
            notify(
                "@" + person.username + " reposted " + (if (n == 1) "a reel" else "$n reels"),
                (if (n == 1 && by != null) "A reel by @" + by + ". " else "") + "Tap to open OffScroll. Nobody is told you looked.",
                ("repost:" + person.username.lowercase()).hashCode(),
                person.username
            )
        }
        store.update(person.username) { it.copy(seenIds = (it.seenIds - REPOST_MARK) + nowIds + REPOST_MARK) }
    }

    private fun notify(title: String, text: String, id: Int, openPerson: String?) {
        val manager = NotificationManagerCompat.from(applicationContext)
        if (!manager.areNotificationsEnabled()) return
        ensureChannel(applicationContext)
        val open = Intent(applicationContext, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (openPerson != null) open.putExtra(EXTRA_OPEN_PERSON, openPerson)
        val pending = PendingIntent.getActivity(
            applicationContext, id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(applicationContext.applicationInfo.icon)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        try {
            manager.notify(id, notification)
        } catch (_: SecurityException) {
            // notification permission was revoked in the meantime
        }
    }

    companion object {
        const val EXTRA_OPEN_PERSON = "open_person"
        private const val CHANNEL_ID = "story_alerts"
        private const val REPOST_PREFIX = "r:"
        private const val REPOST_MARK = "r:baseline"

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(CHANNEL_ID, "Story alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "New stories and reposts from the people you picked"
                }
                context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
            }
        }
    }
}

object StoryAlertScheduler {
    private const val OLD_WORK_NAME = "story-alerts" // the old 30-minute repeating job: always cancelled
    private const val WORK_NAME = "story-alerts-5min"
    private const val INTERVAL_MINUTES = 5L

    /** Starts the 5-minute check chain if any saved person has alerts on, and stops it when none do. */
    fun sync(context: Context) {
        try {
            val manager = WorkManager.getInstance(context.applicationContext)
            manager.cancelUniqueWork(OLD_WORK_NAME)
            if (PeopleStore(context).all().any { it.alerts }) {
                // KEEP: if a check is already waiting its turn, leave its timer alone
                manager.enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request(0L))
            } else {
                manager.cancelUniqueWork(WORK_NAME)
            }
        } catch (_: Exception) {
            // WorkManager unavailable: alerts simply stay off
        }
    }

    /** Called at the end of every check: books the next one. */
    fun scheduleNext(context: Context) {
        try {
            if (PeopleStore(context).all().none { it.alerts }) return
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request(INTERVAL_MINUTES))
        } catch (_: Exception) {
            // WorkManager unavailable: alerts simply stay off
        }
    }

    private fun request(delayMinutes: Long) = OneTimeWorkRequestBuilder<StoryAlertWorker>()
        .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .build()
}
