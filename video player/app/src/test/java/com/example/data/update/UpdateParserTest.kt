package com.example.data.update

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateParserTest {

    private val notes = """
        ## OffScroll – latest build

        Install **OffScroll.apk** on your Android phone (Android 7.0+).

        - Build number: 13
        - Commit: `90cf0b2`
        - Built: 2026-10-30 09:41 UTC
        - Release build: optimised with R8 for smooth scrolling.
    """.trimIndent()

    private fun asset(name: String, size: Long = 5_100_000, url: String? = "https://example.com/$name") =
        JSONObject().put("name", name).put("size", size).apply { if (url != null) put("browser_download_url", url) }

    private fun release(body: Any?, vararg assets: JSONObject): String =
        JSONObject()
            .put("tag_name", "latest")
            .put("body", body ?: JSONObject.NULL)
            .put("assets", JSONArray(assets.toList()))
            .toString()

    @Test
    fun readsBuildNumberCommitTimeAndApk() {
        val info = UpdateParser.parseLatestRelease(release(notes, asset("OffScroll.apk")))!!
        assertEquals(13, info.buildNumber)
        assertEquals("90cf0b2", info.commit)
        assertEquals("2026-10-30 09:41 UTC", info.builtAt)
        assertEquals("https://example.com/OffScroll.apk", info.apkUrl)
        assertEquals(5_100_000L, info.apkSize)
    }

    @Test
    fun prefersOffScrollApkWhenSeveralApksAreAttached() {
        val info = UpdateParser.parseLatestRelease(
            release(notes, asset("other.apk"), asset("OffScroll.apk"), asset("notes.txt"))
        )!!
        assertEquals("https://example.com/OffScroll.apk", info.apkUrl)
    }

    @Test
    fun releaseWithoutAnApkIsRejected() {
        assertNull(UpdateParser.parseLatestRelease(release(notes, asset("readme.txt"))))
        assertNull(UpdateParser.parseLatestRelease(release(notes)))
    }

    @Test
    fun apkWithoutDownloadUrlIsRejected() {
        assertNull(UpdateParser.parseLatestRelease(release(notes, asset("OffScroll.apk", url = null))))
    }

    @Test
    fun garbageIsRejectedWithoutCrashing() {
        assertNull(UpdateParser.parseLatestRelease("not json at all"))
        assertNull(UpdateParser.parseLatestRelease("{}"))
    }

    @Test
    fun nullOrUnnumberedNotesGiveNoBuildNumber() {
        val noBody = UpdateParser.parseLatestRelease(release(null, asset("OffScroll.apk")))!!
        assertNull(noBody.buildNumber)
        val oldStyle = UpdateParser.parseLatestRelease(release("- Commit: `abc1234`", asset("OffScroll.apk")))!!
        assertNull(oldStyle.buildNumber)
        assertEquals("abc1234", oldStyle.commit)
    }

    @Test
    fun onlyAHigherBuildNumberCountsAsNewer() {
        fun info(build: Int?) = ReleaseInfo(build, "https://example.com/a.apk", 1, null, null)
        assertTrue(UpdateParser.isNewer(12, info(13)))
        assertFalse(UpdateParser.isNewer(13, info(13)))
        assertFalse(UpdateParser.isNewer(14, info(13)))
        // A release that doesn't state its build can never be called newer
        assertFalse(UpdateParser.isNewer(1, info(null)))
    }
}
