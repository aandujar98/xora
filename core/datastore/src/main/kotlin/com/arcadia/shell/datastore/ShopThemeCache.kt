package com.arcadia.shell.datastore

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Local cache for theme packs downloaded from SORA Shop (future).
 *
 * Layout: `files/shop_themes/<themeId>/…`. No network API is wired yet — listing is empty until
 * packs are installed into this directory.
 */
@Singleton
class ShopThemeCache @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val rootDir: File
        get() = File(context.filesDir, SHOP_THEMES_DIR).also { it.mkdirs() }

    /** Installed shop theme folder names (stable ids). */
    fun installedThemeIds(): List<String> =
        rootDir.listFiles()
            ?.filter { it.isDirectory && !it.name.startsWith('.') }
            ?.map { it.name }
            ?.sorted()
            .orEmpty()

    fun themeDir(themeId: String): File =
        File(rootDir, themeId).also { it.mkdirs() }

    companion object {
        const val SHOP_THEMES_DIR = "shop_themes"
    }
}
