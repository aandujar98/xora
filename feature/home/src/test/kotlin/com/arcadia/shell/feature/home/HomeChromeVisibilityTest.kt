package com.arcadia.shell.feature.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeChromeVisibilityTest {

    @Test
    fun startSettingsHidesCollapsedPills() {
        assertTrue(shouldHideHomePillChrome(startSettingsOpen = true))
    }

    @Test
    fun idleHomeKeepsPills() {
        assertFalse(shouldHideHomePillChrome(startSettingsOpen = false))
    }

    @Test
    fun launchPageAndOverlaysHidePills() {
        assertTrue(shouldHideHomePillChrome(startSettingsOpen = false, launchPageOpen = true))
        assertTrue(shouldHideHomePillChrome(startSettingsOpen = false, photosOverlayOpen = true))
        assertTrue(shouldHideHomePillChrome(startSettingsOpen = false, raLibraryOpen = true))
    }
}
