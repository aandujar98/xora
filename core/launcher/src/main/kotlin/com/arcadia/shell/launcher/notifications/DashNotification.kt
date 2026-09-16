package com.arcadia.shell.launcher.notifications

/**
 * A Dash Notification (Figma 974:2199): one line of white text on a thin gradient bar in the
 * bottom-left corner, for things the launcher wants to mention rather than announce.
 *
 * These are deliberately not [ShellNotification]s — a Dash line is ambient status with no history,
 * no Android post and nothing to tap. Anything a player should be able to come back to stays a
 * banner.
 */
data class DashNotification(
    val id: String,
    val text: String,
    val kind: DashNotificationKind,
)

/** What a Dash line is about — picks its icon and whether it makes a sound. */
enum class DashNotificationKind {
    /** A song started playing. The only kind that is always silent. */
    Music,
    Scraping,
    Update,
    /** Hours logged, shown on the way back from a game. */
    Playtime,
    Scanning,
    Error,
}

/**
 * How long a line stays up: the stated 3–5s, spent according to how much there is to read, so a
 * two-word line does not sit as long as a full sentence.
 */
fun dashNotificationDurationMs(text: String): Long {
    val words = text.trim().split(Regex("\\s+")).count { it.isNotBlank() }
    return (DASH_MIN_MS + words * DASH_MS_PER_WORD).coerceIn(DASH_MIN_MS, DASH_MAX_MS)
}

const val DASH_MIN_MS = 3_000L
const val DASH_MAX_MS = 5_000L
private const val DASH_MS_PER_WORD = 180L
