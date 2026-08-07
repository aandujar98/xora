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
import com.arcadia.shell.feature.home.SocialPresence
import com.arcadia.shell.launcher.discord.DiscordDmMessage
import com.arcadia.shell.launcher.discord.DiscordDmThreadUiState

private val DiscordAccent = Color(0xFF5865F2)
private val BusyRose = Color(0xFFFF6B8A)

/**
 * Dedicated Discord DM window — opened when messaging a friend so the thread is readable
 * outside the LT Social pill.
 */
@Composable
fun DiscordConversationWindow(
    open: Boolean,
    thread: DiscordDmThreadUiState,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = rememberGlassTokens(GlassTone.Surface)
    val listState = rememberLazyListState()
    LaunchedEffect(thread.messages.size, open) {
        if (open && thread.messages.isNotEmpty()) {
            listState.animateScrollToItem(thread.messages.lastIndex)
        }
    }

    AnimatedVisibility(
        visible = open && thread.peerUserId != null,
        enter = fadeIn(arcadiaTween(ArcadiaMotion.Medium)) +
            scaleIn(arcadiaTween(ArcadiaMotion.Medium), initialScale = 0.96f),
        exit = fadeOut(arcadiaTween(ArcadiaMotion.Fast)) +
            scaleOut(arcadiaTween(ArcadiaMotion.Fast), targetScale = 0.98f),
        modifier = modifier.fillMaxSize(),
    ) {
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
                        displayName = thread.peerDisplayName,
                        presetId = "preset_0",
                        size = 52.dp,
                        imageModel = thread.peerAvatarUrl,
                        presence = SocialPresence.Online,
                        selected = false,
                        sourceTint = DiscordAccent,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = thread.peerDisplayName.ifBlank { "Discord" },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = glass.content,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "Discord DM · A send · B close",
                            style = MaterialTheme.typography.labelMedium,
                            color = glass.contentMuted,
                        )
                    }
                    Text(
                        text = "Close",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = DiscordAccent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }

                when {
                    thread.loading && thread.messages.isEmpty() -> {
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
                    thread.messages.isEmpty() -> {
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
                            items(thread.messages, key = { it.messageId }) { message ->
                                ConversationBubble(message = message)
                            }
                        }
                    }
                }

                val threadError = thread.error
                if (!threadError.isNullOrBlank()) {
                    Text(
                        text = threadError,
                        style = MaterialTheme.typography.labelSmall,
                        color = BusyRose,
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(1.dp, DiscordAccent.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    BasicTextField(
                        value = thread.draft,
                        onValueChange = onDraftChange,
                        singleLine = false,
                        maxLines = 4,
                        enabled = !thread.sending,
                        textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                        cursorBrush = SolidColor(DiscordAccent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.35f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        decorationBox = { inner ->
                            Box {
                                if (thread.draft.isEmpty()) {
                                    Text(
                                        text = "Message ${thread.peerDisplayName.ifBlank { "friend" }}…",
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
                            text = if (thread.sending) "Sending…" else "A · Send",
                            style = MaterialTheme.typography.labelLarge,
                            color = DiscordAccent,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(enabled = !thread.sending, onClick = onSend)
                                .padding(horizontal = 10.dp, vertical = 6.dp),
                        )
                        Text(
                            text = "B · Back",
                            style = MaterialTheme.typography.labelMedium,
                            color = glass.contentMuted,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ConversationBubble(message: DiscordDmMessage) {
    val mine = message.isMine
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        Text(
            text = message.content.ifBlank { " " },
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            modifier = Modifier
                .widthIn(max = 420.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (mine) DiscordAccent.copy(alpha = 0.55f)
                    else Color.White.copy(alpha = 0.12f),
                )
                .padding(horizontal = 12.dp, vertical = 9.dp),
        )
    }
}
