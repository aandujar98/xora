package com.arcadia.shell.scanner

import com.arcadia.shell.model.GamePlatform
import com.arcadia.shell.model.PlatformCatalog
import com.arcadia.shell.model.TitleCleaner
import javax.inject.Inject

class PlatformResolver @Inject constructor() {

    /**
     * Decides which system a file belongs to, or null when it is not a game at all.
     *
     * Folder naming outranks the file extension because extensions are ambiguous: `iso` alone could
     * be GameCube, Wii, PS2, PSP, or 3DO. Only extensions claimed by exactly one platform in the
     * catalog are trusted on their own.
     *
     * Cartridge platforms list [com.arcadia.shell.model.RomArchives] extensions so a No-Intro
     * `nes/Game.zip` resolves from the folder the same way `Game.nes` does. Archives stay
     * non-exclusive, so a bare `Game.zip` with no folder hint is never guessed.
     */
    fun resolve(
        file: ScannedFile,
        forcedPlatformId: String?,
        rootLabel: String? = null,
        rootLocation: String? = null,
    ): GamePlatform? {
        val extension = TitleCleaner.extensionOf(file.name)
        if (extension.isEmpty()) return null

        PlatformCatalog.byId(forcedPlatformId)?.let { forced ->
            return forced.takeIf { extension in forced.extensions }
        }

        // Deepest folder first, so roms/nintendo/snes resolves as SNES and not by the parent.
        // The walk chain alone is not enough: adding ROMS/PSP as the library root used to leave
        // the chain empty, so every ISO under that folder was discarded.
        val folderMatch = FolderHints.deepestFirst(
            folderChain = file.folderChain,
            filePath = file.filePath,
            documentUri = file.documentUri,
            rootLabel = rootLabel,
            rootLocation = rootLocation,
        ).firstNotNullOfOrNull { name ->
            PlatformCatalog.byFolderName(name)?.takeIf { extension in it.extensions }
        }
        if (folderMatch != null) return folderMatch

        return PlatformCatalog.byExclusiveExtension(extension)
    }
}

/**
 * Drops raw disc tracks that sit next to a playlist or cue sheet describing them.
 *
 * A single PlayStation game is routinely stored as `Game.cue` plus `Game (Track 1).bin`, and a
 * multi-disc release adds `Game.m3u` on top. Indexing every file would show one game three or four
 * times and let the user boot a track that most emulators cannot read directly.
 */
internal object DiscTrackFilter {

    private val playlistExtensions = setOf("m3u", "cue", "gdi", "ccd", "mds")
    private val trackExtensions = setOf("bin", "img", "iso", "raw", "sub", "mdf")

    fun filter(files: List<ScannedFile>): List<ScannedFile> {
        val playlistBaseNames = files
            .filter { TitleCleaner.extensionOf(it.name) in playlistExtensions }
            .groupBy({ it.folderChain }, { baseName(it.name) })
            .mapValues { (_, names) -> names.toSet() }

        if (playlistBaseNames.isEmpty()) return files

        return files.filterNot { file ->
            val extension = TitleCleaner.extensionOf(file.name)
            if (extension !in trackExtensions) return@filterNot false

            val siblings = playlistBaseNames[file.folderChain] ?: return@filterNot false
            val base = baseName(file.name)
            // "Game (Track 1).bin" must still match the sheet named "Game.cue".
            siblings.any { sheet -> base == sheet || base.startsWith("$sheet ") }
        }
    }

    private fun baseName(fileName: String): String = fileName.substringBeforeLast('.', fileName)
}
