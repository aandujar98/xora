package com.arcadia.shell.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arcadia.shell.designsystem.XoraFonts
import com.arcadia.shell.designsystem.rememberReduceMotion
import com.arcadia.shell.feature.home.component.ArtworkImage
import com.arcadia.shell.feature.home.component.THUMB_DECODE_MAX_EDGE_PX
import com.arcadia.shell.feature.home.component.xmb.drawableResForPlatformId
import com.arcadia.shell.retroachievements.RaGameProgress
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sign

// Figma artboard units (1920x1080); scaled by `unit` so the pane holds its proportions anywhere.
private const val CARD_WIDTH = 280f
private const val CARD_HEIGHT = 150f
private const val CARD_WIDTH_FOCUS = 462f
private const val CARD_HEIGHT_FOCUS = 248f
private const val CARD_CENTER_X = 407f
private const val CARD_PITCH = 166f
private const val CARD_FOCUS_PITCH = 240f
private const val CARD_RADIUS = 30f
private const val CARD_BORDER = 4f
private const val ROW_CENTER_Y = 540f
private const val TITLE_X = 683f
private const val TITLE_CENTER_Y = 499f
private const val TITLE_SIZE = 48f
private const val SUBTITLE_CENTER_Y = 583f
private const val SUBTITLE_SIZE = 40f
private const val RULE_X = 687f
private const val RULE_WIDTH = 1187.5f
private const val RULE_THICKNESS = 4f
private const val CHECK_DIAMETER = 42f
private const val CHECK_GAP = 24f
private const val ARROW_CENTER_X = 96f
private const val ARROW_SIZE = 32f
private const val SHADOW_ELEVATION = 15f
private const val VISIBLE_CARD_RADIUS = 5f

// Achievements panel (ROM browsing only).
private const val PANEL_LEFT = 1161f
private const val PANEL_TOP = 736f
private const val PANEL_WIDTH = 741f
private const val PANEL_HEIGHT = 326f
private const val PANEL_RADIUS = 30f
private const val PANEL_BORDER = 3f
private const val PANEL_ART = 96f
private const val PANEL_BADGE = 76f
private const val PANEL_BADGE_PITCH = 81f
private const val PANEL_TEXT = 32f
private const val PANEL_BAR_WIDTH = 437f
private const val PANEL_BAR_HEIGHT = 29f

private val PlatformTitleInk = Color(0xFFEBEBEB)
private val CardFill = Color(0xFF101B24)
private val ReadyGreen = Color(0xFF4DDB3A)
private val PanelFill = Color(0xA6000000)
private val PanelBorder = Color(0x59FFFFFF)
private val PanelGlow = Color(0x80FFFFFF)
private val BadgeEarned = Color(0xFFEFBD17)
private val BadgeLocked = Color(0x40FFFFFF)

private const val CARD_SCROLL_MS = 260

/** Which browse step the carousel is showing; only the copy and the side panel differ. */
enum class CardBrowseMode {
    /** All Games → pick a system. */
    Systems,

    /** A system's ROM list. */
    Roms,
}

/**
 * The card-browse rung of the XMB: a vertical band of cards with the focused one blown up at the
 * centre, its name and detail line beside it. Used for both the system picker and the ROM list,
 * which the design draws identically apart from the copy and the achievements panel.
 *
 * Deliberately transparent — it sits over whatever the XMB is already painting, so the wallpaper
 * and the focused ROM's hero art stay visible behind it.
 */
