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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import com.arcadia.shell.datastore.ShellPreferences

/**
 * One history entry for the RT notification center (newest first).
 */
data class ShellNotificationHistoryItem(
    val notification: ShellNotification,
    val receivedAtMs: Long,
    val read: Boolean = false,
)

/**
 * App-wide notification router.
 *
 * - **Foreground** ([AppForegroundTracker.isForeground]): PS-style banner queue via [active].
 * - **Background** (paused / another app on top / process not resumed): Android status-bar
 *   notifications via [ShellSystemNotifier].
 * - **History** ([history] / [unreadCount]): retained for the RT bell notification center.
 *
 * [notificationsEnabled] is the master toggle (Start → Notifications). When false, [emit] drops
 * inbound events unless [force] is true (test preview).
 */
@Singleton
class ShellNotificationCenter @Inject constructor(
    private val foregroundTracker: AppForegroundTracker,
    private val systemNotifier: ShellSystemNotifier,
    private val preferences: ShellPreferences,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val inbound = Channel<ShellNotification>(Channel.UNLIMITED)

    private val _active = MutableStateFlow<ShellNotification?>(null)
    val active: StateFlow<ShellNotification?> = _active.asStateFlow()

    private val _history = MutableStateFlow<List<ShellNotificationHistoryItem>>(emptyList())
    val history: StateFlow<List<ShellNotificationHistoryItem>> = _history.asStateFlow()

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()
    private val _recent = MutableStateFlow<List<ShellNotification>>(emptyList())
    /** Newest-first history for the LT notification center. */
    val recent: StateFlow<List<ShellNotification>> = _recent.asStateFlow()

    private val recentIds = ConcurrentHashMap.newKeySet<String>()
    private val dismissed = DismissedNotificationTracker { ids ->
        scope.launch(Dispatchers.IO) { preferences.addDismissedShellNotificationIds(ids) }
    }
    private var holdJob: Job? = null
    @Volatile private var queueGeneration = 0

    @Volatile var discordFriendOnlineEnabled: Boolean = true
    @Volatile var steamFriendOnlineEnabled: Boolean = true
    @Volatile var xoraFriendOnlineEnabled: Boolean = true

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
        scope.launch(Dispatchers.IO) {
            dismissed.seed(preferences.dismissedShellNotificationIds.first())
        }
        scope.launch {
            for (notification in inbound) {
                val gen = queueGeneration
                _active.value = notification
                holdJob = launch {
                    delay(HOLD_MS)
                    clearIfCurrent(notification.id)
                }
                holdJob?.join()
                if (queueGeneration != gen) continue
                delay(GAP_MS)
            }
        }
    }

    fun emit(notification: ShellNotification, force: Boolean = false) {
        if (!notificationsEnabled && !force) return
        if (!force && !friendOnlineAllowed(notification)) return
        if (isSuppressed(notification)) return
        if (!recentIds.add(notification.id)) return
        if (recentIds.size > MAX_RECENT_IDS) {
            recentIds.clear()
            recentIds.add(notification.id)
        }
        rememberRecent(notification)

        recordHistory(notification)

        if (foregroundTracker.isForegroundNow) {
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

    /** Mark every history item read (clears the profile red-dot badge). */
    fun markAllRead() {
        _history.update { list -> list.map { it.copy(read = true) } }
        _unreadCount.value = 0
    }

    fun isSuppressed(notification: ShellNotification): Boolean =
        dismissed.isDismissed(notification.dismissalKeys())

    fun isSuppressed(keys: Collection<String>): Boolean = dismissed.isDismissed(keys)

    fun suppress(notification: ShellNotification) {
        suppressKeys(notification.dismissalKeys())
    }

    fun suppressKeys(keys: Collection<String>) {
        val trimmed = keys.map { it.trim() }.filter { it.isNotEmpty() }
        if (trimmed.isEmpty()) return
        dismissed.dismiss(trimmed)
        val banned = trimmed.toSet()
        _history.update { list ->
            list.filterNot { item -> item.notification.dismissalKeys().any { it in banned } }
        }
        _recent.update { list ->
            list.filterNot { item -> item.dismissalKeys().any { it in banned } }
        }
        _unreadCount.value = _history.value.count { !it.read }
        trimmed.forEach { recentIds.remove(it) }
        val active = _active.value
        if (active != null && active.dismissalKeys().any { it in banned }) {
            holdJob?.cancel()
            holdJob = null
            _active.value = null
        }
    }

    fun removeFromHistory(id: String) {
        if (id.isBlank()) return
        val item = _history.value.firstOrNull { it.notification.id == id }
        if (item != null) {
            suppress(item.notification)
            return
        }
        suppressKeys(listOf(id))
    }

    fun clearHistory() {
        queueGeneration++
        val pending = mutableListOf<ShellNotification>()
        while (true) {
            val dropped = inbound.tryReceive().getOrNull() ?: break
            pending += dropped
        }
        val keys = (_history.value.map { it.notification } + _recent.value + pending)
            .flatMap { it.dismissalKeys() }
        if (keys.isNotEmpty()) dismissed.dismiss(keys)
        _history.value = emptyList()
        _unreadCount.value = 0
        _recent.value = emptyList()
        recentIds.clear()
        holdJob?.cancel()
        holdJob = null
        _active.value = null
    }

    private fun friendOnlineAllowed(notification: ShellNotification): Boolean {
        val online = notification as? ShellNotification.FriendOnline ?: return true
        return when (online.network) {
            FriendNetwork.Discord -> discordFriendOnlineEnabled
            FriendNetwork.Steam -> steamFriendOnlineEnabled
            FriendNetwork.Xora -> xoraFriendOnlineEnabled
        }
    }

    private fun recordHistory(notification: ShellNotification) {
        val item = ShellNotificationHistoryItem(
            notification = notification,
            receivedAtMs = System.currentTimeMillis(),
            read = false,
        )
        _history.update { current ->
            (listOf(item) + current.filterNot { it.notification.id == notification.id })
                .take(MAX_HISTORY)
        }
        _unreadCount.update { (it + 1).coerceAtMost(MAX_HISTORY) }
    }

    private fun rememberRecent(notification: ShellNotification) {
        val next = listOf(notification) + _recent.value.filterNot { it.id == notification.id }
        _recent.value = next.take(MAX_HISTORY)
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
        private const val MAX_HISTORY = 80
    }
}
