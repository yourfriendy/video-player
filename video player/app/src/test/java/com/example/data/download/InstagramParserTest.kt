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

    @Test
    fun findsAccountInSearchResults() {
        val json = """{"users":[
            {"position":0,"user":{"pk":"111","username":"someone_else"}},
            {"position":1,"user":{"pk":"222","username":"Target.User"}}
        ]}"""
        assertEquals("222", InstagramParser.parseUserIdFromSearch(json, "target.user"))
        assertNull(InstagramParser.parseUserIdFromSearch(json, "nobody"))
        assertNull(InstagramParser.parseUserIdFromSearch("nope", "x"))
    }

    @Test
    fun findsAccountIdInProfileHtml() {
        assertEquals("333", InstagramParser.parseUserIdFromProfileHtml("""..."profilePage_333"...""", "x"))
        assertEquals("444", InstagramParser.parseUserIdFromProfileHtml("""{"id":"444","username":"abc.def"}""", "abc.def"))
        assertEquals("555", InstagramParser.parseUserIdFromProfileHtml("""{"profile_id":"555"}""", "x"))
        assertNull(InstagramParser.parseUserIdFromProfileHtml("<html></html>", "x"))
    }

    @Test
    fun readsProfileFromWebProfileInfo() {
        val json = """{"data":{"user":{"id":"42","username":"cool.person","full_name":"Cool Person",
            "profile_pic_url_hd":"https://cdn.example.com/hd.jpg","profile_pic_url":"https://cdn.example.com/sd.jpg",
            "is_private":true,"is_verified":true,"edge_followed_by":{"count":12345}}},"status":"ok"}"""
        val p = InstagramParser.parseProfile(json)!!
        assertEquals("42", p.id)
        assertEquals("cool.person", p.username)
        assertEquals("Cool Person", p.fullName)
        assertEquals("https://cdn.example.com/hd.jpg", p.picUrl)
        assertTrue(p.isPrivate)
        assertTrue(p.isVerified)
        assertEquals(12345L, p.followers)
    }

    @Test
    fun profileIsNullWhenInstagramSaysNoUser() {
        assertNull(InstagramParser.parseProfile("""{"data":{"user":null},"status":"ok"}"""))
        assertNull(InstagramParser.parseProfile("""{"message":"login_required"}"""))
        assertNull(InstagramParser.parseProfile("not json"))
    }

    @Test
    fun readsProfileFromSearchAndCurrentUser() {
        val search = """{"users":[{"user":{"pk":"7","username":"Other","full_name":"O"}},
            {"user":{"pk":"9","username":"Target","full_name":"The Target","profile_pic_url":"https://c/x.jpg","follower_count":50}}]}"""
        val hit = InstagramParser.parseProfileFromSearch(search, "target")!!
        assertEquals("9", hit.id)
        assertEquals("The Target", hit.fullName)
        assertEquals(50L, hit.followers)
        assertNull(InstagramParser.parseProfileFromSearch(search, "nobody"))

        val me = InstagramParser.parseCurrentUser("""{"user":{"pk":"1","username":"me","full_name":"Me Myself"},"status":"ok"}""")!!
        assertEquals("me", me.username)
        assertNull(InstagramParser.parseCurrentUser("""{"message":"login_required"}"""))
    }

    @Test
    fun describesRepliesWithoutLeakingContent() {
        assertEquals("an empty reply", InstagramParser.describeReply(""))
        assertEquals("a web page with no account data in it", InstagramParser.describeReply("<html></html>"))
        assertTrue(InstagramParser.describeReply("""{"data":{"user":null},"status":"ok"}""").contains("account: none"))
        assertTrue(InstagramParser.describeReply("""{"message":"Please wait","status":"fail"}""").contains("Please wait"))
    }

    @Test
    fun findsStoriesInsideAnyWrapper() {
        // Newer web pages wrap the very same item objects in differently named containers
        val json = """{"data":{"xdt_api__v1__feed__reels_media":{"reels_media":[{"user":{"pk":"5","username":"zed"},
            "items":[{"pk":"900","taken_at":50,"media_type":2,"video_versions":[{"url":"https://cdn/x.mp4"}],
                      "image_versions2":{"candidates":[{"url":"https://cdn/x.jpg"}]}},
                     {"pk":"901","taken_at":60,"image_versions2":{"candidates":[{"url":"https://cdn/y.jpg"}]}}]}]}}}"""
        val items = InstagramParser.parseStoryItemsDeep(json)
        assertEquals(listOf("900", "901"), items.map { it.id })
        assertTrue(items[0].isVideo)
        assertFalse(items[1].isVideo)
        assertTrue(InstagramParser.parseStoryItemsDeep("""{"data":{"user":null}}""").isEmpty())
        assertTrue(InstagramParser.parseStoryItemsDeep("garbage").isEmpty())
    }

    @Test
    fun findsProfileInsideAnyReply() {
        val json = """{"data":{"x":[{"user":{"pk":"5","username":"zed"}},
            {"user":{"pk":"5","username":"Zed","full_name":"Zed Zed","profile_pic_url":"https://cdn/p.jpg","is_verified":true}}]}}"""
        val p = InstagramParser.parseProfileDeep(json, "zed")!!
        assertEquals("5", p.id)
        assertEquals("Zed Zed", p.fullName)
        assertEquals("https://cdn/p.jpg", p.picUrl)
        assertTrue(p.isVerified)
        assertNull(InstagramParser.parseProfileDeep(json, "nobody"))
    }

    @Test
    fun readsEditFormIdentity() {
        val me = InstagramParser.parseEditForm("""{"form_data":{"username":"me.me","first_name":"Mee"},"status":"ok"}""", "77")!!
        assertEquals("me.me", me.username)
        assertEquals("Mee", me.fullName)
        assertEquals("77", me.id)
        assertNull(InstagramParser.parseEditForm("""{"message":"x"}""", "77"))
    }

    @Test
    fun ignoresOtherPeoplesStoriesAndPosts() {
        // Instagram's story page preloads neighbours' stories; feeds carry the same media fields
        val json = """{"data":{"reels_media":[
          {"id":"1","user":{"pk":"1","username":"target"},"items":[
             {"pk":"10","taken_at":5,"image_versions2":{"candidates":[{"url":"https://cdn/mine.jpg"}]}}]},
          {"id":"2","user":{"pk":"2","username":"neighbour"},"items":[
             {"pk":"20","taken_at":6,"image_versions2":{"candidates":[{"url":"https://cdn/theirs.jpg"}]}}]}],
          "feed":{"items":[{"pk":"30","taken_at":7,"user":{"pk":"3","username":"stranger"},
             "video_versions":[{"url":"https://cdn/post.mp4"}]}]}}}"""
        val mine = InstagramParser.parseStoryItemsForOwner(json, "TARGET", "1")
        assertEquals(listOf("10"), mine.map { it.id })
        // the unscoped reader would have returned all three
        assertEquals(3, InstagramParser.parseStoryItemsDeep(json).size)
        assertTrue(InstagramParser.parseStoryItemsForOwner(json, "nobody", "999").isEmpty())
    }

    @Test
    fun itemOwnerOverridesContainer() {
        val json = """{"reel":{"id":"1","user":{"pk":"1","username":"target"},"items":[
            {"pk":"10","taken_at":1,"user":{"pk":"1","username":"target"},"image_versions2":{"candidates":[{"url":"https://c/a.jpg"}]}},
            {"pk":"11","taken_at":2,"user":{"pk":"2","username":"other"},"image_versions2":{"candidates":[{"url":"https://c/b.jpg"}]}}]}}"""
        assertEquals(listOf("10"), InstagramParser.parseStoryItemsForOwner(json, "target", "1").map { it.id })
    }

    @Test
    fun highlightItemsAreMatchedByHighlightId() {
        val json = """{"reels":{"highlight:17890":{"id":"highlight:17890","items":[
            {"pk":"40","taken_at":1,"image_versions2":{"candidates":[{"url":"https://c/h.jpg"}]}}]},
            "highlight:555":{"id":"highlight:555","items":[
            {"pk":"41","taken_at":2,"image_versions2":{"candidates":[{"url":"https://c/o.jpg"}]}}]}}}"""
        assertEquals(listOf("40"), InstagramParser.parseStoryItemsForOwner(json, "", null, "17890").map { it.id })
    }

    @Test
    fun readsLegacyStoryReply() {
        val json = """{"data":{"reels_media":[{"items":[
            {"id":"70","is_video":true,"video_url":"https://c/v.mp4","display_url":"https://c/d.jpg","taken_at_timestamp":9,
             "owner":{"id":"1","username":"target"},"display_resources":[{"src":"https://c/s.jpg"},{"src":"https://c/l.jpg"}]},
            {"id":"71","is_video":false,"display_url":"https://c/p.jpg","taken_at_timestamp":8,"owner":{"id":"2","username":"other"}}
        ]}]}}"""
        val items = InstagramParser.parseLegacyStoryItems(json, "target", "1")
        assertEquals(listOf("70"), items.map { it.id })
        assertTrue(items[0].isVideo)
        assertEquals("https://c/v.mp4", items[0].mediaUrl)
        assertEquals("https://c/s.jpg", items[0].thumbnailUrl)
    }

    @Test
    fun readsPageTokens() {
        val html = """x ["LSD",[],{"token":"AbC123"},323] y ["DTSGInitialData",[],{"token":"NA:xyz"},258] z"""
        assertEquals("AbC123" to "NA:xyz", InstagramParser.parsePageTokens(html))
        assertNull(InstagramParser.parsePageTokens("""["LSD",[],{"token":"only"}]"""))
    }

    @Test
    fun readsRepostsFromOtherAuthorsOnly() {
        val json = """{"data":{"items":[
          {"pk":"1","code":"AAA","user":{"username":"original"},"video_versions":[{"url":"https://c/a.mp4"}],"taken_at":5},
          {"pk":"2","code":"BBB","user":{"username":"me.self"},"image_versions2":{"candidates":[{"url":"https://c/own.jpg"}]}},
          {"pk":"3","code":"CCC","user":{"username":"another"},"carousel_media":[
              {"image_versions2":{"candidates":[{"url":"https://c/first.jpg"}]}},{"image_versions2":{"candidates":[{"url":"https://c/second.jpg"}]}}]}]}}"""
        val items = InstagramParser.parseRepostItems(json, "Me.Self")
        assertEquals(listOf("1", "3"), items.map { it.id })
        assertEquals("original", items[0].author)
        assertTrue(items[0].isVideo)
        assertEquals("https://c/first.jpg", items[1].mediaUrl)
        assertTrue(InstagramParser.parseRepostItems("garbage", "x").isEmpty())
    }

    @Test
    fun reelPreviewNeedsItsVideoLookedUp() {
        // A reposts-tab entry: a picture, a short code and "this is a reel" — but no video address yet
        val json = """{"items":[{"pk":"900","code":"Cabc123","media_type":2,"product_type":"clips","taken_at":1700000000,
            "user":{"username":"orig","full_name":"Original Person"},"video_duration":14.5,"like_count":120,
            "image_versions2":{"candidates":[{"url":"https://c/thumb.jpg"}]},"caption":{"text":"hello reel"}}]}"""
        val item = InstagramParser.parseRepostItems(json, "me").single()
        assertTrue(item.isVideo)
        assertTrue(item.needsResolve)
        assertEquals("Cabc123", item.code)
        assertEquals("hello reel", item.caption)
        assertEquals(120L, item.likes)
        assertEquals("Original Person", item.authorName)
        assertEquals(14.5, item.durationSec!!, 0.001)
    }

    @Test
    fun resolvedReelGetsItsVideoCaptionAndTimes() {
        val preview = InstagramParser.parseRepostItems(
            """{"items":[{"pk":"900","code":"Cabc123","media_type":2,"taken_at":10,"user":{"username":"orig"},
               "image_versions2":{"candidates":[{"url":"https://c/thumb.jpg"}]}}]}""", "me"
        ).single()
        val full = InstagramParser.parseMediaInfo(
            """{"items":[{"pk":"900","code":"Cabc123","media_type":2,"taken_at":1700000123,"play_count":5000,
               "video_versions":[{"url":"https://c/real.mp4"}],"image_versions2":{"candidates":[{"url":"https://c/t.jpg"}]},
               "caption":{"text":"the caption"},"repost_info":{"reposted_at":1700009999}}]}"""
        )!!
        val merged = InstagramParser.mergeResolved(preview, full)
        assertEquals("https://c/real.mp4", merged.mediaUrl)
        assertFalse(merged.needsResolve)
        assertEquals("the caption", merged.caption)
        assertEquals(1700000123L, merged.takenAtSec)
        assertEquals(1700009999L, merged.repostedAtSec)
        assertEquals(5000L, merged.plays)
        assertEquals("orig", merged.author)
    }

    @Test
    fun readsMentionsAndExpiryOfAStory() {
        val json = """{"reels_media":[{"id":"1","items":[{"pk":"5","taken_at":100,"expiring_at":86500,
            "image_versions2":{"candidates":[{"url":"https://c/s.jpg"}]},
            "reel_mentions":[{"user":{"pk":"9","username":"friend","full_name":"A Friend","profile_pic_url":"https://c/f.jpg"}}]}]}]}"""
        val item = InstagramParser.parseStoryItems(json).single()
        assertEquals(86500L, item.expiresAtSec)
        assertEquals("friend", item.mentions.single().username)
        assertEquals("https://c/f.jpg", item.mentions.single().picUrl)
    }

    @Test
    fun readsBioCountsAndHighlights() {
        val p = InstagramParser.parseProfile(
            """{"data":{"user":{"id":"1","username":"u","biography":"hello bio","external_url":"https://x.dev",
               "edge_followed_by":{"count":10},"edge_follow":{"count":20},"edge_owner_to_timeline_media":{"count":30}}}}"""
        )!!
        assertEquals("hello bio", p.bio)
        assertEquals(20L, p.following)
        assertEquals(30L, p.posts)
        assertEquals("https://x.dev", p.externalUrl)

        val h = InstagramParser.parseHighlightsTray(
            """{"tray":[{"id":"highlight:123","title":"Trip","cover_media":{"cropped_image_version":{"url":"https://c/c.jpg"}}}]}"""
        )
        assertEquals("highlight:123", h.single().id)
        assertEquals("Trip", h.single().title)
        assertEquals("https://c/c.jpg", h.single().coverUrl)
    }

    @Test
    fun readsRepostNoteAndClickTimeFromTheWrapper() {
        val json = """{"data":{"edges":[{"repost_info":{"created_at":1700005000,"note":"so true!"},
            "media":{"pk":"7","code":"Cxyz789","taken_at":1600000000,"user":{"username":"orig"},
              "video_versions":[{"url":"https://c/v.mp4"}],"caption":{"text":"ORIGINAL caption"}}}]}}"""
        val item = InstagramParser.parseRepostItems(json, "me").single()
        assertEquals("so true!", item.repostNote)
        assertEquals(1700005000L, item.repostedAtSec)
        assertEquals("ORIGINAL caption", item.caption)
        assertEquals(1600000000L, item.takenAtSec)
        assertTrue(item.debug!!.contains("repost_info.created_at"))
    }

    @Test
    fun reelCaptionIsNeverTakenAsTheRepostNote() {
        val json = """{"items":[{"pk":"8","code":"Cabc","taken_at":5,"user":{"username":"orig"},
            "video_versions":[{"url":"https://c/v.mp4"}],"caption":{"text":"just the reel caption"}}]}"""
        val item = InstagramParser.parseRepostItems(json, "me").single()
        assertNull(item.repostNote)
        assertNull(item.repostedAtSec)
    }
}
