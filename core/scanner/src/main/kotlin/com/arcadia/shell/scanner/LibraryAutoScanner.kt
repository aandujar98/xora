package com.arcadia.shell.scanner

import com.arcadia.shell.database.repository.LibraryRootRepository
import com.arcadia.shell.datastore.ShellPreferences
import com.arcadia.shell.model.LibraryRoot
import com.arcadia.shell.model.RootKind
import com.arcadia.shell.model.ScanProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Scans the library when a dump folder is added and whenever files under those folders change.
 *
 * Manual **Scan now** remains available if inotify misses an event or a pass fails.
 */
@Singleton
class LibraryAutoScanner @Inject constructor(
    private val rootRepository: LibraryRootRepository,
    private val scanner: LibraryScanner,
    private val preferences: ShellPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val started = AtomicBoolean(false)
    private val watchers = mutableListOf<RecursiveDumpFolderObserver>()
    private var debounceJob: Job? = null

    fun start() {
        if (!started.compareAndSet(false, true)) return
        rootRepository.observeRoots()
            .distinctUntilChanged()
            .onEach { roots ->
                rebuildWatchers(roots)
                if (roots.isNotEmpty()) {
                    scanNow()
                }
            }
            .launchIn(scope)
    }

    /**
     * Called when the shell returns to the foreground so SAF roots and copies made while XOrA
     * was in the background still get picked up. Skips if a scan just finished.
     */
    fun onAppForeground() {
        scope.launch {
            if (rootRepository.getRoots().isEmpty()) return@launch
            val last = preferences.settings.first().lastScanAt
            if (System.currentTimeMillis() - last < DumpFolderWatchPolicy.FOREGROUND_MIN_INTERVAL_MS) {
                return@launch
            }
            scanNow()
        }
    }

    suspend fun scanNow(): ScanProgress {
        if (rootRepository.getRoots().isEmpty()) return scanner.progress.value
        return scanner.scan()
    }

    private fun onDumpFolderChanged() {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(DumpFolderWatchPolicy.DEBOUNCE_MS)
            scanNow()
        }
    }

    private fun rebuildWatchers(roots: List<LibraryRoot>) {
        watchers.forEach { it.stop() }
        watchers.clear()
        for (root in roots) {
            val directory = when (root.kind) {
                RootKind.Filesystem -> File(root.location)
                RootKind.SafTree -> null
            } ?: continue
            val watcher = RecursiveDumpFolderObserver(directory, ::onDumpFolderChanged)
            watcher.start()
            watchers += watcher
        }
    }
}
