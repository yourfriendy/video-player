package com.example.data.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class InstagramParserTest {

    // ---- Links ----

    @Test
    fun findsLinkInsideShareText() {
        val text = "Check this out https://www.instagram.com/reel/CxAbC12345d/?igsh=MWx4 so funny!"
        assertEquals(
            "https://www.instagram.com/reel/CxAbC12345d/?igsh=MWx4",
            InstagramParser.extractInstagramUrl(text)
        )
    }

    @Test
    fun ignoresTextWithoutInstagramLink() {
        assertNull(InstagramParser.extractInstagramUrl("https://example.com/reel/abc12345"))
        assertNull(InstagramParser.extractInstagramUrl("hello"))
        assertNull(InstagramParser.extractInstagramUrl(null))
    }

    @Test
    fun readsShortcodeFromAllLinkShapes() {
        assertEquals("CxAbC12345d", InstagramParser.findShortcode("https://www.instagram.com/reel/CxAbC12345d/"))
        assertEquals("CxAbC12345d", InstagramParser.findShortcode("https://instagram.com/reels/CxAbC12345d/?utm=1"))
        assertEquals("CxAbC12345d", InstagramParser.findShortcode("https://www.instagram.com/p/CxAbC12345d"))
        assertEquals("Cx_bC-2345d", InstagramParser.findShortcode("https://www.instagram.com/tv/Cx_bC-2345d/"))
        assertEquals("CxAbC12345d", InstagramParser.findShortcode("https://www.instagram.com/someuser/reel/CxAbC12345d/"))
        assertNull(InstagramParser.findShortcode("https://www.instagram.com/someuser/"))
    }

    @Test
    fun recognisesShareLinks() {
        assertTrue(InstagramParser.isShareLink("https://www.instagram.com/share/reel/BAbc123xyz/"))
        assertFalse(InstagramParser.isShareLink("https://www.instagram.com/reel/CxAbC12345d/"))
    }

    // ---- Video extraction ----

    @Test
    fun extractsFromDoubleEscapedEmbedJson() {
        // Embed pages carry the data as a JSON string inside JavaScript, so quotes and slashes are escaped twice
        val html = """<script>init({\"video_url\":\"https:\\/\\/scontent.cdninstagram.com\\/v\\/t50\\/clip.mp4?_nc_cat=1\\u0026oe=ABC\",\"x\":1})</script>"""
        assertEquals(
            "https://scontent.cdninstagram.com/v/t50/clip.mp4?_nc_cat=1&oe=ABC",
            InstagramParser.extractVideoUrl(html)
        )
    }

    @Test
    fun extractsFromPlainJson() {
        val html = """{"is_video":true,"video_url":"https:\/\/cdn.example.com\/a.mp4?x=1\u0026y=2","display_url":"https:\/\/cdn.example.com\/a.jpg"}"""
        assertEquals("https://cdn.example.com/a.mp4?x=1&y=2", InstagramParser.extractVideoUrl(html))
    }

    @Test
    fun skipsNullVideoUrlAndKeepsLooking() {
        val html = """{"video_url":null,"other":{"video_url":"https:\/\/cdn.example.com\/b.mp4"}}"""
        assertEquals("https://cdn.example.com/b.mp4", InstagramParser.extractVideoUrl(html))
    }

    @Test
    fun extractsFromVideoVersionsBlock() {
        val html = """{"video_versions":[{"type":101,"width":720,"url":"https:\/\/cdn.example.com\/c.mp4?a=1\u0026b=2"}]}"""
        assertEquals("https://cdn.example.com/c.mp4?a=1&b=2", InstagramParser.extractVideoUrl(html))
    }

    @Test
    fun extractsFromOpenGraphTagInEitherAttributeOrder() {
        val a = """<meta property="og:video" content="https://cdn.example.com/d.mp4?a=1&amp;b=2" />"""
        val b = """<meta content="https://cdn.example.com/d.mp4?a=1&amp;b=2" property="og:video:secure_url">"""
        assertEquals("https://cdn.example.com/d.mp4?a=1&b=2", InstagramParser.extractVideoUrl(a))
        assertEquals("https://cdn.example.com/d.mp4?a=1&b=2", InstagramParser.extractVideoUrl(b))
    }

    @Test
    fun extractsFromVideoTag() {
        val html = """<video class="x" src="https://cdn.example.com/e.mp4?q=1&amp;r=2"></video>"""
        assertEquals("https://cdn.example.com/e.mp4?q=1&r=2", InstagramParser.extractVideoUrl(html))
    }

    @Test
    fun returnsNullWhenThereIsNoVideo() {
        assertNull(InstagramParser.extractVideoUrl("<html><body>Just a photo</body></html>"))
        assertNull(InstagramParser.extractVideoUrl("""{"video_url":null}"""))
    }

    // ---- Misc ----

    @Test
    fun readsTitleAndTruncatesIt() {
        val html = """<meta property="og:title" content="A very nice reel &quot;wow&quot;" />"""
        assertEquals("A very nice reel \"wow\"", InstagramParser.extractTitle(html))
        val long = """<meta property="og:title" content="${"x".repeat(200)}" />"""
        assertEquals(80, InstagramParser.extractTitle(long)?.length)
    }

    @Test
    fun detectsLoginWall() {
        assertTrue(InstagramParser.looksLikeLoginWall("<html><form id=\"loginForm\"></form></html>"))
        assertFalse(InstagramParser.looksLikeLoginWall("""<meta property="og:video" content="https://x/y.mp4">"""))
    }

    // ---- Stories ----

    @Test
    fun understandsUsernamesAndLinks() {
        fun target(t: String) = InstagramParser.parseStoryTarget(t)
        assertEquals(InstagramParser.StoryTarget.User("some.user_1"), target("some.user_1"))
        assertEquals(InstagramParser.StoryTarget.User("some.user_1"), target("  @some.user_1 "))
        assertEquals(InstagramParser.StoryTarget.User("someuser"), target("https://www.instagram.com/someuser/"))
        assertEquals(InstagramParser.StoryTarget.User("someuser"), target("https://instagram.com/someuser/?hl=en"))
        assertEquals(InstagramParser.StoryTarget.User("someuser"), target("https://www.instagram.com/stories/someuser/"))
        assertEquals(
            InstagramParser.StoryTarget.Story("someuser", "3210987654321"),
            target("https://www.instagram.com/stories/someuser/3210987654321/?igsh=abc")
        )
        assertEquals(
            InstagramParser.StoryTarget.Highlight("17890123456"),
            target("https://www.instagram.com/stories/highlights/17890123456/")
        )
    }

    @Test
    fun rejectsThingsThatAreNotUsers() {
        assertNull(InstagramParser.parseStoryTarget(""))
        assertNull(InstagramParser.parseStoryTarget("not a username!"))
        assertNull(InstagramParser.parseStoryTarget("https://www.instagram.com/reel/CxAbC12345d/"))
        assertNull(InstagramParser.parseStoryTarget("https://www.instagram.com/explore/"))
        assertNull(InstagramParser.parseStoryTarget("https://example.com/someuser"))
    }

    @Test
    fun spotsStoryLinks() {
        assertTrue(InstagramParser.isStoryLink("look https://www.instagram.com/stories/someuser/123/ wow"))
        assertFalse(InstagramParser.isStoryLink("https://www.instagram.com/reel/CxAbC12345d/"))
    }

    @Test
    fun readsUserId() {
        assertEquals("12345", InstagramParser.parseUserId("""{"data":{"user":{"id":"12345","username":"x"}},"status":"ok"}"""))
        assertNull(InstagramParser.parseUserId("""{"data":{"user":null}}"""))
        assertNull(InstagramParser.parseUserId("not json"))
    }

    @Test
    fun readsVideoAndPhotoStories() {
        val json = """
            {"reels_media":[{"id":"1","items":[
              {"pk":"222","taken_at":2000,"media_type":2,
               "video_versions":[{"url":"https://cdn.example.com/v.mp4?a=1"}],
               "image_versions2":{"candidates":[{"url":"https://cdn.example.com/big.jpg"},{"url":"https://cdn.example.com/small.jpg"}]}},
              {"pk":"111","taken_at":1000,"media_type":1,
               "image_versions2":{"candidates":[{"url":"https://cdn.example.com/p.jpg"}]}}
            ]}],"status":"ok"}
        """.trimIndent()
        val items = InstagramParser.parseStoryItems(json)
        assertEquals(2, items.size)
        // oldest first
        assertEquals("111", items[0].id)
        assertFalse(items[0].isVideo)
        assertEquals("https://cdn.example.com/p.jpg", items[0].mediaUrl)
        assertEquals("222", items[1].id)
        assertTrue(items[1].isVideo)
        assertEquals("https://cdn.example.com/v.mp4?a=1", items[1].mediaUrl)
        assertEquals("https://cdn.example.com/small.jpg", items[1].thumbnailUrl)
    }

    @Test
    fun readsOtherResponseShapes() {
        val reelsMap = """{"reels":{"9":{"items":[{"pk":"5","taken_at":1,"image_versions2":{"candidates":[{"url":"https://c/x.jpg"}]}}]}}}"""
        val single = """{"reel":{"items":[{"id":"7_99","taken_at":1,"video_versions":[{"url":"https://c/y.mp4"}]}]}}"""
        assertEquals(listOf("5"), InstagramParser.parseStoryItems(reelsMap).map { it.id })
        assertEquals(listOf("7"), InstagramParser.parseStoryItems(single).map { it.id })
        assertTrue(InstagramParser.parseStoryItems("""{"reels_media":[]}""").isEmpty())
        assertTrue(InstagramParser.parseStoryItems("garbage").isEmpty())
    }
}
