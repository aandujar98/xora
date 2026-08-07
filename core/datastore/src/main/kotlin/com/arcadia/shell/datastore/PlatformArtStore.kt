package com.arcadia.shell.datastore

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * User-chosen banner art for a console, shown on the XMB system cards.
 *
 * ScreenScraper covers most systems automatically, but it needs credentials and its system media
 * is not always the artwork someone wants. A banner imported here always wins.
 */
@Singleton
class PlatformArtStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val root: File
        get() = File(context.filesDir, ROOT_DIR).also { it.mkdirs() }

    private val banners = MutableStateFlow(scan())

    /** Platform id → absolute path of the user's own banner. */
    val bannerByPlatformId: StateFlow<Map<String, String>> = banners.asStateFlow()

    suspend fun import(platformId: String, uri: Uri): String = withContext(Dispatchers.IO) {
        val stem = stemFor(platformId)
        val extension = guessExtension(uri)
        val target = File(root, "$stem.$extension")
        val temp = File(root, "$stem.part")

        context.contentResolver.openInputStream(uri)?.use { input ->
            temp.outputStream().use { output -> input.copyTo(output) }
        } ?: error("Could not read the selected image.")

        if (temp.length() == 0L) {
            temp.delete()
            error("Selected image was empty.")
        }

        if (target.exists()) target.delete()
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
        // Drop any earlier banner for this platform that used a different extension.
        root.listFiles()
            ?.filter { it.isFile && it.name != target.name && it.name.startsWith("$stem.") }
            ?.forEach { it.delete() }

        banners.value = scan()
        target.absolutePath
    }

    fun clear(platformId: String) {
        val stem = stemFor(platformId)
        root.listFiles()
            ?.filter { it.isFile && it.name.startsWith("$stem.") }
            ?.forEach { it.delete() }
        banners.value = scan()
    }

    private fun scan(): Map<String, String> =
        root.listFiles()
            ?.filter { it.isFile && it.length() > 0L && it.extension.lowercase() in IMAGE_EXTS }
            ?.associate { it.nameWithoutExtension to it.absolutePath }
            .orEmpty()

    private fun stemFor(platformId: String): String =
        platformId.lowercase().replace(Regex("[^a-z0-9._-]"), "_").take(60)

    private fun guessExtension(uri: Uri): String {
        val fromName = uri.lastPathSegment
            ?.substringAfterLast('.', "")
            ?.lowercase()
            ?.takeIf { it in IMAGE_EXTS }
        if (fromName != null) return fromName
        val mime = context.contentResolver.getType(uri)?.lowercase().orEmpty()
        return when {
            mime.contains("png") -> "png"
            mime.contains("webp") -> "webp"
            mime.contains("gif") -> "gif"
            else -> "jpg"
        }
    }

    private companion object {
        const val ROOT_DIR = "platform_art_custom"
        val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "webp", "gif")
    }
}
