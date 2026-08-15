package com.arcadia.shell.libretro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class NsoBezelLocatorTest {

    @Test
    fun parseCfgOverlayName() {
        val cfg = """
            overlays = 1
            overlay0_overlay = nso-gba.png
            overlay0_full_screen = true
        """.trimIndent()
        assertEquals("nso-gba.png", NsoBezelLocator.parseOverlayImageName(cfg))
    }

    @Test
    fun parseCfgStripsQuotes() {
        assertEquals(
            "nso-n64.png",
            NsoBezelLocator.parseOverlayImageName("overlay0_overlay = \"nso-n64.png\""),
        )
    }

    @Test
    fun findsPngNextToRom() {
        val dir = createTempDirectory("nso-bezel").toFile()
        try {
            File(dir, "Mario Kart 64.z64").writeBytes(ByteArray(8))
            File(dir, "nso-n64.png").writeBytes(ByteArray(32))
            val hit = NsoBezelLocator.resolve(
                platformId = "n64",
                coreName = "mupen64plus_next_gles3",
                romFilePath = File(dir, "Mario Kart 64.z64").absolutePath,
                overlaysDir = null,
            )
            assertEquals("nso-n64.png", hit?.name)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun prefersCfgNamedImageInOverlaysFolder() {
        val root = createTempDirectory("nso-pack").toFile()
        try {
            val overlays = File(root, "overlays").apply { mkdirs() }
            File(overlays, "nso-gbc.cfg").writeText("overlay0_overlay = custom-gbc.png\n")
            File(overlays, "custom-gbc.png").writeBytes(ByteArray(16))
            val hit = NsoBezelLocator.resolve(
                platformId = "gbc",
                coreName = "gambatte",
                romFilePath = File(root, "Tetris.gbc").apply { writeBytes(ByteArray(4)) }.absolutePath,
                overlaysDir = null,
            )
            assertEquals("custom-gbc.png", hit?.name)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsNonImages() {
        assertTrue(NsoBezelLocator.isOverlayImage("nso-gba.PNG"))
        assertFalse(NsoBezelLocator.isOverlayImage("nso-gba.cfg"))
    }
}
