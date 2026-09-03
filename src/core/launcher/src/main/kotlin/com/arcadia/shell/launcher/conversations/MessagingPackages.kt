package com.arcadia.shell.launcher.conversations

/**
 * Packages and heuristics that qualify a StatusBarNotification as a conversation candidate.
 */
object MessagingPackages {
    const val STEAM_COMMUNITY = "com.valvesoftware.android.steam.community"
    const val STEAM_CHAT = "com.valvesoftware.android.steam.chat"
    const val DISCORD = "com.discord"

    /** Known messaging apps we always include even without MessagingStyle. */
    val KNOWN: Set<String> = setOf(
        STEAM_COMMUNITY,
        STEAM_CHAT,
        DISCORD,
        "com.whatsapp",
        "com.whatsapp.w4b",
        "org.telegram.messenger",
        "org.telegram.messenger.web",
        "org.thoughtcrime.securesms", // Signal
        "com.instagram.android",
        "com.facebook.orca", // Messenger
        "com.google.android.apps.messaging",
        "com.samsung.android.messaging",
        "com.android.mms",
        "com.snapchat.android",
        "com.Slack",
        "com.microsoft.teams",
        "com.valve.steamchat",
    )

    val STEAM_PACKAGES: Set<String> = setOf(
        STEAM_COMMUNITY,
        STEAM_CHAT,
        "com.valve.steamchat",
    )

    fun sourceFor(packageName: String): ConversationSource = when {
        packageName in STEAM_PACKAGES || packageName.contains("steam", ignoreCase = true) ->
            ConversationSource.Steam
        packageName == DISCORD || packageName.contains("discord", ignoreCase = true) ->
            ConversationSource.Discord
        else -> ConversationSource.Other
    }

    fun isSteamPackage(packageName: String): Boolean =
        packageName in STEAM_PACKAGES ||
            (packageName.startsWith("com.valvesoftware") && packageName.contains("steam", ignoreCase = true))

    fun isDiscordPackage(packageName: String): Boolean =
        packageName == DISCORD || packageName.contains("discord", ignoreCase = true)

    fun appLabelFor(packageName: String): String = when {
        isSteamPackage(packageName) -> "Steam"
        isDiscordPackage(packageName) -> "Discord"
        packageName.contains("whatsapp", ignoreCase = true) -> "WhatsApp"
        packageName.contains("telegram", ignoreCase = true) -> "Telegram"
        packageName.contains("signal", ignoreCase = true) ||
            packageName == "org.thoughtcrime.securesms" -> "Signal"
        packageName.contains("instagram", ignoreCase = true) -> "Instagram"
        packageName.contains("messenger", ignoreCase = true) ||
            packageName == "com.facebook.orca" -> "Messenger"
        packageName.contains("snapchat", ignoreCase = true) -> "Snapchat"
        packageName.contains("slack", ignoreCase = true) -> "Slack"
        packageName.contains("teams", ignoreCase = true) -> "Teams"
        packageName.contains("messaging", ignoreCase = true) ||
            packageName.contains("mms", ignoreCase = true) -> "Messages"
        else -> packageName.substringAfterLast('.').replaceFirstChar { it.uppercase() }
    }
}
