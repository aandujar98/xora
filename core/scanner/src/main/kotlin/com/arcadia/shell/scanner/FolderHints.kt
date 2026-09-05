package com.arcadia.shell.scanner

import com.arcadia.shell.model.LibraryRoot
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Folder names the platform resolver can read when deciding whether an ambiguous file (`.iso`,
 * `.zip`) belongs to a console.
 *
 * Walkers used to start [ScannedFile.folderChain] at the library root, so a user who added
 * `ROMS/PSP` or `ROMS/PS2` — the usual layout — produced an empty chain and every ISO was
 * dropped. The root path, the file path, and a SAF tree id all still contain those names.
 */
internal object FolderHints {

    fun seedForRoot(root: LibraryRoot): List<String> {
        val fromLocation = directorySegments(root.location)
        return if (root.label.isNotBlank() &&
            !fromLocation.lastOrNull().equals(root.label, ignoreCase = true)
        ) {
            fromLocation + root.label
        } else {
            fromLocation
        }
    }

    /**
     * Deepest folder first: the file's parent, then the walk chain, then the library root.
     * Duplicates stay in first-seen order so `ROMS/Nintendo/SNES` still prefers SNES.
     */
    fun deepestFirst(
        folderChain: List<String>,
        filePath: String?,
        documentUri: String?,
        rootLabel: String?,
        rootLocation: String?,
    ): List<String> {
        val seen = LinkedHashSet<String>()
        fun addAll(names: List<String>) {
            names.asReversed().forEach { name ->
                if (name.isNotBlank()) seen += name
            }
        }
        addAll(folderChain)
        addAll(parentDirectoriesOfFile(filePath))
        addAll(segmentsFromSafUri(documentUri))
        addAll(directorySegments(rootLocation))
        if (!rootLabel.isNullOrBlank()) seen += rootLabel
        return seen.toList()
    }

    fun directorySegments(path: String?): List<String> {
        if (path.isNullOrBlank()) return emptyList()
        val trimmed = path.trim()
        if (trimmed.startsWith("content:", ignoreCase = true)) {
            return segmentsFromSafUri(trimmed)
        }
        return splitDirectories(trimmed)
    }

    fun parentDirectoriesOfFile(filePath: String?): List<String> {
        if (filePath.isNullOrBlank()) return emptyList()
        val normalized = filePath.replace('\\', '/').trimEnd('/')
        val slash = normalized.lastIndexOf('/')
        if (slash <= 0) return emptyList()
        return splitDirectories(normalized.substring(0, slash))
    }

    fun segmentsFromSafUri(uri: String?): List<String> {
        if (uri.isNullOrBlank()) return emptyList()
        val decoded = runCatching {
            URLDecoder.decode(uri, StandardCharsets.UTF_8.name())
        }.getOrDefault(uri)
        val documentId = when {
            "/document/" in decoded -> decoded.substringAfterLast("/document/").substringBefore('?')
            "/tree/" in decoded -> decoded.substringAfterLast("/tree/")
                .substringBefore("/document")
                .substringBefore('?')
            else -> return emptyList()
        }
        return segmentsFromDocumentId(documentId)
    }

    private fun segmentsFromDocumentId(documentId: String): List<String> {
        val colon = documentId.indexOf(':')
        val relative = if (colon >= 0) documentId.substring(colon + 1) else documentId
        val parts = splitDirectories(relative)
        val last = parts.lastOrNull() ?: return parts
        return if (last.contains('.')) parts.dropLast(1) else parts
    }

    private fun splitDirectories(path: String): List<String> =
        path.replace('\\', '/')
            .trim('/')
            .split('/')
            .filter { it.isNotBlank() && it != "." && it != ".." }
}
