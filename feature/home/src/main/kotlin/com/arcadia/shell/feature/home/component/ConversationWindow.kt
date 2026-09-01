package com.arcadia.shell.feature.home.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.arcadia.shell.designsystem.ArcadiaMotion
import com.arcadia.shell.designsystem.GlassIntensity
import com.arcadia.shell.designsystem.GlassTone
import com.arcadia.shell.designsystem.XoraForegroundShadow
import com.arcadia.shell.designsystem.arcadiaTween
import com.arcadia.shell.designsystem.liquidGlass
import com.arcadia.shell.designsystem.supportsGlassBlurEffect
import com.arcadia.shell.designsystem.xmbAssetShadow
import com.arcadia.shell.designsystem.xoraSecondaryTextStyle
import com.arcadia.shell.feature.home.R
import com.arcadia.shell.feature.home.SocialPresence
import kotlin.math.min

// Every measurement below is in Figma artboard units and is scaled by `unit` at layout time,
// so the window keeps the designed proportions on any panel.
private const val DESIGN_WIDTH = 717f
private const val DESIGN_HEIGHT = 830f

/** Leaves a margin so the modal never touches the panel edges. */
private const val MODAL_FIT = 0.94f

private const val MODAL_RADIUS = 50f
private const val MODAL_BORDER = 2f
private const val MODAL_GLOW_SPREAD = 8f
private const val MODAL_GLOW_BLUR = 20f

private const val HEADER_TOP = 34f
private const val AVATAR_SIZE = 130f
private const val AVATAR_LEFT = 47f
private const val AVATAR_BORDER = 3f
private const val PRESENCE_DOT = 23f
private const val PRESENCE_DOT_RIM = 3f
private const val HEADER_GAP = 21f
private const val NAME_TEXT = 50f
private const val STATUS_TEXT = 20f

private const val SIDE_MARGIN = 43f
private const val HEADER_TO_LIST = 11f
private const val MESSAGE_SPACING = 26f
private const val LIST_TO_COMPOSER = 58f
private const val PEER_AVATAR = 65f
private const val PEER_AVATAR_GAP = 20f
private const val BUBBLE_RADIUS = 20f
private const val BUBBLE_MAX_WIDTH = 234f
private const val BUBBLE_PAD_H = 19f
private const val BUBBLE_PAD_V = 18f
private const val MESSAGE_TEXT = 18f
private const val SEEN_TEXT = 14f
private const val SEEN_GAP = 5f
private const val MEDIA_MAX = 195f

private const val COMPOSER_HEIGHT = 59f
private const val COMPOSER_PAD_H = 20f
private const val COMPOSER_ICON_GAP = 29f
private const val STICKER_WIDTH = 30f
private const val STICKER_HEIGHT = 29f
private const val SEND_WIDTH = 28f
private const val SEND_HEIGHT = 24f
private const val BOTTOM_MARGIN = 66f

private val ScrimInk = Color.Black.copy(alpha = 0.58f)
private val ModalInk = Color.Black.copy(alpha = 0.65f)
private val ModalBorder = Color.White.copy(alpha = 0.25f)

/** Bubbles and the composer share one translucent white plate in the design. */
private val PlateWhite = Color.White.copy(alpha = 0.40f)
private val StatusGreen = Color(0xFF00FF00)
private val PlaceholderWhite = Color.White.copy(alpha = 0.30f)
private val SeenWhite = Color.White.copy(alpha = 0.20f)
private val MessageErrorRose = Color(0xFFFF6B8A)

// Purple bloom over the top-left, teal bloom centred past the bottom-right corner.
private val BloomPurple = Color(0xFF674FDD)
private val BloomTealNear = Color(0xFF6EECFF)
private val BloomTealMid = Color(0xFF75B7B5)
private val BloomTealFar = Color(0xFF53A6A0)

