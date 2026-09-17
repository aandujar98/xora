package com.arcadia.shell.launcher.notifications

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Where a new line goes in the queue.
 *
 * Playtime jumps ahead of everything still waiting, because it is posted on the way back from a
 * game — exactly when a scrape or a scan the session kicked off is likely to be sitting in front
 * of it, and "you played for two hours" arriving fourth is news about nothing. It goes behind any
 * playtime line already queued rather than in front of it, so two of them stay in the order they
 * happened.
 *
 * Everything else is first in, first out.
 */
internal fun enqueueDashLine(
    queue: ArrayDeque<DashNotification>,
    notification: DashNotification,
) {
    if (notification.kind != DashNotificationKind.Playtime) {
        queue.addLast(notification)
        return
    }
    val insertAt = queue.indexOfFirst { it.kind != DashNotificationKind.Playtime }
    if (insertAt < 0) queue.addLast(notification) else queue.add(insertAt, notification)
}

/**
 * Queue behind the bottom-left Dash line. One shows at a time, for [dashNotificationDurationMs],
 * and the rest wait their turn rather than stacking up the corner.
 */
@Singleton
class DashNotificationCenter @Inject constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /**
     * The waiting lines, in the order they will be shown. A deque rather than a channel because
     * playtime jumps to the front: it is posted on the way back from a game, which is exactly
     * when a scrape or a scan the session kicked off is likely to be queued ahead of it, and
     * "you played for two hours" arriving fourth is news about nothing.
     */
    private val queue = ArrayDeque<DashNotification>()
    private val queueLock = Any()

    /** Wakes the pump. Conflated: one nudge is as good as ten when the deque holds the work. */
    private val nudge = Channel<Unit>(Channel.CONFLATED)

    private val _active = MutableStateFlow<DashNotification?>(null)
    val active: StateFlow<DashNotification?> = _active.asStateFlow()

    /**
     * Fires once per shown line, for whoever owns the speakers. Separate from [active] so a
     * recomposition cannot replay a cue.
     */
    private val _cue = MutableStateFlow<DashNotification?>(null)
    val cue: StateFlow<DashNotification?> = _cue.asStateFlow()

    private val bootIntroHold = MutableStateFlow(false)

    /** True while a song is playing: Dash cues stay silent under it. */
    @Volatile
    var musicPlaying: Boolean = false

    @Volatile
    var enabled: Boolean = true

    fun setBootIntroActive(active: Boolean) {
        bootIntroHold.value = active
    }

    init {
        scope.launch {
            while (true) {
                val notification = takeNext()
                if (notification == null) {
                    nudge.receive()
                    continue
                }
                // Nothing shows over the boot video; the queue waits it out.
                bootIntroHold.first { !it }
                _active.value = notification
                // A music alert never makes a sound, and nothing chimes over a playing song.
                if (notification.kind != DashNotificationKind.Music && !musicPlaying) {
                    _cue.value = notification
                }
                delay(dashNotificationDurationMs(notification.text))
                _active.value = null
                _cue.value = null
                delay(DASH_GAP_MS)
            }
        }
    }

    fun emit(notification: DashNotification) {
        if (!enabled) return
        if (!admits(notification)) return
        synchronized(queueLock) { enqueueDashLine(queue, notification) }
        nudge.trySend(Unit)
    }

    private fun takeNext(): DashNotification? = synchronized(queueLock) { queue.removeFirstOrNull() }

    /**
     * Keeps the dash from chattering. A kind that has just spoken stays quiet for
     * [DASH_COOLDOWN_MS], because a second "Fetching artwork" a moment after the first tells the
     * player nothing they do not already know.
     *
     * Two kinds are exempt, because each of their alerts carries news the last one did not:
     * playtime is only ever posted once on the way back from a game, and a song change is the
     * whole point of the music line. The song still has to actually differ — a track repeating
     * itself, or a pause and resume of the same one, is not news.
     */
    @Synchronized
    private fun admits(notification: DashNotification): Boolean {
        val now = System.currentTimeMillis()
        if (notification.kind == DashNotificationKind.Music) {
            if (notification.text.equals(lastMusicLine, ignoreCase = true)) return false
            lastMusicLine = notification.text
            return true
        }
        if (notification.kind == DashNotificationKind.Playtime) return true
        val last = lastShownAtMs[notification.kind]
        if (last != null && now - last < DASH_COOLDOWN_MS) return false
        lastShownAtMs[notification.kind] = now
        return true
    }

    /** When each rate-limited kind last made it onto the dash. */
    private val lastShownAtMs = mutableMapOf<DashNotificationKind, Long>()

    /** The last song announced, so the same track cannot announce itself twice running. */
    private var lastMusicLine: String? = null

    /** The admission rule on its own, so it can be exercised without a queue or a clock. */
    internal fun admitsForTest(kind: DashNotificationKind, text: String): Boolean =
        admits(DashNotification(id = "test:$kind:$text", text = text, kind = kind))

    fun emit(text: String, kind: DashNotificationKind) {
        val body = text.trim()
        if (body.isEmpty()) return
        emit(
            DashNotification(
                id = "dash:${kind.name.lowercase()}:${System.nanoTime()}",
                text = body,
                kind = kind,
            ),
        )
    }

    fun clear() {
        synchronized(queueLock) { queue.clear() }
        _active.value = null
        _cue.value = null
    }

    private companion object {
        const val DASH_GAP_MS = 220L
    }
}
