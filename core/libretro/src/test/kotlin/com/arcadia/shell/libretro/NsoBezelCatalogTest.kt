package com.arcadia.shell.libretro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NsoBezelCatalogTest {

    @Test
    fun gbaStem() {
        assertEquals("nso-gba", NsoBezelCatalog.overlayStem("gba"))
        assertEquals("nso-gba", NsoBezelCatalog.overlayStem("unknown", "mgba_libretro"))
    }

    @Test
    fun n64FromCoreName() {
        assertEquals("nso-n64", NsoBezelCatalog.overlayStem("n64", "mupen64plus_next_gles3"))
        assertTrue(NsoBezelCatalog.candidateStems("n64").contains("nso-n64"))
    }

    @Test
    fun gbcAliasesIncludeGb() {
        val stems = NsoBezelCatalog.candidateStems("gbc")
        assertTrue(stems.contains("nso-gbc"))
        assertTrue(stems.contains("nso-gb"))
    }

    @Test
    fun preferFullStemComesFirst() {
        val stems = NsoBezelCatalog.candidateStems("gba", preferFull = true)
        assertEquals("nso-gba-full", stems.first())
        assertTrue(stems.contains("nso-gba"))
    }

    @Test
    fun pico8MapsToP8() {
        assertEquals("nso-p8", NsoBezelCatalog.overlayStem("pico8"))
        assertEquals("nso-p8-full", NsoBezelCatalog.overlayStem("p8", full = true))
    }

    @Test
    fun defaultAspects() {
        assertEquals(240f / 160f, NsoBezelCatalog.defaultAspect("gba"), 0.001f)
        assertEquals(4f / 3f, NsoBezelCatalog.defaultAspect("n64"), 0.001f)
        assertEquals(160f / 144f, NsoBezelCatalog.defaultAspect("gbc"), 0.001f)
    }
}
