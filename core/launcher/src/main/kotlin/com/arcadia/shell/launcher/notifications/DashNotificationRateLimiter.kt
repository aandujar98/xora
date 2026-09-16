package com.arcadia.shell.launcher.notifications

/**
 * Keeps the dash from chattering. A kind that has just spoken stays quiet for [cooldownMs],
 * because a second "Fetching artwork" a moment after the first tells the player nothing they do
 * not already know.
 *
 * Two kinds are exempt, because each of their alerts carries news the last one did not:
 * [DashNotificationKind.Playtime] is only ever posted once on the way back from a game, and a
 * song change is the whole point of the music line. The song still has to actually differ — a
 * track repeating itself, or a pause and resume of the same one, is not news.
 *
 * Separate from [DashNotificationCenter] so the rule can be exercised without a queue, and with
 * a clock the test controls.
 */
class DashNotificationRateLimiter(
    private val cooldownMs: Long = DASH_COOLDOWN_MS,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val lastShownAtMs = mutableMapOf<DashNotificationKind, Long>()
    private var lastMusicLine: String? = null

    @Synchronized
    fun admits(kind: DashNotificationKind, text: String): Boolean {
        if (kind == DashNotificationKind.Music) {
            if (text.equals(lastMusicLine, ignoreCase = true)) return false
            lastMusicLine = text
            return true
        }
        if (kind == DashNotificationKind.Playtime) return true
        val at = now()
        val last = lastShownAtMs[kind]
        if (last != null && at - last < cooldownMs) return false
        lastShownAtMs[kind] = at
        return true
    }
}

/** One alert of a kind per minute; see [DashNotificationRateLimiter] for the two exemptions. */
const val DASH_COOLDOWN_MS = 60_000L
