package com.arcadia.shell.scraper

import android.content.Context
import android.provider.DocumentsContract
import androidx.core.net.toUri
import java.io.File
import java.io.FileInputStream

/**
 * Opens ROM bytes for hashing via real paths or SAF document URIs, including sibling files
 * referenced by `.cue` / `.gdi` / `.m3u` sheets.
 */
class RomFileAccess(private val context: Context) {
    fun openPrimary(filePath: String?, documentUri: String?): RaSeekable? {
        if (filePath != null) {
            val file = File(filePath)
            if (file.isFile) return FileRaSeekable(file)
        }
        if (documentUri != null) return openUri(documentUri)
        return null
    }

    fun openRelative(basePath: String?, baseDocumentUri: String?, relativeOrAbsolute: String): RaSeekable? {
        val normalized = relativeOrAbsolute.replace('\\', '/')
        // Absolute filesystem path
        if (normalized.startsWith('/') || (normalized.length > 2 && normalized[1] == ':')) {
            val file = File(normalized)
            if (file.isFile) return FileRaSeekable(file)
        }

        if (basePath != null) {
            val parent = File(basePath).parentFile
            if (parent != null) {
                val byName = File(parent, File(normalized).name)
                if (byName.isFile) return FileRaSeekable(byName)
                val relative = File(parent, normalized)
                if (relative.isFile) return FileRaSeekable(relative)
            }
        }

        if (baseDocumentUri != null) {
            val name = File(normalized).name
            return openSiblingUri(baseDocumentUri, name)
        }
        return null
    }

    fun openResolver(filePath: String?, documentUri: String?): (String) -> RaSeekable? {
        val primaryKey = filePath ?: documentUri
        return { requested ->
            val reqNorm = requested.replace('\\', '/')
            val primaryNorm = primaryKey?.replace('\\', '/')
            if (primaryNorm != null &&
                (reqNorm == primaryNorm || File(reqNorm).name.equals(File(primaryNorm).name, true))
            ) {
                openPrimary(filePath, documentUri)
            } else {
                openRelative(filePath, documentUri, requested)
            }
        }
    }

    private fun openUri(uriString: String): RaSeekable? {
        val uri = runCatching { uriString.toUri() }.getOrNull() ?: return null
        val resolver = context.contentResolver
        val pfd = runCatching { resolver.openFileDescriptor(uri, "r") }.getOrNull()
        if (pfd != null) {
            val channel = FileInputStream(pfd.fileDescriptor).channel
            return ChannelRaSeekable(channel, sizeOverride = null) { pfd.close() }
        }
        // Last resort: fully buffer small streams (should be rare for disc images).
        val bytes = runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            ?: return null
        return ByteArrayRaSeekable(bytes)
    }

    private fun openSiblingUri(documentUri: String, siblingName: String): RaSeekable? {
        val uri = runCatching { documentUri.toUri() }.getOrNull() ?: return null
        val resolver = context.contentResolver
        val docId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull() ?: return null
        val treeDocId = runCatching { DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
        val authority = uri.authority ?: return null

        val parentDocId = parentDocumentId(docId) ?: return null
        val treeUri = if (treeDocId != null) {
            DocumentsContract.buildTreeDocumentUri(authority, treeDocId)
        } else {
            // Some providers still accept child queries from the document uri's tree form.
            uri
        }

        val childrenUri = runCatching {
            DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        }.getOrNull() ?: return null

        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        )
        resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val name = cursor.getString(nameIdx) ?: continue
                if (name.equals(siblingName, ignoreCase = true)) {
                    val childId = cursor.getString(idIdx) ?: continue
                    val childUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                    return openUri(childUri.toString())
                }
            }
        }

        // Fallback: synthesize sibling document id (ExternalStorageProvider style).
        val siblingId = when {
            parentDocId.endsWith(":") -> parentDocId + siblingName
            else -> "$parentDocId/$siblingName"
        }
        val siblingUri = runCatching {
            DocumentsContract.buildDocumentUriUsingTree(treeUri, siblingId)
        }.getOrNull() ?: return null
        return openUri(siblingUri.toString())
    }

    private fun parentDocumentId(docId: String): String? {
        val colon = docId.indexOf(':')
        if (colon < 0) {
            val slash = docId.lastIndexOf('/')
            return if (slash > 0) docId.substring(0, slash) else null
        }
        val prefix = docId.substring(0, colon + 1)
        val path = docId.substring(colon + 1)
        val slash = path.lastIndexOf('/')
        if (slash < 0) return prefix
        return prefix + path.substring(0, slash)
    }
}

private class ByteArrayRaSeekable(private val bytes: ByteArray) : RaSeekable {
    override fun size(): Long = bytes.size.toLong()

    override fun readAt(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
        if (position < 0 || position >= bytes.size) return -1
        val available = (bytes.size - position.toInt()).coerceAtLeast(0)
        val n = minOf(length, available)
        System.arraycopy(bytes, position.toInt(), buffer, offset, n)
        return n
    }

    override fun close() = Unit
}
