package com.arcadia.shell.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

class ShellThemeCatalogTest {

    @Test
    fun usagiPinkIsShownAsUsagiReloadWithoutChangingTheStoredId() {
        assertEquals("usagishade_pink", ShellThemeId.UsagiShadePink.id)
        assertEquals("Usagi Reload", ShellThemeId.UsagiShadePink.displayName)
        assertEquals("usagishade_pink", ShellThemeId.fromId("usagishade_pink").id)
        assertEquals(
            USAGISHADE_PINK_WALLPAPER_ASSET,
            ShellThemeCatalog.UsagiShadePink.wallpaperAssetPath,
        )
        assertEquals(USAGISHADE_BGM_ASSET, ShellThemeCatalog.UsagiShadePink.bgm?.assetPath)
    }
}
