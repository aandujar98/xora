package com.arcadia.shell.model

import java.io.File

/**
 * PlayStation Vita title IDs as Vita3K Android expects them (`PCSE00546`, `PCSG00001`, …).
 *
 * Vita3K does not boot from a `.vpk` path the way RetroArch cores do. Frontends pass the Title ID
 * through `AppStartParameters` (`-r TITLEID`) after the game is installed in the emulator.
 */
object VitaTitleId {
    const val PLACEHOLDER = "{vita.titleId}"

    /**
     * Retail / download / system IDs that show up in dumps, No-Intro names, and maidump folders.
     * Kept case-insensitive so `pcse00546` in a path still matches.
     */
    private val PATTERN = Regex(
        """(?i)\b(PCS[A-GH][0-9]{5}|NPXS[0-9]{5}|PCSI[0-9]{5}|NPSA[0-9]{5}|VLJS[0-9]{5}|VCKS[0-9]{5})\b""",
    )

    fun find(vararg sources: String?): String? {
        for (source in sources) {
            val hit = PATTERN.find(source.orEmpty()) ?: continue
            return hit.value.uppercase()
        }
        return null
    }

    /**
     * Title ID for a scanned Vita row: filename / title first, then the dump folder and param.sfo.
     */
    fun resolve(game: Game): String? {
        val pathName = game.filePath?.let { File(it).name }
        find(game.fileName, game.title, pathName, game.filePath)?.let { return it }
        val dumpDir = game.filePath?.let { File(it) }?.let { file ->
            when {
                file.isDirectory -> file
                file.isFile -> file.parentFile
                else -> null
            }
        } ?: return null
        return fromDumpDirectory(dumpDir)
    }

    fun fromDumpDirectory(dir: File): String? {
        find(dir.name)?.let { return it }
        val sfoCandidates = listOf(
            File(dir, "sce_sys/param.sfo"),
            File(dir, "param.sfo"),
        ) + (dir.listFiles()?.filter { it.isDirectory }?.map { File(it, "sce_sys/param.sfo") }
            ?: emptyList())
        for (sfo in sfoCandidates) {
            if (!sfo.isFile) continue
            find(readAscii(sfo))?.let { return it }
        }
        return null
    }

    private fun readAscii(file: File): String = runCatching {
        file.inputStream().use { input ->
            val bytes = ByteArray(file.length().coerceAtMost(64 * 1024L).toInt())
            val read = input.read(bytes)
            if (read <= 0) "" else String(bytes, 0, read, Charsets.ISO_8859_1)
        }
    }.getOrDefault("")
}

/**
 * Maidump / NoNpDrm folder layouts that should be indexed as a single Vita title rather than
 * walked as loose `eboot.bin` files.
 */
object VitaGameFolder {
    fun isTitleIdName(name: String): Boolean = VitaTitleId.find(name) != null

    fun looksLikeDump(childNames: Collection<String>): Boolean {
        val lower = childNames.map { it.lowercase() }.toSet()
        return "eboot.bin" in lower || "sce_sys" in lower || "param.sfo" in lower
    }

    fun isGameDirectory(dir: File): Boolean {
        if (!dir.isDirectory) return false
        if (isTitleIdName(dir.name)) return true
        val children = dir.list() ?: return false
        return looksLikeDump(children.asList())
    }
}
