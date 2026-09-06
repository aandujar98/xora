package com.arcadia.shell.feature.home

import com.arcadia.shell.model.Game
import com.arcadia.shell.scraper.ArtSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RomEditorArtworkRowsTest {

    @Test
    fun artworkSectionListsShortcutIconAfterLogo() {
        val rows = artworkRows(
            game = sample(shortcutIconPath = "/icon.png"),
            artAlignX = 0f,
            artAlignY = 0f,
            screenshotCount = 0,
            onOpenArtPicker = {},
            actions = actions(),
        )
        val keys = rows.map { it.key }
        assertTrue(keys.contains("shortcuticon"))
        assertEquals(keys.indexOf("logo") + 1, keys.indexOf("shortcuticon"))
        val row = rows.single { it.key == "shortcuticon" }
        assertEquals("Shortcut icon", row.label)
        assertEquals("Set", row.value)
        assertNotNull(row.onClear)
    }

    @Test
    fun emptyShortcutIconHasNoClearAction() {
        val rows = artworkRows(
            game = sample(),
            artAlignX = 0f,
            artAlignY = 0f,
            screenshotCount = 0,
            onOpenArtPicker = {},
            actions = actions(),
        )
        val row = rows.single { it.key == "shortcuticon" }
        assertEquals("None", row.value)
        assertNull(row.onClear)
        assertEquals(ArtSlot.ShortcutIcon.label, "Shortcut icon")
    }

    private fun sample(shortcutIconPath: String? = null) = Game(
        id = "nds:test",
        title = "Test Game",
        sortKey = "Test Game",
        platformId = "nds",
        fileName = "Test Game.nds",
        filePath = null,
        documentUri = null,
        sizeBytes = 1L,
        shortcutIconPath = shortcutIconPath,
    )

    private fun actions() = RomEditorActions(
        onDismiss = {},
        onRename = {},
        onResetName = {},
        onToggleFavorite = {},
        onToggleHidden = {},
        onUploadArt = {},
        onApplyCandidate = { _, _ -> },
        onClearArt = {},
        onNudgeCover = { _, _ -> },
        onResetCover = {},
        onPickSoundBite = {},
        onClearSoundBite = {},
        onPreviewSoundBite = {},
        onUploadTrailer = {},
        onUseYouTubeTrailer = {},
        onClearTrailer = {},
        onImportSaves = {},
        onDeleteSave = {},
        onSetGamePreference = {},
        onSetPlatformPreference = {},
        onChooseEmulator = {},
        onRescrapeGame = {},
        onRescrapePlatform = {},
    )
}
