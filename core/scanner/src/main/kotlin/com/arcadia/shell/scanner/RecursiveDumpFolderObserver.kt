package com.arcadia.shell.scanner

import android.os.FileObserver
import java.io.File

/**
 * Recursive [FileObserver] over one dump-library root. New console folders are watched as they
 * appear; deleted ones are dropped. Callbacks stay cheap — the auto-scanner debounces the scan.
 */
internal class RecursiveDumpFolderObserver(
    private val root: File,
    private val onChanged: () -> Unit,
) {
    private val observers = LinkedHashMap<String, FileObserver>()

    fun start() {
        if (!root.isDirectory) return
        watchTree(root, depth = 0)
    }

    fun stop() {
        observers.values.forEach { observer -> runCatching { observer.stopWatching() } }
        observers.clear()
    }

    private fun watchTree(directory: File, depth: Int) {
        if (depth > WalkRules.MAX_DEPTH) return
        if (!directory.isDirectory) return
        if (!DumpFolderWatchPolicy.shouldWatchDirectory(directory.name) && directory != root) {
            return
        }
        startWatch(directory)
        val children = directory.listFiles() ?: return
        for (child in children) {
            if (child.isDirectory) {
                watchTree(child, depth + 1)
            }
        }
    }

    private fun startWatch(directory: File) {
        val path = directory.absolutePath
        if (observers.containsKey(path)) return
        val observer = object : FileObserver(directory, DumpFolderWatchPolicy.EVENT_MASK) {
            override fun onEvent(event: Int, childName: String?) {
                if (!DumpFolderWatchPolicy.isInterestingEvent(event)) return
                if (DumpFolderWatchPolicy.shouldIgnoreChildName(childName)) return
                val masked = event and ALL_EVENTS
                val child = childName?.let { File(directory, it) }
                if (child != null && child.isDirectory &&
                    masked and (CREATE or MOVED_TO) != 0
                ) {
                    watchTree(child, depthOf(child))
                }
                if (child != null && masked and (DELETE or MOVED_FROM or DELETE_SELF) != 0) {
                    stopWatchTree(child.absolutePath)
                }
                onChanged()
            }
        }
        observers[path] = observer
        runCatching { observer.startWatching() }
    }

    private fun stopWatchTree(prefix: String) {
        val keys = observers.keys.filter { it == prefix || it.startsWith("$prefix/") }
        for (key in keys) {
            runCatching { observers.remove(key)?.stopWatching() }
        }
    }

    private fun depthOf(directory: File): Int {
        val rootPath = root.absolutePath.trimEnd('/')
        val path = directory.absolutePath.trimEnd('/')
        if (path == rootPath || !path.startsWith("$rootPath/")) return 0
        return path.removePrefix("$rootPath/").count { it == '/' } + 1
    }
}
