package com.arcadia.shell.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The Manual section is what 0.4.1 → 0.5.6 added on top of the scrape-only manual pipeline, so
 * these pin the wording and the one piece of behaviour that is easy to get backwards: Clear is
 * offered only when there is actually a manual to clear.
 */
class RomEditorManualRowsTest {

    @Test
    fun withoutAManualTheRowReadsNoneAndCannotBeCleared() {
        val rows = manualRows(manualPath = null, actions = actions())
        val manual = rows.first { it.key == "manual" }

        assertEquals("Game Manual", manual.label)
        assertEquals("None", manual.value)
        assertNull(manual.onClear)
    }

    @Test
    fun withAManualTheRowReadsAttachedAndOffersClear() {
        val rows = manualRows(manualPath = "/data/manuals/ab/cd.pdf", actions = actions())
        val manual = rows.first { it.key == "manual" }

        assertEquals("Attached", manual.value)
        assertNotNull(manual.onClear)
    }

    @Test
    fun theSectionOffersPickAndScrapeAlongsideTheManualRow() {
        val keys = manualRows(manualPath = null, actions = actions()).map { it.key }

        assertEquals(listOf("manual", "manualpick", "manualscrape"), keys)
    }

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
