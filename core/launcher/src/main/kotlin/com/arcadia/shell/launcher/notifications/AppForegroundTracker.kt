package com.arcadia.shell.launcher.notifications

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Process-level foreground = [ProcessLifecycleOwner] resumed.
 *
 * When SORA is paused behind an emulator / home / another app, [isForeground] is false even if
 * the process is still alive — callers should then use Android status-bar notifications.
 */
@Singleton
class AppForegroundTracker @Inject constructor() {

    private val _isForeground = MutableStateFlow(false)

    /** Observable form, for work that should stop while nobody is looking at the shell. */
    val isForeground: StateFlow<Boolean> = _isForeground.asStateFlow()

    val isForegroundNow: Boolean get() = _isForeground.value

    private var started = false

    fun start() {
        if (started) return
        started = true
        // Seed from current state so early emits before the first ON_RESUME are correct.
        _isForeground.value = ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(
            Lifecycle.State.RESUMED,
        )
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onResume(owner: LifecycleOwner) {
                    _isForeground.value = true
                }

                override fun onPause(owner: LifecycleOwner) {
                    _isForeground.value = false
                }
            },
        )
    }
}
