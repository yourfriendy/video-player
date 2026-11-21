package com.example.ui.model

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * What to show instead of a raw file name. [kind] is a short label with an emoji ("📹 Camera") when the name is
 * one the phone generated; null for a name a person chose.
 */
data class SmartTitle(val title: String, val kind: String?)

// A date, optionally followed by a time: 20260121_153012, 2026-01-21-15-30-12, ... Not preceded by another digit.
private val STAMP = Regex("""(?<!\d)(20\d{2})[-_.]?(\d{2})[-_.]?(\d{2})(?:[-_. T]?(\d{2})[-_.]?(\d{2})[-_.]?(\d{2}))?""")
private val WHATSAPP = Regex("""^(vid|img)[-_]\d{8}[-_]wa\d+""")

/**
 * Turns "VID_20260121_153012.mp4" into "Jan 21, 2026 · 3:30 PM" (kind "📹 Camera"), "Screen_Recording_..." into a
 * dated screen recording, and "my_trip-to_goa.mp4" into "My trip to goa". The real file name is still shown in the
 * details sheet; this only changes how the feed and grid read it.
 */
fun smartTitle(displayName: String): SmartTitle {
    val base = displayName.substringBeforeLast('.', displayName).trim()
    val lower = base.lowercase(Locale.ROOT)
    val match = STAMP.find(base)
    val whenLabel = match?.let { stampLabel(it) }

    if (match != null && whenLabel != null) {
        val kind = when {
            WHATSAPP.containsMatchIn(lower) -> "💬 WhatsApp"
            lower.startsWith("screen") -> "🖥 Screen recording"
            listOf("vid", "mov", "mvi", "pxl", "video", "cap").any { lower.startsWith(it) } -> "📹 Camera"
            else -> null
        }
        val letters = base.replace(match.value, "").filter { it.isLetter() }
        if (kind != null || letters.isEmpty()) return SmartTitle(whenLabel, kind)
    }
    return SmartTitle(humanize(base, displayName), null)
}

private fun stampLabel(m: MatchResult): String? {
    val g = m.groupValues
    val year = g[1].toInt()
    val month = g[2].toInt()
    val day = g[3].toInt()
    if (month !in 1..12 || day !in 1..31) return null
    val cal = Calendar.getInstance()
    cal.clear()
    if (g[4].isEmpty()) {
        cal.set(year, month - 1, day)
        return SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(cal.time)
    }
    val hour = g[4].toInt()
    val minute = g[5].toInt()
    val second = g[6].toInt()
    if (hour > 23 || minute > 59 || second > 59) return null
    cal.set(year, month - 1, day, hour, minute, second)
    return SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()).format(cal.time)
}

private fun humanize(base: String, original: String): String {
    var s = base.replace(Regex("[_.]+"), " ")
    // "my-trip-to-goa" uses hyphens as word breaks; a name that already has spaces keeps its own hyphens
    if (!base.contains(' ')) s = s.replace('-', ' ')
    s = s.replace(Regex("\\s+"), " ").trim()
    if (s.isEmpty()) return original
    return s.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
}
