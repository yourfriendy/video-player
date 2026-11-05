package com.example.data.download

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InstagramNotificationListenerTest {

    private fun person(username: String, fullName: String = "") = SavedPerson(username = username, fullName = fullName)

    @Test
    fun matchesTheUsernameAsAWholeWord() {
        assertTrue(InstagramNotificationListener.mentions("sam.k added to their story", person("sam.k")))
        assertTrue(InstagramNotificationListener.mentions("new reel from sam.k", person("sam.k")))
    }

    @Test
    fun doesNotMatchInsideAnotherUsername() {
        assertFalse(InstagramNotificationListener.mentions("sam.kapoor added to their story", person("sam.k")))
        assertFalse(InstagramNotificationListener.mentions("mysam.k posted", person("sam.k")))
    }

    @Test
    fun matchesTheDisplayNameWhenInstagramShowsThatInstead() {
        assertTrue(InstagramNotificationListener.mentions("sam kapoor added to their story", person("sam.k", "Sam Kapoor")))
    }

    @Test
    fun shortOrEmptyNamesNeverMatchEverything() {
        assertFalse(InstagramNotificationListener.mentions("anything at all", person("zz", "")))
        assertFalse(InstagramNotificationListener.mentions("ab", person("sam.k", "ab")))
    }
}
