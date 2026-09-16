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

    @Test
    fun themesDroppedBetween041And056AreGone() {
        val ids = ShellThemeId.entries.map { it.id }

        assertEquals(listOf("default", "usagishade_pink", "usagishade_dark"), ids)
        assertEquals(ids.size, ShellThemeCatalog.all.size)
    }

    @Test
    fun aStoredIdForARemovedThemeFallsBackToDefault() {
        // Devices upgrading from 0.4.x can still hold any of these, and must not be left pointing
        // at a theme the catalog no longer has.
        listOf("persona3_reload", "dreamos", "midnight", "classic_xmb", "warm_arcade").forEach { id ->
            assertEquals(ShellThemeId.Default, ShellThemeId.fromId(id))
        }
    }
}
