package com.arcadia.shell.launcher

import android.content.Context
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Finds installed RetroArch libretro cores on shared storage.
 *
 * Private `/data/data/<pkg>/cores/` is usually inaccessible to SORA; this scanner
 * covers the common shared paths RetroArch's Online Updater also uses.
 */
@Singleton
class RetroArchCoreScanner @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /**
     * Returns core base names found on disk (e.g. `mupen64plus_next`), without the
     * `_libretro_android.so` suffix.
     */
    fun installedCoreNames(retroArchPackage: String? = null): Set<String> {
        val found = linkedSetOf<String>()
        coreDirectories(retroArchPackage).forEach { dir ->
            val files = dir.listFiles() ?: return@forEach
            files.forEach { file ->
                parseCoreName(file.name)?.let(found::add)
            }
        }
        return found
    }

    fun hasCore(core: String, retroArchPackage: String? = null): Boolean =
        coreDirectories(retroArchPackage).any { dir ->
            dir.listFiles()?.any { file ->
                parseCoreName(file.name)?.equals(core, ignoreCase = true) == true
            } == true
        }

    private fun coreDirectories(retroArchPackage: String?): List<File> {
        val roots = mutableListOf<File>()
        val external = Environment.getExternalStorageDirectory()
        roots += File(external, "RetroArch/cores")
        roots += File(external, "retroarch/cores")
        roots += File(external, "Download/RetroArch/cores")
        roots += File(external, "Downloads/RetroArch/cores")
        roots += File(external, "RetroArch/downloads")

        val packages = if (retroArchPackage != null) {
            listOf(retroArchPackage)
        } else {
            RetroArchPackages.CANDIDATE_PACKAGES
        }
        packages.forEach { pkg ->
            roots += File(external, "Android/data/$pkg/files/cores")
            roots += File(external, "Android/data/$pkg/files/RetroArch/cores")
            roots += File(external, "Android/data/$pkg/files/downloads")
            roots += File(external, "Android/obb/$pkg")
            // Readable only when the process shares the UID (rare); cheap to try.
            roots += File("/data/data/$pkg/cores")
            roots += File("/data/user/0/$pkg/cores")
        }

        context.getExternalFilesDir(null)?.let { appExt ->
            // Some sideload layouts mirror RetroArch next to sibling app dirs.
            roots += File(appExt.parentFile?.parentFile ?: appExt, "RetroArch/cores")
        }

        return roots.filter { it.isDirectory }.distinctBy { it.absolutePath }
    }

    companion object {
        /**
         * Android cores are `<core>_libretro_android.so`, but desktop-style `<core>_libretro.so`
         * shows up in hand-copied core folders, and the Online Updater leaves `.so.zip` behind
         * before extraction. All three name the same core.
         */
        private val CORE_FILE = Regex(
            """^(.+?)_libretro(?:_android)?\.so(?:\.zip)?$""",
            RegexOption.IGNORE_CASE,
        )

        fun parseCoreName(fileName: String): String? =
            CORE_FILE.matchEntire(fileName.trim())?.groupValues?.getOrNull(1)?.lowercase()
    }
}
