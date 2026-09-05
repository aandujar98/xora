package com.arcadia.shell.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlatformCatalogTest {

    @Test
    fun `platform ids are unique`() {
        val duplicates = PlatformCatalog.platforms
            .groupBy { it.id }
            .filterValues { it.size > 1 }
            .keys

        assertEquals(emptySet<String>(), duplicates)
    }

    @Test
    fun `folder aliases are matched past case spaces and separators`() {
        assertEquals("wiiu", PlatformCatalog.byFolderName("Wii U")?.id)
        assertEquals("neogeo", PlatformCatalog.byFolderName("Neo-Geo")?.id)
        assertEquals("snes", PlatformCatalog.byFolderName("SuperFamicom")?.id)
        assertEquals("nds", PlatformCatalog.byFolderName("Nintendo DS")?.id)
        assertEquals("nes", PlatformCatalog.byFolderName("Nintendo Entertainment System")?.id)
        assertEquals("snes", PlatformCatalog.byFolderName("Super Nintendo Entertainment System")?.id)
        assertEquals("gb", PlatformCatalog.byFolderName("Game Boy")?.id)
        assertEquals("gbc", PlatformCatalog.byFolderName("Game Boy Color")?.id)
        assertEquals("gba", PlatformCatalog.byFolderName("Game Boy Advance")?.id)
        assertEquals("nds", PlatformCatalog.byFolderName("Nintendo DS Lite")?.id)
        assertEquals("n64", PlatformCatalog.byFolderName("Nintendo 64")?.id)
        assertEquals("n64", PlatformCatalog.byFolderName("Ultra64")?.id)
        assertEquals("n64", PlatformCatalog.byFolderName("Nintendo U64")?.id)
    }

    @Test
    fun `cartridge nintendo platforms accept zipped dumps`() {
        listOf("nes", "snes", "n64", "gb", "gbc", "gba", "nds").forEach { id ->
            val platform = PlatformCatalog.byId(id)!!
            assertTrue(
                "$id should list zip so scans and launchers accept No-Intro archives",
                "zip" in platform.extensions && "7z" in platform.extensions,
            )
        }
    }

    @Test
    fun `an unknown folder name matches nothing`() {
        assertNull(PlatformCatalog.byFolderName("screenshots"))
        assertNull(PlatformCatalog.byFolderName("Games"))
        assertNull(PlatformCatalog.byFolderName("ISOs"))
        assertNull(PlatformCatalog.byFolderName("ROMs"))
    }

    @Test
    fun `console folders accept games isos and roms suffixes`() {
        listOf(
            "PSP Games" to "psp",
            "pspgames" to "psp",
            "PSP ISOs" to "psp",
            "psp iso" to "psp",
            "PS2 Games" to "ps2",
            "ps2games" to "ps2",
            "PS2 ISOs" to "ps2",
            "ps2isos" to "ps2",
            "PlayStation 2 Games" to "ps2",
            "PlayStation Portable Games" to "psp",
            "GameCube ISOs" to "gamecube",
            "GC Games" to "gamecube",
            "Wii Games" to "wii",
            "SNES ROMs" to "snes",
            "N64 Games" to "n64",
            "GB Games" to "gb",
            "GBC Games" to "gbc",
            "GBA ROMs" to "gba",
            "PS1 ISOs" to "ps1",
            "PS3 Games" to "ps3",
            "Dreamcast ISOs" to "dreamcast",
            "ROMs PSP" to "psp",
            "ISO PS2" to "ps2",
        ).forEach { (folder, platformId) ->
            assertEquals(folder, platformId, PlatformCatalog.byFolderName(folder)?.id)
        }
    }

    @Test
    fun `every platform alias still matches when games or isos is glued on`() {
        PlatformCatalog.platforms.forEach { platform ->
            val alias = platform.folderAliases.first()
            listOf("${alias}games", "${alias} Games", "${alias}isos", "$alias ISOs", "${alias}roms")
                .forEach { folder ->
                    assertEquals(
                        "$folder should stay ${platform.id}",
                        platform.id,
                        PlatformCatalog.byFolderName(folder)?.id,
                    )
                }
        }
    }

    @Test
    fun `suffix words do not steal gamecube game boy or playstation portable`() {
        assertEquals("gamecube", PlatformCatalog.byFolderName("GameCube")?.id)
        assertEquals("gamecube", PlatformCatalog.byFolderName("GameCube Games")?.id)
        assertEquals("gb", PlatformCatalog.byFolderName("Game Boy")?.id)
        assertEquals("gbc", PlatformCatalog.byFolderName("Game Boy Color")?.id)
        assertEquals("gbc", PlatformCatalog.byFolderName("Game Boy Color Games")?.id)
        assertEquals("gba", PlatformCatalog.byFolderName("Game Boy Advance")?.id)
        assertEquals("gamegear", PlatformCatalog.byFolderName("Game Gear")?.id)
        assertEquals("psp", PlatformCatalog.byFolderName("PlayStation Portable")?.id)
        assertEquals("ps2", PlatformCatalog.byFolderName("PlayStation 2")?.id)
        assertEquals("ps1", PlatformCatalog.byFolderName("PlayStation")?.id)
    }

    /**
     * The scanner leans on this to identify a file whose folder gives no hint, so an extension that
     * several systems share must not resolve on its own.
     */
    @Test
    fun `shared container extensions are not exclusive`() {
        listOf("iso", "cue", "chd", "bin", "zip").forEach { extension ->
            assertNull(
                "$extension is claimed by several systems and must not identify one",
                PlatformCatalog.byExclusiveExtension(extension),
            )
        }
    }

    @Test
    fun `distinctive extensions identify their platform`() {
        assertEquals("n64", PlatformCatalog.byExclusiveExtension("z64")?.id)
        assertEquals("n64", PlatformCatalog.byExclusiveExtension("n64")?.id)
        assertEquals("n64", PlatformCatalog.byExclusiveExtension("v64")?.id)
        assertEquals("gba", PlatformCatalog.byExclusiveExtension("gba")?.id)
        assertEquals("snes", PlatformCatalog.byExclusiveExtension("SFC")?.id)
        assertEquals("dreamcast", PlatformCatalog.byExclusiveExtension("gdi")?.id)
    }

    @Test
    fun `every platform contributes at least one extension`() {
        val withoutExtensions = PlatformCatalog.platforms.filter { it.extensions.isEmpty() }

        assertTrue(withoutExtensions.map { it.id }.toString(), withoutExtensions.isEmpty())
    }

    @Test
    fun `an unknown id falls back rather than throwing`() {
        assertEquals(GamePlatform.Unknown, PlatformCatalog.requireById("not-a-system"))
    }

    @Test
    fun `android is resolvable but not part of the ROM catalog`() {
        assertEquals(GamePlatform.Android, PlatformCatalog.requireById(GamePlatform.Android.id))
        assertTrue(PlatformCatalog.platforms.none { it.id == GamePlatform.Android.id })
    }
}
