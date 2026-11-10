package com.example.data.download

import org.junit.Assert.assertEquals
import org.junit.Test

class StoryAlertHistoryTest {

    @Test
    fun newestComesFirstAndOldOnesFallOff() {
        var raw = ""
        for (i in 1..12) raw = StoryAlertStatus.appendHistory(raw, i * 1000L, "run $i", keep = 10)
        val parsed = StoryAlertStatus.parseHistory(raw)
        assertEquals(10, parsed.size)
        assertEquals(12_000L to "run 12", parsed.first())
        assertEquals(3_000L to "run 3", parsed.last())
    }

    @Test
    fun linesKeepTheirPipesAndNeverBreakTheFormat() {
        val raw = StoryAlertStatus.appendHistory("", 5L, "a | b\nsecond line", keep = 10)
        assertEquals(listOf(5L to "a | b second line"), StoryAlertStatus.parseHistory(raw))
    }

    @Test
    fun garbageRowsAreIgnored() {
        assertEquals(listOf(7L to "ok"), StoryAlertStatus.parseHistory("oops\n7|ok\n|x"))
    }
}
