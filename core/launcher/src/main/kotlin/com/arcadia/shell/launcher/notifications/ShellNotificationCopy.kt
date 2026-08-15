package com.arcadia.shell.launcher.notifications

/**
 * Shared title / body text for PS banners and Android status-bar notifications.
 * Mirrors [com.arcadia.shell.feature.home.component] banner lines without Compose deps.
 */
data class ShellNotificationCopy(
    val category: String,
    val body: String,
    val subtitle: String,
)

fun ShellNotification.toCopy(): ShellNotificationCopy = when (this) {
    is ShellNotification.AchievementUnlocked -> {
        val points = points?.takeIf { it > 0 }?.let { "$it pts" }
        val hardcoreLabel = if (hardcore) "Hardcore" else null
        val subtitle = listOfNotNull(
            description?.trim()?.takeIf { it.isNotEmpty() },
            points,
            hardcoreLabel,
            gameTitle?.trim()?.takeIf { it.isNotEmpty() },
        ).joinToString(" · ").ifBlank { "Achievement unlocked" }
        ShellNotificationCopy(
            category = "Trophy",
            body = title,
            subtitle = subtitle,
        )
    }

    is ShellNotification.RetroAchievementsSignedIn -> {
        val mode = if (hardcore) "Hardcore" else "Softcore"
        val game = gameTitle?.trim()?.takeIf { it.isNotEmpty() }
        ShellNotificationCopy(
            category = "RetroAchievements",
            body = "Logged in as $username",
            subtitle = listOfNotNull(mode, game).joinToString(" · "),
        )
    }

    is ShellNotification.DiscordMessage -> ShellNotificationCopy(
        category = "Messages",
        body = snippet.ifBlank { "New Discord message" },
        subtitle = sender,
    )

    is ShellNotification.SteamMessage -> ShellNotificationCopy(
        category = "Messages",
        body = snippet.ifBlank { "New Steam message" },
        subtitle = sender,
    )

    is ShellNotification.XoraMessage -> ShellNotificationCopy(
        category = "Messages",
        body = snippet.ifBlank { "New XOrA Network message" },
        subtitle = "$sender · XOrA Network",
    )

    is ShellNotification.XoraFriendRequest -> ShellNotificationCopy(
        category = "Friends",
        body = "Added you as a friend",
        subtitle = "$displayName · XOrA Network",
    )

    is ShellNotification.XoraNetplayInvite -> ShellNotificationCopy(
        category = "Netplay",
        body = "Invited you to play ${gameTitle.ifBlank { "a game" }}",
        subtitle = "$displayName · XOrA Network",
    )

    is ShellNotification.XoraSessionJoined -> ShellNotificationCopy(
        category = "Netplay",
        body = detail.ifBlank { "$displayName joined the session" },
        subtitle = "XOrA Network",
    )

    is ShellNotification.FriendOnline -> {
        val networkLabel = when (network) {
            FriendNetwork.Discord -> "Discord"
            FriendNetwork.Steam -> "Steam"
            FriendNetwork.Xora -> "XOrA Network"
        }
        val activity = activityLabel?.trim().orEmpty()
        ShellNotificationCopy(
            category = "Friends",
            body = activity.ifEmpty { "Online" },
            subtitle = "$displayName · $networkLabel",
        )
    }

    is ShellNotification.GameDownloading -> ShellNotificationCopy(
        category = "Download",
        body = title,
        subtitle = progressLabel ?: "Downloading…",
    )

    is ShellNotification.InstallComplete -> ShellNotificationCopy(
        category = "Download",
        body = title,
        subtitle = this.subtitle ?: "Install complete",
    )
}
