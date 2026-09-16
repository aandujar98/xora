package com.arcadia.shell.libretro.netplay

import java.io.File

/**
 * Pretendo does not run inside XOrA Emulator.
 *
 * DS Kaeru/Wiimmfi work in XOrA because melonDS libretro exposes a WFC DNS option.
 * 3DS Pretendo is Nimbus (Home Menu CIA + Luma/3GX patches) on a dumped NAND, plus
 * standalone Azahar's "required LLE modules for online" and 3GX plugin loader.
 * Upstream Azahar libretro never parses those settings — extra core options are ignored.
 */
data class AzaharPretendoUi(
    val prepEnabled: Boolean = false,
    val nandPresent: Boolean = false,
    val nimbusPatches: Boolean = false,
    val userDir: String = "",
) {
    fun overlaySubtitle(): String = when {
        nandPresent && nimbusPatches ->
            "Not in XOrA Emulator · NAND + Nimbus files are for standalone Azahar"
        nandPresent || nimbusPatches ->
            "Not in XOrA Emulator · play Pretendo in standalone Azahar"
        else ->
            "Not in XOrA Emulator · Nimbus + NAND + LLE, not a DNS switch"
    }
}

object AzaharPretendo {
    const val USER_FOLDER = "Azahar"

    fun userDir(saveDir: File): File = File(saveDir, USER_FOLDER)

    fun sdmcDir(saveDir: File): File = File(userDir(saveDir), "sdmc")

    fun nandDir(saveDir: File): File = File(userDir(saveDir), "nand")

    fun ensureDirs(saveDir: File): File {
        val root = userDir(saveDir)
        root.mkdirs()
        sdmcDir(saveDir).mkdirs()
        nandDir(saveDir).mkdirs()
        return root
    }

    fun scan(saveDir: File, prepEnabled: Boolean): AzaharPretendoUi {
        val root = userDir(saveDir)
        return AzaharPretendoUi(
            prepEnabled = prepEnabled,
            nandPresent = hasNand(nandDir(saveDir)),
            nimbusPatches = hasNimbusPatches(sdmcDir(saveDir)),
            userDir = root.absolutePath,
        )
    }

    fun hasNand(nand: File): Boolean {
        if (!nand.isDirectory) return false
        val named = listOf("data", "sysdata", "ticket", "title", "rw")
        if (named.any { File(nand, it).isDirectory }) return true
        return nand.walkTopDown().maxDepth(2).any { it.isFile && it.length() > 0L }
    }

    fun hasNimbusPatches(sdmc: File): Boolean {
        if (!sdmc.isDirectory) return false
        val markers = listOf(
            File(sdmc, "luma"),
            File(sdmc, "3ds/nimbus"),
            File(sdmc, "luma/titles"),
            File(sdmc, "3ds/nimbus/update"),
        )
        return markers.any { it.isDirectory && (it.list()?.isNotEmpty() == true) }
    }

    /** Core options libretro Azahar actually reads today. */
    fun coreOptions(): Map<String, String> = mapOf(
        "citra_is_new_3ds" to "New 3DS",
        "azahar_is_new_3ds" to "New 3DS",
        "citra_use_virtual_sd" to "enabled",
        "azahar_use_virtual_sd" to "enabled",
        "citra_use_libretro_save_path" to "LibRetro Default",
        "azahar_use_libretro_save_path" to "LibRetro Default",
    )
}
