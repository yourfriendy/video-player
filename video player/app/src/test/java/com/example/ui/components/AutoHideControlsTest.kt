package com.example.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoHideControlsTest {

    private fun hide(
        playing: Boolean = true,
        visible: Boolean = true,
        search: Boolean = false,
        overlay: Boolean = false,
        grid: Boolean = false,
        menu: Boolean = false
    ) = shouldAutoHideControls(playing, visible, search, overlay, grid, menu)

    @Test fun hidesDuringPlainPlayback() = assertTrue(hide())

    @Test fun neverHidesUnderAnOpenFilterOrFolderMenu() = assertFalse(hide(menu = true))

    @Test fun neverHidesInTheGrid() = assertFalse(hide(grid = true))

    @Test fun neverHidesWhileSearchingOrWithASheetOpen() {
        assertFalse(hide(search = true))
        assertFalse(hide(overlay = true))
    }

    @Test fun nothingToHideWhenPausedOrAlreadyHidden() {
        assertFalse(hide(playing = false))
        assertFalse(hide(visible = false))
    }
}
