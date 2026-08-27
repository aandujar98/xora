package com.arcadia.shell.scanner

import com.arcadia.shell.model.LibraryRoot

interface RomWalker {
    /** Lazily yields every file beneath [root]. Directory filtering happens here, not upstream. */
    fun walk(root: LibraryRoot): Sequence<ScannedFile>
}

internal object WalkRules {
    /** Guards against symlink cycles and pathological library layouts. */
    const val MAX_DEPTH = 12

    /**
     * Directories that never contain user ROMs but are expensive or dangerous to descend into.
     * `Android` in particular holds every app's private data and can be enormous.
     */
    private val skippedNames = setOf(
        "android",
        "lost.dir",
        "lost+found",
        "system volume information",
        ".thumbnails",
        ".trashed",
        "cache",
    )

    fun shouldSkipDirectory(name: String): Boolean {
        val normalized = name.lowercase()
        return name.startsWith(".") || normalized in skippedNames
    }

    fun shouldSkipFile(name: String): Boolean = name.startsWith(".")
}
