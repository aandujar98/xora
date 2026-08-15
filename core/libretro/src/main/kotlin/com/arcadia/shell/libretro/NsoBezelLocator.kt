package com.arcadia.shell.libretro

import java.io.File

/**
 * Finds an NSO-style overlay image for a ROM.
 *
 * Search order:
 * 1. App `overlays/` directory (imported / dropped packs)
 * 2. `<rom-dir>/overlays/`
 * 3. The ROM directory itself
 * 4. One folder up (the usual "ROMs" root) and its `overlays/` child
 *
 * A `.cfg` next to the PNG wins when it names `overlay0_overlay`.
 */
object NsoBezelLocator {
    val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp")

    fun resolve(
        platformId: String,
        coreName: String,
        romFilePath: String?,
        overlaysDir: File?,
    ): File? {
        val stems = NsoBezelCatalog.candidateStems(platformId, coreName)
        val dirs = searchDirs(romFilePath, overlaysDir)
        for (dir in dirs) {
            findInDir(dir, stems)?.let { return it }
        }
        return null
    }

    fun parseOverlayImageName(cfgText: String): String? {
        cfgText.lineSequence().forEach { raw ->
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty()) return@forEach
            val eq = line.indexOf('=')
            if (eq <= 0) return@forEach
            val key = line.substring(0, eq).trim().lowercase()
            if (key == "overlay0_overlay" || key == "overlay0_image") {
                val value = line.substring(eq + 1).trim().trim('"')
                if (value.isNotBlank()) return File(value).name
            }
        }
        return null
    }

    fun isOverlayImage(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext in IMAGE_EXTENSIONS
    }

    private fun findInDir(dir: File, stems: List<String>): File? {
        if (!dir.isDirectory) return null
        val files = dir.listFiles() ?: return null
        val byName = files.associateBy { it.name.lowercase() }

        for (stem in stems) {
            val cfg = byName["$stem.cfg"]
            if (cfg != null && cfg.isFile) {
                val named = runCatching { parseOverlayImageName(cfg.readText()) }.getOrNull()
                named?.let { fileName ->
                    resolveNamed(dir, byName, fileName)?.let { return it }
                }
            }
            for (ext in IMAGE_EXTENSIONS) {
                byName["$stem.$ext"]?.takeIf { it.isFile && it.length() > 0L }?.let { return it }
            }
        }

        // Pack folders sometimes use `nso-GBA.PNG` with mixed case already handled by lowercase map.
        return null
    }

    private fun resolveNamed(
        dir: File,
        byName: Map<String, File>,
        fileName: String,
    ): File? {
        val direct = byName[fileName.lowercase()]
        if (direct != null && direct.isFile && direct.length() > 0L) return direct
        val nested = File(dir, fileName)
        if (nested.isFile && nested.length() > 0L) return nested
        return null
    }

    private fun searchDirs(romFilePath: String?, overlaysDir: File?): List<File> {
        val rom = romFilePath?.let(::File)?.takeIf { it.path.isNotBlank() }
        val parent = rom?.parentFile
        val grand = parent?.parentFile
        return listOfNotNull(
            overlaysDir,
            parent?.let { File(it, "overlays") },
            parent,
            grand?.let { File(it, "overlays") },
            grand,
        ).distinctBy { it.absolutePath }
    }
}
