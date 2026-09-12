package com.arcadia.shell.feature.home

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Facts about *this process*, not this ViewModel.
 *
 * The boot clip is a cold-start event, so it has to be scoped to the process. Held on the
 * ViewModel it replayed every time the Activity was recreated — and a media picker handing focus
 * back is enough to do that on a memory-tight handheld, which is why adding a wallpaper looked
 * like the app restarting.
 */
@Singleton
class ShellSessionState @Inject constructor() {
    /** True until the first resume of the process consumes it. */
    private var coldStartPending: Boolean = true

    /** Returns true once per process, then false for the life of it. */
    fun consumeColdStart(): Boolean {
        if (!coldStartPending) return false
        coldStartPending = false
        return true
    }

    /** Onboarding restarts the shell's sense of "first run" without a new process. */
    fun markColdStartPending() {
        coldStartPending = true
    }

    fun clearColdStart() {
        coldStartPending = false
    }
}