private val DotRimTop = Color(0xFFFEFEFE)
private val DotRimBottom = Color(0xFF9C9C9D)

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
 * Networks differ only in [accent] (the caret), the header strings, and whether they can
 * [onAttachMedia].
 *
 * Blurred glass modal over a dimmed shell: the peer's avatar and name head it, their current
 * activity reads underneath in green, the thread sits bottom-anchored above a pill composer.
 */
@Composable
fun ConversationWindow(
    open: Boolean,
    peerName: String,
    peerFallbackName: String,
    statusLine: String,
    avatarModel: String?,
    presence: SocialPresence,
    accent: Color,
    messages: List<ConversationMessage>,
    draft: String,
    loading: Boolean,
    sending: Boolean,
    error: String?,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    /** Read receipt under the last outgoing bubble; no network reports one yet. */
    seenLabel: String? = null,
    onAttachMedia: (() -> Unit)? = null,
) {
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
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(ScrimInk)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
            contentAlignment = Alignment.Center,
        ) {
            val unit = min(
                maxWidth.value / DESIGN_WIDTH,
                maxHeight.value / DESIGN_HEIGHT,
            ) * MODAL_FIT
            val shape = RoundedCornerShape((MODAL_RADIUS * unit).dp)

            Box(
                modifier = Modifier
                    .size(
                        width = (DESIGN_WIDTH * unit).dp,
                        height = (DESIGN_HEIGHT * unit).dp,
                    )
                    .xmbAssetShadow(unit = unit, shape = shape, alpha = XoraForegroundShadow.Alpha)
                    .liquidGlass(
                        shape = shape,
                        tone = GlassTone.OverMedia,
                        intensity = GlassIntensity.Subtle,
                        blurRadius = 5.dp,
                    )
                    .drawBehind { drawModalBlooms() }
                    // Swallows the tap so clicking inside the window never dismisses it.
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
            ) {
                ModalInnerGlow(unit = unit, shape = shape)

                Column(modifier = Modifier.fillMaxSize()) {
                    Spacer(modifier = Modifier.height((HEADER_TOP * unit).dp))
                    ConversationHeader(
                        peerName = peerName.ifBlank { peerFallbackName },
                        statusLine = statusLine,
                        avatarModel = avatarModel,
                        presence = presence,
                        unit = unit,
                    )
                    Spacer(modifier = Modifier.height((HEADER_TO_LIST * unit).dp))

                    when {
                        loading && messages.isEmpty() -> ThreadPlaceholder(
                            text = "Loading messages…",
                            unit = unit,
                        )
                        messages.isEmpty() -> ThreadPlaceholder(
                            text = "No messages yet — say hello",
                            unit = unit,
                        )
                        else -> LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .padding(horizontal = (SIDE_MARGIN * unit).dp),
                            verticalArrangement = Arrangement.spacedBy(
                                space = (MESSAGE_SPACING * unit).dp,
                                alignment = Alignment.Bottom,
                            ),
                        ) {
                            items(messages, key = { it.id }) { message ->
                                ConversationRow(
                                    message = message,
                                    avatarModel = avatarModel,
                                    peerName = peerName.ifBlank { peerFallbackName },
                                    seenLabel = seenLabel?.takeIf {
                                        message.mine && message.id == messages.last().id
                                    },
                                    unit = unit,
                                )
                            }
                        }
                    }

                    if (!error.isNullOrBlank()) {
                        Text(
                            text = error,
                            style = xoraSecondaryTextStyle(
                                weight = FontWeight.Bold,
                                fontSize = (SEEN_TEXT * unit).sp,
                                lineHeight = (SEEN_TEXT * 1.4f * unit).sp,
                            ),
                            color = MessageErrorRose,
                            modifier = Modifier.padding(
                                horizontal = (SIDE_MARGIN * unit).dp,
                                vertical = (SEEN_GAP * unit).dp,
                            ),
                        )
                    }

                    Spacer(modifier = Modifier.height((LIST_TO_COMPOSER * unit).dp))
                    MessageComposer(
                        draft = draft,
                        peerName = peerName.ifBlank { peerFallbackName },
                        accent = accent,
                        sending = sending,
                        unit = unit,
                        onDraftChange = onDraftChange,
                        onSend = onSend,
                        onAttachMedia = onAttachMedia,
                    )
                    Spacer(modifier = Modifier.height((BOTTOM_MARGIN * unit).dp))
                }
            }
        }
    }
}

