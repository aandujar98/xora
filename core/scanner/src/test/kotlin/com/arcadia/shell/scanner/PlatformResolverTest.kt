package com.arcadia.shell.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlatformResolverTest {

    private val resolver = PlatformResolver()

    @Test
    fun `exclusive cartridge extensions identify platforms without a folder hint`() {
        assertEquals("nes", resolve("Super Mario Bros.nes")?.id)
        assertEquals("snes", resolve("Chrono Trigger.sfc")?.id)
        assertEquals("n64", resolve("Super Mario 64.z64")?.id)
        assertEquals("n64", resolve("Mario Kart 64.n64")?.id)
        assertEquals("n64", resolve("GoldenEye.v64")?.id)
        assertEquals("gb", resolve("Tetris.gb")?.id)
        assertEquals("gba", resolve("Pokemon Emerald.gba")?.id)
        assertEquals("nds", resolve("Mario Kart DS.nds")?.id)
    }

    @Test
    fun `zipped dumps resolve from console folder names`() {
        assertEquals("nes", resolve("Mario.zip", folders = listOf("NES"))?.id)
        assertEquals("snes", resolve("Zelda.7z", folders = listOf("Super Nintendo"))?.id)
        assertEquals("n64", resolve("Mario 64.zip", folders = listOf("Nintendo 64"))?.id)
        assertEquals("n64", resolve("Zelda.7z", folders = listOf("Ultra64"))?.id)
        assertEquals("gb", resolve("Tetris.zip", folders = listOf("Game Boy"))?.id)
        assertEquals("gba", resolve("Advance Wars.zip", folders = listOf("GBA"))?.id)
        assertEquals("nds", resolve("Brain Age.zip", folders = listOf("Nintendo DS"))?.id)
    }

    @Test
    fun `deepest folder wins over a broader nintendo parent`() {
        assertEquals(
            "snes",
            resolve("Game.sfc", folders = listOf("Nintendo", "SNES"))?.id,
        )
        assertEquals(
            "gba",
            resolve("Game.gba", folders = listOf("Nintendo", "Game Boy Advance"))?.id,
        )
    }

    @Test
    fun `a zip with no platform folder is not guessed`() {
        assertNull(resolve("Mystery.zip", folders = listOf("Downloads")))
    }

    @Test
    fun `forced platform accepts zipped dumps for that system`() {
        val file = scanned("Metroid.zip", folders = emptyList())
        assertEquals("nes", resolver.resolve(file, forcedPlatformId = "nes")?.id)
        assertNull(resolver.resolve(file, forcedPlatformId = "ps2"))
    }

    private fun resolve(
        name: String,
        folders: List<String> = emptyList(),
    ) = resolver.resolve(scanned(name, folders), forcedPlatformId = null)

    private fun scanned(name: String, folders: List<String>) = ScannedFile(
        name = name,
        filePath = "/roms/${folders.joinToString("/")}/$name",
        documentUri = null,
        sizeBytes = 1_024,
        lastModified = 0L,
        folderChain = folders,
    )
}
