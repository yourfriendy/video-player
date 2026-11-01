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
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Every ~30 minutes, checks the saved people that have alerts on and notifies about NEW stories.
 * It only reads (it never opens or "views" a story), exactly like the Stories screen.
 */
class StoryAlertWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val store = PeopleStore(applicationContext)
        val people = store.all().filter { it.alerts }
        if (people.isEmpty()) return Result.success()
        if (!withContext(Dispatchers.Main) { InstagramStoryService.isSignedIn() }) return Result.success()

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
            }
        } finally {
            downloader.release()
        }
        return Result.success()
    }

    private fun record(store: PeopleStore, person: SavedPerson, nowIds: Set<String>) {
        val fresh = nowIds - person.seenIds
        if (person.baselined && fresh.isNotEmpty()) {
            val n = fresh.size
            notify(
                "@${person.username} posted ${if (n == 1) "a new story" else "$n new stories"}",
                "Tap to watch it in OffScroll. Nobody is told you looked.",
                person.username.lowercase().hashCode(),
                person.username
            )
        }
        store.update(person.username) { it.copy(seenIds = it.seenIds + nowIds, baselined = true) }
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

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(CHANNEL_ID, "Story alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "New stories from the people you picked"
                }
                context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
            }
        }
    }
}

object StoryAlertScheduler {
    private const val WORK_NAME = "story-alerts"

    /** Starts the periodic check if any saved person has alerts on, and stops it when none do. */
    fun sync(context: Context) {
        try {
            val manager = WorkManager.getInstance(context.applicationContext)
            if (PeopleStore(context).all().any { it.alerts }) {
                val request = PeriodicWorkRequestBuilder<StoryAlertWorker>(30, TimeUnit.MINUTES)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build()
                manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
            } else {
                manager.cancelUniqueWork(WORK_NAME)
            }
        } catch (_: Exception) {
            // WorkManager unavailable: alerts simply stay off
        }
    }
}