@Composable
fun XoraCardBrowsePane(
    items: List<XoraXmbItem>,
    selectedIndex: Int,
    mode: CardBrowseMode,
    onSelectItem: (Int) -> Unit,
    onActivateItem: () -> Unit,
    modifier: Modifier = Modifier,
    achievements: RaGameProgress? = null,
) {
    val reduceMotion = rememberReduceMotion()
    val scrollSpec = remember(reduceMotion) {
        if (reduceMotion) {
            tween(0)
        } else {
            tween<Float>(durationMillis = CARD_SCROLL_MS, easing = FastOutSlowInEasing)
        }
    }
    val scroll = remember { Animatable(selectedIndex.toFloat()) }
    LaunchedEffect(selectedIndex, items.size) {
        val target = selectedIndex.toFloat()
            .coerceIn(0f, (items.size - 1).coerceAtLeast(0).toFloat())
        scroll.animateTo(target, scrollSpec)
    }

    BoxWithConstraints(modifier = modifier) {
        val unit = min(
            maxWidth.value / XORA_DESIGN_WIDTH,
            maxHeight.value / XORA_DESIGN_HEIGHT,
        )
        val originX = (maxWidth.value - (XORA_DESIGN_WIDTH * unit)) / 2f
        val originY = (maxHeight.value - (XORA_DESIGN_HEIGHT * unit)) / 2f

        fun designX(x: Float): Dp = (originX + (x * unit)).dp
        fun designY(y: Float): Dp = (originY + (y * unit)).dp

        val focused = items.getOrNull(selectedIndex)

        BackHintArrow(
            size = (ARROW_SIZE * unit).dp,
            modifier = Modifier.offset(
                x = designX(ARROW_CENTER_X - (ARROW_SIZE / 2f)),
                y = designY(ROW_CENTER_Y - (ARROW_SIZE / 2f)),
            ),
        )

        // Far cards first so the enlarged focus card layers over its neighbours.
        items.indices.sortedByDescending { abs(it - scroll.value) }.forEach { index ->
            val delta = index - scroll.value
            if (abs(delta) > VISIBLE_CARD_RADIUS) return@forEach
            val closeness = (1f - abs(delta)).coerceIn(0f, 1f)
            val width = CARD_WIDTH + ((CARD_WIDTH_FOCUS - CARD_WIDTH) * closeness)
            val height = CARD_HEIGHT + ((CARD_HEIGHT_FOCUS - CARD_HEIGHT) * closeness)
            val centreY = ROW_CENTER_Y + cardOffsetFor(delta)

            BrowseCard(
                item = items[index],
                unit = unit,
                width = (width * unit).dp,
                height = (height * unit).dp,
                onClick = {
                    if (index == selectedIndex) onActivateItem() else onSelectItem(index)
                },
                modifier = Modifier.offset(
                    x = designX(CARD_CENTER_X - (width / 2f)),
                    y = designY(centreY - (height / 2f)),
                ),
            )
        }

        if (focused != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy((CHECK_GAP * unit).dp),
                modifier = Modifier.offset(
                    x = designX(TITLE_X),
                    y = designY(TITLE_CENTER_Y - (TITLE_SIZE / 2f)),
                ),
            ) {
                BrowseHeadline(
                    text = focused.title,
                    sizeDesignUnits = TITLE_SIZE,
                    unit = unit,
                    maxWidthDesignUnits = RULE_WIDTH - CHECK_DIAMETER - CHECK_GAP,
                )
                if (mode == CardBrowseMode.Systems && focused.ready) {
                    ReadyCheck(diameter = (CHECK_DIAMETER * unit).dp)
                }
            }

            Box(
                modifier = Modifier
                    .offset(
                        x = designX(RULE_X),
                        y = designY(ROW_CENTER_Y - (RULE_THICKNESS / 2f)),
                    )
                    .size(
                        width = (RULE_WIDTH * unit).dp,
                        height = (RULE_THICKNESS * unit).dp,
                    )
                    .shadow((SHADOW_ELEVATION * unit).dp)
                    .background(Color.White),
            )

            BrowseHeadline(
                text = when (mode) {
                    CardBrowseMode.Systems -> "Total Games: ${focused.gameCount}"
                    CardBrowseMode.Roms -> "Playtime: ${formatXmbPlaytime(focused.playTimeMs)}"
                },
                sizeDesignUnits = SUBTITLE_SIZE,
                unit = unit,
                maxWidthDesignUnits = RULE_WIDTH,
                modifier = Modifier.offset(
                    x = designX(TITLE_X),
                    y = designY(SUBTITLE_CENTER_Y - (SUBTITLE_SIZE / 2f)),
                ),
            )

            if (mode == CardBrowseMode.Roms) {
                RomDetailPanel(
                    item = focused,
                    progress = achievements,
                    unit = unit,
                    modifier = Modifier.offset(x = designX(PANEL_LEFT), y = designY(PANEL_TOP)),
                )
            }
        }
    }
}

/** Cards next to the focus sit a wider step away, as the design spaces them. */
private fun cardOffsetFor(delta: Float): Float {
    val distance = abs(delta)
    val magnitude = if (distance <= 1f) {
        distance * CARD_FOCUS_PITCH
    } else {
        CARD_FOCUS_PITCH + ((distance - 1f) * CARD_PITCH)
    }
    return magnitude * sign(delta)
}

