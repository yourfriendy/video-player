package com.example.ui.model

/** Length buckets offered as quick filters on the search page. */
enum class DurationBucket(val label: String, val minMs: Long, val maxMs: Long) {
    ANY("Any length", 0L, Long.MAX_VALUE),
    SHORT("Under 1 min", 0L, 60_000L),
    MEDIUM("1 – 10 min", 60_000L, 600_000L),
    LONG("10 min +", 600_000L, Long.MAX_VALUE);

    /** Half-open [minMs, maxMs) so buckets never overlap. */
    fun matches(durationMs: Long): Boolean = durationMs >= minMs && durationMs < maxMs
}

enum class SearchSort(val label: String) {
    BEST_MATCH("Best match"),
    NEWEST("Newest"),
    OLDEST("Oldest"),
    LONGEST("Longest"),
    SHORTEST("Shortest"),
    NAME("Name A–Z")
}

/** Everything the search page can narrow results by, besides the typed text. */
data class SearchOptions(
    val duration: DurationBucket = DurationBucket.ANY,
    val favoritesOnly: Boolean = false,
    val folder: String? = null,
    val sort: SearchSort = SearchSort.BEST_MATCH
) {
    /** True when nothing narrows the results, so an empty query should show suggestions instead. */
    val isDefault: Boolean
        get() = duration == DurationBucket.ANY && !favoritesOnly && folder == null
}
