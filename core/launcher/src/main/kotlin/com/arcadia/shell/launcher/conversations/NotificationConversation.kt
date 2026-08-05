package com.arcadia.shell.launcher.conversations

/**
 * A messaging-ish notification surfaced as a Cocoon-style conversation row.
 *
 * Reply actions ([PendingIntent] + [android.app.RemoteInput]) live only in
 * [ConversationRepository] memory — they cannot be persisted across process death.
 */
data class NotificationConversation(
    val key: String,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val postedAtEpochMs: Long,
    val canReply: Boolean,
    val source: ConversationSource,
    /** Best-effort SteamID64 when [source] is Steam and the title matched a known friend. */
    val steamIdHint: String? = null,
)

enum class ConversationSource {
    Steam,
    Discord,
    Other,
}

data class ConversationsUiState(
    val listenerEnabled: Boolean = false,
    val listenerConnected: Boolean = false,
    val conversations: List<NotificationConversation> = emptyList(),
) {
    val steamConversations: List<NotificationConversation>
        get() = conversations.filter { it.source == ConversationSource.Steam }

    val discordConversations: List<NotificationConversation>
        get() = conversations.filter { it.source == ConversationSource.Discord }

    val otherConversations: List<NotificationConversation>
        get() = conversations.filter { it.source == ConversationSource.Other }
}
