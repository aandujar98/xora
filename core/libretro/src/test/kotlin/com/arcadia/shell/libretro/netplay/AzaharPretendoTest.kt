package com.arcadia.shell.libretro.netplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AzaharPretendoTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun scanReportsMissingNandAndNimbusOnEmptySaveDir() {
        val saveDir = tmp.newFolder("3ds")
        val ui = AzaharPretendo.scan(saveDir, prepEnabled = true)
        assertFalse(ui.nandPresent)
        assertFalse(ui.nimbusPatches)
        assertTrue(ui.prepEnabled)
        assertTrue(ui.userDir.endsWith("Azahar"))
        assertTrue(ui.overlaySubtitle().contains("NAND", ignoreCase = true))
        assertTrue(ui.overlaySubtitle().contains("Nimbus", ignoreCase = true))
    }

    @Test
    fun hasNandRequiresNamedNandFoldersOrFiles() {
        val saveDir = tmp.newFolder("3ds")
        val nand = AzaharPretendo.nandDir(saveDir)
        assertFalse(AzaharPretendo.hasNand(nand))
        nand.mkdirs()
        assertFalse(AzaharPretendo.hasNand(nand))
        File(nand, "data").mkdirs()
        assertTrue(AzaharPretendo.hasNand(nand))
    }

    @Test
    fun hasNimbusPatchesLooksUnderSdmcLuma() {
        val saveDir = tmp.newFolder("3ds")
        val sdmc = AzaharPretendo.sdmcDir(saveDir)
        assertFalse(AzaharPretendo.hasNimbusPatches(sdmc))
        File(sdmc, "luma/titles").mkdirs()
        File(sdmc, "luma/titles/0004000000000000").mkdirs()
        assertTrue(AzaharPretendo.hasNimbusPatches(sdmc))
    }

    @Test
    fun ensureDirsCreatesNandAndSdmcUnderAzahar() {
        val saveDir = tmp.newFolder("3ds")
        val root = AzaharPretendo.ensureDirs(saveDir)
        assertEquals(AzaharPretendo.userDir(saveDir).absolutePath, root.absolutePath)
        assertTrue(AzaharPretendo.nandDir(saveDir).isDirectory)
        assertTrue(AzaharPretendo.sdmcDir(saveDir).isDirectory)
    }

    @Test
    fun coreOptionsPinNew3dsVirtualSdAndLibretroSavePath() {
        val opts = AzaharPretendo.coreOptions()
        assertEquals("New 3DS", opts["azahar_is_new_3ds"])
        assertEquals("enabled", opts["azahar_use_virtual_sd"])
        assertEquals("LibRetro Default", opts["azahar_use_libretro_save_path"])
        assertEquals("New 3DS", opts["citra_is_new_3ds"])
        assertEquals("enabled", opts["citra_use_virtual_sd"])
        assertEquals("LibRetro Default", opts["citra_use_libretro_save_path"])
    }

    @Test
    fun overlaySubtitleDoesNotClaimADnsLobby() {
        val idle = AzaharPretendoUi().overlaySubtitle()
        assertTrue(idle.contains("Nimbus"))
        assertTrue(idle.contains("DNS"))
        val ready = AzaharPretendoUi(nandPresent = true, nimbusPatches = true).overlaySubtitle()
        assertTrue(ready.contains("standalone Azahar"))
        assertFalse(ready.contains("lobby", ignoreCase = true))
    }
}
