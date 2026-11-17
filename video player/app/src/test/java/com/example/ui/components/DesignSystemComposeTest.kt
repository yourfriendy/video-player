package com.example.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Movie
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.example.ui.model.DownloaderTab
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Compose tests for the shared building blocks every screen is made from. They run on the JVM (Robolectric), so
 * they are part of every CI build. Only components without endless animations are covered here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DesignSystemComposeTest {

    @get:Rule
    val rule = createComposeRule()

    @After
    fun tidy() = Feedback.dismiss()

    @Test
    fun primaryButtonShowsItsTextAndClicks() {
        var clicks = 0
        rule.setContent { DsButton(text = "Save", onClick = { clicks++ }) }
        rule.onNodeWithText("Save").assertIsDisplayed().performClick()
        assertEquals(1, clicks)
    }

    @Test
    fun buttonsAreAtLeastAsTallAsAFingerTip() {
        rule.setContent {
            DsButton(text = "Primary", onClick = {}, modifier = Modifier.testTag("primary"))
            DsButton(text = "Secondary", onClick = {}, style = DsButtonStyle.Secondary, modifier = Modifier.testTag("secondary"))
        }
        rule.onNodeWithTag("primary").assertHeightIsAtLeast(44.dp)
        rule.onNodeWithTag("secondary").assertHeightIsAtLeast(44.dp)
    }

    @Test
    fun theOldButtonNamesStillWorkAsViewsOfTheOneButton() {
        var gradient = 0
        var outline = 0
        rule.setContent {
            GradientButton(text = "Gradient", onClick = { gradient++ })
            OutlineButton(text = "Outline", onClick = { outline++ })
        }
        rule.onNodeWithText("Gradient").performClick()
        rule.onNodeWithText("Outline").performClick()
        assertEquals(1, gradient)
        assertEquals(1, outline)
    }

    @Test
    fun feedbackShowsTheMessage() {
        Feedback.show("Link copied")
        rule.setContent { FeedbackPill() }
        rule.onNodeWithText("Link copied").assertIsDisplayed()
    }

    @Test
    fun feedbackActionRunsAndDismisses() {
        var viewed = 0
        Feedback.show("Saved to your library", "View") { viewed++ }
        rule.setContent { FeedbackPill() }
        rule.onNodeWithText("View").performClick()
        assertEquals(1, viewed)
        assertNull(Feedback.current.value)
    }

    @Test
    fun useCaseTabsNameWhatYouDoAndReportTheChoice() {
        var chosen = DownloaderTab.STORIES
        rule.setContent {
            UseCaseTabs(
                selected = chosen,
                icons = mapOf(
                    DownloaderTab.STORIES to Icons.Default.Group,
                    DownloaderTab.REELS to Icons.Default.Movie,
                    DownloaderTab.ANY to Icons.Default.Link
                ),
                onSelect = { chosen = it }
            )
        }
        rule.onNodeWithText("Watch stories").assertIsDisplayed()
        rule.onNodeWithText("Save videos").assertIsDisplayed()
        rule.onNodeWithText("Save reels").performClick()
        assertEquals(DownloaderTab.REELS, chosen)
    }

    @Test
    fun heroCardShowsTitleAndSubtitle() {
        rule.setContent { HeroCard(icon = Icons.Default.Movie, title = "Save a reel", subtitle = "Paste a link") }
        rule.onNodeWithText("Save a reel").assertIsDisplayed()
        rule.onNodeWithText("Paste a link").assertIsDisplayed()
    }

    @Test
    fun stepsRowNumbersEachStep() {
        rule.setContent { StepsRow(listOf("Copy the link", "Paste it here", "Save")) }
        rule.onNodeWithText("Copy the link").assertIsDisplayed()
        rule.onNodeWithText("3").assertIsDisplayed()
    }

    @Test
    fun avatarFallsBackToTheFirstLetter() {
        rule.setContent { Avatar(url = null, size = 48.dp, initial = "alice") }
        rule.onNodeWithText("A").assertIsDisplayed()
    }

    @Test
    fun statusChipShowsItsLabel() {
        rule.setContent { StatusChip(label = "Notifications", ok = true, icon = Icons.Default.Group) }
        rule.onNodeWithText("Notifications").assertIsDisplayed()
    }
}
