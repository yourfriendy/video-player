package com.example.ui

import com.example.ui.model.MOVIE_MIN_DURATION_MS
import com.example.ui.model.MovieAutoMode
import com.example.ui.model.MovieFit
import com.example.ui.model.formatMovieTime
import com.example.ui.model.isHorizontal
import com.example.ui.model.shouldAutoEnterMovieMode
import com.example.ui.model.skipTarget
import com.example.ui.viewmodel.VideoFeedUiState
import com.example.ui.viewmodel.applyMovieRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MovieModeTest {
    private val long = MOVIE_MIN_DURATION_MS
    private val short = 60_000L

    @Test
    fun horizontalMeansWiderThanTall() {
        assertTrue(isHorizontal(1920, 1080))
        assertFalse(isHorizontal(1080, 1920))
        assertFalse(isHorizontal(1080, 1080))
        assertFalse(isHorizontal(0, 0))
    }

    @Test
    fun longOnlyStartsForWideVideosOfFiveMinutesOrMore() {
        val m = MovieAutoMode.LONG_ONLY
        assertTrue(shouldAutoEnterMovieMode(1920, 1080, long, false, m))
        assertFalse(shouldAutoEnterMovieMode(1920, 1080, long - 1, false, m))
        assertFalse(shouldAutoEnterMovieMode(1080, 1920, long * 3, false, m))
    }

    @Test
    fun allWideStartsForAnyWideVideoButNeverForTallOrSampleOnes() {
        val m = MovieAutoMode.ALL_HORIZONTAL
        assertTrue(shouldAutoEnterMovieMode(1280, 720, short, false, m))
        assertFalse(shouldAutoEnterMovieMode(720, 1280, long, false, m))
        assertFalse(shouldAutoEnterMovieMode(1280, 720, long, true, m))
    }

    @Test
    fun offNeverStartsByItself() {
        assertFalse(shouldAutoEnterMovieMode(1920, 1080, long * 4, false, MovieAutoMode.OFF))
    }

    @Test
    fun skippingBackStopsAtTheStart() {
        assertEquals(0L, skipTarget(4_000, -10_000, 600_000))
        assertEquals(590_000L, skipTarget(600_000, -10_000, 700_000))
    }

    @Test
    fun skippingForwardStopsAtTheEnd() {
        assertEquals(600_000L, skipTarget(595_000, 10_000, 600_000))
        assertEquals(15_000L, skipTarget(5_000, 10_000, 0)) // length unknown: no upper limit
    }

    @Test
    fun timesReadNaturally() {
        assertEquals("0:05", formatMovieTime(5_000))
        assertEquals("1:05", formatMovieTime(65_000))
        assertEquals("1:02:05", formatMovieTime(3_725_000))
        assertEquals("0:00", formatMovieTime(-1))
    }

    @Test
    fun fitCyclesThroughAllThree() {
        assertEquals(MovieFit.STRETCH, MovieFit.FIT.next())
        assertEquals(MovieFit.ZOOM, MovieFit.STRETCH.next())
        assertEquals(MovieFit.FIT, MovieFit.ZOOM.next())
    }

    // ---- the rules that run whenever a new video comes on screen ----

    @Test
    fun aLongWideVideoStartsMovieMode() {
        val s = applyMovieRules(VideoFeedUiState(), 1L, 1920, 1080, long, false)
        assertTrue(s.isMovieMode)
    }

    @Test
    fun aShortWideVideoDoesNotByDefault() {
        val s = applyMovieRules(VideoFeedUiState(), 1L, 1920, 1080, short, false)
        assertFalse(s.isMovieMode)
    }

    @Test
    fun aTallVideoEndsMovieMode() {
        val inMovie = VideoFeedUiState(isMovieMode = true, isMovieLocked = true)
        val s = applyMovieRules(inMovie, 2L, 1080, 1920, long, false)
        assertFalse(s.isMovieMode)
        assertFalse(s.isMovieLocked)
    }

    @Test
    fun staysInMovieModeWhenTheNextVideoIsAlsoWide() {
        val inMovie = VideoFeedUiState(isMovieMode = true)
        assertTrue(applyMovieRules(inMovie, 3L, 1920, 1080, short, false).isMovieMode)
    }

    @Test
    fun leavingMovieModeIsNotUndoneOnTheSameVideo() {
        val left = VideoFeedUiState(movieOptOutVideoId = 5L)
        val sameVideo = applyMovieRules(left, 5L, 1920, 1080, long, false)
        assertFalse(sameVideo.isMovieMode)
        assertEquals(5L, sameVideo.movieOptOutVideoId)
    }

    @Test
    fun swipingToAnotherVideoClearsTheOptOut() {
        val left = VideoFeedUiState(movieOptOutVideoId = 5L)
        val next = applyMovieRules(left, 6L, 1920, 1080, long, false)
        assertNull(next.movieOptOutVideoId)
        assertTrue(next.isMovieMode)
    }
}
