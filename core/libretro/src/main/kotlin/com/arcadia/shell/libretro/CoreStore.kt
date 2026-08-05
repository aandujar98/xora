package com.arcadia.shell.libretro

import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-disk Libretro cores under `filesDir/cores/` plus system/save directories for the host.
 */
@Singleton
class CoreStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val root = File(context.filesDir, "cores")
    val systemDir: File = File(context.filesDir, "system").also { it.mkdirs() }
    val savesRoot: File = File(context.filesDir, "saves").also { it.mkdirs() }

    private val installed = MutableStateFlow<Set<String>>(emptySet())
    val installedCoreNames: StateFlow<Set<String>> = installed.asStateFlow()

    init {
        root.mkdirs()
        refreshInstalled()
    }

    fun abiFolder(): String = when (Build.SUPPORTED_ABIS.firstOrNull()) {
        "arm64-v8a" -> "arm64-v8a"
        "armeabi-v7a" -> "armeabi-v7a"
        "x86_64" -> "x86_64"
        "x86" -> "x86"
        else -> "arm64-v8a"
    }

    fun coreFileName(core: String): String = "${core}_libretro_android.so"

    fun corePath(core: String): File = File(root, coreFileName(core))

    fun isInstalled(core: String): Boolean {
        val file = corePath(core)
        return file.isFile && file.length() > 0L
    }

    fun resolveInstalledPath(core: String): String? =
        corePath(core).takeIf { it.isFile && it.length() > 0L }?.absolutePath

    fun saveDirFor(platformId: String): File =
        File(savesRoot, platformId).also { it.mkdirs() }

    fun stateFile(platformId: String, gameKey: String, slot: Int): File =
        File(saveDirFor(platformId), "${sanitize(gameKey)}.state$slot")

    /** Silent resume file written when the emulator is backgrounded (not a user slot). */
    fun autosaveFile(platformId: String, gameKey: String): File =
        File(saveDirFor(platformId), "${sanitize(gameKey)}.autosave")

    fun refreshInstalled() {
        installed.value = root.listFiles()
            ?.filter { it.isFile && it.name.endsWith("_libretro_android.so") }
            ?.map { it.name.removeSuffix("_libretro_android.so") }
            ?.toSet()
            ?: emptySet()
    }

    fun importCore(source: File, coreBaseName: String): File? {
        if (!source.isFile || source.length() == 0L) return null
        root.mkdirs()
        val target = corePath(coreBaseName)
        source.copyTo(target, overwrite = true)
        refreshInstalled()
        return target.takeIf { it.isFile }
    }

    fun removeCore(core: String): Boolean {
        val ok = corePath(core).delete()
        refreshInstalled()
        return ok
    }

    private fun sanitize(key: String): String =
        key.lowercase().replace(Regex("[^a-z0-9._-]"), "_").take(120)
}
