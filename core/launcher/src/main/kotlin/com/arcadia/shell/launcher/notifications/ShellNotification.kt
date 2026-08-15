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

    /** Shown when XOrA Emulator successfully authenticates with RetroAchievements. */
    data class RetroAchievementsSignedIn(
        override val id: String,
        val username: String,
        val hardcore: Boolean = false,
        val gameTitle: String? = null,
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

    /** A website / in-network DM from the XOrA Network inbox. */
    data class XoraMessage(
        override val id: String,
        val sender: String,
        val snippet: String,
        val avatarUrl: String? = null,
    ) : ShellNotification

    /** Someone sent an XOrA Network friend request to this account. */
    data class XoraFriendRequest(
        override val id: String,
        val displayName: String,
        val avatarUrl: String? = null,
    ) : ShellNotification

    /**
     * A friend invited this account to a Netplay session. Tapping the banner or pressing Back
     * opens a Join / Not now window — it does not auto-join.
     */
    data class XoraNetplayInvite(
        override val id: String,
        val displayName: String,
        val gameTitle: String,
        val avatarUrl: String? = null,
        val sessionCode: String = "",
        val platformId: String = "",
        val coreName: String = "",
        val fromUsername: String = "",
    ) : ShellNotification

    /** Both host and joiner see this when the netplay lobby links. */
    data class XoraSessionJoined(
        override val id: String,
        val displayName: String,
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
    Xora,
}
