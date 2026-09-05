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
    fun titleIsSettingsOnTheRootList() {
        val root = StartSettingsUiState(open = true, inCategory = false)
        assertEquals("Settings", root.title)
        val drilled = root.copy(inCategory = true, category = StartSettingsCategory.Sound)
        assertEquals("Sound", drilled.title)
        assertFalse(root.inCategory)
        assertTrue(drilled.inCategory)
    }
}
