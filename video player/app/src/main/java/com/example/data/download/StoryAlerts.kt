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

    override suspend fun doWork(): Result {
        try {
            StoryAlertRun(applicationContext).check(inputData.getString(KEY_ONLY))
        } finally {
            StoryAlertScheduler.scheduleNext(applicationContext)
        }
        return Result.success()
    }

    companion object {
        const val EXTRA_OPEN_PERSON = "open_person"
        const val KEY_ONLY = "only"
        private const val CHANNEL_ID = "story_alerts"

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

        internal fun post(context: Context, title: String, text: String, id: Int, openPerson: String?): Boolean {
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

/**
 * One round of checks: reads the stories and reposts of the people who have alerts on and notifies about new ones.
 * Used by the background worker AND by the live-mode service ([StoryWatchService]). Only one round runs at a time
 * in the app: if another is already running, this call does nothing (it would only repeat the same requests).
 */
class StoryAlertRun(private val context: Context) {

    private var sent = 0

    suspend fun check(only: String?) {
        if (!lock.tryLock()) return
        try {
            runChecks(only)
        } finally {
            lock.unlock()
        }
    }

    private fun status(line: String) = StoryAlertStatus.record(context, line)

    private suspend fun runChecks(only: String?) {
        val store = PeopleStore(context)
        val people = store.all().filter { it.alerts && (only == null || it.username.equals(only, ignoreCase = true)) }
        if (people.isEmpty()) {
            status("nothing to check, no person has alerts on")
            return
        }
        if (!withContext(Dispatchers.Main) { InstagramStoryService.isSignedIn() }) {
            status("not signed in to Instagram, so nothing was checked")
            return
        }
        val blocked = StoryAlertWorker.notificationsBlocked(context)
        status("checking " + people.size + " person(s)...")

        var storiesOk = 0
        var failed = 0
        var lastError = ""
        var repostsRead = 0
        var note = ""
        val downloader = withContext(Dispatchers.Main) { InstagramDownloader(context) }
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
        if (StoryAlertWorker.post(context, title, text, id, openPerson)) sent++
    }

    private companion object {
        val lock = kotlinx.coroutines.sync.Mutex()
        const val REPOST_PREFIX = "r:"
        const val REPOST_MARK = "r:baseline"
    }
}

/** The result line of the latest alert check, plus a short history of earlier ones. */
object StoryAlertStatus {
    private const val PREFS = "story_alert_status"
    private const val KEEP = 10

    fun record(context: Context, line: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        // "checking..." is progress, not a result: it stays out of the history
        val history = if (line.startsWith("checking ")) prefs.getString("history", "").orEmpty()
        else appendHistory(prefs.getString("history", "").orEmpty(), now, line, KEEP)
        prefs.edit()
            .putLong("at", now)
            .putString("line", line)
            .putString("history", history)
            .apply()
        if (!line.startsWith("checking ") && (line.contains("Couldn't read") || line.contains("not signed in"))) {
            com.example.data.Diagnostics.log("alerts", line)
        }
    }

    /** (time in milliseconds, result line), or null if no check has run yet. */
    fun read(context: Context): Pair<Long, String>? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val at = prefs.getLong("at", 0L)
        return if (at == 0L) null else at to (prefs.getString("line", "") ?: "")
    }

    /** Newest first. */
    fun history(context: Context): List<Pair<Long, String>> =
        parseHistory(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("history", "").orEmpty())

    internal fun appendHistory(existing: String, nowMs: Long, line: String, keep: Int): String =
        (listOf("$nowMs|${line.replace('\n', ' ')}") + existing.lines().filter { it.isNotBlank() }).take(keep).joinToString("\n")

    internal fun parseHistory(raw: String): List<Pair<Long, String>> =
        raw.lines().mapNotNull { row ->
            val cut = row.indexOf('|')
            if (cut <= 0) null else row.substring(0, cut).toLongOrNull()?.let { it to row.substring(cut + 1) }
        }
}

object StoryAlertScheduler {
    private const val OLD_WORK_NAME = "story-alerts" // the old 30-minute repeating job: always cancelled
    private const val WORK_NAME = "story-alerts-5min"
    private const val NOW_WORK_NAME = "story-alerts-now"

    /** Starts the 5-minute check chain if any saved person has alerts on, and stops it when none do. */
    fun sync(context: Context, restart: Boolean = false) {
        try {
            val manager = WorkManager.getInstance(context.applicationContext)
            manager.cancelUniqueWork(OLD_WORK_NAME)
            val anyAlerts = PeopleStore(context).all().any { it.alerts }
            if (anyAlerts) {
                // KEEP: if a check is already waiting its turn, leave its timer alone (unless the timer was just changed)
                manager.enqueueUniqueWork(WORK_NAME, if (restart) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP, request(0L))
            } else {
                manager.cancelUniqueWork(WORK_NAME)
            }
            // Short timers: Android won't run background work that often, so a small ongoing notification keeps a loop alive
            if (anyAlerts && StoryAlertSettings.isLive(context)) StoryWatchService.start(context, restart) else StoryWatchService.stop(context)
        } catch (_: Exception) {
            // WorkManager unavailable: alerts simply stay off
        }
    }

    /** Called at the end of every check: books the next one. */
    fun scheduleNext(context: Context) {
        try {
            if (PeopleStore(context).all().none { it.alerts }) return
            // In live mode the service does the frequent checks; this chain stays as the 5-minute safety net
            val delay = if (StoryAlertSettings.isLive(context)) 5L else StoryAlertSettings.intervalMinutes(context).toLong()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.REPLACE, request(delay))
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
            if (StoryAlertSettings.isLive(context)) StoryWatchService.start(context)
            val last = StoryAlertStatus.read(context)?.first ?: 0L
            if (System.currentTimeMillis() - last > (StoryAlertSettings.intervalMinutes(context) + 1) * 60_000L) checkNow(context)
        } catch (_: Exception) {
            // WorkManager unavailable
        }
    }

    /** The "Check now" button: one extra check straight away (the 5-minute chain carries on as usual). */
    fun checkNow(context: Context, only: String? = null) {
        try {
            // A targeted check (Instagram just told us about this person) never replaces a running "check everyone"
            val name = if (only == null) NOW_WORK_NAME else "$NOW_WORK_NAME-${only.lowercase()}"
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, request(0L, only))
        } catch (_: Exception) {
            // WorkManager unavailable
        }
    }

    private fun request(delayMinutes: Long, only: String? = null) = OneTimeWorkRequestBuilder<StoryAlertWorker>()
        .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
        .setInputData(androidx.work.Data.Builder().apply { if (only != null) putString(StoryAlertWorker.KEY_ONLY, only) }.build())
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .build()
}
