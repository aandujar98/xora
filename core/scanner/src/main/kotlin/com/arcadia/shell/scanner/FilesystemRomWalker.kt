package com.arcadia.shell.scanner

import com.arcadia.shell.model.LibraryRoot
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
                        documentUri = null,
                        sizeBytes = entry.length(),
                        lastModified = entry.lastModified(),
                        folderChain = folderChain,
                    ),
                )
            }
        }
    }
}