@Composable
private fun BrowseCard(
    item: XoraXmbItem,
    unit: Float,
    width: Dp,
    height: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape((CARD_RADIUS * unit).dp)
    Box(
        modifier = modifier
            .size(width = width, height = height)
            .shadow(elevation = (SHADOW_ELEVATION * unit).dp, shape = shape)
            .clip(shape)
            .background(CardFill)
            .border(width = (CARD_BORDER * unit).dp, color = Color.White, shape = shape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (item.artPath != null) {
            ArtworkImage(
                path = item.artPath,
                contentDescription = item.title,
                fallbackText = item.title,
                contentScale = ContentScale.Crop,
                decodeMaxEdgePx = THUMB_DECODE_MAX_EDGE_PX,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            BrowseCardFallback(item = item, unit = unit, height = height)
        }
    }
}

/** No banner yet — fall back to the platform's own glyph over the card plate. */
@Composable
private fun BrowseCardFallback(item: XoraXmbItem, unit: Float, height: Dp) {
    val platformId = (item.action as? XoraXmbAction.DrillSystem)?.platformId
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy((10f * unit).dp),
        modifier = Modifier.padding(horizontal = (18f * unit).dp),
    ) {
        Image(
            painter = painterResource(drawableResForPlatformId(platformId)),
            contentDescription = null,
            modifier = Modifier.size(height * 0.42f),
        )
        Text(
            text = item.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleSmall.copy(
                fontFamily = XoraFonts.Secondary,
                fontSize = with(LocalDensity.current) { (22f * unit).dp.toSp() },
                fontWeight = FontWeight.SemiBold,
            ),
            color = Color.White,
        )
    }
}

@Composable
private fun BrowseHeadline(
    text: String,
    sizeDesignUnits: Float,
    unit: Float,
    maxWidthDesignUnits: Float,
    modifier: Modifier = Modifier,
) {
    val fontSize = with(LocalDensity.current) { (sizeDesignUnits * unit).dp.toSp() }
    Text(
        text = text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.headlineMedium.copy(
            fontFamily = XoraFonts.Secondary,
            fontSize = fontSize,
            lineHeight = fontSize,
            fontWeight = FontWeight.SemiBold,
            shadow = Shadow(
                color = Color.Black.copy(alpha = 0.5f),
                offset = Offset(10f * unit, 10f * unit),
                blurRadius = 15f * unit,
            ),
        ),
        color = PlatformTitleInk,
        // Sized to the text so the ready tick sits against the name, not out at the rule's end.
        modifier = modifier.widthIn(max = (maxWidthDesignUnits * unit).dp),
    )
}

/**
 * The focused ROM's RetroAchievements card. Falls back to just the box art, title and platform
 * when the game is unmatched or the player is signed out.
 */
@Composable
private fun RomDetailPanel(
    item: XoraXmbItem,
    progress: RaGameProgress?,
    unit: Float,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape((PANEL_RADIUS * unit).dp)
    val bodySize = with(LocalDensity.current) { (PANEL_TEXT * unit).dp.toSp() }
    Box(
        modifier = modifier
            .size(width = (PANEL_WIDTH * unit).dp, height = (PANEL_HEIGHT * unit).dp)
            .clip(shape)
            .background(PanelFill)
            .drawBehind {
                drawRect(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to PanelGlow,
                            0.4f to Color.Transparent,
                            0.6f to Color.Transparent,
                            1f to PanelGlow,
                        ),
                    ),
                )
            }
            .border(width = (PANEL_BORDER * unit).dp, color = PanelBorder, shape = shape)
            .padding((20f * unit).dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy((16f * unit).dp)) {
            Box(
                modifier = Modifier
                    .size((PANEL_ART * unit).dp)
                    .clip(RoundedCornerShape((10f * unit).dp))
                    .background(CardFill)
                    .border(
                        width = (PANEL_BORDER * unit).dp,
                        color = PanelBorder,
                        shape = RoundedCornerShape((10f * unit).dp),
                    ),
            ) {
                ArtworkImage(
                    path = item.artPath,
                    contentDescription = null,
                    fallbackText = item.title.take(2).uppercase(),
                    contentScale = ContentScale.Crop,
                    decodeMaxEdgePx = THUMB_DECODE_MAX_EDGE_PX,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Column(
                verticalArrangement = Arrangement.spacedBy((10f * unit).dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy((12f * unit).dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = item.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontFamily = XoraFonts.Secondary,
                            fontSize = bodySize,
                            lineHeight = bodySize,
                        ),
                        color = PlatformTitleInk,
                        modifier = Modifier.widthIn(max = (420f * unit).dp),
                    )
                    item.platformLabel?.let { label ->
                        Text(
                            text = label,
                            maxLines = 1,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = XoraFonts.Title,
                                fontSize = with(LocalDensity.current) { (20f * unit).dp.toSp() },
                            ),
                            color = Color.White,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.18f))
                                .padding(
                                    horizontal = (14f * unit).dp,
                                    vertical = (4f * unit).dp,
                                ),
                        )
                    }
                }

                if (progress != null && progress.numAchievements > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy((12f * unit).dp),
                    ) {
                        Text(
                            text = progress.progressLabel,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = XoraFonts.Secondary,
                                fontSize = bodySize,
                            ),
                            color = PlatformTitleInk,
                        )
                        AchievementProgressBar(
                            fraction = progress.completionFraction,
                            unit = unit,
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(
                            ((PANEL_BADGE_PITCH - PANEL_BADGE) * unit).dp,
                        ),
                    ) {
                        progress.achievements
                            .sortedByDescending { it.earned }
                            .take(MAX_PANEL_BADGES)
                            .forEach { achievement ->
                                AchievementBadgeThumb(
                                    url = achievement.badgeUrl,
                                    earned = achievement.earned,
                                    unit = unit,
                                )
                            }
                    }
                } else {
                    Text(
                        text = "No RetroAchievements data for this title.",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = XoraFonts.Secondary,
                            fontSize = with(LocalDensity.current) { (22f * unit).dp.toSp() },
                        ),
                        color = PlatformTitleInk.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

private const val MAX_PANEL_BADGES = 7

@Composable
private fun AchievementBadgeThumb(url: String, earned: Boolean, unit: Float) {
    val shape = RoundedCornerShape((5f * unit).dp)
    ArtworkImage(
        path = url,
        contentDescription = null,
        fallbackText = "",
        contentScale = ContentScale.Crop,
        decodeMaxEdgePx = THUMB_DECODE_MAX_EDGE_PX,
        modifier = Modifier
            .size((PANEL_BADGE * unit).dp)
            .clip(shape)
            .border(
                width = (2f * unit).dp,
                color = if (earned) BadgeEarned else BadgeLocked,
                shape = shape,
            ),
    )
}

@Composable
private fun AchievementProgressBar(fraction: Float, unit: Float) {
    val shape = CircleShape
    Box(
        modifier = Modifier
            .size(
                width = (PANEL_BAR_WIDTH * unit).dp,
                height = (PANEL_BAR_HEIGHT * unit).dp,
            )
            .clip(shape)
            .background(Color.White.copy(alpha = 0.18f))
            .border(width = (2f * unit).dp, color = Color.Black.copy(alpha = 0.25f), shape = shape),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                .size(height = (PANEL_BAR_HEIGHT * unit).dp, width = (PANEL_BAR_WIDTH * unit).dp)
                .clip(shape)
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0x40989CB3), Color(0x804D4655)),
                    ),
                ),
        )
    }
}

