package com.arcadia.shell.feature.home

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arcadia.shell.designsystem.ArcadiaMotion
import com.arcadia.shell.designsystem.XoraFonts
import com.arcadia.shell.designsystem.XoraSecondaryText
import com.arcadia.shell.designsystem.rememberReduceMotion
import com.arcadia.shell.feature.home.component.ArtworkImage
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/** Figma HOME - GAME (node 545:1974) on a 1920×1080 artboard. */
private const val CAT_TOP = 229f
private const val CAT_CENTER_X = 608f
private const val CAT_PITCH = 290f
private const val CAT_ROW_HEIGHT = 106f
private const val ABOVE_GAP = 85f
private const val BELOW_GAP = 85f
private const val STACK_GAP = 64f
private const val INACTIVE_SCALE = 0.8f
private const val INACTIVE_ALPHA = 0.5f
private const val PLATE_LEFT = 207f
private const val PLATE_W_FOCUS = 462f
private const val PLATE_H_FOCUS = 248f
private const val PLATE_W = 280f
private const val PLATE_H = 150f
private const val PLATE_RADIUS = 30f
private const val PLATE_BORDER = 4f
private const val TITLE_X = 715f
private const val TITLE_SIZE = 48f
private const val SUBTITLE_SIZE = 40f
private const val RULE_WIDTH = 1157f
private const val RULE_THICKNESS = 4f
private const val TITLE_TO_RULE = 90f
private const val RULE_TO_SUBTITLE = 16f
private const val SHADOW_ELEVATION = 15f
private const val XMB_SCROLL_MS = 340
private const val VISIBLE_ITEM_RADIUS = 5
private val PlateEmptyFill = Color(0xFF3A3A3A)
private val HoverInk = Color(0xFFEBEBEB)

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

    BoxWithConstraints(modifier = modifier) {
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

        val categories = XoraXmbCategory.entries
        val items = xmb.items
        val atRoot = xmb.depth == XoraXmbDepth.Category
        val catScroll = categoryScroll.value
        val rowScroll = itemScroll.value
        val enterAlpha = listEnterAlpha.value
        val focusedCategory = categories.getOrElse(xmb.categoryIndex) { XoraXmbCategory.Games }
        val catRowHeight = categoryDesignSize(focusedCategory).second

        categories.forEachIndexed { index, category ->
            val delta = index - catScroll
            val distance = abs(delta)
            val closeness = (1f - distance).coerceIn(0f, 1f)
            val scale = lerp(INACTIVE_SCALE, 1f, closeness)
            val alpha = lerp(INACTIVE_ALPHA, if (atRoot) 1f else 0.45f, closeness)
            val (fullW, fullH) = categoryDesignSize(category)
            val visW = fullW * scale
            val visH = fullH * scale
            val centerX = CAT_CENTER_X + delta * CAT_PITCH
            val left = centerX - visW / 2f
            val top = CAT_TOP + (CAT_ROW_HEIGHT - visH) / 2f
            val intro = rememberIntroAppear(
                reveal = introReveal,
                delayMs = (abs(index - xmb.categoryIndex) * 26).coerceAtMost(180),
                reduceMotion = reduceMotion,
            )
            val icon = category.toXmbIcon()

            Box(
                modifier = Modifier
                    .graphicsLayer {
                        translationX = pxX(left)
                        translationY = pxY(top) + intro.dropPx
                        scaleX = intro.scale
                        scaleY = intro.scale
                        this.alpha = alpha * intro.alpha
                        transformOrigin = TransformOrigin.Center
                        clip = false
                    }
                    .requiredSize(du(visW), du(visH))
                    .clickable(
                        interactionSource = remember(index) { MutableInteractionSource() },
                        indication = null,
                    ) { onSelectCategory(index) },
                contentAlignment = Alignment.Center,
            ) {
                XmbVectorIcon(
                    icon = icon,
                    width = du(visW),
                    height = du(visH),
                    glass = icon.vectorDrawableRes() == null,
                    outlined = icon.vectorDrawableRes() == null,
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
                    translationX = pxX(TITLE_X)
                    translationY = pxY(CAT_TOP + catRowHeight + BELOW_GAP) + emptyIntro.dropPx
                    alpha = enterAlpha * emptyIntro.alpha
                },
            )
        } else {
            val i0 = rowScroll.toInt().coerceIn(0, items.lastIndex)
            val frac = (rowScroll - i0).coerceIn(0f, 1f)
            val layoutA = layoutColumn(items, i0, CAT_TOP, catRowHeight)
            val layoutB = layoutColumn(
                items,
                (i0 + 1).coerceAtMost(items.lastIndex),
                CAT_TOP,
                catRowHeight,
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
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .graphicsLayer {
                            translationX = pxX(slot.left)
                            translationY = pxY(slot.top) + intro.dropPx
                            this.alpha = slot.alpha * enterAlpha * intro.alpha
                            scaleX = intro.scale
                            scaleY = intro.scale
                            transformOrigin = TransformOrigin.Center
                            clip = false
                        }
                        .requiredSize(du(slot.width), du(slot.height))
                        .clickable(
                            interactionSource = remember(item.id) { MutableInteractionSource() },
                            indication = null,
                        ) {
                            if (selected) onActivateItem() else onSelectItem(index)
                        },
                ) {
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
                val subtitle = hoverSubtitle(focusItem, xmb)
                val titleTop = ruleY - TITLE_TO_RULE
                AnimatedContent(
                    targetState = Triple(focusItem.id, focusItem.title, subtitle),
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
                    modifier = Modifier
                        .offset(
                            x = (originX + TITLE_X * unit).dp,
                            y = (originY + titleTop * unit).dp,
                        )
                        .graphicsLayer {
                            alpha = enterAlpha * detailIntro.alpha
                            translationY = detailIntro.dropPx
                        },
                ) { (_, title, line) ->
                    Column {
                        XmbHoverLine(
                            text = title,
                            sizeDesignUnits = TITLE_SIZE,
                            unit = unit,
                        )
                        Box(
                            modifier = Modifier
                                .padding(top = ((TITLE_TO_RULE - TITLE_SIZE) * unit).dp)
                                .width((RULE_WIDTH * unit).dp)
                                .height((RULE_THICKNESS * unit).dp)
                                .shadow((SHADOW_ELEVATION * unit).dp)
                                .background(Color.White),
                        )
                        XmbHoverLine(
                            text = line,
                            sizeDesignUnits = SUBTITLE_SIZE,
                            unit = unit,
                            modifier = Modifier.padding(top = (RULE_TO_SUBTITLE * unit).dp),
                        )
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
    when {
        isGamePlate(item) -> XmbGamePlate(
            title = item.title,
            artPath = item.artPath,
            selected = selected,
            width = width,
            height = height,
            unit = unit,
        )
        item.icon == XmbIcon.Folder -> XmbFolderImgIcon(
            artPath = item.artPath,
            width = width,
            height = height,
        )
        !item.artPath.isNullOrBlank() -> {
            val shape = RoundedCornerShape((12f * unit).dp)
            Box(
                modifier = Modifier
                    .requiredSize(width, height)
                    .shadow((SHADOW_ELEVATION * unit).dp, shape)
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
            .shadow(elevation = (SHADOW_ELEVATION * unit).dp, shape = shape)
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
        color = HoverInk,
        modifier = modifier.widthIn(max = (RULE_WIDTH * unit).dp),
    )
}

private fun hoverSubtitle(item: XoraXmbItem, xmb: XoraXmbUiState): String = when {
    item.action is XoraXmbAction.LaunchContinueOrFavorite -> "Recently Played"
    item.action is XoraXmbAction.LaunchGame -> "Playtime: ${formatXmbPlaytime(item.playTimeMs)}"
    !item.subtitle.isNullOrBlank() -> item.subtitle
    else -> xmb.category.label
}

private fun isGamePlate(item: XoraXmbItem): Boolean =
    item.action is XoraXmbAction.LaunchContinueOrFavorite

private fun categoryDesignSize(category: XoraXmbCategory): Pair<Float, Float> = when (category) {
    XoraXmbCategory.Profiles -> 90f to 90f
    XoraXmbCategory.Settings -> 90f to 90f
    XoraXmbCategory.Games -> 178f to 106f
    XoraXmbCategory.Media -> 128f to 90f
    XoraXmbCategory.Music -> 90f to 90f
    XoraXmbCategory.Network -> 90f to 90f
}

private fun itemDesignSize(item: XoraXmbItem, focused: Boolean): Pair<Float, Float> {
    if (isGamePlate(item)) {
        return if (focused) PLATE_W_FOCUS to PLATE_H_FOCUS else PLATE_W to PLATE_H
    }
    val (w, h) = when (item.icon) {
        XmbIcon.Trophy -> 100f to 90f
        XmbIcon.Device -> 121f to 68f
        XmbIcon.Folder -> 154f to 109f
        else -> 90f to 90f
    }
    val scale = if (focused) 1f else INACTIVE_SCALE
    return w * scale to h * scale
}

private fun itemLeft(item: XoraXmbItem, width: Float): Float {
    if (isGamePlate(item)) return PLATE_LEFT
    val fullW = when (item.icon) {
        XmbIcon.Trophy -> 100f
        XmbIcon.Device -> 121f
        XmbIcon.Folder -> 154f
        else -> 90f
    }
    val unscaledLeft = when (item.icon) {
        XmbIcon.Trophy -> 480f
        XmbIcon.Device -> 369f
        XmbIcon.Folder -> 353f
        else -> CAT_CENTER_X - fullW / 2f
    }
    val center = unscaledLeft + fullW / 2f
    return center - width / 2f
}

private fun layoutColumn(
    items: List<XoraXmbItem>,
    focusIndex: Int,
    catTop: Float,
    catHeight: Float,
): List<XmbSlot> {
    val n = items.size
    if (n == 0) return emptyList()
    val focus = focusIndex.coerceIn(0, n - 1)
    val sizes = items.mapIndexed { i, item -> itemDesignSize(item, i == focus) }
    val tops = FloatArray(n)
    val catBottom = catTop + catHeight
    tops[focus] = catBottom + BELOW_GAP
    for (i in focus + 1 until n) {
        tops[i] = tops[i - 1] + sizes[i - 1].second + STACK_GAP
    }
    if (focus > 0) {
        var bottom = catTop - ABOVE_GAP
        for (i in focus - 1 downTo 0) {
            tops[i] = bottom - sizes[i].second
            bottom = tops[i] - STACK_GAP
        }
    }
    return items.indices.map { i ->
        val (w, h) = sizes[i]
        XmbSlot(
            left = itemLeft(items[i], w),
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

private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t.coerceIn(0f, 1f)
