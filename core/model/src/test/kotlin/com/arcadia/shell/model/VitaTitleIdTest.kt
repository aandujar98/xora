package com.arcadia.shell.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class VitaTitleIdTest {

    @Test
    fun findsRetailIdsInFilenames() {
        assertEquals("PCSE00546", VitaTitleId.find("[PCSE00546] Retro City.vpk"))
        assertEquals("PCSG00001", VitaTitleId.find("PCSG00001 - Persona 4 Golden"))
        assertEquals("PCSF00243", VitaTitleId.find("folder/pcsf00243/eboot.bin"))
    }

    @Test
    fun ignoresNoiseWithoutAnId() {
        assertNull(VitaTitleId.find("Uncharted Golden Abyss.vpk"))
        assertNull(VitaTitleId.find("vita"))
    }

    @Test
    fun dumpFolderLooksLikeMaidump() {
        assertTrue(VitaGameFolder.looksLikeDump(listOf("eboot.bin", "sce_module")))
        assertTrue(VitaGameFolder.looksLikeDump(listOf("sce_sys", "data")))
        assertFalse(VitaGameFolder.looksLikeDump(listOf("game.vpk", "covers")))
        assertTrue(VitaGameFolder.isTitleIdName("PCSE00546"))
        assertFalse(VitaGameFolder.isTitleIdName("vita"))
    }

    @Test
    fun resolvesFromGameFilename() {
        val game = Game(
            id = "psvita:1",
            title = "Uncharted",
            sortKey = "Uncharted",
            platformId = "psvita",
            fileName = "[PCSE00001] Uncharted.vpk",
            filePath = "/roms/vita/[PCSE00001] Uncharted.vpk",
            documentUri = null,
            sizeBytes = 10L,
        )
        assertEquals("PCSE00001", VitaTitleId.resolve(game))
    }

    @Test
    fun resolvesFromDumpDirectoryName() {
        val dir = File.createTempFile("vita", "dir").apply {
            delete()
            mkdirs()
        }
        val dump = File(dir, "PCSE00546").apply { mkdirs() }
        File(dump, "eboot.bin").writeText("x")
        try {
            val game = Game(
                id = "psvita:2",
                title = "Retro City",
                sortKey = "Retro City",
                platformId = "psvita",
                fileName = "PCSE00546.psvita",
                filePath = dump.absolutePath,
                documentUri = null,
                sizeBytes = 0L,
            )
            assertEquals("PCSE00546", VitaTitleId.resolve(game))
            assertTrue(VitaGameFolder.isGameDirectory(dump))
        } finally {
            dump.deleteRecursively()
            dir.deleteRecursively()
        }
    }
}
