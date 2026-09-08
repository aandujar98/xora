package com.arcadia.shell.feature.home

import com.arcadia.shell.feature.home.component.shouldShowNotificationBanner
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

class NotificationBannerVisibilityTest {

    @Test
    fun bannerShowsWhenActiveAndLtIsCollapsed() {
        assertTrue(
            shouldShowNotificationBanner(
                notificationsEnabled = true,
                hasActive = true,
                ltExpanded = false,
            ),
        )
    }

    @Test
    fun bannerHidesWhenLtIsOpenOrMasterToggleIsOff() {
        assertFalse(
            shouldShowNotificationBanner(
                notificationsEnabled = true,
                hasActive = true,
                ltExpanded = true,
            ),
        )
        assertFalse(
            shouldShowNotificationBanner(
                notificationsEnabled = false,
                hasActive = true,
                ltExpanded = false,
            ),
        )
        assertFalse(
            shouldShowNotificationBanner(
                notificationsEnabled = true,
                hasActive = false,
                ltExpanded = false,
            ),
        )
    }
}
