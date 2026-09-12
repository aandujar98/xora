package com.arcadia.shell.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StartSettingsUiStateTest {

    @Test
    fun categoryListHasEveryPageAndOpensThatPage() {
        val rows = buildStartSettingsCategoryRows()
        assertEquals(StartSettingsCategory.entries.size, rows.size)
        assertEquals(
            StartSettingsCategory.entries.toList(),
            rows.map { row ->
                val action = (row as StartSettingsRow.Action).action
                (action as StartSettingsAction.OpenCategory).category
            },
        )
        assertEquals("Display", rows.first().title)
        assertEquals("General", rows.last().title)
    }

    @Test
    fun backFromACategoryClosesTheOverlay() {
        assertTrue(startSettingsDismissClosesOverlay(inCategory = true))
        assertTrue(startSettingsDismissClosesOverlay(inCategory = false))
    }

    @Test
    fun titleIsSettingsOnTheRootList() {
        val root = StartSettingsUiState(open = true, inCategory = false)
        assertEquals("Settings", root.title)
        val drilled = root.copy(inCategory = true, category = StartSettingsCategory.Sound)
        assertEquals("Sound", drilled.title)
        assertFalse(root.inCategory)
        assertTrue(drilled.inCategory)
    }

    @Test
    fun displayPageDropsHomeDualScreenRows() {
        val rows = buildStartSettingsRows(
            category = StartSettingsCategory.Display,
            settings = com.arcadia.shell.datastore.ShellSettings(),
            isScraping = false,
            isScanning = false,
            hasCustomBgm = false,
            deviceSuggestsLite = true,
            deviceRamLabel = "5.2 GB RAM",
        )
        val ids = rows.map { it.id }
        assertFalse(ids.contains("switch_mode"))
        assertFalse(ids.contains("second_screen"))
        assertTrue(ids.contains("trailer_display"))
        assertTrue(ids.contains("music_art_backdrop"))
        assertTrue(ids.contains("visual_performance"))
        val musicArt = rows.first { it.id == "music_art_backdrop" } as StartSettingsRow.Toggle
        assertTrue(musicArt.checked)
        assertEquals(StartSettingsAction.ToggleMusicCategoryArt, musicArt.action)
        val performance = rows.first { it.id == "visual_performance" }
        assertEquals("Auto on this device · Performance · 5.2 GB RAM", performance.subtitle)
        assertEquals(
            StartSettingsAction.OpenVisualPerformance,
            (performance as StartSettingsRow.Action).action,
        )
    }

    @Test
    fun notificationsPageIncludesFriendPlayingToggle() {
        val rows = buildStartSettingsRows(
            category = StartSettingsCategory.Notifications,
            settings = com.arcadia.shell.datastore.ShellSettings(),
            isScraping = false,
            isScanning = false,
            hasCustomBgm = false,
        )
        val ids = rows.map { it.id }
        assertTrue(ids.contains("friend_playing"))
        val playing = rows.first { it.id == "friend_playing" } as StartSettingsRow.Toggle
        assertEquals("Friend is playing", playing.title)
        assertTrue(playing.checked)
        assertEquals(StartSettingsAction.ToggleFriendPlaying, playing.action)
    }

    @Test
    fun generalPageDropsXmbTitleStyle() {
        val rows = buildStartSettingsRows(
            category = StartSettingsCategory.General,
            settings = com.arcadia.shell.datastore.ShellSettings(),
            isScraping = false,
            isScanning = false,
            hasCustomBgm = false,
        )
        val ids = rows.map { it.id }
        assertFalse(ids.contains("xmb_title_style"))
        assertTrue(ids.contains("edit_home"))
        assertTrue(ids.contains("all_settings"))
        val emulators = rows.first { it.id == "scan_emulators" }
        assertEquals("Emulators", emulators.title)
        assertTrue(emulators.subtitle.orEmpty().contains("Auto-detects", ignoreCase = true))
    }
}
