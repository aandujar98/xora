package com.arcadia.shell.feature.home

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Facts about *this process*, not this ViewModel and not this Activity.
 *
 * The boot clip is a cold-start event. Two earlier attempts at scoping it were both wrong:
 *
 * - On the ViewModel it replayed every time the Activity was rebuilt, so handing focus to a media
 *   picker looked like the app restarting.
 * - Keying off `savedInstanceState != null` to detect a rebuild was worse: Android hands back a
 *   bundle for a *process-death restore* too, which is a real cold start, so the clip stopped
 *   playing at the one moment it should.
 *
 * The reliable signal is this object's own lifetime. It is created with the process, so the first
 * Activity to check in is a cold start by definition and every later one is a rebuild.
 */
@Singleton
class ShellSessionState @Inject constructor() {
    private var activityEverCreated: Boolean = false
    private var coldStartPending: Boolean = true

    /**
     * Called from `MainActivity.onCreate`. The first call in a process leaves the boot clip
     * pending; any later one is an Activity rebuild and clears it.
     */
    fun onActivityCreated() {
        if (activityEverCreated) coldStartPending = false
        activityEverCreated = true
    }

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
