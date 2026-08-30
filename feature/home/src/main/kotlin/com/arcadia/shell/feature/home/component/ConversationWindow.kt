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
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.arcadia.shell.designsystem.ArcadiaGlass
import com.arcadia.shell.designsystem.ArcadiaMotion
import com.arcadia.shell.designsystem.GlassIntensity
import com.arcadia.shell.designsystem.GlassTone
import com.arcadia.shell.designsystem.arcadiaTween
import com.arcadia.shell.designsystem.liquidGlass
import com.arcadia.shell.designsystem.rememberGlassTokens
import com.arcadia.shell.feature.home.SocialPresence

private val MessageErrorRose = Color(0xFFFF6B8A)

/** One rendered message, independent of whether it came from Discord or XOrA Network. */
data class ConversationMessage(
    val id: String,
    val body: String,
    val mine: Boolean,
    val mediaUrls: List<String> = emptyList(),
    val attachmentLabel: String? = null,
)

/**
 * The DM thread window shared by every network, so messaging looks the same whoever the peer is.
 * Networks differ only in [accent], the header strings, and whether they can [onAttachMedia].
 */
@Composable
fun ConversationWindow(
    open: Boolean,
    peerName: String,
    peerFallbackName: String,
    subtitle: String,
    avatarModel: String?,
    presence: SocialPresence,
    accent: Color,
    messages: List<ConversationMessage>,
    draft: String,
    loading: Boolean,
    sending: Boolean,
    error: String?,
    footnote: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onAttachMedia: (() -> Unit)? = null,
) {
    val glass = rememberGlassTokens(GlassTone.Surface)
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size, open) {
        if (open && messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    AnimatedVisibility(
        visible = open,
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
                        displayName = peerName,
                        presetId = "preset_0",
                        size = 52.dp,
                        imageModel = avatarModel,
                        presence = presence,
                        selected = false,
                        sourceTint = accent,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = peerName.ifBlank { peerFallbackName },
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = glass.content,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.labelMedium,
                            color = glass.contentMuted,
                        )
                    }
                    Text(
                        text = "Close",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onDismiss)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }

                when {
                    loading && messages.isEmpty() -> ThreadPlaceholder(
                        text = "Loading messages…",
                        color = glass.contentMuted,
                    )
                    messages.isEmpty() -> ThreadPlaceholder(
                        text = "No messages yet — say hello",
                        color = glass.contentMuted,
                    )
                    else -> {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(vertical = 4.dp),
                        ) {
                            items(messages, key = { it.id }) { message ->
                                ConversationBubble(message = message, accent = accent)
                            }
                        }
                    }
                }

                if (!error.isNullOrBlank()) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.labelSmall,
                        color = MessageErrorRose,
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    BasicTextField(
                        value = draft,
                        onValueChange = onDraftChange,
                        singleLine = false,
                        maxLines = 4,
                        enabled = !sending,
                        textStyle = TextStyle(color = Color.White, fontSize = 15.sp),
                        cursorBrush = SolidColor(accent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black.copy(alpha = 0.35f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        decorationBox = { inner ->
                            Box {
                                if (draft.isEmpty()) {
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
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (sending) "Sending…" else "A · Send",
                                style = MaterialTheme.typography.labelLarge,
                                color = accent,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .clickable(enabled = !sending, onClick = onSend)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                            )
                            if (onAttachMedia != null) {
                                Text(
                                    text = "Photo / GIF",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.White.copy(alpha = 0.1f))
                                        .clickable(onClick = onAttachMedia)
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                )
                            }
                        }
                        Text(
                            text = "B · Back",
                            style = MaterialTheme.typography.labelMedium,
                            color = glass.contentMuted,
                        )
                    }
                    Text(
                        text = footnote,
                        style = MaterialTheme.typography.labelSmall,
                        color = glass.contentMuted,
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.ThreadPlaceholder(
    text: String,
    color: Color,
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
        )
    }
}

@Composable
private fun ConversationBubble(
    message: ConversationMessage,
    accent: Color,
) {
    val mine = message.mine
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .widthIn(max = 420.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (mine) accent.copy(alpha = 0.55f)
                    else Color.White.copy(alpha = 0.12f),
                )
                .padding(horizontal = 12.dp, vertical = 9.dp),
        ) {
            message.mediaUrls.forEach { url ->
                MessageMedia(url = url)
            }
            message.attachmentLabel?.let { label ->
                AttachmentChip(label = label)
            }
            val hasExtras = message.mediaUrls.isNotEmpty() || message.attachmentLabel != null
            if (message.body.isNotEmpty() || !hasExtras) {
                Text(
                    text = message.body.ifBlank { " " },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                )
            }
        }
    }
}

/** Inline picture for a linked image or GIF; animates through the shell's animated decoder. */
@Composable
private fun MessageMedia(url: String) {
    AsyncImage(
        model = ImageRequest.Builder(LocalPlatformContext.current)
            .data(url)
            .crossfade(true)
            .build(),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .widthIn(max = 320.dp)
            .heightIn(max = 240.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.25f)),
    )
}

/** The Social SDK reports uploads without a URL, so say what arrived rather than showing nothing. */
@Composable
private fun AttachmentChip(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = Color.White.copy(alpha = 0.85f),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.Black.copy(alpha = 0.3f))
            .padding(horizontal = 9.dp, vertical = 5.dp),
    )
}
