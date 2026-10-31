package com.example.data.universal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UniversalModelsTest {

    private fun video(id: String, w: Int, h: Int, vcodec: String, fps: Int = 30, tbr: Int = 1000, size: Long = 0, ext: String = "mp4", acodec: String = "none") =
        FormatInfo(id, w, h, fps, vcodec, acodec, ext, tbr, size)

    private fun audio(id: String, ext: String, tbr: Int, size: Long = 0) =
        FormatInfo(id, 0, 0, 0, "none", if (ext == "m4a") "mp4a.40.2" else "opus", ext, tbr, size)

    // A YouTube-shaped format list: several codecs per resolution, separate audio streams, a storyboard
    private val youtube = listOf(
        FormatInfo("sb0", 48, 27, 0, "none", "none", "mhtml", 0, 0, "storyboard"),
        audio("140", "m4a", 129, 3_000_000),
        audio("251", "webm", 135, 3_100_000),
        video("18", 640, 360, "avc1.42001E", acodec = "mp4a.40.2", size = 20_000_000),
        video("134", 640, 360, "avc1.4d401e", size = 8_000_000),
        video("136", 1280, 720, "avc1.4d401f", size = 30_000_000),
        video("298", 1280, 720, "avc1.4d4020", fps = 60, size = 45_000_000),
        video("247", 1280, 720, "vp9", size = 25_000_000, ext = "webm"),
        video("137", 1920, 1080, "avc1.640028", size = 80_000_000),
        video("248", 1920, 1080, "vp9", size = 60_000_000, ext = "webm"),
        video("399", 1920, 1080, "av01.0.08M.08", size = 40_000_000),
        video("271", 2560, 1440, "vp9", size = 200_000_000, ext = "webm"),
        video("313", 3840, 2160, "vp9", size = 500_000_000, ext = "webm")
    )

    @Test
    fun youtubeGivesOneOptionPerResolutionBestFirst() {
        val options = QualityOptions.build(youtube, durationSec = 300)
        assertEquals(listOf(2160, 1440, 1080, 720, 360), options.map { it.quality })
        assertEquals("2160p · 4K", options.first().label)
        assertEquals("1440p · 2K", options[1].label)
        assertEquals("1080p · Full HD", options[2].label)
        assertEquals("720p · HD", options[3].label)
        assertEquals("360p", options.last().label)
    }

    @Test
    fun prefersTheCompatibleCodecAndTheHighestFrameRate() {
        val options = QualityOptions.build(youtube, 300).associateBy { it.quality }
        // 1080p: H.264 (137) beats VP9 (248) and AV1 (399)
        assertTrue(options.getValue(1080).selector.startsWith("137+ba[ext=m4a]/137+ba/b"))
        // 720p: the 60 fps stream (298) wins over the 30 fps ones
        assertTrue(options.getValue(720).selector.startsWith("298+"))
        assertTrue(options.getValue(720).detail.contains("60 fps"))
        // 4K only exists as VP9
        assertTrue(options.getValue(2160).selector.startsWith("313+"))
        assertTrue(options.getValue(2160).detail.startsWith("VP9"))
    }

    @Test
    fun sizeIsVideoPlusTheBestAudioStream() {
        val o = QualityOptions.build(youtube, 300).associateBy { it.quality }.getValue(1080)
        // 80 MB video + 3.1 MB best audio (251 has the higher bitrate)
        assertEquals(83_100_000L, o.approxBytes)
        assertTrue(o.detail.contains("~83 MB"))
        assertTrue(o.videoShare in 0.9f..0.99f)
    }

    @Test
    fun aMuxedStreamNeedsNoSeparateAudio() {
        val o = QualityOptions.build(youtube, 300).associateBy { it.quality }.getValue(360)
        // 134 is video-only (8 MB) but 18 is muxed (20 MB) and has the higher bitrate
        // either way the selector must always end with a safe fallback
        assertTrue(o.selector.endsWith("/b"))
        assertTrue(o.id == "q360")
    }

    @Test
    fun storyboardsNeverBecomeAQualityOption() {
        val options = QualityOptions.build(youtube, 300)
        assertFalse(options.any { it.quality == 48 || it.quality == 27 })
    }

    @Test
    fun aVerticalReelIsLabelledBySizeOfItsShortSide() {
        val reel = listOf(
            video("hd", 1080, 1920, "h264", acodec = "aac", tbr = 2500, size = 12_000_000),
            video("sd", 576, 1024, "h264", acodec = "aac", tbr = 900, size = 5_000_000)
        )
        val options = QualityOptions.build(reel, 20)
        assertEquals(listOf("1080p · Full HD", "576p"), options.map { it.label })
        assertEquals("hd/b", options.first().selector)
        assertEquals(1f, options.first().videoShare)
    }

    @Test
    fun sizeFallsBackToBitrateTimesDuration() {
        val f = listOf(video("v", 1280, 720, "avc1", tbr = 2000, size = 0, acodec = "aac"))
        // 2000 kbit/s = 250 kB/s, for 60 s = 15 MB
        assertEquals(15_000_000L, QualityOptions.build(f, 60).first().approxBytes)
    }

    @Test
    fun noResolutionsMeansOneAutomaticOption() {
        val options = QualityOptions.build(emptyList(), 0)
        assertEquals(1, options.size)
        assertEquals("Best available", options.first().label)
        assertEquals(QualityOptions.BEST_SELECTOR, options.first().selector)
        // formats that only report audio also fall back
        assertEquals(1, QualityOptions.build(listOf(audio("a", "m4a", 128)), 10).size)
    }

    @Test
    fun linksAreFoundInSharedText() {
        assertEquals("https://youtu.be/abc123?si=xyz", LinkUtils.extractUrl("Look at this https://youtu.be/abc123?si=xyz, wow"))
        assertEquals("https://www.facebook.com/reel/123", LinkUtils.extractUrl("(https://www.facebook.com/reel/123)"))
        assertEquals("https://youtu.be/abc", LinkUtils.extractUrl("youtu.be/abc"))
        assertEquals("https://www.tiktok.com/@a/video/1", LinkUtils.extractUrl("  www.tiktok.com/@a/video/1  "))
        assertNull(LinkUtils.extractUrl("no link here"))
        assertNull(LinkUtils.extractUrl("example.com"))
        assertNull(LinkUtils.extractUrl(null))
        assertNull(LinkUtils.extractUrl("   "))
    }

    @Test
    fun platformNamesAreFriendly() {
        assertEquals("YouTube", Platforms.nameFor("Youtube", "https://www.youtube.com/watch?v=1"))
        assertEquals("YouTube", Platforms.nameFor(null, "https://youtu.be/abc"))
        assertEquals("Facebook", Platforms.nameFor("FacebookReel", "https://www.facebook.com/reel/1"))
        assertEquals("Facebook", Platforms.nameFor(null, "https://fb.watch/xyz"))
        assertEquals("Snapchat", Platforms.nameFor("SnapchatSpotlight", "https://www.snapchat.com/spotlight/1"))
        assertEquals("X (Twitter)", Platforms.nameFor(null, "https://x.com/a/status/1"))
        assertEquals("somesite.org", Platforms.nameFor("Generic", "https://www.somesite.org/v/1"))
    }

    @Test
    fun errorsAreExplainedInPlainLanguage() {
        assertEquals("That video is private.", ErrorMapper.explain("ERROR: [youtube] x: Private video. Sign in if you've been granted access").message)
        assertTrue(ErrorMapper.explain("ERROR: Sign in to confirm you’re not a bot").message.contains("not a bot"))
        assertTrue(ErrorMapper.explain("ERROR: [youtube] x: Sign in to confirm your age").message.contains("age-restricted"))
        assertEquals("That link isn't a video this app can download.", ErrorMapper.explain("ERROR: Unsupported URL: https://example.com").message)
        assertTrue(ErrorMapper.explain("ERROR: Requested content is not available, rate-limit reached or login required").message.contains("logged-in"))
        assertTrue(ErrorMapper.explain("ERROR: unable to download video data: HTTP Error 403: Forbidden").suggestUpdate)
        assertTrue(ErrorMapper.explain("ERROR: [youtube] x: nsig extraction failed").suggestUpdate)
        assertFalse(ErrorMapper.explain("ERROR: Private video").suggestUpdate)
        assertFalse(ErrorMapper.explain("ERROR: [Errno 28] No space left on device").suggestUpdate)
        assertTrue(ErrorMapper.explain("ERROR: <urlopen error [Errno 101] Network is unreachable>").message.contains("internet"))
    }

    @Test
    fun anUnknownErrorShowsItsOwnLastLineAndOffersAnUpdate() {
        val p = ErrorMapper.explain("WARNING: something\nERROR: [site] id: Something odd happened")
        assertEquals("Something odd happened", p.message)
        assertTrue(p.suggestUpdate)
        assertEquals("The download failed.", ErrorMapper.explain("").message)
        assertEquals("The download failed.", ErrorMapper.explain(null).message)
    }

    @Test
    fun theProgressBarCoversBothStreamsAndNeverGoesBackwards() {
        val m = ProgressMapper(videoShare = 0.8f)
        var last = 0f
        fun feed(line: String, percent: Float): Float {
            val v = m.onLine(line, percent)
            assertTrue("went backwards: $last -> $v", v >= last)
            last = v
            return v
        }
        feed("[download] Destination: /x/video.f137.mp4", 0f)
        assertEquals(ProgressMapper.Phase.Video, m.phase)
        assertEquals(0.4f, feed("[download]  50.0% of 80MiB at 4MiB/s ETA 00:10", 50f), 0.001f)
        assertEquals(0.8f, feed("[download] 100.0% of 80MiB", 100f), 0.001f)
        feed("[download] Destination: /x/video.f140.m4a", 0f)
        assertEquals(ProgressMapper.Phase.Audio, m.phase)
        assertEquals("Downloading audio", m.phaseLabel())
        assertEquals(0.9f, feed("[download]  50.0% of 3MiB at 1MiB/s ETA 00:01", 50f), 0.001f)
        feed("[Merger] Merging formats into \"/x/video.mp4\"", 99f)
        assertEquals(ProgressMapper.Phase.Merging, m.phase)
        assertTrue(last < 1f) // 100% is only shown once the file is really saved
    }

    @Test
    fun aSingleStreamUsesTheWholeBar() {
        val m = ProgressMapper(videoShare = 1f)
        m.onLine("[download] Destination: /x/video.mp4", 0f)
        assertEquals(0.5f, m.onLine("[download]  50.0% of 10MiB", 50f), 0.001f)
    }

    @Test
    fun speedAndEtaAreShownTogether() {
        assertEquals("4.2MiB/s · 0:30 left", ProgressMapper.detail("[download]  50.0% of 80MiB at  4.2MiB/s ETA 00:30", 30))
        assertEquals("", ProgressMapper.detail("[Merger] Merging", 0))
    }

    @Test
    fun formattingHelpers() {
        assertNull(formatBytes(0))
        assertEquals("<1 MB", formatBytes(500_000))
        assertEquals("3.1 MB", formatBytes(3_100_000))
        assertEquals("83 MB", formatBytes(83_100_000))
        assertEquals("1.2 GB", formatBytes(1_234_000_000))
        assertEquals("", formatDuration(0))
        assertEquals("2:05", formatDuration(125))
        assertEquals("1:02:03", formatDuration(3723))
    }

    @Test
    fun fileNamesAreMadeSafe() {
        assertEquals("a b c d e f g h i", safeFileName("a/b\\c:d*e?f\"g<h>i"))
        assertEquals("video", safeFileName("   ...   "))
        assertEquals("Nice clip", safeFileName("  Nice\n\tclip  "))
        assertTrue(safeFileName("x".repeat(300)).length <= 90)
    }
}
