package com.arcadia.shell.launcher.notifications

/**
 * Transient PS4/PS5-style shell banners (not a notification center).
 *
 * Emitted via [ShellNotificationCenter]; UI hosts show one at a time from a queue.
 */
sealed interface ShellNotification {
    /** Stable id for dedupe / Compose keys. */
    val id: String

    data class AchievementUnlocked(
        override val id: String,
        val title: String,
        val description: String?,
        val points: Int?,
        val badgeUrl: String?,
        val gameTitle: String? = null,
        val hardcore: Boolean = false,
    ) : ShellNotification

    data class DiscordMessage(
        override val id: String,
        val sender: String,
        val snippet: String,
        val avatarUrl: String? = null,
    ) : ShellNotification

    data class SteamMessage(
        override val id: String,
        val sender: String,
        val snippet: String,
        val avatarUrl: String? = null,
    ) : ShellNotification

    data class FriendOnline(
        override val id: String,
        val displayName: String,
        val network: FriendNetwork,
        val avatarUrl: String? = null,
        val activityLabel: String? = null,
    ) : ShellNotification

    /**
     * Progress-style banner. There is no ROM/APK download pipeline yet; library scan
     * and similar long jobs reuse this shape.
     */
    data class GameDownloading(
        override val id: String,
        val title: String,
        val progressLabel: String? = null,
        val progressFraction: Float? = null,
    ) : ShellNotification

    data class InstallComplete(
        override val id: String,
        val title: String,
        val subtitle: String? = null,
    ) : ShellNotification
}

enum class FriendNetwork {
    Discord,
    Steam,
}
