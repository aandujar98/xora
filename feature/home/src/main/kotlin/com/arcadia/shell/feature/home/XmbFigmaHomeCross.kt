package com.arcadia.shell.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arcadia.shell.designsystem.ArcadiaMotion
import com.arcadia.shell.designsystem.XoraFonts
import com.arcadia.shell.designsystem.XoraForegroundShadow
import com.arcadia.shell.designsystem.XoraSecondaryText
import com.arcadia.shell.designsystem.rememberReduceMotion
import com.arcadia.shell.designsystem.xmbAssetShadow
import com.arcadia.shell.feature.home.component.ArtworkImage
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 1080p home XMB, adapted from Figma Make `src/App.tsx` (1920×1080).
 *
 * Active tab is centered on (430, 282) and contain-fits a 178×106 box
 * scaled 1.25 (Make controller: left 341, top 229, 178×106). Inactive tabs
 * contain-fit 128×123 on that same center line so the music glyph (112×123)
 * is not height-squashed; pitch is 310 (Make gaps ~292–342).
 * Column items share x=430. The focused recents plate’s TOP is 420.5
 * (Make 420.49, 462×248). Hover copy sits at plate-right + 54 → x=715.
 * Neighbour above center = 105 (Make trophy 60+90/2), neighbour below
 * center = 785 (Make device 750+69/2), further items stack 63px
 * (Make folder top 882 after device bottom 819).
 *
 * Kept from the current build (not Make): seven-tab order including Profiles
 * and Videos, south-east [XoraForegroundShadow] (not a 0 0 6px glow), and
 * INACTIVE_ALPHA 0.60 (Make 0.5 vanished on-device; 0.75 still read as full). Hover copy is white
 * FOT-NewRodin Pro.
 */
private const val TAB_CENTER_X = 430f
private const val TAB_CENTER_Y = 282f
private const val TAB_BOX_W = 178f
private const val TAB_BOX_H = 106f
/** Active category glyph is 25% larger than the Make 178×106 controller frame. */
private const val TAB_ACTIVE_SCALE = 1.25f
private const val TAB_ACTIVE_W = TAB_BOX_W * TAB_ACTIVE_SCALE
private const val TAB_ACTIVE_H = TAB_BOX_H * TAB_ACTIVE_SCALE
private const val INACTIVE_BOX_W = 128f
private const val INACTIVE_BOX_H = 123f
private const val CAT_PITCH = 310f

/**
 * Focused column glyphs keep the pre-Make 178×136 tab frame × 1.25 so shrinking
 * the category tab box to the Make controller (178×106) does not shrink
 * Settings / Device / Trophy / folders. Game plates and cover-art rows are
 * excluded: those are sized by their artwork.
 */
private const val GLYPH_FOCUS_SCALE = 1.25f
private const val GLYPH_BOX_W_FOCUS = 178f * GLYPH_FOCUS_SCALE
private const val GLYPH_BOX_H_FOCUS = 136f * GLYPH_FOCUS_SCALE

private const val ITEM_FOCUS_TOP = 420.5f
private const val ITEM_ABOVE_Y = 105f
private const val ITEM_BELOW_Y = 785f
private const val ITEM_STACK_GAP = 63f
private const val PLATE_W_FOCUS = 462f
private const val PLATE_H_FOCUS = 248f

/** Shared hover plane: the vertical center of a 248px plate whose top is 420.5. */
private const val ITEM_FOCUS_Y = ITEM_FOCUS_TOP + PLATE_H_FOCUS / 2f

/** Dim, but still readable as #EBEBEB on the WAVE cyan. 0.5 vanished on-device. */
private const val INACTIVE_ALPHA = 0.60f
private const val PLATE_W = 280f
private const val PLATE_H = 150f
/** Music covers are square so they don't borrow the landscape game plate. */
private const val MUSIC_COVER_FOCUS = 178f
private const val MUSIC_COVER_REST = 128f
private const val PLATE_RADIUS = 30f
private const val PLATE_BORDER = 4f
private const val TITLE_GAP = 54f

/**
 * Left edge of the hover title, rule and subtitle. Anchored off the widest focused
 * frame (the recents plate) so the fixed-width [RULE_WIDTH] rule always ends on the
 * board's 56px right margin; keying it to the focused frame instead would swing this
 * by ~169px and leave the rule ragged whenever a glyph rather than a plate is focused.
 */