/** Base ink plus the two radial blooms the design layers over it. */
private fun DrawScope.drawModalBlooms() {
    drawRect(ModalInk)
    drawRect(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0f to BloomPurple.copy(alpha = 0.40f),
                0.5f to BloomPurple.copy(alpha = 0.20f),
                1f to BloomPurple.copy(alpha = 0.10f),
            ),
            center = Offset(size.width * 0.30f, size.height * 0.34f),
            radius = size.maxDimension * 0.85f,
        ),
        alpha = 0.5f,
    )
    drawRect(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0f to BloomTealNear.copy(alpha = 0.20f),
                0.5f to BloomTealMid.copy(alpha = 0.60f),
                1f to BloomTealFar.copy(alpha = 0f),
            ),
            center = Offset(size.width * 1.17f, size.height * 1.34f),
            radius = size.maxDimension * 1.2f,
        ),
        alpha = 0.6f,
    )
}

/**
 * The design's inset white shadow. Compose has no inset shadow, so a thick white ring is blurred
 * and clipped back to the modal shape, which leaves the same bloom hugging the inside edge.
 *
 * [Modifier.blur] is a no-op below API 31, where an unblurred ring would read as a hard white
 * band — those devices get a faint rim instead.
 */
@Composable
private fun BoxScope.ModalInnerGlow(
    unit: Float,
    shape: RoundedCornerShape,
) {
    if (supportsGlassBlurEffect()) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .blur(
                    radius = (MODAL_GLOW_BLUR * unit).dp,
                    edgeTreatment = BlurredEdgeTreatment(shape),
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border(
                        width = (MODAL_GLOW_SPREAD * unit).dp,
                        color = Color.White.copy(alpha = 0.55f),
                        shape = shape,
                    ),
            )
        }
    } else {
        Box(
            modifier = Modifier
                .matchParentSize()
                .border((MODAL_GLOW_SPREAD * 0.5f * unit).dp, Color.White.copy(alpha = 0.10f), shape),
        )
    }
    Box(
        modifier = Modifier
            .matchParentSize()
            .border((MODAL_BORDER * unit).dp, ModalBorder, shape),
    )
}

