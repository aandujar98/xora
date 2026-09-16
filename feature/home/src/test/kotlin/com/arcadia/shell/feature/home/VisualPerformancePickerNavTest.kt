package com.arcadia.shell.feature.home

import com.arcadia.shell.datastore.VisualPerformanceMode
import com.arcadia.shell.input.NavAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualPerformancePickerNavTest {

    @Test
    fun upAndDownStayOnTheThreeChoices() {
        assertEquals(0, visualPerformancePickerIndex(NavAction.Up, current = 0))
        assertEquals(1, visualPerformancePickerIndex(NavAction.Down, current = 0))
        assertEquals(2, visualPerformancePickerIndex(NavAction.Down, current = 1))
        assertEquals(2, visualPerformancePickerIndex(NavAction.Down, current = 2))
        assertEquals(VisualPerformanceMode.Auto, visualPerformancePickerMode(0))
        assertEquals(VisualPerformanceMode.Smooth, visualPerformancePickerMode(1))
        assertEquals(VisualPerformanceMode.Quality, visualPerformancePickerMode(2))
        assertEquals(1, visualPerformancePickerFocusIndex(VisualPerformanceMode.Smooth))
    }
}

class HomeWallpaperLiteFallbackTest {

    @Test
    fun liteVideoUsesTheStaticPlateUnlessAStillIsPicked() {
        assertTrue(
            shouldUseLiteStaticWallpaper(
                lite = true,
                customIsVideo = false,
                hasCustomStill = false,
                themeIsVideo = true,
            ),
        )
        assertTrue(
            shouldUseLiteStaticWallpaper(
                lite = true,
                customIsVideo = true,
                hasCustomStill = false,
                themeIsVideo = false,
            ),
        )
        assertFalse(
            shouldUseLiteStaticWallpaper(
                lite = true,
                customIsVideo = false,
                hasCustomStill = true,
                themeIsVideo = true,
            ),
        )
        assertFalse(
            shouldUseLiteStaticWallpaper(
                lite = false,
                customIsVideo = false,
                hasCustomStill = false,
                themeIsVideo = true,
            ),
        )
    }
}
