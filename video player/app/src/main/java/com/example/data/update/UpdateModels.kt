package com.example.data.update

import org.json.JSONException
import org.json.JSONObject

/** One published build on GitHub (the repo's "latest" release). */
data class ReleaseInfo(
    /** The CI run number of that build. It is also the app's versionCode, so it can be compared directly. */
    val buildNumber: Int?,
    val apkUrl: String,
    val apkSize: Long,
    val commit: String?,
    /** Human-readable build time taken from the release notes, e.g. "2026-10-30 09:41 UTC". */
    val builtAt: String?,
    /** The bullets under the release notes' "What's new" heading: what changed in this build. May be empty. */
    val whatsNew: List<String> = emptyList(),
    /** The release's own title on GitHub, e.g. "OffScroll 1.1 (build 151)". */
    val title: String? = null
)

/** An error whose message is safe to show to the user as-is. */
class UpdateException(message: String) : Exception(message)

/**
 * Reads GitHub's `GET /repos/{owner}/{repo}/releases/latest` answer.
 *
 * CONTRACT with .github/workflows/build-apk.yml: the workflow writes a line `Build number: <n>`
 * into the release notes (n = GITHUB_RUN_NUMBER, which is also the APK's versionCode).
 * If that line ever disappears, the app can no longer tell whether a release is newer.
 * Every push to main becomes its own new release (v<version>.<build>) marked Latest, so /releases/latest is always the
 * newest build. Branch builds are pre-releases, which /releases/latest never returns.
 */
object UpdateParser {
    private val BUILD_NUMBER = Regex("""Build number:\s*(\d+)""", RegexOption.IGNORE_CASE)
    private val COMMIT = Regex("""Commit:\s*`?([0-9a-f]{7,40})`?""", RegexOption.IGNORE_CASE)
    private val BUILT = Regex("""Built:\s*(\d{4}-\d{2}-\d{2}[^\r\n]*)""", RegexOption.IGNORE_CASE)
    private val WHATS_NEW_HEADING = Regex("""#{1,6}\s*what'?s\s+new.*""", RegexOption.IGNORE_CASE)

    /**
     * CONTRACT with .github/workflows/build-apk.yml: a line `### What's new` followed by bullet lines (`- text`).
     * The list ends at the first blank line or the first line that is not a bullet. No heading means no list.
     */
    fun parseWhatsNew(notes: String): List<String> {
        val lines = notes.lines()
        val start = lines.indexOfFirst { WHATS_NEW_HEADING.matches(it.trim()) }
        if (start < 0) return emptyList()
        val items = mutableListOf<String>()
        for (raw in lines.drop(start + 1)) {
            val line = raw.trim()
            if (line.isEmpty()) {
                if (items.isEmpty()) continue else break
            }
            if (!(line.startsWith("- ") || line.startsWith("* "))) break
            val text = line.drop(2).replace("**", "").replace("`", "").trim()
            if (text.isNotEmpty()) items += text
        }
        return items.take(12)
    }

    fun parseLatestRelease(json: String): ReleaseInfo? {
        return try {
            val root = JSONObject(json)
            val assets = root.optJSONArray("assets") ?: return null
            var apk: JSONObject? = null
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name")
                if (!name.endsWith(".apk", ignoreCase = true)) continue
                if (apk == null || name.equals("OffScroll.apk", ignoreCase = true)) apk = asset
            }
            val chosen = apk ?: return null
            val url = chosen.optString("browser_download_url")
            if (url.isBlank()) return null

            val notes = if (root.isNull("body")) "" else root.optString("body")
            ReleaseInfo(
                buildNumber = BUILD_NUMBER.find(notes)?.groupValues?.get(1)?.toIntOrNull(),
                apkUrl = url,
                apkSize = chosen.optLong("size", 0L),
                commit = COMMIT.find(notes)?.groupValues?.get(1),
                builtAt = BUILT.find(notes)?.groupValues?.get(1)?.trim(),
                whatsNew = parseWhatsNew(notes),
                title = root.optString("name").takeIf { it.isNotBlank() }
            )
        } catch (e: JSONException) {
            null
        }
    }

    /** True only when the release states a build number that is higher than the installed one. */
    fun isNewer(installedBuild: Int, release: ReleaseInfo): Boolean {
        val build = release.buildNumber ?: return false
        return build > installedBuild
    }
}