@Composable
private fun ConversationHeader(
    peerName: String,
    statusLine: String,
    avatarModel: String?,
    presence: SocialPresence,
    unit: Float,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (AVATAR_LEFT * unit).dp, end = (SIDE_MARGIN * unit).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.size((AVATAR_SIZE * unit).dp)) {
            ProfileAvatar(
                displayName = peerName,
                presetId = "preset_0",
                size = (AVATAR_SIZE * unit).dp,
                imageModel = avatarModel,
                borderColor = Color.Transparent,
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .border((AVATAR_BORDER * unit).dp, ModalBorder, CircleShape),
            )
            PresenceGlowDot(
                presence = presence,
                unit = unit,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }
        Spacer(modifier = Modifier.width((HEADER_GAP * unit).dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = peerName,
                style = xoraSecondaryTextStyle(
                    weight = FontWeight.Bold,
                    fontSize = (NAME_TEXT * unit).sp,
                    lineHeight = (NAME_TEXT * 1.3f * unit).sp,
                ),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (statusLine.isNotBlank()) {
                Text(
                    text = statusLine,
                    style = xoraSecondaryTextStyle(
                        weight = FontWeight.Bold,
                        fontSize = (STATUS_TEXT * unit).sp,
                        lineHeight = (STATUS_TEXT * 1.3f * unit).sp,
                    ),
                    color = StatusGreen,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Gradient-filled presence bead with the design's bright rim. */
@Composable
private fun PresenceGlowDot(
    presence: SocialPresence,
    unit: Float,
    modifier: Modifier = Modifier,
) {
    val (top, bottom) = presenceDotGradient(presence)
    Box(
        modifier = modifier
            .size((PRESENCE_DOT * unit).dp)
            .drawBehind {
                val rim = (PRESENCE_DOT_RIM * unit).dp.toPx()
                val radius = (size.minDimension - rim) / 2f
                drawCircle(
                    brush = Brush.verticalGradient(listOf(top, bottom)),
                    radius = radius,
                )
                drawCircle(
                    brush = Brush.verticalGradient(listOf(DotRimTop, DotRimBottom)),
                    radius = radius,
                    style = Stroke(width = rim),
                )
            },
    )
}

private fun presenceDotGradient(presence: SocialPresence): Pair<Color, Color> = when (presence) {
    SocialPresence.Online, SocialPresence.InGame ->
        Color(0xFF9BF772) to Color(0xFF38B640)
    SocialPresence.Away -> Color(0xFFFFD98A) to Color(0xFFE0A21F)
    SocialPresence.Busy -> Color(0xFFFF9AA8) to Color(0xFFD1394E)
    SocialPresence.Offline -> Color(0xFFB9BDC4) to Color(0xFF6E747C)
}

@Composable
private fun ColumnScope.ThreadPlaceholder(
    text: String,
    unit: Float,
) {
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = xoraSecondaryTextStyle(
                fontSize = (MESSAGE_TEXT * unit).sp,
                lineHeight = (MESSAGE_TEXT * 1.4f * unit).sp,
            ),
            color = PlaceholderWhite,
        )
    }
}

/**
 * Incoming messages lead with the peer's avatar bottom-aligned to the bubble; outgoing ones sit
 * flush right with no avatar and carry the read receipt.
 */
@Composable
private fun ConversationRow(
    message: ConversationMessage,
    avatarModel: String?,
    peerName: String,
    seenLabel: String?,
    unit: Float,
) {
    if (message.mine) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.End,
        ) {
            MessageBubble(message = message, unit = unit)
            if (seenLabel != null) {
                Spacer(modifier = Modifier.height((SEEN_GAP * unit).dp))
                Text(
                    text = seenLabel,
                    style = xoraSecondaryTextStyle(
                        fontSize = (SEEN_TEXT * unit).sp,
                        lineHeight = (SEEN_TEXT * 1.3f * unit).sp,
                    ),
                    color = SeenWhite,
                )
            }
        }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            ProfileAvatar(
                displayName = peerName,
                presetId = "preset_0",
                size = (PEER_AVATAR * unit).dp,
                imageModel = avatarModel,
                borderColor = ModalBorder,
            )
            Spacer(modifier = Modifier.width((PEER_AVATAR_GAP * unit).dp))
            MessageBubble(message = message, unit = unit)
        }
    }
}

@Composable
private fun MessageBubble(
    message: ConversationMessage,
    unit: Float,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy((SEEN_GAP * unit).dp),
        modifier = Modifier
            .widthIn(max = (BUBBLE_MAX_WIDTH * unit).dp)
            .clip(RoundedCornerShape((BUBBLE_RADIUS * unit).dp))
            .background(PlateWhite)
            .padding(
                horizontal = (BUBBLE_PAD_H * unit).dp,
                vertical = (BUBBLE_PAD_V * unit).dp,
            ),
    ) {
        message.mediaUrls.forEach { url ->
            MessageMedia(url = url, unit = unit)
        }
        message.attachmentLabel?.let { label ->
            AttachmentChip(label = label, unit = unit)
        }
        val hasExtras = message.mediaUrls.isNotEmpty() || message.attachmentLabel != null
        if (message.body.isNotEmpty() || !hasExtras) {
            Text(
                text = message.body.ifBlank { " " },
                style = xoraSecondaryTextStyle(
                    weight = FontWeight.Bold,
                    fontSize = (MESSAGE_TEXT * unit).sp,
                    lineHeight = (MESSAGE_TEXT * 1.28f * unit).sp,
                ),
                color = Color.White,
            )
        }
    }
}

