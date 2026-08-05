package com.arcadia.shell.launcher

import android.content.Context
import androidx.core.content.FileProvider
import com.arcadia.shell.model.Game
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

class MissingPlaceholderException(
    val placeholder: String,
    override val message: String,
) : Exception(message)

/**
 * Fills `{file.*}` placeholders in a launch template.
 *
 * The distinction between the path and uri forms is not cosmetic. Dolphin and DuckStation read a
 * raw filesystem path out of a string extra, while PPSSPP and AetherSX2 expect a content uri as
 * intent data. A library indexed through the Storage Access Framework has no real path at all, so
 * some emulators simply cannot be driven from a SAF root, and that has to surface as a clear
 * failure rather than a broken launch.
 *
 * Content URIs must be ones this app can grant to the emulator. A real SAF [Game.documentUri] is
 * fine (persistable tree grant). An owned FileProvider URI is fine (explicit grant). Synthesizing
 * `content://com.android.externalstorage.documents/document/…` from a path is not: it passes
 * DocumentsContract shape checks but the shell never obtained access via OPEN_DOCUMENT, so the
 * target UID hits SecurityException when it opens the ROM.
 */
@Singleton
class PlaceholderResolver @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val authority get() = "${context.packageName}.files"

    @Throws(MissingPlaceholderException::class)
    fun resolve(raw: String, game: Game): String {
        var value = raw

        if (value.contains(PATH)) {
            val path = game.filePath ?: throw MissingPlaceholderException(
                PATH,
                "${game.title} was indexed through the document picker, which cannot provide a " +
                    "real file path. This emulator requires one.",
            )
            value = value.replace(PATH, path)
        }

        if (value.contains(DOCUMENT_URI)) {
            value = value.replace(DOCUMENT_URI, grantableContentUri(game, DOCUMENT_URI))
        }

        if (value.contains(URI)) {
            value = value.replace(URI, grantableContentUri(game, URI))
        }

        if (value.contains(NAME)) {
            value = value.replace(NAME, game.fileName)
        }

        if (value.contains(NAME_NO_EXT)) {
            value = value.replace(NAME_NO_EXT, game.fileName.substringBeforeLast('.'))
        }

        if (value.contains(DIRECTORY)) {
            val parent = game.filePath?.let { File(it).parent } ?: throw MissingPlaceholderException(
                DIRECTORY,
                "${game.title} has no filesystem directory because it was indexed through SAF.",
            )
            value = value.replace(DIRECTORY, parent)
        }

        return value
    }

    /**
     * A content URI the shell can hand to another package with [Intent.FLAG_GRANT_READ_URI_PERMISSION].
     *
     * Prefer an owned [FileProvider] URI whenever a real filesystem path exists: those grants always
     * target the destination package. A persisted SAF [Game.documentUri] is used only when there is
     * no path (document-picker libraries). Never synthesize external-storage DocumentsContract URIs.
     */
    private fun grantableContentUri(game: Game, placeholder: String): String {
        val path = game.filePath
        if (path != null) {
            return runCatching {
                FileProvider.getUriForFile(context, authority, File(path)).toString()
            }.getOrElse {
                throw MissingPlaceholderException(
                    placeholder,
                    "Could not share ${game.fileName} with another app: ${it.message}",
                )
            }
        }

        game.documentUri?.let { return it }

        throw MissingPlaceholderException(
            placeholder,
            "${game.title} has neither a file path nor a document uri.",
        )
    }

    private companion object {
        const val PATH = "{file.path}"
        const val URI = "{file.uri}"
        const val DOCUMENT_URI = "{file.documenturi}"
        const val NAME = "{file.name}"
        const val NAME_NO_EXT = "{file.nameNoExt}"
        const val DIRECTORY = "{file.dir}"
    }
}
