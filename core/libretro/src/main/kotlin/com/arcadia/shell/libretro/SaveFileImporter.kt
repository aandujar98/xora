package com.arcadia.shell.libretro

import android.os.Environment
import android.util.Log
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Finds battery / SRAM / memcard saves for a ROM outside XOrA's save dir (beside the ROM or
 * common RetroArch folders) and copies them into [CoreStore.saveDirFor] before the core loads.
 */
@Singleton
class SaveFileImporter @Inject constructor(
    private val coreStore: CoreStore,
) {
    data class ImportResult(
        val imported: List<File> = emptyList(),
        val alreadyPresent: List<File> = emptyList(),
        val message: String? = null,
    )

    /**
     * Copy matching saves into XOrA's platform save directory. Safe to call repeatedly —
     * existing destination files are left alone.
     */
    fun importForGame(platformId: String, romPath: String): ImportResult {
        val romFile = File(romPath)
        if (!romFile.exists() && romFile.parentFile?.isDirectory != true) {
            return ImportResult(message = null)
        }
        val baseNames = candidateBaseNames(romFile)
        val destDir = coreStore.saveDirFor(platformId)
        val already = mutableListOf<File>()
        val imported = mutableListOf<File>()

        for (base in baseNames) {
            for (ext in SAVE_EXTENSIONS) {
                val dest = File(destDir, "$base$ext")
                if (dest.isFile && dest.length() > 0L) {
                    already += dest
                    continue
                }
                val source = findSourceSave(romFile, base, ext) ?: continue
                runCatching {
                    source.copyTo(dest, overwrite = false)
                    if (dest.isFile && dest.length() > 0L) {
                        imported += dest
                        Log.i(TAG, "Imported save ${source.absolutePath} → ${dest.absolutePath}")
                    }
                }.onFailure {
                    Log.w(TAG, "Failed to import ${source.absolutePath}: ${it.message}")
                }
            }
        }

        val message = when {
            imported.isNotEmpty() -> {
                val names = imported.joinToString(", ") { it.name }
                "Imported save${if (imported.size > 1) "s" else ""}: $names"
            }
            already.isNotEmpty() -> "Existing save detected"
            else -> null
        }
        return ImportResult(imported = imported, alreadyPresent = already, message = message)
    }

    private fun candidateBaseNames(romFile: File): List<String> {
        val names = linkedSetOf<String>()
        val primary = romFile.nameWithoutExtension
        if (primary.isNotBlank()) names += primary
        // ZIP / archive: also try common inner basenames already extracted beside the ROM.
        romFile.parentFile
            ?.listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.name.startsWith(".xora_extracted_") }
            ?.forEach { extracted ->
                val inner = extracted.name.removePrefix(".xora_extracted_")
                val noExt = inner.substringBeforeLast('.', missingDelimiterValue = inner)
                if (noExt.isNotBlank()) names += noExt
            }
        return names.toList()
    }

    private fun findSourceSave(romFile: File, base: String, ext: String): File? {
        val fileName = "$base$ext"
        val candidates = ArrayList<File>()

        // Beside the ROM.
        romFile.parentFile?.let { parent ->
            candidates += File(parent, fileName)
            candidates += File(parent, "saves/$fileName")
            candidates += File(parent, "save/$fileName")
        }

        // Common RetroArch Android locations.
        val external = Environment.getExternalStorageDirectory()
        if (external != null) {
            candidates += File(external, "RetroArch/saves/$fileName")
            candidates += File(external, "RetroArch/saves/$base/$fileName")
            candidates += File(external, "Android/data/com.retroarch/files/saves/$fileName")
            candidates += File(external, "Android/data/com.retroarch.aarch64/files/saves/$fileName")
        }

        // App-shared external RetroArch-style folder if present under XOrA files.
        candidates += File(coreStore.savesRoot.parentFile, "RetroArch/saves/$fileName")

        return candidates.firstOrNull { it.isFile && it.length() > 0L }
    }

    private companion object {
        const val TAG = "SaveFileImporter"
        val SAVE_EXTENSIONS = listOf(
            ".srm",
            ".sav",
            ".rtc",
            ".mcr",
            ".mcd",
            ".mem",
            ".eep",
            ".fla",
            ".sra",
        )
    }
}
