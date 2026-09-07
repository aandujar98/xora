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
        )
        val ids = rows.map { it.id }
        assertFalse(ids.contains("switch_mode"))
        assertFalse(ids.contains("second_screen"))
        assertTrue(ids.contains("trailer_display"))
        assertTrue(ids.contains("visual_performance"))
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
    }
}
