package com.example.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsTest {

    @Test
    fun keepsOnlyTheLatestSixtyEvents() {
        Diagnostics.clear()
        repeat(75) { Diagnostics.log("t", "event $it", nowMs = 0L) }
        val recent = Diagnostics.recent()
        assertEquals(60, recent.size)
        assertTrue(recent.last().endsWith("event 74"))
        assertTrue(recent.first().endsWith("event 15"))
        Diagnostics.clear()
    }

    @Test
    fun aLogLineIsOneShortLine() {
        val line = Diagnostics.line(0L, "viewer", "first\nsecond " + "x".repeat(400))
        assertFalse(line.contains('\n'))
        assertTrue(line.length < 260)
        assertTrue(line.contains("[viewer]"))
    }

    @Test
    fun emptySectionsAreLeftOutOfTheReport() {
        val report = Diagnostics.format(listOf("A" to listOf("one"), "B" to emptyList(), "C" to listOf("two", "three")))
        assertTrue(report.contains("== A =="))
        assertFalse(report.contains("== B =="))
        assertTrue(report.contains("two\nthree"))
        assertTrue(report.contains("no passwords"))
    }
}
