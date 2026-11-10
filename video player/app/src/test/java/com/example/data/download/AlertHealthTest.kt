package com.example.data.download

import org.junit.Assert.assertEquals
import org.junit.Test

class AlertHealthTest {

    private val now = 1_700_000_000_000L
    private fun health(
        anyAlerts: Boolean = true,
        allowed: Boolean = true,
        lastAgoMin: Long? = 2,
        line: String? = "checked 2 person(s): stories read for 2, reposts found 0, alerts sent 0",
        interval: Int = 5
    ) = AlertHealthRules.compute(anyAlerts, allowed, lastAgoMin?.let { now - it * 60_000L }, line, interval, now)

    @Test fun nobodyWatchedMeansOff() = assertEquals(AlertHealth.Off, health(anyAlerts = false))

    @Test fun blockedNotificationsBeatEverythingElse() =
        assertEquals(AlertHealth.NotAllowed, health(allowed = false, lastAgoMin = 500, line = "Couldn't read 1"))

    @Test fun signedOutIsAnInstagramProblem() =
        assertEquals(AlertHealth.InstagramProblem, health(line = "not signed in to Instagram, so nothing was checked"))

    @Test fun unreadablePeopleAreAnInstagramProblem() =
        assertEquals(AlertHealth.InstagramProblem, health(line = "checked 1 person(s): stories read for 0. Couldn't read 1 (timeout), will retry"))

    @Test fun silenceForTooLongIsLate() {
        // 5 min timer: late after 3 x 5 + 2 = 17 minutes
        assertEquals(AlertHealth.Working, health(lastAgoMin = 16))
        assertEquals(AlertHealth.Late, health(lastAgoMin = 18))
    }

    @Test fun aFreshCheckIsWorking() = assertEquals(AlertHealth.Working, health())

    @Test fun noCheckYetGetsTheBenefitOfTheDoubt() = assertEquals(AlertHealth.Working, health(lastAgoMin = null, line = null))

    @Test fun shortTimersGoLateSooner() = assertEquals(AlertHealth.Late, health(lastAgoMin = 6, interval = 1))
}
