package com.arcadia.shell.datastore

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Copies user-picked home wallpaper / BGM / shortcut art into app-private storage so picker
 * URI grants are not required after import.
 */
@Singleton
class HomeThemeMediaStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val wallpaperDir: File
        get() = File(context.filesDir, WALLPAPER_DIR).also { it.mkdirs() }

    private val bgmDir: File
        get() = File(context.filesDir, BGM_DIR).also { it.mkdirs() }

    private val shortcutArtDir: File
        get() = File(context.filesDir, SHORTCUT_ART_DIR).also { it.mkdirs() }

    /**
     * Import still image, animated GIF, or looping video (mp4/webm) as Home wallpaper.
     */
    suspend fun importWallpaper(uri: Uri): String = importNamed(
        uri = uri,
        dir = wallpaperDir,
        stem = WALLPAPER_STEM,
        defaultExt = "jpg",
        imageOnly = false,
        wallpaperMedia = true,
    )

    suspend fun importBgm(uri: Uri): String = importNamed(
        uri = uri,
        dir = bgmDir,
        stem = BGM_STEM,
        defaultExt = "mp3",
        imageOnly = false,
    )

    suspend fun importShortcutArt(uri: Uri, id: String): String = importNamed(
        uri = uri,
        dir = shortcutArtDir,
        stem = "shortcut_$id",
        defaultExt = "jpg",
        imageOnly = true,
    )

    fun resolveWallpaper(absoluteOrRelative: String?): File? = resolve(absoluteOrRelative, wallpaperDir)

    fun resolveBgm(absoluteOrRelative: String?): File? = resolve(absoluteOrRelative, bgmDir)

    fun resolveShortcutArt(absoluteOrRelative: String?): File? =
        resolve(absoluteOrRelative, shortcutArtDir)

    fun clearWallpaper() {
        runCatching { wallpaperDir.listFiles()?.forEach { it.delete() } }
    }

    fun clearBgm() {
        runCatching { bgmDir.listFiles()?.forEach { it.delete() } }
    }

    private fun resolve(path: String?, fallbackDir: File): File? {
        val raw = path?.takeIf { it.isNotBlank() } ?: return null
        val asFile = File(raw)
        if (asFile.isFile && asFile.length() > 0L) return asFile
        val nested = File(fallbackDir, File(raw).name)
        return nested.takeIf { it.isFile && it.length() > 0L }
    }

    private suspend fun importNamed(
        uri: Uri,
        dir: File,
        stem: String,
        defaultExt: String,
        imageOnly: Boolean,
        wallpaperMedia: Boolean = false,
    ): String = withContext(Dispatchers.IO) {
        val extension = guessExtension(uri, defaultExt, imageOnly, wallpaperMedia)
        val fileName = "$stem.$extension"
        val target = File(dir, fileName)
        val temp = File(dir, "$stem.part")

        context.contentResolver.openInputStream(uri)?.use { input ->
            temp.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Could not read the selected file.")

        if (temp.length() == 0L) {
            temp.delete()
            error("Selected file was empty.")
        }

        if (target.exists()) target.delete()
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }

        dir.listFiles()
            ?.filter { it.isFile && it.name != fileName && it.name.startsWith("$stem.") }
            ?.forEach { it.delete() }

        target.absolutePath
    }

    private fun guessExtension(
        uri: Uri,
        defaultExt: String,
        imageOnly: Boolean,
        wallpaperMedia: Boolean = false,
    ): String {
        val fromName = uri.lastPathSegment
            ?.substringAfterLast('.', "")
            ?.lowercase()
            ?.takeIf { it.length in 2..5 && it.all(Char::isLetterOrDigit) }
        if (fromName != null) {
            if (wallpaperMedia && fromName in WALLPAPER_VIDEO_EXTS) return fromName
            if (!imageOnly || fromName in WALLPAPER_IMAGE_EXTS) return fromName
        }

        val mime = context.contentResolver.getType(uri)?.lowercase().orEmpty()
        return when {
            mime.contains("png") -> "png"
            mime.contains("webp") -> "webp"
            mime.contains("gif") -> "gif"
            mime.contains("jpeg") || mime.contains("jpg") -> "jpg"
            wallpaperMedia && (mime.contains("mp4") || mime.contains("mpeg4")) -> "mp4"
            wallpaperMedia && mime.contains("webm") -> "webm"
            mime.contains("mpeg") || mime.contains("mp3") -> "mp3"
            mime.contains("ogg") -> "ogg"
            mime.contains("wav") -> "wav"
            mime.contains("aac") || mime.contains("m4a") -> "m4a"
            imageOnly -> defaultExt
            else -> defaultExt
        }
    }

    companion object {
        const val WALLPAPER_DIR = "home_wallpaper"
        const val BGM_DIR = "home_bgm"
        const val SHORTCUT_ART_DIR = "home_shortcut_art"
        private const val WALLPAPER_STEM = "wallpaper"
        private const val BGM_STEM = "bgm"
        private val WALLPAPER_IMAGE_EXTS = setOf("jpg", "jpeg", "png", "webp", "gif")
        private val WALLPAPER_VIDEO_EXTS = setOf("mp4", "webm", "mkv", "mov")
    }
}
