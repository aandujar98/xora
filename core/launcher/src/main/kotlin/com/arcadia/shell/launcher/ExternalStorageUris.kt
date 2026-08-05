package com.arcadia.shell.launcher

import android.provider.DocumentsContract
import androidx.core.net.toUri

/**
 * Builds Storage Access Framework document URIs that correspond to real filesystem paths.
 *
 * Kept for path↔document-id helpers and tests. Launch code must not hand these synthesized URIs to
 * other apps: the shell never obtained them via OPEN_DOCUMENT, so
 * [Intent.FLAG_GRANT_READ_URI_PERMISSION] cannot authorize the emulator UID and open fails with
 * SecurityException. Prefer a real SAF [com.arcadia.shell.model.Game.documentUri] or a FileProvider
 * URI the shell owns and can grant.
 */
object ExternalStorageUris {
    const val AUTHORITY = "com.android.externalstorage.documents"

    /**
     * Returns a document id such as `primary:Roms/game.iso` or `ABCD-1234:Roms/game.iso`,
     * or null when [absolutePath] is not under a recognizable shared-storage root.
     */
    fun documentIdForPath(absolutePath: String): String? {
        // Prefer a string normalize over File.canonicalPath: Android ROM paths are Unix-shaped, and
        // canonicalPath on a Windows host rewrites them into nonsense during unit tests.
        val path = absolutePath.replace('\\', '/').trimEnd('/')

        // Primary shared storage: /storage/emulated/0/...
        val primaryPrefixes = listOf(
            "/storage/emulated/0",
            "/sdcard",
            "/mnt/sdcard",
        )
        for (prefix in primaryPrefixes) {
            if (path == prefix) return "primary:"
            if (path.startsWith("$prefix/")) {
                return "primary:" + path.removePrefix("$prefix/")
            }
        }

        // Adopted / portable volumes: /storage/XXXX-XXXX/...
        val volumeMatch = STORAGE_VOLUME.matchEntire(path) ?: return null
        val volume = volumeMatch.groupValues[1]
        val relative = volumeMatch.groupValues[2]
        if (volume == "emulated") return null
        return if (relative.isEmpty()) "$volume:" else "$volume:$relative"
    }

    fun documentUriForPath(absolutePath: String): String? {
        val documentId = documentIdForPath(absolutePath) ?: return null
        return DocumentsContract.buildDocumentUri(AUTHORITY, documentId).toString()
    }

    fun isExternalStorageDocumentUri(uri: String): Boolean =
        runCatching { uri.toUri().authority == AUTHORITY }.getOrDefault(false)

    private val STORAGE_VOLUME = Regex("^/storage/([^/]+)(?:/(.*))?$")
}
