package com.example.ui

import com.example.ui.model.smartTitle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartTitleTest {
    @Test
    fun cameraNamesBecomeADate() {
        val t = smartTitle("VID_20260121_153012.mp4")
        assertEquals("📹 Camera", t.kind)
        assertTrue(t.title, t.title.contains("2026") && t.title.contains("21"))
    }

    @Test
    fun screenRecordingIsRecognised() {
        assertEquals("🖥 Screen recording", smartTitle("Screen_Recording_20260121-153012.mp4").kind)
    }

    @Test
    fun whatsappIsRecognisedAndKeepsOnlyTheDate() {
        val t = smartTitle("VID-20260121-WA0003.mp4")
        assertEquals("💬 WhatsApp", t.kind)
        assertTrue(t.title, t.title.contains("2026"))
    }

    @Test
    fun humanNamesAreTidiedNotReplaced() {
        val t = smartTitle("my_trip-to_goa.mp4")
        assertEquals("My trip to goa", t.title)
        assertNull(t.kind)
    }

    @Test
    fun impossibleDatesAreLeftAlone() {
        assertEquals("VID 20261399 000000", smartTitle("VID_20261399_000000.mp4").title)
    }

    @Test
    fun emptyNameFallsBackToTheOriginal() {
        assertEquals(".mp4", smartTitle(".mp4").title)
    }
}
