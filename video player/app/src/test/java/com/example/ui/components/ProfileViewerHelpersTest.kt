package com.example.ui.components

import com.example.data.download.InstagramParser
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileViewerHelpersTest {

    private fun item(video: Boolean, product: String? = null) = InstagramParser.StoryItem(
        id = "1", isVideo = video, mediaUrl = "u", thumbnailUrl = null, takenAtSec = 0,
        extras = InstagramParser.MediaExtras(productType = product)
    )

    @Test
    fun elapsedTextShowsEveryUnitDownToTheSecond() {
        assertEquals("0 s", elapsedText(0))
        assertEquals("0 s", elapsedText(-5))
        assertEquals("36 s", elapsedText(36))
        assertEquals("2 min 5 s", elapsedText(125))
        assertEquals("2 h 14 min 36 s", elapsedText(8_076))
        assertEquals("1 d 1 h 1 min 1 s", elapsedText(90_061))
    }

    @Test
    fun resolutionTextGivesRatioAndOrientation() {
        assertEquals("1080 × 1920 (9:16, portrait)", resolutionText(1080, 1920))
        assertEquals("1920 × 1080 (16:9, landscape)", resolutionText(1920, 1080))
        assertEquals("1000 × 1000 (1:1, square)", resolutionText(1000, 1000))
    }

    @Test
    fun aStoryIsTaggedStoryPlusItsMedia() {
        assertEquals(listOf("STORY", "VIDEO"), typeTags(item(true), inStories = true, slideIndex = 0, slideCount = 3))
        assertEquals(listOf("STORY", "PHOTO"), typeTags(item(false), inStories = true, slideIndex = 1, slideCount = 3))
    }

    @Test
    fun aReelIsTaggedReelAndAPlainVideoIsTaggedVideo() {
        assertEquals(listOf("REEL"), typeTags(item(true, "clips"), inStories = false, slideIndex = 0, slideCount = 1))
        assertEquals(listOf("VIDEO"), typeTags(item(true), inStories = false, slideIndex = 0, slideCount = 1))
        assertEquals(listOf("PHOTO"), typeTags(item(false), inStories = false, slideIndex = 0, slideCount = 1))
    }

    @Test
    fun aCarouselSlideShowsItsPosition() {
        assertEquals(listOf("CAROUSEL 2/3", "PHOTO"), typeTags(item(false), inStories = false, slideIndex = 1, slideCount = 3))
    }
}