private const val HOVER_TITLE_X = TAB_CENTER_X + PLATE_W_FOCUS / 2f + TITLE_GAP
private const val TITLE_SIZE = 48f
private const val SUBTITLE_SIZE = 40f
private const val RULE_WIDTH = 1157f
private const val RULE_THICKNESS = 4f
/** Design file LINE y=543 minus TITLE top 453.49 on a plate whose center is 544.49. */
private const val TITLE_TO_RULE = 91f
/** Design file PLAYTIME top 568.49 minus LINE y=543. */
private const val RULE_TO_SUBTITLE = 25f
private const val XMB_SCROLL_MS = 340
private const val VISIBLE_ITEM_RADIUS = 5
private val PlateEmptyFill = Color(0xFF3A3A3A)
private val HoverInk = Color.White

private data class XmbSlot(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
    val alpha: Float,
)

/**
 * Home XMB cross: Figma category row, sandwich column (items above the tab sit above it),
 * recents plate, and NewRodin hover copy. No labels under tabs.
 */
@Composable
internal fun XmbCross(
    xmb: XoraXmbUiState,
    introReveal: Boolean,
    onSelectCategory: (Int) -> Unit,
    onSelectItem: (Int) -> Unit,
    onActivateItem: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberReduceMotion()
    val scrollSpec = remember(reduceMotion) {
        if (reduceMotion) {
            tween(0)
        } else {
            tween<Float>(durationMillis = XMB_SCROLL_MS, easing = FastOutSlowInEasing)
        }
    }

    val categoryScroll = remember { Animatable(xmb.categoryIndex.toFloat()) }
    val itemScroll = remember { Animatable(xmb.itemIndex.toFloat()) }
    val listEnterAlpha = remember { Animatable(1f) }
    LaunchedEffect(xmb.categoryIndex) {
        categoryScroll.animateTo(xmb.categoryIndex.toFloat(), scrollSpec)
    }
    LaunchedEffect(xmb.itemIndex) {
        val target = xmb.itemIndex.toFloat()
            .coerceIn(0f, (xmb.items.size - 1).coerceAtLeast(0).toFloat())
        itemScroll.animateTo(target, scrollSpec)
    }
    LaunchedEffect(xmb.depth, xmb.categoryIndex, xmb.drilledPlatformId) {
        itemScroll.snapTo(
            xmb.itemIndex.toFloat()
                .coerceIn(0f, (xmb.items.size - 1).coerceAtLeast(0).toFloat()),
        )
        if (reduceMotion) {
            listEnterAlpha.snapTo(1f)
            return@LaunchedEffect
        }
        listEnterAlpha.snapTo(0.28f)
        listEnterAlpha.animateTo(1f, tween(ArcadiaMotion.Medium, easing = FastOutSlowInEasing))
    }

    BoxWithConstraints(modifier = modifier.graphicsLayer { clip = false }) {
        val density = LocalDensity.current
        val unit = min(
            maxWidth.value / XORA_DESIGN_WIDTH,
            maxHeight.value / XORA_DESIGN_HEIGHT,
        )
        val originX = (maxWidth.value - (XORA_DESIGN_WIDTH * unit)) / 2f
        val originY = (maxHeight.value - (XORA_DESIGN_HEIGHT * unit)) / 2f
        fun pxX(x: Float) = with(density) { (originX + x * unit).dp.toPx() }
        fun pxY(y: Float) = with(density) { (originY + y * unit).dp.toPx() }
        fun du(v: Float) = (v * unit).dp
        val shadowOffset = du(XoraForegroundShadow.DesignOffset)
        val shadowBlur = du(XoraForegroundShadow.DesignBlur)
        val glyphStroke = du(XmbGlyphStrokeDesignPx)

        val categories = XoraXmbCategory.entries
        val items = xmb.items
        val atRoot = xmb.depth == XoraXmbDepth.Category
        val catScroll = categoryScroll.value
        val rowScroll = itemScroll.value
        val enterAlpha = listEnterAlpha.value

        categories.forEachIndexed { index, category ->
            val delta = index - catScroll
            val distance = abs(delta)
            val closeness = (1f - distance).coerceIn(0f, 1f)
            val alpha = lerp(INACTIVE_ALPHA, if (atRoot) 1f else 0.45f, closeness)
            val boxW = lerp(INACTIVE_BOX_W, TAB_ACTIVE_W, closeness)
            val boxH = lerp(INACTIVE_BOX_H, TAB_ACTIVE_H, closeness)
            val (visW, visH) = category.toXmbIcon().intrinsicDesignSize().fitInBox(boxW, boxH)
            val left = TAB_CENTER_X + delta * CAT_PITCH - visW / 2f
            // Centered in the frame on both axes, so a tab grows about its middle
            // as it gains focus instead of hanging off a shared top edge.
            val top = TAB_CENTER_Y - visH / 2f
            val intro = rememberIntroAppear(
                reveal = introReveal,
                delayMs = (abs(index - xmb.categoryIndex) * 26).coerceAtMost(180),
                reduceMotion = reduceMotion,
            )
            val pad = XoraForegroundShadow.DesignExtent
            val icon = category.toXmbIcon()
            val hovered = atRoot && index == xmb.categoryIndex

            Box(
                modifier = Modifier
                    .graphicsLayer {
                        // Pad the offscreen alpha layer so the 10×10 / 15px blur is not cropped.
                        translationX = pxX(left - pad)
                        translationY = pxY(top - pad) + intro.dropPx
                        scaleX = intro.scale
                        scaleY = intro.scale
                        this.alpha = alpha * intro.alpha
                        transformOrigin = TransformOrigin.Center
                        clip = false
                    }
                    .requiredSize(du(visW + pad * 2f), du(visH + pad * 2f))
                    .clickable(
                        interactionSource = remember(index) { MutableInteractionSource() },
                        indication = null,
                    ) { onSelectCategory(index) },
                contentAlignment = Alignment.Center,
            ) {
                XmbHoverGlow(
                    enabled = hovered,
                    modifier = Modifier.requiredSize(du(visW * 1.55f), du(visH * 1.55f)),
                )
                XmbVectorIcon(
                    icon = icon,
                    width = du(visW),
                    height = du(visH),
                    glass = icon.vectorDrawableRes() == null,
                    outlined = icon.vectorDrawableRes() == null,
                    shadowOffsetX = shadowOffset,
                    shadowOffsetY = shadowOffset,
                    shadowBlur = shadowBlur,
                    shadowAlpha = XoraForegroundShadow.Alpha,
                    strokeWidth = glyphStroke,
                )
            }
        }

        if (items.isEmpty()) {
            val emptyIntro = rememberIntroAppear(
                reveal = introReveal,
                delayMs = 80,
                reduceMotion = reduceMotion,
            )
            XoraSecondaryText(
                text = "Nothing here yet",
                fontSize = with(density) { du(TITLE_SIZE * 0.4f).toSp() },
                fillColor = Color.White,
                modifier = Modifier.graphicsLayer {
                    translationX = pxX(HOVER_TITLE_X)
                    translationY = pxY(ITEM_FOCUS_Y) + emptyIntro.dropPx
                    alpha = enterAlpha * emptyIntro.alpha
                },
            )
        } else {
            val i0 = rowScroll.toInt().coerceIn(0, items.lastIndex)
            val frac = (rowScroll - i0).coerceIn(0f, 1f)
            val layoutA = layoutColumn(items, i0)
            val layoutB = layoutColumn(
                items,
                (i0 + 1).coerceAtMost(items.lastIndex),
            )
            val slots = if (frac <= 0f || i0 == items.lastIndex) {
                layoutA
            } else {
                lerpSlots(layoutA, layoutB, frac)
            }

            val first = (rowScroll - VISIBLE_ITEM_RADIUS - 1f).toInt().coerceAtLeast(0)
            val last = (rowScroll + VISIBLE_ITEM_RADIUS + 1f).roundToInt()
                .coerceAtMost(items.lastIndex)
            val drawOrder = (first..last).sortedByDescending { abs(it - rowScroll) }
            for (index in drawOrder) {
                val item = items[index]
                val slot = slots[index]
                val selected = index == xmb.itemIndex
                val intro = rememberIntroAppear(
                    reveal = introReveal,
                    delayMs = (abs(index - xmb.itemIndex) * 22).coerceAtMost(160) + 40,
                    reduceMotion = reduceMotion,
                )
                val pad = XoraForegroundShadow.DesignExtent
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .graphicsLayer {
                            // Inactive icons use layer alpha, which otherwise crops the drop
                            // shadow to the glyph box. Pad so the full 10×10 / 15px blur fits.
                            translationX = pxX(slot.left - pad)
                            translationY = pxY(slot.top - pad) + intro.dropPx
                            this.alpha = slot.alpha * enterAlpha * intro.alpha
                            scaleX = intro.scale
                            scaleY = intro.scale
                            transformOrigin = TransformOrigin.Center
                            clip = false
                        }
                        .requiredSize(du(slot.width + pad * 2f), du(slot.height + pad * 2f))
                        .clickable(
                            interactionSource = remember(item.id) { MutableInteractionSource() },
                            indication = null,
                        ) {
                            if (selected) onActivateItem() else onSelectItem(index)
                        },
                ) {
                XmbHoverGlow(
                    enabled = !atRoot && selected,
                    modifier = Modifier.requiredSize(
                        du(slot.width * 1.55f),
                        du(slot.height * 1.55f),
                    ),
                )
                    XmbColumnGlyph(
                        item = item,
                        selected = selected,
                        width = du(slot.width),
                        height = du(slot.height),
                        unit = unit,
                    )
                }
            }

            val focusItem = items.getOrNull(xmb.itemIndex)
            val focusSlot = slots.getOrNull(xmb.itemIndex)
            if (focusItem != null && focusSlot != null) {
                val ruleY = focusSlot.top + focusSlot.height / 2f
                val detailIntro = rememberIntroAppear(
                    reveal = introReveal,
                    delayMs = 90,
                    reduceMotion = reduceMotion,
                )
                val title = hoverTitle(focusItem)
                val subtitle = hoverSubtitle(focusItem, xmb)
                val titleTop = ruleY - TITLE_TO_RULE
                val settledId = rememberXmbSettledFocus(focusItem.id)
                AnimatedVisibility(
                    visible = settledId == focusItem.id,
                    enter = fadeIn(tween(ArcadiaMotion.Medium, easing = FastOutSlowInEasing)),
                    exit = fadeOut(tween(110, easing = FastOutSlowInEasing)),
                    modifier = Modifier
                        .offset(
                            x = (originX + HOVER_TITLE_X * unit).dp,
                            y = (originY + titleTop * unit).dp,
                        )
                        .graphicsLayer {
                            alpha = enterAlpha * detailIntro.alpha
                            translationY = detailIntro.dropPx
                        },
                ) {
                    AnimatedContent(
                        targetState = Triple(focusItem.id, title, subtitle),
                        transitionSpec = {
                            (
                                fadeIn(tween(ArcadiaMotion.Medium, easing = FastOutSlowInEasing)) +
                                    slideInHorizontally(
                                        tween(XMB_SCROLL_MS, easing = FastOutSlowInEasing),
                                    ) { it / 5 }
                                ) togetherWith fadeOut(tween(110, easing = FastOutSlowInEasing))
                        },
                        contentKey = { it.first },
                        label = "xmbFocusDetail",
                    ) { (_, headline, line) ->
                        Column {
                            XmbHoverLine(
                                text = headline,
                                sizeDesignUnits = TITLE_SIZE,
                                unit = unit,
                                fontWeight = FontWeight.Normal,
                            )
                            Box(
                                modifier = Modifier
                                    .padding(top = ((TITLE_TO_RULE - TITLE_SIZE) * unit).dp)
                                    .width((RULE_WIDTH * unit).dp)
                                    .height((RULE_THICKNESS * unit).dp)
                                    .xmbAssetShadow(
                                        unit = unit,
                                        shape = RectangleShape,
                                        alpha = XoraForegroundShadow.TitleAlpha,
                                    )
                                    .background(Color.White),
                            )
                            XmbHoverLine(
                                text = line,
                                sizeDesignUnits = SUBTITLE_SIZE,
                                unit = unit,
                                fontWeight = FontWeight.Normal,
                                modifier = Modifier.padding(top = (RULE_TO_SUBTITLE * unit).dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun XmbColumnGlyph(
    item: XoraXmbItem,
    selected: Boolean,
    width: Dp,
    height: Dp,
    unit: Float,
) {
    val shadowOffset = (XoraForegroundShadow.DesignOffset * unit).dp
    val shadowBlur = (XoraForegroundShadow.DesignBlur * unit).dp
    val glyphStroke = (XmbGlyphStrokeDesignPx * unit).dp
    when {
        item.isMusicCoverArt() -> {
            val shape = RoundedCornerShape((12f * unit).dp)
            Box(
                modifier = Modifier
                    .requiredSize(width, height)
                    .xmbAssetShadow(
                        unit = unit,
                        shape = shape,
                        alpha = XoraForegroundShadow.Alpha,
                    )
                    .clip(shape)
                    .border(
                        width = if (selected) (2f * unit).dp else 0.dp,
                        color = if (selected) Color.White else Color.Transparent,
                        shape = shape,
                    ),
            ) {
                ArtworkImage(
                    path = item.artPath,
                    contentDescription = item.title,
                    fallbackText = item.title.take(1),
                    contentScale = ContentScale.Crop,
                    cacheInMemory = true,
                    decodeMaxEdgePx = 256,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        isGamePlate(item) -> XmbGamePlate(
            title = item.title,
            artPath = item.artPath,
            selected = selected,
            width = width,
            height = height,
            unit = unit,
        )
        item.icon.isFolderGlyph() -> XmbFolderImgIcon(
            artPath = item.artPath,
            windowIcon = item.icon.folderWindowIcon(),
            width = width,
            height = height,
            shadowOffsetX = shadowOffset,
            shadowOffsetY = shadowOffset,
            shadowBlur = shadowBlur,
            shadowAlpha = XoraForegroundShadow.Alpha,
            strokeWidth = glyphStroke,
        )
        !item.artPath.isNullOrBlank() -> {
            val shape = RoundedCornerShape((12f * unit).dp)
            Box(
                modifier = Modifier
                    .requiredSize(width, height)
                    .xmbAssetShadow(
                        unit = unit,
                        shape = shape,
                        alpha = XoraForegroundShadow.Alpha,
                    )
                    .clip(shape)
                    .border(
                        width = if (selected) (2f * unit).dp else 0.dp,
                        color = if (selected) Color.White else Color.Transparent,
                        shape = shape,
                    ),
            ) {
                ArtworkImage(
                    path = item.artPath,
                    contentDescription = item.title,
                    fallbackText = item.title.take(1),
                    contentScale = ContentScale.Crop,
                    cacheInMemory = true,
                    decodeMaxEdgePx = 256,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        else -> {
            val icon = item.icon
            XmbVectorIcon(
                icon = icon,
                width = width,
                height = height,
                glass = icon.vectorDrawableRes() == null,
                outlined = icon.vectorDrawableRes() == null,
                shadowOffsetX = shadowOffset,
                shadowOffsetY = shadowOffset,
                shadowBlur = shadowBlur,
                shadowAlpha = XoraForegroundShadow.Alpha,
                strokeWidth = glyphStroke,
            )
        }
    }
}

@Composable
private fun XmbGamePlate(
    title: String,
    artPath: String?,
    selected: Boolean,
    width: Dp,
    height: Dp,
    unit: Float,
) {
    val shape = RoundedCornerShape((PLATE_RADIUS * unit).dp)
    Box(
        modifier = Modifier
            .requiredSize(width, height)
            .xmbAssetShadow(
                unit = unit,
                shape = shape,
                alpha = XoraForegroundShadow.Alpha,
            )
            .clip(shape)
            .background(PlateEmptyFill)
            .border(
                width = (PLATE_BORDER * unit).dp,
                color = Color.White.copy(alpha = if (selected) 1f else 0.55f),
                shape = shape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (!artPath.isNullOrBlank()) {
            ArtworkImage(
                path = artPath,
                contentDescription = title,
                fallbackText = title,
                contentScale = ContentScale.Crop,
                cacheInMemory = true,
                decodeMaxEdgePx = 512,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun XmbHoverLine(
    text: String,
    sizeDesignUnits: Float,
    unit: Float,
    fontWeight: FontWeight,
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
            fontFamily = XoraFonts.XmbLabel,
            fontSize = fontSize,
            lineHeight = fontSize,
            fontWeight = fontWeight,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.Both,
            ),
            shadow = Shadow(
                color = Color.Black.copy(alpha = XoraForegroundShadow.TitleAlpha),
                offset = Offset(shadowPx, shadowPx),
                blurRadius = blurPx,
            ),
        ),
        color = HoverInk,
        modifier = modifier.widthIn(max = (RULE_WIDTH * unit).dp),
    )
}

private fun isRecentsItem(item: XoraXmbItem): Boolean =
    item.id == "continue" ||
        item.id == "favorite" ||
        item.action is XoraXmbAction.LaunchContinueOrFavorite

private fun hoverTitle(item: XoraXmbItem): String = when {
    item.id == "all_games" || item.action is XoraXmbAction.DrillAllGames -> "Device"
    item.id == "home_folder" || item.action is XoraXmbAction.PickHomeFolderImage -> "Folder_IMG"
    else -> item.title
}

private fun hoverSubtitle(item: XoraXmbItem, xmb: XoraXmbUiState): String = when {
    isRecentsItem(item) -> "Recently Played"
    item.id == "all_games" || item.action is XoraXmbAction.DrillAllGames -> "View Games"
    item.id == "home_folder" || item.action is XoraXmbAction.PickHomeFolderImage -> "Customize"
    item.action is XoraXmbAction.LaunchGame -> "Playtime: ${formatXmbPlaytime(item.playTimeMs)}"
    !item.subtitle.isNullOrBlank() -> item.subtitle
    else -> xmb.category.label
}

private fun isGamePlate(item: XoraXmbItem): Boolean = isRecentsItem(item)

/**
 * True when the row paints artwork rather than a vector glyph, mirroring the branch order in
 * [XmbColumnGlyph]: every folder shell (Folder_IMG and the Photo / Video / Music content
 * folders) keeps its glyph even once a still is picked, so folders count as glyphs.
 */
private fun hasCoverArt(item: XoraXmbItem): Boolean =
    !item.icon.isFolderGlyph() && !item.artPath.isNullOrBlank()

private fun itemDesignSize(item: XoraXmbItem, focused: Boolean): Pair<Float, Float> {
    if (isGamePlate(item)) {
        return if (focused) PLATE_W_FOCUS to PLATE_H_FOCUS else PLATE_W to PLATE_H
    }
    if (item.isMusicCoverArt()) {
        val edge = if (focused) MUSIC_COVER_FOCUS else MUSIC_COVER_REST
        return edge to edge
    }
    val boxW: Float
    val boxH: Float
    if (!focused) {
        boxW = INACTIVE_BOX_W
        boxH = INACTIVE_BOX_H
    } else if (hasCoverArt(item)) {
        boxW = TAB_BOX_W
        boxH = TAB_BOX_H
    } else {
        boxW = GLYPH_BOX_W_FOCUS
        boxH = GLYPH_BOX_H_FOCUS
    }
    return item.icon.intrinsicDesignSize().fitInBox(boxW, boxH)
}

private fun layoutColumn(
    items: List<XoraXmbItem>,
    focusIndex: Int,
): List<XmbSlot> {
    val n = items.size
    if (n == 0) return emptyList()
    val focus = focusIndex.coerceIn(0, n - 1)
    val sizes = items.mapIndexed { i, item -> itemDesignSize(item, i == focus) }
    val tops = FloatArray(n)
    // Hovered item is centered on the recents-plate mid-line (544.5) so a 248px
    // plate still starts at y=420.5 (below the tab) and every other glyph shares
    // that same horizontal plane when it becomes the active row.
    tops[focus] = ITEM_FOCUS_Y - sizes[focus].second / 2f
    for (i in focus - 1 downTo 0) {
        tops[i] =
            if (i == focus - 1) {
                ITEM_ABOVE_Y - sizes[i].second / 2f
            } else {
                tops[i + 1] - sizes[i].second - ITEM_STACK_GAP
            }
    }
    for (i in focus + 1 until n) {
        tops[i] =
            if (i == focus + 1) {
                ITEM_BELOW_Y - sizes[i].second / 2f
            } else {
                tops[i - 1] + sizes[i - 1].second + ITEM_STACK_GAP
            }
    }
    return items.indices.map { i ->
        val (w, h) = sizes[i]
        XmbSlot(
            left = TAB_CENTER_X - w / 2f,
            top = tops[i],
            width = w,
            height = h,
            alpha = if (i == focus) 1f else INACTIVE_ALPHA,
        )
    }
}

private fun lerpSlots(a: List<XmbSlot>, b: List<XmbSlot>, t: Float): List<XmbSlot> {
    val n = min(a.size, b.size)
    return List(n) { i ->
        XmbSlot(
            left = lerp(a[i].left, b[i].left, t),
            top = lerp(a[i].top, b[i].top, t),
            width = lerp(a[i].width, b[i].width, t),
            height = lerp(a[i].height, b[i].height, t),
            alpha = lerp(a[i].alpha, b[i].alpha, t),
        )
    }
}

private fun Pair<Float, Float>.fitInBox(boxW: Float, boxH: Float): Pair<Float, Float> {
    val srcW = first.coerceAtLeast(1f)
    val srcH = second.coerceAtLeast(1f)
    val scale = min(boxW / srcW, boxH / srcH)
    return srcW * scale to srcH * scale
}

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t.coerceIn(0f, 1f)
