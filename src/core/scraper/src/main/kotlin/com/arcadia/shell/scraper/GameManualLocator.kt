package com.arcadia.shell.scraper

import com.arcadia.shell.model.Game
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Best-effort local manual discovery beside a ROM. Scraped ScreenScraper manuals are resolved
 * separately; the Game model does not yet persist a manual path.
 */
@Singleton
class GameManualLocator @Inject constructor() {

    suspend fun findLocalManual(game: Game): String? = withContext(Dispatchers.IO) {
        val romPath = game.filePath?.takeIf { it.isNotBlank() } ?: return@withContext null
        val rom = File(romPath)
        val dir = rom.parentFile?.takeIf { it.isDirectory } ?: return@withContext null
        val stem = rom.nameWithoutExtension

        val candidates = buildList {
            add(File(dir, "$stem.pdf"))
            add(File(dir, "$stem.PDF"))
            add(File(dir, "$stem - Manual.pdf"))
            add(File(dir, "$stem Manual.pdf"))
            add(File(dir, "manual.pdf"))
            add(File(dir, "Manual.pdf"))
            add(File(dir, "$stem-manual.pdf"))
            MANUAL_IMAGE_EXT.forEach { ext ->
                add(File(dir, "$stem-manual.$ext"))
                add(File(dir, "$stem.manual.$ext"))
                add(File(dir, "manual.$ext"))
            }
            listOf("manuals", "Manuals", "manual", "Manual").forEach { folder ->
                val nested = File(dir, folder)
                if (nested.isDirectory) {
                    add(File(nested, "$stem.pdf"))
                    add(File(nested, "${stem}.pdf"))
                    add(File(nested, "manual.pdf"))
                    MANUAL_IMAGE_EXT.forEach { ext ->
                        add(File(nested, "$stem.$ext"))
                        add(File(nested, "manual.$ext"))
                    }
                }
            }
        }

        candidates.firstOrNull { it.isFile && it.length() > 0L }?.absolutePath
    }

    private companion object {
        val MANUAL_IMAGE_EXT = listOf("png", "jpg", "jpeg", "webp")
    }
}
