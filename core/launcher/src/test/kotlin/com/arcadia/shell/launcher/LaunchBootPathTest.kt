package com.arcadia.shell.launcher

import com.arcadia.shell.model.Game
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LaunchBootPathTest {

    @Test
    fun prefersFilesystemPathOverDocumentUri() {
        val path = "/storage/emulated/0/Roms/ps2/Shadow of the Colossus.iso"
        val resolved = LaunchBootPath.resolve(
            sample(
                filePath = path,
                documentUri = "content://com.android.externalstorage.documents/document/primary%3ARoms",
            ),
        )
        assertEquals(path, resolved)
        assertFalse(resolved.startsWith("content://"))
    }

    @Test
    fun neverWrapsPathInFileProviderUri() {
        val path = "/storage/emulated/0/Roms/ps2/Game.iso"
        val resolved = LaunchBootPath.resolve(sample(filePath = path, documentUri = null))
        assertEquals(path, resolved)
        assertFalse(resolved.contains("com.sora.shell.files"))
        assertFalse(resolved.contains("rom_library"))
    }

    @Test
    fun fallsBackToSafDocumentUriWhenThereIsNoPath() {
        val uri = "content://com.android.externalstorage.documents/document/primary%3ARoms%2Fps2%2FGame.iso"
        assertEquals(uri, LaunchBootPath.resolve(sample(filePath = null, documentUri = uri)))
    }

    @Test
    fun ps2RecipesUseBootpathNotFileUri() {
        listOf("nethersx2.ps2", "aethersx2.ps2").forEach { id ->
            val args = BuiltInPlayers.all.first { it.uniqueId == id }.amStartArguments
            assertTrue(args.contains("bootPath {file.bootpath}"))
            assertFalse(args.contains("bootPath {file.uri}"))
        }
    }

    @Test(expected = MissingPlaceholderException::class)
    fun failsWhenGameHasNeitherPathNorDocument() {
        LaunchBootPath.resolve(sample(filePath = null, documentUri = null))
    }

    private fun sample(filePath: String?, documentUri: String?) = Game(
        id = "ps2:test",
        title = "Test Game",
        sortKey = "Test Game",
        platformId = "ps2",
        fileName = "Game.iso",
        filePath = filePath,
        documentUri = documentUri,
        sizeBytes = 1L,
    )
}
