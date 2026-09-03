package com.arcadia.shell.launcher

import android.provider.DocumentsContract
import androidx.core.net.toUri
import com.arcadia.shell.model.StorageDocumentIds

/**
 * Builds Storage Access Framework document URIs that correspond to real filesystem paths.
 *
 * Path↔document-id mapping lives in [StorageDocumentIds] so the scanner can use the same identity
 * rules. Launch code must not hand synthesized URIs to other apps: the shell never obtained them
 * via OPEN_DOCUMENT, so [Intent.FLAG_GRANT_READ_URI_PERMISSION] cannot authorize the emulator UID
 * and open fails with SecurityException. Prefer a real SAF [com.arcadia.shell.model.Game.documentUri]
 * or a FileProvider URI the shell owns and can grant.
 */
object ExternalStorageUris {
    const val AUTHORITY = "com.android.externalstorage.documents"

    fun documentIdForPath(absolutePath: String): String? =
        StorageDocumentIds.documentIdForPath(absolutePath)

    fun documentUriForPath(absolutePath: String): String? {
        val documentId = documentIdForPath(absolutePath) ?: return null
        return DocumentsContract.buildDocumentUri(AUTHORITY, documentId).toString()
    }

    fun isExternalStorageDocumentUri(uri: String): Boolean =
        runCatching { uri.toUri().authority == AUTHORITY }.getOrDefault(false)
}
