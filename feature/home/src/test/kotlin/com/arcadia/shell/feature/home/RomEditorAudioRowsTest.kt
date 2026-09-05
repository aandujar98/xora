package com.arcadia.shell.feature.home

import com.arcadia.shell.model.Game
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RomEditorAudioRowsTest {

    @Test
    fun removeSoundBiteRowIsAlwaysPresent() {
        val rows = audioRows(sampleGame(), actions())
        assertTrue(rows.any { it.key == "removebite" && it.label == "Remove sound bite" })
        assertNull(rows.single { it.key == "removebite" }.onActivate)
    }

    @Test
    fun importedBiteExposesRemoveActionEvenIfFileIsMissing() {
        val rows = audioRows(sampleGame(soundBitePath = "/tmp/does-not-exist.mp3"), actions())
        val remove = rows.single { it.key == "removebite" }
        assertNotNull(remove.onActivate)
        assertNotNull(remove.onClear)
        assertEquals("Set", remove.value)
        assertNotNull(rows.single { it.key == "bite" }.onClear)
    }

    @Test
    fun sidecarBiteExposesRemoveAction() {
        val dir = kotlin.io.path.createTempDirectory("xora-editor-bite").toFile()
        try {
            val rom = java.io.File(dir, "Zelda.nds").apply { writeText("rom") }
            java.io.File(dir, "Zelda.mp3").apply { writeText("audio") }
            val rows = audioRows(
                sampleGame(filePath = rom.absolutePath, fileName = rom.name, title = "Zelda"),
                actions(),
            )
            assertNotNull(rows.single { it.key == "removebite" }.onActivate)
            assertEquals("Found beside ROM", rows.single { it.key == "bite" }.value)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun sampleGame(
        soundBitePath: String? = null,
        filePath: String? = null,
        title: String = "Test Game",
        fileName: String = "Test Game.nds",
    ) = Game(
        id = "nds:test",
        title = title,
        sortKey = title,
        platformId = "nds",
        fileName = fileName,
        filePath = filePath,
        documentUri = null,
        sizeBytes = 1L,
        soundBitePath = soundBitePath,
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
