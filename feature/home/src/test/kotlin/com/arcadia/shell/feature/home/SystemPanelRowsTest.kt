package com.arcadia.shell.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test

class SystemPanelRowsTest {

    @Test
    fun `favorite picker lists library games rather than RetroAchievements ids`() {
        val rows = buildSystemPanelRows(
            favoritePickerOpen = true,
            favoritePickerGameIds = listOf("n64:oot", "ps2:sly2"),
        )
        assertEquals(
            listOf(
                SystemPanelRow.ClearFavorite,
                SystemPanelRow.LibraryFavoritePick("n64:oot"),
                SystemPanelRow.LibraryFavoritePick("ps2:sly2"),
            ),
            rows,
        )
    }
}
