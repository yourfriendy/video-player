package com.example.data.download

/** The one plain-language answer to "are my alerts working?". */
enum class AlertHealth { Off, Working, Late, NotAllowed, InstagramProblem }

object AlertHealthRules {

    /**
     * Turns the facts the app already has into one state, worst problem first, so the screen can show a single
     * message with a single fix button instead of a line of technical text.
     *
     *  - nobody has alerts on                                  -> Off
     *  - Android is blocking OffScroll's notifications         -> NotAllowed
     *  - the last check couldn't read Instagram / not signed in -> InstagramProblem
     *  - no check for more than three intervals plus 2 minutes  -> Late (the phone paused background work)
     *  - otherwise                                              -> Working
     */
    fun compute(
        anyAlerts: Boolean,
        notificationsAllowed: Boolean,
        lastCheckAtMs: Long?,
        lastLine: String?,
        intervalMinutes: Int,
        nowMs: Long
    ): AlertHealth {
        if (!anyAlerts) return AlertHealth.Off
        if (!notificationsAllowed) return AlertHealth.NotAllowed
        val line = lastLine.orEmpty().lowercase()
        if (line.contains("not signed in") || line.contains("couldn't read")) return AlertHealth.InstagramProblem
        if (lastCheckAtMs != null && nowMs - lastCheckAtMs > (intervalMinutes * 3L + 2L) * 60_000L) return AlertHealth.Late
        return AlertHealth.Working
    }
}
