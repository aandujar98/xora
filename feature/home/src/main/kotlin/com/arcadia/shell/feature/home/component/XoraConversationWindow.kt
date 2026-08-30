package com.arcadia.shell.feature.home.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.arcadia.shell.feature.home.xoraFriendPresence
import com.arcadia.shell.xoranetwork.XoraNetworkClient
import com.arcadia.shell.xoranetwork.XoraNetworkState

private val XoraAccent = Color(0xFF0070D1)

/**
 * Dedicated XOrA Network DM window. Shares [ConversationWindow] with Discord so messaging looks
 * identical across networks; the open thread is refreshed by the HomeViewModel poll while this
 * window is on screen.
 */
@Composable
fun XoraConversationWindow(
    open: Boolean,
    network: XoraNetworkState,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dm = network.dm
    val peerUsername = dm.peerUsername.orEmpty()
    val peerName = dm.peerDisplayName.ifBlank { peerUsername }
    val friend = network.friends.firstOrNull {
        it.username.equals(peerUsername, ignoreCase = true)
    }
    val selfUsername = network.account?.username.orEmpty()

    ConversationWindow(
        open = open && dm.isOpen,
        peerName = peerName,
        peerFallbackName = "XOrA Network",
        subtitle = "XOrA Network DM · A send · B close",
        avatarModel = friend?.resolvedAvatarUrl
            ?: peerUsername.takeIf { it.isNotBlank() }
                ?.let { XoraNetworkClient.avatarUrlFor(it) },
        presence = xoraFriendPresence(friend),
        accent = XoraAccent,
        messages = dm.messages.map { message ->
            ConversationMessage(
                id = message.id,
                body = message.body,
                mine = message.fromUsername.equals(selfUsername, ignoreCase = true),
            )
        },
        draft = dm.draft,
        loading = dm.loading,
        sending = dm.sending,
        error = dm.error,
        footnote = "Messages sync with account.xoranetwork.com — " +
            "your friend can reply from the website or another launcher.",
        onDraftChange = onDraftChange,
        onSend = onSend,
        onDismiss = onDismiss,
        modifier = modifier,
    )
}
