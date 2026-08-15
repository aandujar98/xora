package com.arcadia.shell.feature.home.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arcadia.shell.designsystem.ArcadiaGlass
import com.arcadia.shell.designsystem.ArcadiaMotion
import com.arcadia.shell.designsystem.GlassIntensity
import com.arcadia.shell.designsystem.GlassTone
import com.arcadia.shell.designsystem.arcadiaTween
import com.arcadia.shell.designsystem.liquidGlass
import com.arcadia.shell.designsystem.rememberGlassTokens
import com.arcadia.shell.feature.home.xoraFriendPresence
import com.arcadia.shell.xoranetwork.XoraDirectMessage
import com.arcadia.shell.xoranetwork.XoraNetworkClient
import com.arcadia.shell.xoranetwork.XoraNetworkState

private val XoraAccent = Color(0xFF0070D1)
private val BusyRose = Color(0xFFFF6B8A)

/**
 * Dedicated XOrA Network DM window — the same layout as the Discord conversation window, so
 * messaging feels identical across networks. A sends, B backs out, and the open thread is
 * refreshed by the HomeViewModel poll while this window is on screen.
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
    val glass = rememberGlassTokens(GlassTone.Surface)
    val dm = network.dm
    val listState = rememberLazyListState()
    LaunchedEffect(dm.messages.size, open) {
        if (open && dm.messages.isNotEmpty()) {
            listState.animateScrollToItem(dm.messages.lastIndex)
        }
    }

    AnimatedVisibility(
        visible = open && dm.isOpen,
        enter = fadeIn(arcadiaTween(ArcadiaMotion.Medium)) +
            scaleIn(arcadiaTween(ArcadiaMotion.Medium), initialScale = 0.96f),
        exit = fadeOut(arcadiaTween(ArcadiaMotion.Fast)) +
            scaleOut(arcadiaTween(ArcadiaMotion.Fast), targetScale = 0.98f),
        modifier = modifier.fillMaxSize(),
    ) {
        val peerUsername = dm.peerUsername.orEmpty()
        val peerName = dm.peerDisplayName.ifBlank { peerUsername }
        val friend = network.friends.firstOrNull {
            it.username.equals(peerUsername, ignoreCase = true)
        }
        val selfUsername = network.account?.username.orEmpty()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.58f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.78f)
                    .fillMaxHeight(0.88f)
                    .liquidGlass(
                        shape = ArcadiaGlass.PanelShape,
                        tone = GlassTone.Surface,
                        intensity = GlassIntensity.Strong,
                        shimmer = true,
                    )
                    .clickable(enabled = false, onClick = {})
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PresenceAvatar(
                        displayName = peerName,
                        presetId = "preset_0",
                        size = 52.dp,
                        imageModel = friend?.resolvedAvatarUrl
                            ?: peerUsername.takeIf { it.isNotBlank() }
                                ?.let { XoraNetworkClient.avatarUrlFor(it) },
                        presence = xoraFriendPresence(friend),
                        selected = false,
                        sourceTint = XoraAccent,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = peerName.ifBlank { "XOrA Network" },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = glass.content,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "XOrA Network DM · A send · B close",
                            style = MaterialTheme.typography.labelMedium,
                            color = glass.contentMuted,
                        )
                    }
                    Text(
                        text = "Close",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = XoraAccent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }

                when {
                    dm.loading && dm.messages.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "Loading messages…",
                                style = MaterialTheme.typography.bodyMedium,
                                color = glass.contentMuted,
                            )
                        }
                    }
                    dm.messages.isEmpty() -> {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "No messages yet — say hello",
                                style = MaterialTheme.typography.bodyMedium,
                                color = glass.contentMuted,
                            )
                        }
                    }
                    else -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 4.dp),
                        ) {
                            items(dm.messages, key = { it.id }) { message ->
                                XoraConversationBubble(
                                    message = message,
                                    mine = message.fromUsername.equals(
                                        selfUsername,
                                        ignoreCase = true,
                                    ),
                                )
                            }
                        }
                    }
                }

                val dmError = dm.error
                if (!dmError.isNullOrBlank()) {
                    Text(
                        text = dmError,
                        style = MaterialTheme.typography.labelSmall,
                        color = BusyRose,
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(1.dp, XoraAccent.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    BasicTextField(
                        value = dm.draft,
                        onValueChange = onDraftChange,
                        singleLine = false,
                        maxLines = 4,
                        enabled = !dm.sending,
                        textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                        cursorBrush = SolidColor(XoraAccent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.35f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        decorationBox = { inner ->
                            Box {
                                if (dm.draft.isEmpty()) {
                                    Text(
                                        text = "Message ${peerName.ifBlank { "friend" }}…",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White.copy(alpha = 0.45f),
                                    )
                                }
                                inner()
                            }
                        },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (dm.sending) "Sending…" else "A · Send",
                            style = MaterialTheme.typography.labelLarge,
                            color = XoraAccent,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(enabled = !dm.sending, onClick = onSend)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                        Text(
                            text = "B · Back",
                            style = MaterialTheme.typography.labelMedium,
                            color = glass.contentMuted,
                        )
                    }
                    Text(
                        text = "Messages sync with account.xoranetwork.com — " +
                            "your friend can reply from the website or another launcher.",
                        style = MaterialTheme.typography.labelSmall,
                        color = glass.contentMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun XoraConversationBubble(
    message: XoraDirectMessage,
    mine: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
            modifier = Modifier
                .widthIn(max = 420.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (mine) XoraAccent.copy(alpha = 0.55f)
                    else Color.White.copy(alpha = 0.12f),
                )
                .padding(horizontal = 12.dp, vertical = 9.dp),
        ) {
            Text(
                text = message.body.ifBlank { " " },
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White,
            )
        }
    }
}
