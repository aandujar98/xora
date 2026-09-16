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
    fun parseCfgKeepsImgRelativePath() {
        val cfg = """
            overlays = 1
            overlay0_overlay = img/nso-gba.png
            overlay0_full_screen = true
            overlay0_descs = 0
        """.trimIndent()
        assertEquals("img/nso-gba.png", NsoBezelLocator.parseOverlayImagePath(cfg))
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
    fun resolvesNsoPackCfgImgLayout() {
        val root = createTempDirectory("nso-root").toFile()
        try {
            val pack = File(root, "NSO - angel").apply { mkdirs() }
            File(pack, "cfg").mkdirs()
            File(pack, "img").mkdirs()
            File(pack, "cfg/nso-gba.cfg").writeText(
                """
                overlays = 1
                overlay0_overlay = img/nso-gba.png
                overlay0_full_screen = true
                overlay0_descs = 0
                """.trimIndent(),
            )
            File(pack, "img/nso-gba.png").writeBytes(ByteArray(64))
            val rom = File(File(root, "roms").apply { mkdirs() }, "Zelda.gba")
                .apply { writeBytes(ByteArray(4)) }
            val hit = NsoBezelLocator.resolve(
                platformId = "gba",
                coreName = "mgba",
                romFilePath = rom.absolutePath,
                overlaysDir = null,
            )
            assertEquals("nso-gba.png", hit?.name)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun resolvesAppOverlaysCfgImgLayout() {
        val overlays = createTempDirectory("app-overlays").toFile()
        val romDir = createTempDirectory("roms").toFile()
        try {
            File(overlays, "cfg").mkdirs()
            File(overlays, "img").mkdirs()
            File(overlays, "cfg/nso-gbc.cfg").writeText(
                "overlay0_overlay = img/nso-gbc.png\n",
            )
            File(overlays, "img/nso-gbc.png").writeBytes(ByteArray(32))
            val rom = File(romDir, "Tetris.gbc").apply { writeBytes(ByteArray(4)) }
            val hit = NsoBezelLocator.resolve(
                platformId = "gbc",
                coreName = "gambatte",
                romFilePath = rom.absolutePath,
                overlaysDir = overlays,
            )
            assertEquals("nso-gbc.png", hit?.name)
            assertTrue(hit!!.absolutePath.contains("${File.separator}img${File.separator}"))
        } finally {
            overlays.deleteRecursively()
            romDir.deleteRecursively()
        }
    }

    @Test
    fun preferFullPicksFullPng() {
        val pack = createTempDirectory("nso-full").toFile()
        try {
            File(pack, "cfg").mkdirs()
            File(pack, "img").mkdirs()
            File(pack, "cfg/nso-n64.cfg").writeText("overlay0_overlay = img/nso-n64.png\n")
            File(pack, "cfg/nso-n64-full.cfg").writeText("overlay0_overlay = img/nso-n64-full.png\n")
            File(pack, "img/nso-n64.png").writeBytes(ByteArray(8))
            File(pack, "img/nso-n64-full.png").writeBytes(ByteArray(8))
            val rom = File(pack, "Mario.z64").apply { writeBytes(ByteArray(4)) }
            val native = NsoBezelLocator.resolve(
                platformId = "n64",
                coreName = "mupen64plus_next_gles3",
                romFilePath = rom.absolutePath,
                overlaysDir = pack,
                preferFull = false,
            )
            val full = NsoBezelLocator.resolve(
                platformId = "n64",
                coreName = "mupen64plus_next_gles3",
                romFilePath = rom.absolutePath,
                overlaysDir = pack,
                preferFull = true,
            )
            assertEquals("nso-n64.png", native?.name)
            assertEquals("nso-n64-full.png", full?.name)
        } finally {
            pack.deleteRecursively()
        }
    }

    @Test
    fun rejectsNonImages() {
        assertTrue(NsoBezelLocator.isOverlayImage("nso-gba.PNG"))
        assertFalse(NsoBezelLocator.isOverlayImage("nso-gba.cfg"))
    }
}
