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
     * A friend wrote a new custom status. [status] is their own wording, carried into the banner
     * so the update reads as what they said rather than "changed their status".
     */
    data class FriendStatusUpdated(
        override val id: String,
        val displayName: String,
        val status: String,
        val network: FriendNetwork,
        val avatarUrl: String? = null,
    ) : ShellNotification

    /**
     * A friend put a song on. The banner reads "<name> is now listening to" over
     * "<song> by <artist>", so the track is the line that carries the weight.
     */
    data class FriendListening(
        override val id: String,
        val displayName: String,
        val songTitle: String,
        val artist: String,
        val network: FriendNetwork,
        val avatarUrl: String? = null,
    ) : ShellNotification

    /**
     * A friend on XOrA Network unlocked something. Deliberately separate from
     * [AchievementUnlocked], which is your own trophy: this one leads with who earned it, and it
     * only ever carries XOrA Network friends — the point is that it is people you actually know
     * here, not every RetroAchievements account being followed.
     */
    data class FriendAchievementUnlocked(
        override val id: String,
        val displayName: String,
        val title: String,
        val gameTitle: String? = null,
        val points: Int? = null,
        val badgeUrl: String? = null,
        val avatarUrl: String? = null,
        val hardcore: Boolean = false,
    ) : ShellNotification

    /** A friend on Steam, Discord, or XOrA Network started (or switched) a game. */
    data class FriendPlaying(
        override val id: String,
        val displayName: String,
        val gameTitle: String,
        val network: FriendNetwork,
        val avatarUrl: String? = null,
        /**
         * The game's bubble icon from this device's library, when the title is one we hold. The
         * banner leads with the game rather than the friend's picture — what they are playing is
         * the news, and their name is already in the line.
         */
        val gameIconPath: String? = null,
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
        is ShellNotification.FriendOnline -> {
            val name = self.displayName.trim().lowercase()
            if (name.isNotBlank()) {
                add("friend-online:${self.network.name.lowercase()}:$name")
            }
        }
        is ShellNotification.FriendListening -> {
            val name = self.displayName.trim().lowercase()
            val song = self.songTitle.trim().lowercase()
            if (name.isNotBlank() && song.isNotBlank()) {
                add("friend-listening:${self.network.name.lowercase()}:$name:$song")
            }
        }
        is ShellNotification.FriendStatusUpdated -> {
            val name = self.displayName.trim().lowercase()
            val status = self.status.trim().lowercase()
            if (name.isNotBlank() && status.isNotBlank()) {
                add("friend-status:${self.network.name.lowercase()}:$name:$status")
            }
        }
        is ShellNotification.FriendPlaying -> {
            val name = self.displayName.trim().lowercase()
            val game = self.gameTitle.trim().lowercase()
            if (name.isNotBlank() && game.isNotBlank()) {
                add("friend-playing:${self.network.name.lowercase()}:$name:$game")
            }
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
