package com.arcadia.shell.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RetroArchCoreScannerTest {

    @Test
    fun `parseCoreName strips libretro android suffix`() {
        assertEquals(
            "mupen64plus_next",
            RetroArchCoreScanner.parseCoreName("mupen64plus_next_libretro_android.so"),
        )
        assertEquals(
            "parallel_n64",
            RetroArchCoreScanner.parseCoreName("parallel_n64_libretro_android.so"),
        )
        assertNull(RetroArchCoreScanner.parseCoreName("readme.txt"))
    }

    @Test
    fun `N64 catalog includes Mupen64Plus-Next and ParaLLEl`() {
        val n64 = RetroArchCoreCatalog.forPlatform("n64")
        assertTrue(n64.any { it.core == RetroArchPackages.MUPEN64PLUS_NEXT_CORE })
        assertTrue(n64.any { it.core == "parallel_n64" })
        assertEquals(
            BuiltInPlayers.RETROARCH_N64_PLAYER_ID,
            n64.first { it.core == RetroArchPackages.MUPEN64PLUS_NEXT_CORE }.playerId,
        )
    }

    @Test
    fun `BuiltInPlayers seeds alternate N64 Parallel core`() {
        val parallel = BuiltInPlayers.all.first {
            it.uniqueId == "retroarch.n64.parallel_n64"
        }
        assertTrue(parallel.amStartArguments.contains("parallel_n64_libretro_android.so"))
        assertTrue("n64" in parallel.platformIds)
    }

    @Test
    fun `parseCoreName accepts desktop-style and zipped core files`() {
        assertEquals(
            "mupen64plus_next_gles3",
            RetroArchCoreScanner.parseCoreName("mupen64plus_next_gles3_libretro_android.so"),
        )
        assertEquals(
            "snes9x",
            RetroArchCoreScanner.parseCoreName("snes9x_libretro.so"),
        )
        assertEquals(
            "flycast",
            RetroArchCoreScanner.parseCoreName("flycast_libretro_android.so.zip"),
        )
        assertEquals(
            "mesen",
            RetroArchCoreScanner.parseCoreName("Mesen_libretro_android.so"),
        )
    }

    @Test
    fun `N64 catalog offers the GLES Mupen variants`() {
        val cores = RetroArchCoreCatalog.forPlatform("n64").map { it.core }
        assertTrue("mupen64plus_next_gles3" in cores)
        assertTrue("mupen64plus_next_gles2" in cores)
    }

    @Test
    fun `platformForCore classifies variants and uncatalogued cores`() {
        assertEquals("n64", RetroArchCoreCatalog.platformForCore("mupen64plus_next_gles3"))
        // Unknown suffix still resolves through the longest catalog prefix.
        assertEquals("n64", RetroArchCoreCatalog.platformForCore("mupen64plus_next_gles31"))
        assertEquals("ps1", RetroArchCoreCatalog.platformForCore("beetle_psx_hw"))
        assertEquals("nds", RetroArchCoreCatalog.platformForCore("desmume"))
        assertEquals("snes", RetroArchCoreCatalog.platformForCore("bsnes_hd_beta"))
        assertNull(RetroArchCoreCatalog.platformForCore("totally_made_up_core"))
    }

    @Test
    fun `labelForCore reads like the RetroArch core name`() {
        assertEquals(
            "Mupen64Plus-Next GLES3",
            RetroArchCoreCatalog.labelForCore("mupen64plus_next_gles3"),
        )
        assertEquals("Mupen64Plus-Next", RetroArchCoreCatalog.labelForCore("mupen64plus_next"))
        assertEquals("Mupen64Plus Next GLES31", RetroArchCoreCatalog.labelForCore("mupen64plus_next_gles31"))
    }

    @Test
    fun `discovered cores get a stable player id`() {
        assertEquals(
            "retroarch.n64.gles3",
            RetroArchCoreCatalog.playerIdForDiscovered("n64", "mupen64plus_next_gles3"),
        )
        assertEquals(
            "retroarch.n64.mupen64plus_next_gles31",
            RetroArchCoreCatalog.playerIdForDiscovered("n64", "mupen64plus_next_gles31"),
        )
    }
}
