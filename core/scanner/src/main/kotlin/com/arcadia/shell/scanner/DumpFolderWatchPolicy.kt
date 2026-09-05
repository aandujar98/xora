package com.arcadia.shell.scanner

import android.os.FileObserver

/**
 * Which dump-folder inotify events should start an automatic library scan.
 *
 * The parent dump folder can be named anything. We watch it (and console folders under it) so a
 * new ROM or a rename triggers a scan without requiring the folder to be called "ROMs".
 */
internal object DumpFolderWatchPolicy {
    const val DEBOUNCE_MS = 2_500L

    /** Skip a resume scan if one finished this recently, so flipping windows is not a full walk. */
    const val FOREGROUND_MIN_INTERVAL_MS = 10_000L

    val EVENT_MASK: Int =
        FileObserver.CREATE or
            FileObserver.DELETE or
            FileObserver.MOVED_FROM or
            FileObserver.MOVED_TO or
            FileObserver.CLOSE_WRITE or
            FileObserver.MODIFY or
            FileObserver.ATTRIB or
            FileObserver.DELETE_SELF or
            FileObserver.MOVE_SELF

    fun shouldWatchDirectory(name: String): Boolean = !WalkRules.shouldSkipDirectory(name)

    fun shouldIgnoreChildName(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        return WalkRules.shouldSkipFile(name) || WalkRules.shouldSkipDirectory(name)
    }

    fun isInterestingEvent(event: Int): Boolean {
        val masked = event and FileObserver.ALL_EVENTS
        return masked != 0 && masked and EVENT_MASK != 0
    }
}
