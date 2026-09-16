package com.arcadia.shell.feature.home

import com.arcadia.shell.feature.home.component.BOOT_INTRO_ASSET
import com.arcadia.shell.feature.home.component.BOOT_INTRO_LITE_ASSET
import com.arcadia.shell.feature.home.component.bootIntroAsset
import org.junit.Assert.assertEquals
import org.junit.Test

class BootIntroAssetTest {

    @Test
    fun qualityAlwaysUsesTheFullClip() {
        assertEquals(BOOT_INTRO_ASSET, bootIntroAsset(lite = false, liteExists = true))
        assertEquals(BOOT_INTRO_ASSET, bootIntroAsset(lite = false, liteExists = false))
    }

    @Test
    fun performanceUsesLiteWhenBundled() {
        assertEquals(BOOT_INTRO_LITE_ASSET, bootIntroAsset(lite = true, liteExists = true))
    }

    @Test
    fun performanceFallsBackToQualityIfLiteMissing() {
        assertEquals(BOOT_INTRO_ASSET, bootIntroAsset(lite = true, liteExists = false))
    }
}
