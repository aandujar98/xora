package com.arcadia.shell.model

enum class RootKind {
    /**
     * A real filesystem path, only usable with all-files access. Yields `{file.path}`, and
     * `{file.documenturi}` / `{file.uri}` as grantable FileProvider content URIs.
     */
    Filesystem,

    /** A Storage Access Framework tree uri. Yields `{file.documenturi}` but never a real path. */
    SafTree,
}

data class LibraryRoot(
    val id: String,
    /** Absolute path for [RootKind.Filesystem], or a tree uri string for [RootKind.SafTree]. */
    val location: String,
    val kind: RootKind,
    val label: String,
    /** When set, every file under this root is forced to this platform instead of auto-detected. */
    val forcedPlatformId: String? = null,
    val recursive: Boolean = true,
)

data class ScanProgress(
    val isRunning: Boolean = false,
    val currentRoot: String? = null,
    val filesSeen: Int = 0,
    val gamesFound: Int = 0,
    val finishedAt: Long? = null,
    val error: String? = null,
)
