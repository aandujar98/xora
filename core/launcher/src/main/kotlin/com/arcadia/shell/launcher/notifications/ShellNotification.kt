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
     * A friend invited this account to a Netplay session. Tapping the banner or opening
     * Notifications shows Accept / Decline — it does not auto-join.
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
        /** Full line like "angel joined pal's session"; blank falls back to a generic line. */
        val detail: String = "",
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

    /**
     * A newer XOrA build is published on GitHub Releases. Activating the banner (or the history
     * row) opens the System Update window so the user can download and install it.
     */
    data class UpdateAvailable(
        override val id: String,
        val versionName: String,
    ) : ShellNotification
}

/**
 * Keys recorded when the user clears a banner. Exact [ShellNotification.id] plus a stable
 * XOrA Network alias so the same inbox/invite item cannot toast again after an app update.
 */
fun ShellNotification.dismissalKeys(): Set<String> = buildSet {
    val self = this@dismissalKeys
    if (self.id.isNotBlank()) add(self.id.trim())
    when (self) {
        is ShellNotification.XoraNetplayInvite -> {
            netplaySessionDismissalKey(self.fromUsername.ifBlank { self.displayName }, self.sessionCode)
                ?.let { add(it) }
        }
        is ShellNotification.XoraFriendRequest -> {
            val name = self.displayName.trim().lowercase()
            if (name.isNotBlank()) add("xora-request:$name")
        }
        is ShellNotification.XoraMessage -> {
            if (self.id.isNotBlank()) add(self.id.trim())
        }
        else -> Unit
    }
}

fun netplaySessionDismissalKey(fromUsername: String, sessionCode: String): String? {
    val from = fromUsername.trim().lowercase()
    val code = sessionCode.trim()
    if (from.isBlank() || code.isBlank()) return null
    return "xora-netplay-session:$from|$code"
}

enum class FriendNetwork {
    Discord,
    Steam,
    Xora,
}
