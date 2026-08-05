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
 * Imports per-ROM custom box art, background (hero), and sound bites into app-private storage.
 */
@Singleton
class GameCustomMediaStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val root: File
        get() = File(context.filesDir, ROOT_DIR).also { it.mkdirs() }

    suspend fun importBoxArt(gameId: String, uri: Uri): String =
        importNamed(uri, stemFor(gameId, "box"), defaultExt = "jpg", imageOnly = true)

    suspend fun importBackground(gameId: String, uri: Uri): String =
        importNamed(uri, stemFor(gameId, "hero"), defaultExt = "jpg", imageOnly = false)

    suspend fun importSoundBite(gameId: String, uri: Uri): String =
        importNamed(uri, stemFor(gameId, "bite"), defaultExt = "mp3", imageOnly = false)

    fun clearBoxArt(gameId: String) = clearStem(stemFor(gameId, "box"))

    fun clearBackground(gameId: String) = clearStem(stemFor(gameId, "hero"))

    fun clearSoundBite(gameId: String) = clearStem(stemFor(gameId, "bite"))

    private fun stemFor(gameId: String, kind: String): String {
        val safe = gameId.lowercase().replace(Regex("[^a-z0-9._-]"), "_").take(80)
        return "${kind}_$safe"
    }

    private fun clearStem(stem: String) {
        root.listFiles()
            ?.filter { it.isFile && it.name.startsWith("$stem.") }
            ?.forEach { it.delete() }
    }

    private suspend fun importNamed(
        uri: Uri,
        stem: String,
        defaultExt: String,
        imageOnly: Boolean,
    ): String = withContext(Dispatchers.IO) {
        val extension = guessExtension(uri, defaultExt, imageOnly)
        val fileName = "$stem.$extension"
        val target = File(root, fileName)
        val temp = File(root, "$stem.part")

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

        root.listFiles()
            ?.filter { it.isFile && it.name != fileName && it.name.startsWith("$stem.") }
            ?.forEach { it.delete() }

        target.absolutePath
    }

    private fun guessExtension(uri: Uri, defaultExt: String, imageOnly: Boolean): String {
        val fromName = uri.lastPathSegment
            ?.substringAfterLast('.', "")
            ?.lowercase()
            ?.takeIf { it.length in 2..5 && it.all(Char::isLetterOrDigit) }
        if (fromName != null) {
            if (!imageOnly || fromName in IMAGE_EXTS) return fromName
        }
        val mime = context.contentResolver.getType(uri)?.lowercase().orEmpty()
        return when {
            mime.contains("png") -> "png"
            mime.contains("webp") -> "webp"
            mime.contains("gif") -> "gif"
            mime.contains("jpeg") || mime.contains("jpg") -> "jpg"
            mime.contains("mpeg") || mime.contains("mp3") -> "mp3"
            mime.contains("ogg") -> "ogg"
            mime.contains("wav") -> "wav"
            mime.contains("aac") || mime.contains("m4a") -> "m4a"
            imageOnly -> defaultExt
            else -> defaultExt
        }
    }

    companion object {
        const val ROOT_DIR = "game_custom_media"
        private val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "webp", "gif")
    }
}
