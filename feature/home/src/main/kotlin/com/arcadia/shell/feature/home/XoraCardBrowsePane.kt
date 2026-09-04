package com.arcadia.shell.feature.home

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arcadia.shell.datastore.XmbTitleStyle
import com.arcadia.shell.designsystem.XoraFonts
import com.arcadia.shell.designsystem.XoraForegroundShadow
import com.arcadia.shell.designsystem.rememberReduceMotion
import com.arcadia.shell.designsystem.xmbAssetShadow
import com.arcadia.shell.designsystem.xoraForegroundShadow
import com.arcadia.shell.designsystem.xoraPlateStroke
import com.arcadia.shell.feature.home.component.ArtworkImage
import com.arcadia.shell.feature.home.component.THUMB_DECODE_MAX_EDGE_PX
import com.arcadia.shell.feature.home.component.xmb.drawableResForPlatformId
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sign

// Figma artboard units (1920x1080); scaled by `unit` so the pane holds its proportions anywhere.
private const val CARD_WIDTH = 280f
private const val CARD_HEIGHT = 150f
private const val CARD_WIDTH_FOCUS = 462f
private const val CARD_HEIGHT_FOCUS = 248f
/** Same 25% shrink as the XMB folder glyphs. */
private const val FOLDER_SIZE_SCALE = 0.75f
/** Folder_Music reads taller than Photos / Videos; pull it in another 15%. */
private const val MUSIC_FOLDER_SCALE = 0.85f
/** Music albums / tracks: 1×1, same height rhythm as the game cards. */
private const val MUSIC_CARD = 150f
private const val MUSIC_CARD_FOCUS = 248f
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
private const val RULE_WIDTH = 1157f
private const val RULE_THICKNESS = 4f
private const val TITLE_TOP_Y = TITLE_CENTER_Y - TITLE_SIZE / 2f
/** Rule Y relative to the title block, so logos can grow without moving playtime. */
private const val TITLE_TO_RULE_Y = (ROW_CENTER_Y - RULE_THICKNESS / 2f) - TITLE_TOP_Y
private const val TITLE_TO_SUBTITLE_Y = (SUBTITLE_CENTER_Y - SUBTITLE_SIZE / 2f) - TITLE_TOP_Y
private const val CHECK_DIAMETER = 42f
private const val CHECK_GAP = 24f
private const val ARROW_CENTER_X = 96f
private const val ARROW_SIZE = 32f
private const val VISIBLE_CARD_RADIUS = 5f


private val PlatformTitleInk = Color.White
private val CardFill = Color(0xFF101B24)
private val ReadyGreen = Color(0xFF4DDB3A)

private const val CARD_SCROLL_MS = 260

/** Which browse step the carousel is showing; only the copy and the side panel differ. */
enum class CardBrowseMode {
    /** All Games → pick a system. */
    Systems,

    /** A system's ROM list. */
    Roms,

    /** Music → Link DSP Accounts → Spotify / Apple Music / YouTube Music. */
    DspAccounts,

    /** Music → Playlist → album / playlist cards. */
    MusicAlbums,

    /** Music → an album's songs, or All music. */
    MusicTracks,
}

