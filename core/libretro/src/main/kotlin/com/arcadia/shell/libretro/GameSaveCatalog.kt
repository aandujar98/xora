package com.arcadia.shell.libretro

import com.arcadia.shell.model.Game
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

enum class GameSaveKind {
    /** Battery / SRAM / memcard in XOrA's save dir. */
    Battery,
    /** User savestate slot. */
    State,
    /** Silent resume file from backgrounding. */
    Autosave,
    /** Matching save found outside XOrA (beside ROM / RetroArch) not yet imported. */
    External,
}

data class GameSaveEntry(
    val path: String,
    val fileName: String,
    val kind: GameSaveKind,
    val label: String,
    val sizeBytes: Long,
    val lastModifiedMs: Long,
)

/**
 * Lists battery saves, savestates, and external RetroArch-style saves for a library ROM so the
 * shell can surface them in the Select → ROM options menu.
 */
@Singleton
class GameSaveCatalog @Inject constructor(
    private val coreStore: CoreStore,
    private val saveFileImporter: SaveFileImporter,
) {
    fun listForGame(game: Game): List<GameSaveEntry> {
        if (game.isAndroidApp) return emptyList()
        val dir = coreStore.saveDirFor(game.platformId)
        val bases = saveFileImporter.candidateBaseNamesFor(game)
        val gameKey = sanitize(game.id)
        val entries = linkedMapOf<String, GameSaveEntry>()

        dir.listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.length() > 0L }
            ?.forEach { file ->
                val name = file.name
                val kind = when {
                    name.equals("$gameKey.autosave", ignoreCase = true) -> GameSaveKind.Autosave
                    name.startsWith("$gameKey.state", ignoreCase = true) -> GameSaveKind.State
                    bases.any { base ->
                        SaveFileImporter.SAVE_EXTENSIONS.any { ext ->
                            name.equals("$base$ext", ignoreCase = true)
                        }
                    } -> GameSaveKind.Battery
                    else -> null
                } ?: return@forEach
                entries[file.absolutePath] = file.toEntry(kind)
            }

        // External matches that are not already in XOrA's save dir.
        for (source in saveFileImporter.findExternalSaves(game)) {
            val destName = source.name
            val dest = File(dir, destName)
            if (dest.isFile && dest.length() > 0L) continue
            entries.putIfAbsent(source.absolutePath, source.toEntry(GameSaveKind.External))
        }

        return entries.values.sortedWith(
            compareBy<GameSaveEntry> { it.kind.ordinal }
                .thenBy { it.fileName.lowercase() },
        )
    }

    fun importExternal(game: Game): SaveFileImporter.ImportResult {
        val romPath = game.filePath ?: return SaveFileImporter.ImportResult(
            message = "Open this ROM once from storage with a real file path to import saves.",
        )
        return saveFileImporter.importForGame(game.platformId, romPath)
    }

    fun delete(entry: GameSaveEntry): Boolean {
        val file = File(entry.path)
        if (!file.isFile) return false
        // Never delete external sources from RetroArch / beside-ROM; only XOrA-managed files.
        if (entry.kind == GameSaveKind.External) return false
        return file.delete()
    }

    private fun File.toEntry(kind: GameSaveKind): GameSaveEntry = GameSaveEntry(
        path = absolutePath,
        fileName = name,
        kind = kind,
        label = when (kind) {
            GameSaveKind.Battery -> "Battery save · $name"
            GameSaveKind.State -> {
                val slot = name.substringAfter(".state", missingDelimiterValue = "")
                if (slot.isBlank()) "Save state · $name" else "Save state slot $slot"
            }
            GameSaveKind.Autosave -> "Autosave (resume)"
            GameSaveKind.External -> "Detected · $name"
        },
        sizeBytes = length(),
        lastModifiedMs = lastModified(),
    )

    private fun sanitize(key: String): String =
        key.lowercase().replace(Regex("[^a-z0-9._-]"), "_").take(120)
}
