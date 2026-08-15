package com.arcadia.shell.libretro

import java.io.File

/**
 * Finds an NSO-style overlay image for a ROM.
 *
 * Pack layout from the NSO cfg zip:
 * ```
 * cfg/nso-gba.cfg
 * img/nso-gba.png          ← overlay0_overlay = img/nso-gba.png
 * ```
 *
 * Search order:
 * 1. App `overlays/` (bundled cfg + dropped `img/` PNGs)
 * 2. Nearby pack folders that contain `cfg/` or `img/` (e.g. `NSO - angel`)
 * 3. `<rom-dir>/overlays/`, the ROM directory, and one folder up
 */
object NsoBezelLocator {
    val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp")

    fun resolve(
        platformId: String,
        coreName: String,
        romFilePath: String?,
        overlaysDir: File?,
        preferFull: Boolean = false,
    ): File? {
        val stems = NsoBezelCatalog.candidateStems(platformId, coreName, preferFull)
        val dirs = searchDirs(romFilePath, overlaysDir)
        for (dir in dirs) {
            findInDir(dir, stems)?.let { return it }
        }
        return null
    }

    /** Relative overlay path from a RetroArch cfg (`img/nso-gba.png`). */
    fun parseOverlayImagePath(cfgText: String): String? {
        cfgText.lineSequence().forEach { raw ->
            val line = raw.substringBefore('#').trim()
            if (line.isEmpty()) return@forEach
            val eq = line.indexOf('=')
            if (eq <= 0) return@forEach
            val key = line.substring(0, eq).trim().lowercase()
            if (key == "overlay0_overlay" || key == "overlay0_image") {
                val value = line.substring(eq + 1).trim().trim('"').replace('\\', '/')
                if (value.isNotBlank()) return value
            }
        }
        return null
    }

    fun parseOverlayImageName(cfgText: String): String? =
        parseOverlayImagePath(cfgText)?.let { File(it).name }

    fun isOverlayImage(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext in IMAGE_EXTENSIONS
    }

    fun resolveFromCfg(cfg: File, extraRoots: List<File> = emptyList()): File? {
        if (!cfg.isFile) return null
        val relative = runCatching { parseOverlayImagePath(cfg.readText()) }.getOrNull()
            ?: return null
        val name = File(relative).name
        val cfgParent = cfg.parentFile
        val packRoot = if (cfgParent != null && cfgParent.name.equals("cfg", ignoreCase = true)) {
            cfgParent.parentFile
        } else {
            cfgParent
        }
        val candidates = buildList {
            cfgParent?.let {
                add(File(it, relative))
                add(File(it, name))
            }
            packRoot?.let {
                add(File(it, relative))
                add(File(it, name))
                add(File(it, "img/$name"))
            }
            extraRoots.forEach { root ->
                add(File(root, relative))
                add(File(root, name))
                add(File(root, "img/$name"))
                add(File(root, "overlays/$relative"))
                add(File(root, "overlays/img/$name"))
            }
        }
        return candidates
            .distinctBy { it.absolutePath }
            .firstOrNull { it.isFile && it.length() > 0L }
    }

    private fun findInDir(dir: File, stems: List<String>): File? {
        if (!dir.isDirectory) return null
        val cfgDirs = listOf(dir, File(dir, "cfg"), File(dir, "overlays"), File(dir, "overlays/cfg"))
            .filter { it.isDirectory }
            .distinctBy { it.absolutePath }
        val packRoots = listOf(dir, dir.parentFile, File(dir, "overlays"))
            .filterNotNull()
            .distinctBy { it.absolutePath }

        for (stem in stems) {
            for (cfgDir in cfgDirs) {
                val cfg = File(cfgDir, "$stem.cfg")
                if (cfg.isFile) {
                    resolveFromCfg(cfg, packRoots)?.let { return it }
                }
            }
            for (root in packRoots) {
                for (ext in IMAGE_EXTENSIONS) {
                    listOf(
                        File(root, "$stem.$ext"),
                        File(root, "img/$stem.$ext"),
                        File(root, "overlays/img/$stem.$ext"),
                    ).firstOrNull { it.isFile && it.length() > 0L }?.let { return it }
                }
            }
        }
        return null
    }

    private fun searchDirs(romFilePath: String?, overlaysDir: File?): List<File> {
        val rom = romFilePath?.let(::File)?.takeIf { it.path.isNotBlank() }
        val parent = rom?.parentFile
        val grand = parent?.parentFile
        val seeds = listOfNotNull(
            overlaysDir,
            overlaysDir?.let { File(it, "cfg") },
            parent,
            parent?.let { File(it, "overlays") },
            grand,
            grand?.let { File(it, "overlays") },
        )
        val out = linkedSetOf<File>()
        for (seed in seeds) {
            out += seed
            packDirsNear(seed).forEach { out += it }
        }
        return out.toList()
    }

    /** Folders that look like the NSO pack (`cfg/` + `img/` sitting together). */
    private fun packDirsNear(dir: File): List<File> {
        if (!dir.isDirectory) return emptyList()
        val hits = mutableListOf<File>()
        if (File(dir, "cfg").isDirectory || File(dir, "img").isDirectory) hits += dir
        dir.listFiles()
            ?.filter { child ->
                child.isDirectory &&
                    (File(child, "cfg").isDirectory || File(child, "img").isDirectory)
            }
            ?.forEach { hits += it }
        return hits
    }
}