/**
 * The card-browse rung of the XMB: a vertical band of cards with the focused one blown up at the
 * centre, its name and detail line beside it. Used for both the system picker and the ROM list,
 * which the design draws identically apart from the copy (and ROM title icons vs text).
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
    /** ROM rows honour the shell's title preference: clear-logo art or plain text. */
    titleStyle: XmbTitleStyle = XmbTitleStyle.TitleIcons,
    trailer: HeroTrailerState = HeroTrailerState(),
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
        // ROM copy fades out while the wheel is moving and stays gone until the hero
        // wallpaper (and its sound bite) have had time to appear on the new title.
        val settledRomId = rememberXmbSettledFocus(
            focused?.id,
            settleMs = XMB_GAME_SELECT_SETTLE_MS,
        )
        val heldId = rememberXmbHeldFocus(
            focused?.id,
            settleMs = XMB_FOCUS_SETTLE_MS,
        )
        val shownId = if (mode == CardBrowseMode.Roms) settledRomId else heldId
        val shownItem = items.firstOrNull { it.id == shownId }

        val (arrowW, arrowH) = XmbIcon.Back.intrinsicDesignSize()
        val arrowScale = min(ARROW_SIZE / arrowW, ARROW_SIZE / arrowH)
        val visArrowW = arrowW * arrowScale
        val visArrowH = arrowH * arrowScale
        XmbVectorIcon(
            icon = XmbIcon.Back,
            width = (visArrowW * unit).dp,
            height = (visArrowH * unit).dp,
            glass = false,
            outlined = false,
            strokeWidth = 0.dp,
            modifier = Modifier.offset(
                x = designX(ARROW_CENTER_X - (visArrowW / 2f)),
                y = designY(ROW_CENTER_Y - (visArrowH / 2f)),
            ),
        )

        // Far cards first so the enlarged focus card layers over its neighbours. Keyed on the
        // item so the reshuffling draw order does not restart each card's artwork request.
        items.indices
            .filter { abs(it - scroll.value) <= VISIBLE_CARD_RADIUS }
            .sortedByDescending { abs(it - scroll.value) }
            .forEach { index ->
                val item = items[index]
                val delta = index - scroll.value
                val closeness = (1f - abs(delta)).coerceIn(0f, 1f)
                val square = mode == CardBrowseMode.MusicAlbums ||
                    mode == CardBrowseMode.MusicTracks
                val restW = if (square) MUSIC_CARD else CARD_WIDTH
                val restH = if (square) MUSIC_CARD else CARD_HEIGHT
                val focusW = if (square) MUSIC_CARD_FOCUS else CARD_WIDTH_FOCUS
                val focusH = if (square) MUSIC_CARD_FOCUS else CARD_HEIGHT_FOCUS
                val width = restW + ((focusW - restW) * closeness)
                val height = restH + ((focusH - restH) * closeness)
                val centreY = ROW_CENTER_Y + cardOffsetFor(delta)

                key(item.id) {
                    BrowseCard(
                        item = item,
                        focused = index == selectedIndex,
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
                        trailer = trailer.takeIf { index == selectedIndex } ?: HeroTrailerState(),
                    )
                }
            }

        AnimatedContent(
            targetState = shownItem,
            transitionSpec = { xmbCopyTransition(reduceMotion) },
            contentKey = { it?.id },
            label = "cardBrowseCopy",
            modifier = Modifier.offset(
                x = designX(TITLE_X),
                y = designY(TITLE_CENTER_Y - (TITLE_SIZE / 2f)),
            ),
        ) { item ->
            if (item == null) return@AnimatedContent
            // Offset children (rule / playtime) sit below the title row. Size the plate to that
            // full block so AnimatedContent fades and slides title and playtime together.
            Box(
                modifier = Modifier.size(
                    width = (RULE_WIDTH * unit).dp,
                    height = ((TITLE_TO_SUBTITLE_Y + SUBTITLE_SIZE) * unit).dp,
                ),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy((CHECK_GAP * unit).dp),
                ) {
                    val logoPath = item.logoPath
                        ?.takeIf {
                            mode == CardBrowseMode.Roms && titleStyle == XmbTitleStyle.TitleIcons
                        }
                    if (logoPath != null) {
                        ArtworkImage(
                            path = logoPath,
                            contentDescription = item.title,
                            fallbackText = item.title,
                            contentScale = ContentScale.Fit,
                            decodeMaxEdgePx = THUMB_DECODE_MAX_EDGE_PX,
                            modifier = Modifier
                                .height((TITLE_SIZE * 1.6f * unit).dp)
                                .widthIn(max = (RULE_WIDTH * 0.6f * unit).dp),
                        )
                    } else {
                        BrowseHeadline(
                            text = item.title,
                            sizeDesignUnits = TITLE_SIZE,
                            unit = unit,
                            maxWidthDesignUnits = RULE_WIDTH - CHECK_DIAMETER - CHECK_GAP,
                            fontFamily = XoraFonts.Secondary,
                        )
                    }
                    // Systems: core ready. DSP: account linked.
                    if ((mode == CardBrowseMode.Systems || mode == CardBrowseMode.DspAccounts) &&
                        item.ready
                    ) {
                        ReadyCheck(diameter = (CHECK_DIAMETER * unit).dp)
                    }
                }
                Box(
                    modifier = Modifier
                        .offset(
                            x = ((RULE_X - TITLE_X) * unit).dp,
                            y = (TITLE_TO_RULE_Y * unit).dp,
                        )
                        .size(
                            width = (RULE_WIDTH * unit).dp,
                            height = (RULE_THICKNESS * unit).dp,
                        )
                        .xmbAssetShadow(
                            unit = unit,
                            shape = RectangleShape,
                            alpha = XoraForegroundShadow.TitleAlpha,
                        )
                        .background(Color.White),
                )
                BrowseHeadline(
                    text = when (mode) {
                        CardBrowseMode.Systems -> "Total Games: ${item.gameCount}"
                        CardBrowseMode.Roms -> "Playtime: ${formatXmbPlaytime(item.playTimeMs)}"
                        CardBrowseMode.DspAccounts -> item.subtitle.orEmpty()
                        CardBrowseMode.MusicAlbums -> "Total Tracks: ${item.gameCount}"
                        CardBrowseMode.MusicTracks -> item.subtitle.orEmpty()
                    },
                    sizeDesignUnits = SUBTITLE_SIZE,
                    unit = unit,
                    maxWidthDesignUnits = RULE_WIDTH,
                    fontFamily = XoraFonts.Secondary,
                    modifier = Modifier.offset(y = (TITLE_TO_SUBTITLE_Y * unit).dp),
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
    focused: Boolean,
    unit: Float,
    width: Dp,
    height: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailer: HeroTrailerState = HeroTrailerState(),
) {
    val shape = RoundedCornerShape((CARD_RADIUS * unit).dp)
    Box(
        modifier = modifier.size(width = width, height = height),
        contentAlignment = Alignment.Center,
    ) {
    XmbHoverGlow(
        enabled = focused,
        modifier = Modifier
            .matchParentSize()
            .graphicsLayer {
                scaleX = 1.5f
                scaleY = 1.5f
            },
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .xmbAssetShadow(unit = unit, shape = shape, alpha = XoraForegroundShadow.Alpha)
            .xoraPlateStroke(
                unit = unit,
                radiusDesign = CARD_RADIUS,
                borderDesign = CARD_BORDER,
                alpha = if (focused) 1f else 0.55f,
            )
            .clip(shape)
            .background(CardFill)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (item.icon.isFolderGlyph() && !item.artPath.isNullOrBlank()) {
            val (designW, designH) = item.icon.intrinsicDesignSize()
            val folderScale = FOLDER_SIZE_SCALE *
                if (item.icon == XmbIcon.FolderMusic) MUSIC_FOLDER_SCALE else 1f
            val scale = (width.value / designW) * folderScale
            XmbFolderImgIcon(
                artPath = item.artPath,
                windowIcon = item.icon.folderWindowIcon(),
                width = (designW * scale).dp,
                height = (designH * scale).dp,
                castShadow = false,
                strokeWidth = (XmbGlyphStrokeDesignPx * unit).dp,
            )
        } else if (item.icon.isFolderGlyph()) {
            val (designW, designH) = item.icon.intrinsicDesignSize()
            val folderScale = FOLDER_SIZE_SCALE *
                if (item.icon == XmbIcon.FolderMusic) MUSIC_FOLDER_SCALE else 1f
            val scale = (width.value / designW) * folderScale
            XmbVectorIcon(
                icon = item.icon,
                width = (designW * scale).dp,
                height = (designH * scale).dp,
                glass = false,
                outlined = false,
                castShadow = false,
                strokeWidth = 0.dp,
            )
        } else if (item.artPath != null) {
            GameIconIdleArt(
                coverPath = item.artPath,
                title = item.title,
                focused = focused,
                trailer = trailer,
                screenshotPaths = listOfNotNull(item.screenshotPath),
                artAlignX = item.artAlignX,
                artAlignY = item.artAlignY,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            BrowseCardFallback(item = item, unit = unit, height = height)
        }
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
    fontFamily: FontFamily,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val fontSize = with(density) { (sizeDesignUnits * unit).dp.toSp() }
    val shadowPx = with(density) { (XoraForegroundShadow.DesignOffset * unit).dp.toPx() }
    val blurPx = with(density) { (XoraForegroundShadow.DesignBlur * unit).dp.toPx() }
    Text(
        text = text,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.headlineMedium.copy(
            fontFamily = fontFamily,
            fontSize = fontSize,
            lineHeight = fontSize,
            fontWeight = FontWeight.SemiBold,
            shadow = Shadow(
                color = Color.Black.copy(alpha = XoraForegroundShadow.TitleAlpha),
                offset = Offset(shadowPx, shadowPx),
                blurRadius = blurPx,
            ),
        ),
        color = PlatformTitleInk,
        // Sized to the text so the ready tick sits against the name, not out at the rule's end.
        modifier = modifier.widthIn(max = (maxWidthDesignUnits * unit).dp),
    )
}





/** Green tick beside the platform name: an emulator is assigned, so this system is ready to play. */
@Composable
private fun ReadyCheck(diameter: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(diameter)
            .xoraForegroundShadow(CircleShape)
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