/** Green tick beside the platform name: an emulator is assigned, so this system is ready to play. */
@Composable
private fun ReadyCheck(diameter: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(diameter)
            .shadow(elevation = diameter * 0.2f, shape = CircleShape)
            .clip(CircleShape)
            .background(ReadyGreen)
            .border(width = diameter * 0.095f, color = Color.White, shape = CircleShape)
            .drawBehind {
                val tick = Path().apply {
                    moveTo(size.width * 0.28f, size.height * 0.52f)
                    lineTo(size.width * 0.44f, size.height * 0.68f)
                    lineTo(size.width * 0.76f, size.height * 0.32f)
                }
                drawPath(
                    path = tick,
                    color = Color.White,
                    style = Stroke(width = size.minDimension * 0.14f, cap = StrokeCap.Round),
                )
            },
    )
}

/** Mirrors the design's left chevron: B steps back out of this list. */
@Composable
private fun BackHintArrow(size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .drawBehind {
                val arrow = Path().apply {
                    moveTo(this@drawBehind.size.width * 0.72f, 0f)
                    lineTo(this@drawBehind.size.width * 0.18f, this@drawBehind.size.height * 0.5f)
                    lineTo(this@drawBehind.size.width * 0.72f, this@drawBehind.size.height)
                }
                drawPath(
                    path = arrow,
                    color = Color.White.copy(alpha = 0.85f),
                    style = Stroke(
                        width = this@drawBehind.size.minDimension * 0.16f,
                        cap = StrokeCap.Round,
                    ),
                )
            },
    )
}
