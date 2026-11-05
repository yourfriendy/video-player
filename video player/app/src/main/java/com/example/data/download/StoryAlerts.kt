package com.example.data.download

import com.example.R
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
 * Every run writes a one-line result to [StoryAlertStatus] so the Stories screen can show what happened.
 */
class StoryAlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    private var sent = 0

    override suspend fun doWork(): Result {
        try {
            runChecks()
        } finally {
            StoryAlertScheduler.scheduleNext(applicationContext)
        }
        return Result.success()
    }

    private fun status(line: String) = StoryAlertStatus.record(applicationContext, line)

    private suspend fun runChecks() {
        val store = PeopleStore(applicationContext)
        val people = store.all().filter { it.alerts }
        if (people.isEmpty()) {
            status("nothing to check, no person has alerts on")
            return
        }
        if (!withContext(Dispatchers.Main) { InstagramStoryService.isSignedIn() }) {
            status("not signed in to Instagram, so nothing was checked")
            return
        }
        val blocked = notificationsBlocked(applicationContext)
        status("checking " + people.size + " person(s)...")

        var storiesOk = 0
        var failed = 0
        var lastError = ""
        var repostsRead = 0
        var note = ""
        val downloader = withContext(Dispatchers.Main) { InstagramDownloader(applicationContext) }
        try {
            for ((index, person) in people.withIndex()) {
                if (index > 0) delay(4_000L) // be gentle: never hammer Instagram
                try {
                    val items = downloader.stories.load(InstagramParser.StoryTarget.User(person.username)).items
                    record(store, person, items.map { it.id }.toSet())
                    storiesOk++
                } catch (e: NoStoriesException) {
                    record(store, person, emptySet())
                    storiesOk++
                } catch (e: LoginRequiredException) {
                    note = ". Instagram sign-in expired"
                    notify("Instagram sign-in expired", "Open OffScroll and sign in again to keep getting story alerts.", "relogin".hashCode(), null)
                    break
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Instagram hiccup: try again at the next run, but say so in the status line
                    failed++
                    lastError = (e.message ?: e.javaClass.simpleName).lineSequence().firstOrNull().orEmpty().take(90)
                }
                try {
                    delay(2_000L)
                    repostsRead += checkReposts(downloader, store, person.username)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // reposts are a beta feature: a failure here must never stop the story alerts
                }
            }
        } finally {
            downloader.release()
        }
        status(
            "checked " + people.size + " person(s): stories read for " + storiesOk + ", reposts found " + repostsRead +
                ", alerts sent " + sent + note +
                (if (failed > 0) ". Couldn't read " + failed + " (" + lastError + "), will retry" else "") +
                (if (blocked) ". NOTIFICATIONS ARE OFF for OffScroll in phone settings, so nothing can be shown" else "")
        )
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
     * Returns how many reposts were read.
     */
    private suspend fun checkReposts(downloader: InstagramDownloader, store: PeopleStore, username: String): Int {
        val items = try {
            downloader.stories.loadReposts(username, null, limit = 12).items
        } catch (e: NoStoriesException) {
            emptyList()
        }
        if (items.isEmpty()) return 0 // nothing readable: don't mark anything as seen
        val person = store.all().firstOrNull { it.username.equals(username, ignoreCase = true) } ?: return items.size
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
        return items.size
    }

    private fun notify(title: String, text: String, id: Int, openPerson: String?) {
        if (post(applicationContext, title, text, id, openPerson)) sent++
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

        /** True if the phone would swallow our notifications (switched off for the app, or the channel is muted). */
        fun notificationsBlocked(context: Context): Boolean {
            if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return true
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL_ID)
                if (channel != null && channel.importance == NotificationManager.IMPORTANCE_NONE) return true
            }
            return false
        }

        /** Shows a notification right now, so you can see that this phone lets OffScroll show alerts. */
        fun sendTest(context: Context): Boolean = post(
            context.applicationContext,
            "OffScroll test alert",
            "If you can read this, alerts can be shown on this phone.",
            "test-alert".hashCode(),
            null
        )

        private fun post(context: Context, title: String, text: String, id: Int, openPerson: String?): Boolean {
            val manager = NotificationManagerCompat.from(context)
            if (!manager.areNotificationsEnabled()) return false
            ensureChannel(context)
            val open = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            if (openPerson != null) open.putExtra(EXTRA_OPEN_PERSON, openPerson)
            val pending = PendingIntent.getActivity(
                context, id, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_offscroll)
                .setColor(0xFFEC4899.toInt())
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(text))
                .setAutoCancel(true)
                .setContentIntent(pending)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
            return try {
                manager.notify(id, notification)
                true
            } catch (_: SecurityException) {
                false // notification permission was revoked in the meantime
            }
        }
    }
}

/** The result line of the latest alert check, shown on the Stories screen. */
object StoryAlertStatus {
    private const val PREFS = "story_alert_status"

    fun record(context: Context, line: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("at", System.currentTimeMillis())
            .putString("line", line)
            .apply()
    }

    /** (time in milliseconds, result line), or null if no check has run yet. */
    fun read(context: Context): Pair<Long, String>? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val at = prefs.getLong("at", 0L)
        return if (at == 0L) null else at to (prefs.getString("line", "") ?: "")
    }
}

object StoryAlertScheduler {
    private const val OLD_WORK_NAME = "story-alerts" // the old 30-minute repeating job: always cancelled
    private const val WORK_NAME = "story-alerts-5min"
    private const val NOW_WORK_NAME = "story-alerts-now"
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

    /**
     * Phones pause background work while idle, so a check can be late. When OffScroll comes to the front and the
     * last check is more than a few minutes old, run one straight away instead of waiting for the next slot.
     */
    fun catchUp(context: Context) {
        try {
            if (PeopleStore(context).all().none { it.alerts }) return
            val last = StoryAlertStatus.read(context)?.first ?: 0L
            if (System.currentTimeMillis() - last > (INTERVAL_MINUTES + 1) * 60_000L) checkNow(context)
        } catch (_: Exception) {
            // WorkManager unavailable
        }
    }

    /** The "Check now" button: one extra check straight away (the 5-minute chain carries on as usual). */
    fun checkNow(context: Context) {
        try {
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(NOW_WORK_NAME, ExistingWorkPolicy.REPLACE, request(0L))
        } catch (_: Exception) {
            // WorkManager unavailable
        }
    }

    private fun request(delayMinutes: Long) = OneTimeWorkRequestBuilder<StoryAlertWorker>()
        .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .build()
}
