package com.arcadia.shell.feature.home.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.arcadia.shell.feature.home.discordFriendActivity
import com.arcadia.shell.feature.home.discordFriendPresence
import com.arcadia.shell.launcher.discord.DiscordDmThreadUiState
import com.arcadia.shell.launcher.discord.DiscordFriendEntry

private val DiscordAccent = Color(0xFF5865F2)

/**
 * Dedicated Discord DM window — opened when messaging a friend so the thread is readable
 * outside the LT Social pill. Everything but the Discord specifics lives in [ConversationWindow].
 */
@Composable
fun DiscordConversationWindow(
    open: Boolean,
    thread: DiscordDmThreadUiState,
    friends: List<DiscordFriendEntry>,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttachMedia: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val friend = friends.firstOrNull { it.userId == thread.peerUserId }

    ConversationWindow(
        open = open && thread.peerUserId != null,
        peerName = thread.peerDisplayName,
        peerFallbackName = "Discord",
        statusLine = discordFriendActivity(friend) ?: "Discord",
        avatarModel = thread.peerAvatarUrl,
        presence = discordFriendPresence(friend),
        accent = DiscordAccent,
        messages = thread.messages.map { message ->
            ConversationMessage(
                id = message.messageId,
                body = message.text,
                mine = message.isMine,
                mediaUrls = message.mediaUrls,
                attachmentLabel = message.attachment?.label,
            )
        },
        draft = thread.draft,
        loading = thread.loading,
        sending = thread.sending,
        error = thread.error,
        onDraftChange = onDraftChange,
        onSend = onSend,
        onDismiss = onDismiss,
        onAttachMedia = onAttachMedia,
        modifier = modifier,
    )
}
