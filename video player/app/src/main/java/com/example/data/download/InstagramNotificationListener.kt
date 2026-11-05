package com.example.data.download

import android.app.Notification
import android.content.Context
import android.os.SystemClock
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat

/**
 * Makes story alerts instant. Instagram only sends its real-time push to the official app, so OffScroll can't
 * receive it directly. But when you turn on Instagram's own bell for someone (Stories / Reels), the Instagram
 * app posts a notification the moment they post. With the "notification access" permission, this service sees
 * that notification (ONLY Instagram's, nothing else is read) and immediately runs an OffScroll check for that
 * one person, instead of waiting for the next 5-minute round. OffScroll then shows its own alert, with the
 * story ready to watch or save.
 *
 * Instagram's text changes between languages and versions, so nothing is parsed: if a notification mentions a
 * saved person who has alerts on (by username or by display name), a check runs. A check only alerts about
 * stories OffScroll hasn't seen, so a false match (a like, a comment) costs one quick check and nothing else.
 */
class InstagramNotificationListener : NotificationListenerService() {

    private val lastTrigger = HashMap<String, Long>()

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        val notification = sbn ?: return
        if (notification.packageName !in INSTAGRAM_PACKAGES) return
        val extras = notification.notification?.extras ?: return
        val text = listOf(Notification.EXTRA_TITLE, Notification.EXTRA_TEXT, Notification.EXTRA_BIG_TEXT, Notification.EXTRA_SUB_TEXT)
            .mapNotNull { extras.getCharSequence(it)?.toString() }
            .joinToString(" ")
            .lowercase()
        if (text.isBlank()) return

        val people = try {
            PeopleStore(applicationContext).all().filter { it.alerts }
        } catch (e: Exception) {
            return
        }
        val now = SystemClock.elapsedRealtime()
        for (person in people) {
            if (!mentions(text, person)) continue
            val key = person.username.lowercase()
            if (now - (lastTrigger[key] ?: 0L) < DEBOUNCE_MS) continue
            lastTrigger[key] = now
            StoryAlertScheduler.checkNow(applicationContext, only = person.username)
        }
    }

    companion object {
        private val INSTAGRAM_PACKAGES = setOf("com.instagram.android", "com.instagram.barcelona")
        private const val DEBOUNCE_MS = 45_000L

        /** True if [text] (already lower-cased) names this person by username or by display name. */
        internal fun mentions(text: String, person: SavedPerson): Boolean {
            val username = person.username.lowercase()
            if (username.isNotEmpty() &&
                Regex("(?<![a-z0-9._])" + Regex.escape(username) + "(?![a-z0-9._])").containsMatchIn(text)
            ) return true
            val fullName = person.fullName.trim().lowercase()
            return fullName.length >= 3 && text.contains(fullName)
        }

        /** Has the user allowed OffScroll to read notifications ("Notification access" in phone settings)? */
        fun isEnabled(context: Context): Boolean =
            NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }
}
