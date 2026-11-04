package com.example.data.universal

import com.example.data.universal.engine.EngineInfoParser
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineInfoParserTest {

    private val youtube = """
        {
          "title": "  A clip  ", "fulltitle": "A clip (full)", "uploader": "Some Channel",
          "thumbnail": "https://img.example/t.jpg", "duration": 60,
          "extractor": "youtube", "extractorKey": "Youtube",
          "formats": [
            {"formatId":"137","width":1920,"height":1080,"fps":30,"vcodec":"avc1.640028","acodec":"none","ext":"mp4","tbr":4000,"fileSize":30000000,"fileSizeApproximate":0,"formatNote":"1080p"},
            {"formatId":"136","width":1280,"height":720,"fps":30,"vcodec":"avc1.4d401f","acodec":"none","ext":"mp4","tbr":2000,"fileSize":15000000,"fileSizeApproximate":0,"formatNote":"720p"},
            {"formatId":"140","width":0,"height":0,"fps":0,"vcodec":"none","acodec":"mp4a.40.2","ext":"m4a","tbr":128,"fileSize":1000000,"fileSizeApproximate":0,"formatNote":null}
          ]
        }
    """.trimIndent()

    @Test
    fun buildsQualityListBestFirst() {
        val info = EngineInfoParser.parse("https://youtu.be/x", JSONObject(youtube))
        assertEquals("A clip", info.title)
        assertEquals("Some Channel", info.uploader)
        assertEquals(60, info.durationSec)
        assertEquals(listOf(1080, 720), info.options.map { it.quality })
        // Video-only streams get the best audio added to them
        assertTrue(info.options.first().selector.contains("137+ba"))
    }

    @Test
    fun jsonNullAndEmptyStringAreNotGiven() {
        val json = JSONObject(
            """{"title":"","fulltitle":"","uploader":"","thumbnail":"","duration":0,"extractor":"","extractorKey":"",
                "formats":[{"formatId":"18","width":640,"height":360,"fps":0,"vcodec":null,"acodec":null,"ext":"mp4","tbr":0,"fileSize":0,"fileSizeApproximate":5000,"formatNote":null}]}"""
        )
        val info = EngineInfoParser.parse("https://example.com/v", json)
        assertEquals("Video", info.title)
        assertNull(info.uploader)
        assertNull(info.thumbnail)
        assertEquals(1, info.options.size)
        assertEquals(360, info.options.first().quality)
    }

    @Test
    fun singleFileSitesWithoutFormatsStillGetAnOption() {
        val json = JSONObject("""{"title":"Reel","formatId":"0","ext":"mp4","width":720,"height":1280,"fileSize":2000000,"formats":[]}""")
        val info = EngineInfoParser.parse("https://example.com/r", json)
        assertEquals(1, info.options.size)
        assertEquals(720, info.options.first().quality)
    }
}
