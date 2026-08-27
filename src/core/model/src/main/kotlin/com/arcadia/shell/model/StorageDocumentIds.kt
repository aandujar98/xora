package com.arcadia.shell.model

/**
 * Bidirectional mapping between shared-storage filesystem paths and Storage Access Framework
 * document ids (`primary:Roms/game.gba`, `ABCD-1234:Roms/game.gba`).
 *
 * Pure string helpers so scanner and launcher can agree on the same ROM identity whether a file was
 * indexed via all-files access or the document picker.
 */
object StorageDocumentIds {
    private val STORAGE_VOLUME = Regex("^/storage/([^/]+)(?:/(.*))?$")

    /**
     * Returns a document id such as `primary:Roms/psp/Game.iso` or `ABCD-1234:Roms/Game.iso`,
     * or null when [absolutePath] is not under a recognizable shared-storage root.
     */
    fun documentIdForPath(absolutePath: String): String? {
        // Prefer a string normalize over File.canonicalPath: Android ROM paths are Unix-shaped, and
        // canonicalPath on a Windows host rewrites them into nonsense during unit tests.
        val path = absolutePath.replace('\\', '/').trimEnd('/')

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

        val volumeMatch = STORAGE_VOLUME.matchEntire(path) ?: return null
        val volume = volumeMatch.groupValues[1]
        val relative = volumeMatch.groupValues[2]
        if (volume == "emulated") return null
        return if (relative.isEmpty()) "$volume:" else "$volume:$relative"
    }

    /**
     * Inverse of [documentIdForPath] for the common primary / volume layouts. Returns null when the
     * id cannot be mapped to a real path this process could open with all-files access.
     */
    fun pathForDocumentId(documentId: String): String? {
        val trimmed = documentId.trim()
        if (trimmed.isEmpty()) return null
        val colon = trimmed.indexOf(':')
        if (colon < 0) return null
        val volume = trimmed.substring(0, colon)
        val relative = trimmed.substring(colon + 1).trimStart('/')
        return when {
            volume == "primary" && relative.isEmpty() -> "/storage/emulated/0"
            volume == "primary" -> "/storage/emulated/0/$relative"
            volume.isNotEmpty() && relative.isEmpty() -> "/storage/$volume"
            volume.isNotEmpty() -> "/storage/$volume/$relative"
            else -> null
        }
    }

    /** Stable key for matching the same physical file across path and content-uri indexing. */
    fun logicalKeyForPath(absolutePath: String): String? =
        documentIdForPath(absolutePath)?.let(::normalizeDocumentId)

    fun logicalKeyForDocumentId(documentId: String): String =
        normalizeDocumentId(documentId)

    /**
     * Document ids from [DocumentsContract.buildDocumentUriUsingTree] are already decoded when
     * read via [DocumentsContract.getDocumentId]; normalize separators for matching.
     */
    fun normalizeDocumentId(documentId: String): String =
        documentId.replace('\\', '/').trim().trimEnd('/')
}
