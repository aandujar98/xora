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
 * Copies a user-picked avatar into app-private storage so the grant on the picker URI is not
 * required after the import completes.
 */
@Singleton
class ProfileAvatarStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val dir: File
        get() = File(context.filesDir, DIR_NAME).also { it.mkdirs() }

    /** Imports [uri] and returns the relative file name stored under [DIR_NAME]. */
    suspend fun importFromUri(uri: Uri): String = withContext(Dispatchers.IO) {
        val extension = guessExtension(uri)
        val fileName = "$FILE_STEM.$extension"
        val target = File(dir, fileName)
        val temp = File(dir, "$FILE_STEM.part")

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

        // Drop any previous avatar that used a different extension.
        dir.listFiles()
            ?.filter { it.isFile && it.name != fileName && it.name.startsWith("$FILE_STEM.") }
            ?.forEach { it.delete() }

        fileName
    }

    fun resolveFile(fileName: String?): File? {
        val safe = fileName?.let { File(it).name }?.takeIf { it.isNotBlank() } ?: return null
        val file = File(dir, safe)
        return file.takeIf { it.isFile && it.length() > 0L }
    }

    fun clear() {
        runCatching {
            dir.listFiles()?.forEach { it.delete() }
        }
    }

    private fun guessExtension(uri: Uri): String {
        val fromName = uri.lastPathSegment
            ?.substringAfterLast('.', "")
            ?.lowercase()
            ?.takeIf { it.length in 2..4 && it.all(Char::isLetterOrDigit) }
        if (fromName != null) return fromName

        val mime = context.contentResolver.getType(uri)?.lowercase().orEmpty()
        return when {
            mime.contains("png") -> "png"
            mime.contains("webp") -> "webp"
            mime.contains("gif") -> "gif"
            else -> "jpg"
        }
    }

    companion object {
        const val DIR_NAME = "avatars"
        private const val FILE_STEM = "profile"
    }
}
