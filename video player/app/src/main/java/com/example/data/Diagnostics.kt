package com.example.data

/**
 * A small in-memory log of things that went wrong, plus a plain-text report people can copy and send when something
 * doesn't work. It holds no passwords, cookies or media addresses: only short event descriptions.
 */
object Diagnostics {
    private const val KEEP = 60
    private val events = ArrayDeque<String>()

    @Synchronized
    fun log(tag: String, message: String, nowMs: Long = System.currentTimeMillis()) {
        events.addLast(line(nowMs, tag, message))
        while (events.size > KEEP) events.removeFirst()
    }

    @Synchronized
    fun recent(): List<String> = events.toList()

    @Synchronized
    fun clear() = events.clear()

    internal fun line(nowMs: Long, tag: String, message: String): String {
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US).format(java.util.Date(nowMs))
        return "$time [$tag] ${message.replace('\n', ' ').take(200)}"
    }

    /** Assembles the report from sections, skipping empty ones. */
    fun format(sections: List<Pair<String, List<String>>>): String = buildString {
        appendLine("OffScroll diagnostics")
        appendLine("(Contains no passwords or login cookies.)")
        sections.filter { it.second.isNotEmpty() }.forEach { (title, lines) ->
            appendLine()
            appendLine("== $title ==")
            lines.forEach { appendLine(it) }
        }
    }.trim()
}
