package com.arcadia.shell.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun `the badge strip is only a stop when there is something on it`() {
        assertFalse(
            "an empty strip should not be somewhere the cursor can land",
            buildSystemPanelRows(recentBadgeCount = 0).contains(SystemPanelRow.RecentBadges),
        )
        val rows = buildSystemPanelRows(recentBadgeCount = 3)
        assertTrue(rows.contains(SystemPanelRow.RecentBadges))
        // Between the status bubble and the favorite game, as the card draws it.
        assertEquals(
            rows.indexOf(SystemPanelRow.Status) + 1,
            rows.indexOf(SystemPanelRow.RecentBadges),
        )
        assertEquals(
            rows.indexOf(SystemPanelRow.RecentBadges) + 1,
            rows.indexOf(SystemPanelRow.FavoriteGame),
        )
    }

    @Test
    fun `the favorite picker has no badge strip whatever was earned`() {
        assertFalse(
            buildSystemPanelRows(favoritePickerOpen = true, recentBadgeCount = 6)
                .contains(SystemPanelRow.RecentBadges),
        )
    }
}
