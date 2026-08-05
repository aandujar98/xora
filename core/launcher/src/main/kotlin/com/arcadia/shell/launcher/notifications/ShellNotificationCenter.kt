package com.arcadia.shell.launcher.notifications

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-wide notification router.
 *
 * - **Foreground** ([AppForegroundTracker.isForeground]): PS-style banner queue via [active].
 * - **Background** (paused / another app on top / process not resumed): Android status-bar
 *   notifications via [ShellSystemNotifier].
 *
 * [notificationsEnabled] is the master toggle (Start → Notifications). When false, [emit] drops
 * inbound events unless [force] is true (test preview).
 */
@Singleton
class ShellNotificationCenter @Inject constructor(
    private val foregroundTracker: AppForegroundTracker,
    private val systemNotifier: ShellSystemNotifier,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val inbound = Channel<ShellNotification>(Channel.UNLIMITED)

    private val _active = MutableStateFlow<ShellNotification?>(null)
    val active: StateFlow<ShellNotification?> = _active.asStateFlow()

    private val recentIds = ConcurrentHashMap.newKeySet<String>()
    private var holdJob: Job? = null

    /**
     * Master enable for banners **and** Android notifications.
     * Mirrored onto [ShellSystemNotifier.notificationsEnabled].
     */
    @Volatile
    var notificationsEnabled: Boolean = true
        set(value) {
            field = value
            systemNotifier.notificationsEnabled = value
        }

    /** @deprecated Prefer [notificationsEnabled]; kept for call-site compatibility. */
    var bannersEnabled: Boolean
        get() = notificationsEnabled
        set(value) {
            notificationsEnabled = value
        }

    init {
        scope.launch {
            for (notification in inbound) {
                _active.value = notification
                holdJob = launch {
                    delay(HOLD_MS)
                    clearIfCurrent(notification.id)
                }
                holdJob?.join()
                delay(GAP_MS)
            }
        }
    }

    fun emit(notification: ShellNotification, force: Boolean = false) {
        if (!notificationsEnabled && !force) return
        if (!recentIds.add(notification.id)) return
        if (recentIds.size > MAX_RECENT_IDS) {
            recentIds.clear()
            recentIds.add(notification.id)
        }

        if (foregroundTracker.isForeground) {
            inbound.trySend(notification)
        } else if (force && !systemNotifier.notificationsEnabled) {
            // Test preview while master toggle is off: briefly allow the system post.
            systemNotifier.notificationsEnabled = true
            try {
                systemNotifier.post(notification)
            } finally {
                systemNotifier.notificationsEnabled = false
            }
        } else {
            systemNotifier.post(notification)
        }
    }

    /** Dismiss the visible banner early (tap / optional controller). */
    fun dismiss() {
        val current = _active.value ?: return
        holdJob?.cancel()
        holdJob = null
        clearIfCurrent(current.id)
    }

    private fun clearIfCurrent(id: String) {
        if (_active.value?.id == id) {
            _active.value = null
        }
    }

    companion object {
        /** Visible hold before slide-out (~PS toast timing). */
        const val HOLD_MS = 4_500L
        /** Brief gap so exit/enter animations do not collide. */
        const val GAP_MS = 220L
        private const val MAX_RECENT_IDS = 120
    }
}
