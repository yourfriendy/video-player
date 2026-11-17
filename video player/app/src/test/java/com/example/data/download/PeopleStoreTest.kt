package com.example.data.download

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ui.components.GestureTourState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PeopleStoreTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun liveAndViewedStoriesSurviveSavingAndLoading() {
        val store = PeopleStore(context)
        store.saveAll(listOf(SavedPerson("sam.k", alerts = true, liveIds = setOf("1", "2"), viewedIds = setOf("1"))))
        val back = store.all().single()
        assertEquals(setOf("1", "2"), back.liveIds)
        assertEquals(setOf("1"), back.viewedIds)
        assertTrue(back.hasUnseenStory)
    }

    @Test
    fun theRingStopsGlowingOnceEveryLiveStoryWasWatched() {
        assertFalse(SavedPerson("a", liveIds = setOf("1"), viewedIds = setOf("1")).hasUnseenStory)
        assertFalse(SavedPerson("a").hasUnseenStory)
        assertTrue(SavedPerson("a", liveIds = setOf("1", "2"), viewedIds = setOf("1")).hasUnseenStory)
    }

    @Test
    fun updatingOnePersonLeavesOthersAlone() {
        val store = PeopleStore(context)
        store.saveAll(listOf(SavedPerson("a"), SavedPerson("b")))
        store.update("a") { it.copy(liveIds = setOf("9")) }
        val all = store.all().associateBy { it.username }
        assertEquals(setOf("9"), all.getValue("a").liveIds)
        assertTrue(all.getValue("b").liveIds.isEmpty())
    }

    @Test
    fun theGestureTourAppearsOnceOnly() {
        // fresh install: it should show; after it was seen, never again
        context.getSharedPreferences("offscroll_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        assertTrue(GestureTourState.shouldShow(context))
        GestureTourState.markSeen(context)
        assertFalse(GestureTourState.shouldShow(context))
    }
}
