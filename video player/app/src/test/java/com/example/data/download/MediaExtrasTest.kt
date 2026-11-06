package com.example.data.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaExtrasTest {

    private val reel = """
        {
          "product_type": "clips", "media_type": 2, "original_width": 1080, "original_height": 1920, "has_audio": true,
          "caption": {"text": "Sunset #vibes #Chill see https://example.com/a."},
          "location": {"name": "Marine Drive", "city": "Mumbai"},
          "clips_metadata": {"music_info": {"music_asset_info": {"title": "Song", "display_artist": "Artist"}}},
          "usertags": {"in": [{"user": {"pk": "11", "username": "alice", "full_name": "Alice"}}]},
          "coauthor_producers": [{"username": "bob"}],
          "is_paid_partnership": true,
          "story_hashtags": [{"hashtag": {"name": "night"}}],
          "story_polls": [{}]
        }
    """.trimIndent()

    @Test
    fun readsTypeSizeAudioAndPlace() {
        val x = InstagramParser.parseExtras(reel)
        assertEquals("clips", x.productType)
        assertEquals(2, x.mediaType)
        assertEquals(1080, x.width)
        assertEquals(1920, x.height)
        assertEquals(true, x.hasAudio)
        assertEquals("Marine Drive, Mumbai", x.location)
    }

    @Test
    fun readsMusicPeopleAndPartnership() {
        val x = InstagramParser.parseExtras(reel)
        assertEquals("Song, Artist", x.music)
        assertEquals(listOf("alice"), x.taggedUsers.map { it.username })
        assertEquals(listOf("bob"), x.coauthors)
        assertTrue(x.paidPartnership)
    }

    @Test
    fun collectsHashtagsLinksAndStickers() {
        val x = InstagramParser.parseExtras(reel)
        assertEquals(listOf("night", "vibes", "Chill"), x.hashtags)
        assertEquals(listOf("https://example.com/a"), x.links)
        assertTrue(x.stickers.contains("Poll"))
        assertTrue(x.stickers.contains("Hashtag"))
    }

    @Test
    fun missingFieldsStayEmptyInsteadOfInventedValues() {
        val x = InstagramParser.parseExtras("""{"media_type": 1}""")
        assertNull(x.location)
        assertNull(x.music)
        assertNull(x.hasAudio)
        assertNull(x.width)
        assertTrue(x.hashtags.isEmpty())
        assertTrue(x.taggedUsers.isEmpty())
    }

    @Test
    fun aCarouselSlideInheritsThePostsPlaceAndMusic() {
        val post = InstagramParser.parseExtras(reel)
        val slide = InstagramParser.MediaExtras(mediaType = 1).withParent(post)
        assertEquals("Marine Drive, Mumbai", slide.location)
        assertEquals("Song, Artist", slide.music)
        assertEquals(1, slide.mediaType)
    }

    @Test
    fun brokenJsonGivesEmptyExtras() {
        assertEquals(InstagramParser.MediaExtras(), InstagramParser.parseExtras("not json"))
    }
}
