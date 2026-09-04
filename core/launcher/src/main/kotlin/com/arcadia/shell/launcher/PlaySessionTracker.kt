package com.arcadia.shell.launcher

import com.arcadia.shell.database.repository.LibraryRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Measures how long a game was played by timing the gap between handing off to an emulator and the
 * shell regaining focus.
 *
 * This is deliberately not UsageStatsManager. That would be more accurate but requires the user to
 * grant a scary special access permission in system settings, and the shell already knows the exact
 * moment a launch happened.
 */
@Singleton
class PlaySessionTracker @Inject constructor(
    private val libraryRepository: LibraryRepository,
) {
    private var pendingGameId: String? = null
    private var launchedAt: Long = 0

    fun onLaunched(gameId: String) {
        pendingGameId = gameId
        launchedAt = System.currentTimeMillis()
    }

    fun hasPendingSession(): Boolean = pendingGameId != null

    /** Elapsed ms since [onLaunched], or 0 when no session is open. */
    fun pendingElapsedMs(): Long {
        if (pendingGameId == null) return 0L
        return (System.currentTimeMillis() - launchedAt).coerceAtLeast(0L)
    }

    /**
     * Called when the shell comes back to the foreground. Sessions shorter than [minimumMs] are
     * discarded, since they mean the emulator failed to start or the user immediately backed out.
     */
    suspend fun settlePendingSession(minimumMs: Long = MINIMUM_SESSION_MS) {
        val gameId = pendingGameId ?: return
        pendingGameId = null

        val elapsed = System.currentTimeMillis() - launchedAt
        if (elapsed < minimumMs) return

        libraryRepository.recordPlaySession(gameId, elapsed)
    }

    private companion object {
        const val MINIMUM_SESSION_MS = 10_000L
    }
}