/** Inline picture for a linked image or GIF; animates through the shell's animated decoder. */
@Composable
private fun MessageMedia(url: String, unit: Float) {
    AsyncImage(
        model = ImageRequest.Builder(LocalPlatformContext.current)
            .data(url)
            .crossfade(true)
            .build(),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .widthIn(max = (MEDIA_MAX * unit).dp)
            .heightIn(max = (MEDIA_MAX * unit).dp)
            .clip(RoundedCornerShape((BUBBLE_RADIUS * 0.6f * unit).dp))
            .background(Color.Black.copy(alpha = 0.25f)),
    )
}

/** The Social SDK reports uploads without a URL, so say what arrived rather than showing nothing. */
@Composable
private fun AttachmentChip(label: String, unit: Float) {
    Text(
        text = label,
        style = xoraSecondaryTextStyle(
            fontSize = (SEEN_TEXT * unit).sp,
            lineHeight = (SEEN_TEXT * 1.4f * unit).sp,
        ),
        color = Color.White.copy(alpha = 0.85f),
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape((SEEN_GAP * unit).dp))
            .background(Color.Black.copy(alpha = 0.3f))
            .padding(
                horizontal = (SEEN_GAP * 1.8f * unit).dp,
                vertical = (SEEN_GAP * unit).dp,
            ),
    )
}

/** Single-line pill: draft on the left, sticker and send glyphs pinned right. */
@Composable
private fun MessageComposer(
    draft: String,
    peerName: String,
    accent: Color,
    sending: Boolean,
    unit: Float,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttachMedia: (() -> Unit)?,
) {
    Row(
        modifier = Modifier
            .padding(horizontal = (SIDE_MARGIN * unit).dp)
            .fillMaxWidth()
            .height((COMPOSER_HEIGHT * unit).dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(PlateWhite)
            .padding(horizontal = (COMPOSER_PAD_H * unit).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val messageStyle = xoraSecondaryTextStyle(
            weight = FontWeight.Bold,
            fontSize = (MESSAGE_TEXT * unit).sp,
            lineHeight = (MESSAGE_TEXT * 1.28f * unit).sp,
        )
        BasicTextField(
            value = draft,
            onValueChange = onDraftChange,
            singleLine = true,
            enabled = !sending,
            textStyle = messageStyle.copy(color = Color.White),
            cursorBrush = SolidColor(accent),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (draft.isEmpty()) {
                        Text(
                            text = "Favorite Game You’ve been Playing...?",
                            style = messageStyle,
                            color = PlaceholderWhite,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    inner()
                }
            },
        )

        if (onAttachMedia != null) {
            Spacer(modifier = Modifier.width((COMPOSER_ICON_GAP * unit).dp))
            ComposerGlyph(
                iconRes = R.drawable.ic_chat_sticker,
                width = (STICKER_WIDTH * unit).dp,
                height = (STICKER_HEIGHT * unit).dp,
                onClick = onAttachMedia,
            )
        }
        Spacer(modifier = Modifier.width((COMPOSER_ICON_GAP * unit).dp))
        ComposerGlyph(
            iconRes = R.drawable.ic_chat_send,
            width = (SEND_WIDTH * unit).dp,
            height = (SEND_HEIGHT * unit).dp,
            alpha = if (sending) 0.4f else 1f,
            onClick = { if (!sending) onSend() },
        )
    }
}

@Composable
private fun ComposerGlyph(
    iconRes: Int,
    width: Dp,
    height: Dp,
    onClick: () -> Unit,
    alpha: Float = 1f,
) {
    Image(
        painter = painterResource(iconRes),
        contentDescription = null,
        alpha = alpha,
        modifier = Modifier
            .size(width = width, height = height)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    )
}
