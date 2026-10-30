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
    val builtAt: String?
)

/** An error whose message is safe to show to the user as-is. */
class UpdateException(message: String) : Exception(message)

/**
 * Reads GitHub's `GET /repos/{owner}/{repo}/releases/latest` answer.
 *
 * CONTRACT with .github/workflows/build-apk.yml: the workflow writes a line `Build number: <n>`
 * into the release notes (n = GITHUB_RUN_NUMBER, which is also the APK's versionCode).
 * If that line ever disappears, the app can no longer tell whether a release is newer.
 */
object UpdateParser {
    private val BUILD_NUMBER = Regex("""Build number:\s*(\d+)""", RegexOption.IGNORE_CASE)
    private val COMMIT = Regex("""Commit:\s*`?([0-9a-f]{7,40})`?""", RegexOption.IGNORE_CASE)
    private val BUILT = Regex("""Built:\s*(\d{4}-\d{2}-\d{2}[^\r\n]*)""", RegexOption.IGNORE_CASE)

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
                builtAt = BUILT.find(notes)?.groupValues?.get(1)?.trim()
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
