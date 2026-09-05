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
    fun `shared iso files resolve from games and isos folder names`() {
        assertEquals("psp", resolve("Crisis Core.iso", folders = listOf("PSP Games"))?.id)
        assertEquals("psp", resolve("Monster Hunter.cso", folders = listOf("pspgames"))?.id)
        assertEquals("ps2", resolve("Kingdom Hearts.iso", folders = listOf("PS2 ISOs"))?.id)
        assertEquals("ps2", resolve("Shadow of the Colossus.iso", folders = listOf("ps2games"))?.id)
        assertEquals("gamecube", resolve("Wind Waker.iso", folders = listOf("GameCube ISOs"))?.id)
        assertEquals("wii", resolve("Mario Kart.iso", folders = listOf("Wii Games"))?.id)
        assertNull(resolve("Mystery.iso", folders = listOf("ISOs")))
        assertNull(resolve("Mystery.iso", folders = listOf("Games")))
    }

    @Test
    fun `forced platform accepts zipped dumps for that system`() {
        val file = scanned("Metroid.zip", folders = emptyList())
        assertEquals("nes", resolver.resolve(file, forcedPlatformId = "nes")?.id)
        assertNull(resolver.resolve(file, forcedPlatformId = "ps2"))
    }

    @Test
    fun `iso files resolve when the console folder is the library root`() {
        assertEquals(
            "psp",
            resolvePath("Crisis Core.iso", "/storage/emulated/0/ROMS/PSP/Crisis Core.iso")?.id,
        )
        assertEquals(
            "ps2",
            resolvePath("Kingdom Hearts.iso", "/storage/emulated/0/ROMS/PS2/Kingdom Hearts.iso")?.id,
        )
        assertEquals(
            "psp",
            resolvePath("Monster Hunter.iso", "/sdcard/ROMS/PSP/ISO/Monster Hunter.iso")?.id,
        )
        assertEquals(
            "ps2",
            resolvePath("Shadow of the Colossus.iso", "/storage/XXXX-XXXX/ROMS/PS2/Shadow.iso")?.id,
        )
        assertNull(
            resolvePath("Mystery.iso", "/storage/emulated/0/Download/Mystery.iso"),
        )
    }

    @Test
    fun `dump library folder name does not matter only the platform folder does`() {
        listOf(
            "/storage/emulated/0/My Games/PSP/Crisis Core.iso" to "psp",
            "/storage/emulated/0/Emulation/PS2/Kingdom Hearts.iso" to "ps2",
            "/sdcard/ISO Library/GameCube/Wind Waker.iso" to "gamecube",
            "/storage/XXXX-XXXX/dumps/Wii/Mario Kart.iso" to "wii",
            "/storage/emulated/0/Retro/PSP Games/Monster Hunter.iso" to "psp",
            "/storage/emulated/0/anything/PS2 ISOs/Shadow.iso" to "ps2",
        ).forEach { (path, platformId) ->
            assertEquals(path, platformId, resolvePath(path.substringAfterLast('/'), path)?.id)
        }
        assertNull(
            resolvePath("Loose.iso", "/storage/emulated/0/My Games/Loose.iso"),
        )
        assertNull(
            resolvePath("Loose.iso", "/storage/emulated/0/Emulation/Loose.iso"),
        )
    }

    @Test
    fun `iso files resolve from the added folder name when the walk chain is empty`() {
        val file = ScannedFile(
            name = "Crisis Core.iso",
            filePath = null,
            documentUri = "content://com.android.externalstorage.documents/tree/primary%3AROMS%2FPSP/document/primary%3AROMS%2FPSP%2FCrisis%20Core.iso",
            sizeBytes = 1_024,
            lastModified = 0L,
            folderChain = emptyList(),
        )
        assertEquals(
            "psp",
            resolver.resolve(
                file,
                forcedPlatformId = null,
                rootLabel = "PSP",
                rootLocation = "content://com.android.externalstorage.documents/tree/primary%3AROMS%2FPSP",
            )?.id,
        )
        assertEquals(
            "ps2",
            resolver.resolve(
                scanned("Kingdom Hearts.iso", folders = emptyList(), filePath = null),
                forcedPlatformId = null,
                rootLabel = "PS2",
                rootLocation = "/storage/emulated/0/ROMS/PS2",
            )?.id,
        )
    }

    private fun resolve(
        name: String,
        folders: List<String> = emptyList(),
    ) = resolver.resolve(scanned(name, folders), forcedPlatformId = null)

    private fun resolvePath(name: String, filePath: String) = resolver.resolve(
        scanned(name, folders = emptyList(), filePath = filePath),
        forcedPlatformId = null,
    )

    private fun scanned(
        name: String,
        folders: List<String>,
        filePath: String? = "/roms/${folders.joinToString("/")}/$name",
    ) = ScannedFile(
        name = name,
        filePath = filePath,
        documentUri = null,
        sizeBytes = 1_024,
        lastModified = 0L,
        folderChain = folders,
    )
}
