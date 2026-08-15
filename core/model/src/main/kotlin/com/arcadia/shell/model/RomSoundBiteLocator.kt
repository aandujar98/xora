package com.arcadia.shell.model

import java.io.File

/**
 * Finds a short audio clip to play when a ROM is focused.
 *
 * Preference:
 * 1. An imported path from ROM options, if the file is still on disk
 * 2. A sidecar in the ROM's folder (or the folder above it — the usual "ROMs" root)
 *    named after the ROM file or the game title: `Super Mario 64.mp3` / `.wav`
 */
object RomSoundBiteLocator {
    val AUDIO_EXTENSIONS = setOf("mp3", "wav")

    fun resolve(game: Game): String? =
        resolve(
            explicitPath = game.soundBitePath,
            romFilePath = game.filePath,
            title = game.title,
            romFileName = game.fileName,
        )

    fun resolve(
        explicitPath: String?,
        romFilePath: String?,
        title: String,
        romFileName: String,
    ): String? {
        existingFile(explicitPath)?.let { return it }
        return findSidecar(romFilePath, title, romFileName)
    }

    fun isAudioFile(fileName: String): Boolean =
        TitleCleaner.extensionOf(fileName) in AUDIO_EXTENSIONS

    /** True when [audioFileName] is a sound bite for this ROM / title. */
    fun matches(audioFileName: String, title: String, romFileName: String): Boolean {
        if (!isAudioFile(audioFileName)) return false
        val audioStem = stemOf(audioFileName)
        val romStem = stemOf(romFileName)
        val cleanedAudio = TitleCleaner.clean(audioFileName)
        val cleanedRom = TitleCleaner.clean(romFileName)
        val cleanedTitle = title.trim()
        return audioStem.equals(romStem, ignoreCase = true) ||
            audioStem.equals(cleanedRom, ignoreCase = true) ||
            audioStem.equals(cleanedTitle, ignoreCase = true) ||
            cleanedAudio.equals(cleanedRom, ignoreCase = true) ||
            cleanedAudio.equals(cleanedTitle, ignoreCase = true)
    }

    fun findSidecar(romFilePath: String?, title: String, romFileName: String): String? {
        val rom = romFilePath?.let(::File)?.takeIf { it.path.isNotBlank() } ?: return null
        val dirs = listOfNotNull(rom.parentFile, rom.parentFile?.parentFile)
            .distinctBy { it.absolutePath }
        for (dir in dirs) {
            val files = dir.listFiles() ?: continue
            val hit = files
                .asSequence()
                .filter { it.isFile && it.length() > 0L && matches(it.name, title, romFileName) }
                .minByOrNull { matchRank(it.name, title, romFileName) }
            if (hit != null) return hit.absolutePath
        }
        return null
    }

    private fun matchRank(audioFileName: String, title: String, romFileName: String): Int {
        val audioStem = stemOf(audioFileName)
        val romStem = stemOf(romFileName)
        val cleanedRom = TitleCleaner.clean(romFileName)
        return when {
            audioStem.equals(romStem, ignoreCase = true) -> 0
            audioStem.equals(title.trim(), ignoreCase = true) -> 1
            audioStem.equals(cleanedRom, ignoreCase = true) -> 2
            else -> 3
        }
    }

    private fun stemOf(fileName: String): String = fileName.substringBeforeLast('.')

    private fun existingFile(path: String?): String? {
        val file = path?.takeIf { it.isNotBlank() }?.let(::File) ?: return null
        return file.takeIf { it.isFile && it.length() > 0L }?.absolutePath
    }
}
