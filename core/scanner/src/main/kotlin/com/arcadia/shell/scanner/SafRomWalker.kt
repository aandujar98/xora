package com.arcadia.shell.scanner

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.net.toUri
import com.arcadia.shell.model.LibraryRoot
import com.arcadia.shell.model.StorageDocumentIds
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject

/**
 * Walks a Storage Access Framework tree with one [android.content.ContentResolver] query per
 * directory.
 *
 * `DocumentFile.listFiles()` is the obvious API here and is roughly an order of magnitude slower,
 * because it issues a separate query per child to populate each wrapper object. On a library with
 * thousands of ROMs that difference is the gap between a few seconds and several minutes.
 *
 * When all-files access is granted, each document is also resolved to a real filesystem path so
 * XOrA Emulator and path-based players share the same library row as the launcher grid.
 */
class SafRomWalker @Inject constructor(
    @ApplicationContext private val context: Context,
    private val storageAccess: StorageAccess,
) : RomWalker {

    override fun walk(root: LibraryRoot): Sequence<ScannedFile> = sequence {
        val treeUri = runCatching { root.location.toUri() }.getOrNull() ?: return@sequence
        val rootDocumentId = runCatching {
            DocumentsContract.getTreeDocumentId(treeUri)
        }.getOrNull() ?: return@sequence
        val resolvePaths = storageAccess.hasAllFilesAccess

        yieldAll(
            walkDocument(
                treeUri = treeUri,
                documentId = rootDocumentId,
                folderChain = emptyList(),
                depth = 0,
                recursive = root.recursive,
                resolvePaths = resolvePaths,
            ),
        )
    }

    private fun walkDocument(
        treeUri: Uri,
        documentId: String,
        folderChain: List<String>,
        depth: Int,
        recursive: Boolean,
        resolvePaths: Boolean,
    ): Sequence<ScannedFile> = sequence {
        if (depth > WalkRules.MAX_DEPTH) return@sequence

        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        val directories = mutableListOf<Pair<String, String>>()

        queryChildren(childrenUri) { cursor ->
            val id = cursor.getString(COLUMN_ID) ?: return@queryChildren
            val name = cursor.getString(COLUMN_NAME) ?: return@queryChildren
            val mimeType = cursor.getString(COLUMN_MIME)

            if (mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                if (recursive && !WalkRules.shouldSkipDirectory(name)) {
                    directories += id to name
                }
            } else if (!WalkRules.shouldSkipFile(name)) {
                val documentUri = DocumentsContract
                    .buildDocumentUriUsingTree(treeUri, id)
                    .toString()
                val filePath = if (resolvePaths) {
                    StorageDocumentIds.pathForDocumentId(id)
                        ?.takeIf { File(it).isFile }
                } else {
                    null
                }
                yield(
                    ScannedFile(
                        name = name,
                        filePath = filePath,
                        documentUri = documentUri,
                        sizeBytes = cursor.getLong(COLUMN_SIZE),
                        lastModified = cursor.getLong(COLUMN_MODIFIED),
                        folderChain = folderChain,
                    ),
                )
            }
        }

        // Subdirectories are visited only after this cursor is closed, so deep trees never hold
        // dozens of cursors open at once.
        for ((childId, childName) in directories) {
            yieldAll(
                walkDocument(
                    treeUri = treeUri,
                    documentId = childId,
                    folderChain = folderChain + childName,
                    depth = depth + 1,
                    recursive = true,
                    resolvePaths = resolvePaths,
                ),
            )
        }
    }

    private suspend fun SequenceScope<ScannedFile>.queryChildren(
        childrenUri: Uri,
        onRow: suspend SequenceScope<ScannedFile>.(Cursor) -> Unit,
    ) {
        val cursor = runCatching {
            context.contentResolver.query(childrenUri, PROJECTION, null, null, null)
        }.getOrNull() ?: return

        cursor.use {
            while (it.moveToNext()) {
                onRow(it)
            }
        }
    }

    private fun Cursor.getString(index: Int): String? = if (isNull(index)) null else getString(index)

    private fun Cursor.getLong(index: Int): Long = if (isNull(index)) 0L else getLong(index)

    private companion object {
        val PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        const val COLUMN_ID = 0
        const val COLUMN_NAME = 1
        const val COLUMN_MIME = 2
        const val COLUMN_SIZE = 3
        const val COLUMN_MODIFIED = 4
    }
}
