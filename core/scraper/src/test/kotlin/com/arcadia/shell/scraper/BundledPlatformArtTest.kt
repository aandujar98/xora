package com.arcadia.shell.scraper

import com.arcadia.shell.model.PlatformCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BundledPlatformArtTest {

    @Test
    fun `every bundled banner maps to a catalog platform`() {
        BundledPlatformArt.PLATFORM_IDS.forEach { id ->
            assertNotNull("missing catalog platform $id", PlatformCatalog.byId(id))
        }
    }

    @Test
    fun `asset names are platformId png files`() {
        assertEquals("n64.png", BundledPlatformArt.assetNameFor("n64"))
        assertEquals("gamecube.png", BundledPlatformArt.assetNameFor("gamecube"))
        assertTrue(BundledPlatformArt.PLATFORM_IDS.contains("ps1"))
        assertTrue(BundledPlatformArt.PLATFORM_IDS.contains("3ds"))
    }
}
