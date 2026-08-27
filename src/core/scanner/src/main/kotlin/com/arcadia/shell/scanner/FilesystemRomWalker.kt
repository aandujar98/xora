package com.arcadia.shell.scanner

import android.provider.DocumentsContract
import com.arcadia.shell.model.LibraryRoot
import com.arcadia.shell.model.StorageDocumentIds
import java.io.File
import javax.inject.Inject

class FilesystemRomWalker @Inject constructor() : RomWalker {

    override fun walk(root: LibraryRoot): Sequence<ScannedFile> = sequence {
        val start = File(root.location)
        if (!start.isDirectory) return@sequence
        yieldAll(walkDirectory(start, emptyList(), depth = 0, recursive = root.recursive))
    }

    private fun walkDirectory(
        directory: File,
        folderChain: List<String>,
        depth: Int,
        recursive: Boolean,
    ): Sequence<ScannedFile> = sequence {
        if (depth > WalkRules.MAX_DEPTH) return@sequence

        // listFiles returns null on permission errors rather than throwing, so a single
        // unreadable directory must not abort the whole scan.
        val entries = directory.listFiles() ?: return@sequence

        for (entry in entries) {
            if (entry.isDirectory) {
                if (!recursive || WalkRules.shouldSkipDirectory(entry.name)) continue
                yieldAll(
                    walkDirectory(
                        directory = entry,
                        folderChain = folderChain + entry.name,
                        depth = depth + 1,
                        recursive = true,
                    ),
                )
            } else {
                if (WalkRules.shouldSkipFile(entry.name)) continue
                yield(
                    ScannedFile(
                        name = entry.name,
                        filePath = entry.absolutePath,
                        // Keep a document uri twin so URI-based emulators and FS↔SAF merge share
                        // one row. This is a synthesized externalstorage uri (not grantable to
                        // third apps); launch still prefers a persisted SAF uri when present.
                        documentUri = StorageDocumentIds.documentIdForPath(entry.absolutePath)
                            ?.let { DocumentsContract.buildDocumentUri(EXTERNAL_STORAGE_AUTHORITY, it).toString() },
                        sizeBytes = entry.length(),
                        lastModified = entry.lastModified(),
                        folderChain = folderChain,
                    ),
                )
            }
        }
    }

    private companion object {
        const val EXTERNAL_STORAGE_AUTHORITY = "com.android.externalstorage.documents"
    }
}
