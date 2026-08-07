package com.arcadia.shell.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arcadia.shell.designsystem.ArcadiaMotion
import com.arcadia.shell.designsystem.XoraFonts
import com.arcadia.shell.designsystem.arcadiaTween
import com.arcadia.shell.designsystem.rememberReduceMotion
import com.arcadia.shell.feature.home.component.ArtworkImage
import com.arcadia.shell.feature.home.component.THUMB_DECODE_MAX_EDGE_PX
import com.arcadia.shell.launcher.InstalledAppSync
import com.arcadia.shell.model.HomeShortcut
import com.arcadia.shell.model.HomeShortcutKind
import kotlin.math.min

// Every measurement below is in Figma artboard units and is scaled by `unit` at layout time,
// so the tray keeps the designed proportions on any panel.
private const val BUBBLE_DIAMETER = 233.256f
private const val BUBBLE_GLASS_DIAMETER = 240.349f
private const val BUBBLE_GLASS_ROTATION = 165f
private const val COLUMN_PITCH = 379f
private const val ROW_PITCH = 253f
private const val SELECTION_RING_WIDTH = 10f
private const val NAME_PILL_HEIGHT = 96f
private const val NAME_PILL_GAP = 15.8f
private const val NAME_PILL_BORDER = 3f
private const val NAME_TEXT_SIZE = 32f
private const val PAGE_DOT_DIAMETER = 34f
private const val PAGE_DOT_PITCH = 59f
private const val PAGE_DOT_CENTER_X = 72f
private const val XMB_ROW_ICON = 52f
private const val XMB_ROW_PITCH = 94f
private const val XMB_ROW_BOTTOM = 1047.8f

/** How far a bubble may sway from its slot when the device is tilted. */
private const val TILT_SHIFT_FRACTION = 0.115f

private val VitaSkyTop = Color(0xFF2ACBFD)
private val VitaSkyBottom = Color(0xFFDEF9FF)
private val RingGradientTop = Color.White
private val RingGradientBottom = Color(0xFFB5EFFF)
private val SelectionHalo = Color(0xB3E4FAFF)
private val NamePillFill = Color(0xA6000000)
private val NamePillBorder = Color(0x59FFFFFF)
private val NamePillInnerGlow = Color(0x80FFFFFF)
private val PageDotIdle = Color(0x33FFFFFF)
private val PageDotActive = Color(0xD9FFFFFF)

// The bottom row sits on the pale end of the sky, so the glyphs read dark rather than white.
private val XmbRowActive = Color(0xE6102734)
private val XmbRowIdle = Color(0x80102734)

/** Sits under icons with transparent corners so every slot still reads as a glass bubble. */
private val BubbleFill = Color(0x4D0E2230)

/** A page holds three staggered rows, matching the bubble grid in the design. */
private val VITA_TRAY_ROW_CAPACITIES = intArrayOf(3, 4, 3)
internal const val VITA_TRAY_PAGE_SIZE = 10

/**
 * PS Vita LiveArea-style shortcut field: staggered bubbles over a wave sky, the focused bubble
 * ringed and named, page dots down the left edge, and the XMB category row peeking along the
 * bottom. Bubbles sway with the device's gyroscope.
 */
@Composable
fun VitaShortcutTray(
    visible: Boolean,
    shortcuts: List<HomeShortcut>,
    selectedIndex: Int,
    editMode: Boolean,
    xmbCategoryIndex: Int,
    onSelect: (Int) -> Unit,
    onActivate: (Int) -> Unit,
    onAddSlot: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val enter = slideInVertically(
        animationSpec = arcadiaTween(ArcadiaMotion.Slow),
        initialOffsetY = { -it / 3 },
    ) + fadeIn(arcadiaTween(ArcadiaMotion.Slow))
    val exit = slideOutVertically(
        animationSpec = arcadiaTween(ArcadiaMotion.Medium),
        targetOffsetY = { -it / 3 },
    ) + fadeOut(arcadiaTween(ArcadiaMotion.Medium))

    val includeAdd = editMode || shortcuts.isEmpty()
    val slots = remember(shortcuts, includeAdd) { buildVitaShortcutSlots(shortcuts, includeAdd) }
    val focus = selectedIndex.coerceIn(0, slots.lastIndex.coerceAtLeast(0))
    val pageCount = vitaTrayPageCount(slots.size)
    val page = (focus / VITA_TRAY_PAGE_SIZE).coerceIn(0, pageCount - 1)
    val rows = remember(slots.size, page) { vitaTrayPageRows(slots.size, page) }

    AnimatedVisibility(
        visible = visible,
        enter = enter,
        exit = exit,
        modifier = modifier,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            WaveSky(
                topColor = VitaSkyTop,
                bottomColor = VitaSkyBottom,
                field = VitaWaveField,
                modifier = Modifier.fillMaxSize(),
            )

            val unit = min(
                maxWidth.value / XORA_DESIGN_WIDTH,
                maxHeight.value / XORA_DESIGN_HEIGHT,
            )
            val bubbleDiameter = (BUBBLE_DIAMETER * unit).dp
            val glass = ImageBitmap.imageResource(R.drawable.vita_bubble_glass)
            val reduceMotion = rememberReduceMotion()
            val tilt = rememberDeviceTilt(active = visible && !reduceMotion)
            val motion = rememberVitaBubbleMotion(
                count = slots.size,
                tilt = tilt,
                maxShiftPx = with(LocalDensity.current) {
                    bubbleDiameter.toPx() * TILT_SHIFT_FRACTION
                },
                enabled = visible && !reduceMotion,
            )

            rows.forEachIndexed { rowIndex, row ->
                val rowShift = (rowIndex - ((rows.size - 1) / 2f)) * ROW_PITCH
                row.forEachIndexed { column, slotIndex ->
                    val columnShift = (column - ((row.size - 1) / 2f)) * COLUMN_PITCH
                    VitaBubble(
                        slot = slots[slotIndex],
                        selected = slotIndex == focus,
                        diameter = bubbleDiameter,
                        glass = glass,
                        offsetProvider = { motion.offsetAt(slotIndex) },
                        onClick = {
                            onSelect(slotIndex)
                            when (slots[slotIndex]) {
                                is VitaShortcutSlot.Filled -> onActivate(slotIndex)
                                VitaShortcutSlot.Add -> onAddSlot()
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.Center)
                            .offset(x = (columnShift * unit).dp, y = (rowShift * unit).dp),
                    )
                }
            }

            // Drawn after every bubble so the focused name always reads over its neighbours.
            rows.forEachIndexed { rowIndex, row ->
                val focusColumn = row.indexOf(focus)
                if (focusColumn < 0) return@forEachIndexed
                val rowShift = (rowIndex - ((rows.size - 1) / 2f)) * ROW_PITCH
                val columnShift = (focusColumn - ((row.size - 1) / 2f)) * COLUMN_PITCH
                val pillCentre = rowShift + (BUBBLE_DIAMETER / 2f) + NAME_PILL_GAP +
                    (NAME_PILL_HEIGHT / 2f)
                SoftwareNamePill(
                    label = slots[focus].label(editMode),
                    unit = unit,
                    minWidth = bubbleDiameter,
                    offsetProvider = { motion.offsetAt(focus) },
                    modifier = Modifier
                        .align(Alignment.Center)
                        .offset(x = (columnShift * unit).dp, y = (pillCentre * unit).dp),
                )
            }

            PageDots(
                count = pageCount,
                current = page,
                unit = unit,
                modifier = Modifier.align(Alignment.CenterStart),
            )

            XmbCategoryRow(
                selectedIndex = xmbCategoryIndex,
                unit = unit,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = ((XORA_DESIGN_HEIGHT - XMB_ROW_BOTTOM) * unit).dp),
            )
        }
    }
}

@Composable
private fun VitaBubble(
    slot: VitaShortcutSlot,
    selected: Boolean,
    diameter: Dp,
    glass: ImageBitmap,
    offsetProvider: () -> Offset,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ringWidth = diameter * (SELECTION_RING_WIDTH / BUBBLE_DIAMETER)
    Box(
        modifier = modifier
            .size(diameter + ringWidth)
            .graphicsLayer {
                val shift = offsetProvider()
                translationX = shift.x
                translationY = shift.y
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(diameter)
                .drawBehind {
                    if (!selected) return@drawBehind
                    val haloRadius = size.minDimension * 0.68f
                    drawCircle(
                        brush = Brush.radialGradient(
                            colorStops = arrayOf(
                                0.52f to SelectionHalo,
                                1f to Color.Transparent,
                            ),
                            center = center,
                            radius = haloRadius,
                        ),
                        radius = haloRadius,
                    )
                }
                .shadow(
                    elevation = if (selected) 18.dp else 10.dp,
                    shape = CircleShape,
                    clip = false,
                )
                .clip(CircleShape)
                // Isolated so the glass sheen blends against the icon, not the sky behind it.
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .background(BubbleFill)
                .drawWithContent {
                    drawContent()
                    withTransform({
                        rotate(BUBBLE_GLASS_ROTATION)
                        val factor = (size.width * (BUBBLE_GLASS_DIAMETER / BUBBLE_DIAMETER)) /
                            glass.width
                        scale(factor, factor)
                    }) {
                        drawImage(
                            image = glass,
                            topLeft = Offset(
                                (size.width - glass.width) / 2f,
                                (size.height - glass.height) / 2f,
                            ),
                            blendMode = BlendMode.Overlay,
                        )
                    }
                },
        ) {
            when (slot) {
                is VitaShortcutSlot.Filled -> ArtworkImage(
                    path = slot.artworkPath(),
                    contentDescription = slot.shortcut.title,
                    fallbackText = slot.shortcut.title.take(2).uppercase(),
                    contentScale = ContentScale.Crop,
                    decodeMaxEdgePx = THUMB_DECODE_MAX_EDGE_PX,
                    modifier = Modifier.fillMaxSize(),
                )
                VitaShortcutSlot.Add -> Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x40FFFFFF)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "+",
                        color = Color.White,
                        style = MaterialTheme.typography.displaySmall.copy(
                            fontFamily = XoraFonts.Secondary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = with(LocalDensity.current) { (diameter * 0.42f).toSp() },
                        ),
                    )
                }
            }
        }

        if (selected) {
            Box(
                modifier = Modifier
                    .size(diameter + ringWidth)
                    .drawBehind {
                        drawCircle(
                            brush = Brush.verticalGradient(
                                listOf(RingGradientTop, RingGradientBottom),
                            ),
                            radius = (size.minDimension - ringWidth.toPx()) / 2f,
                            style = Stroke(width = ringWidth.toPx()),
                        )
                    },
            )
        }
    }
}

/** Focused software title: dark plate, specular rim, and the inner bloom from the design. */
@Composable
private fun SoftwareNamePill(
    label: String,
    unit: Float,
    minWidth: Dp,
    offsetProvider: () -> Offset,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(percent = 50)
    val textSize = with(LocalDensity.current) { (NAME_TEXT_SIZE * unit).dp.toSp() }
    Box(
        modifier = modifier
            .graphicsLayer {
                val shift = offsetProvider()
                translationX = shift.x
                translationY = shift.y
            }
            .widthIn(min = minWidth, max = minWidth * 2.2f)
            .clip(shape)
            .background(NamePillFill)
            // Stands in for the design's inset white bloom, which has no Compose equivalent.
            .drawBehind {
                drawRect(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to NamePillInnerGlow,
                            0.45f to Color.Transparent,
                            0.55f to Color.Transparent,
                            1f to NamePillInnerGlow,
                        ),
                    ),
                )
            }
            .border(width = (NAME_PILL_BORDER * unit).dp, color = NamePillBorder, shape = shape)
            .padding(
                horizontal = (28f * unit).dp,
                vertical = ((NAME_PILL_HEIGHT - NAME_TEXT_SIZE) / 2f * unit).dp,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = XoraFonts.Secondary,
                fontSize = textSize,
                lineHeight = textSize,
                fontWeight = FontWeight.SemiBold,
            ),
            color = Color.White,
        )
    }
}

@Composable
private fun PageDots(
    count: Int,
    current: Int,
    unit: Float,
    modifier: Modifier = Modifier,
) {
    if (count <= 1) return
    val diameter = (PAGE_DOT_DIAMETER * unit).dp
    Column(
        modifier = modifier.offset(
            x = ((PAGE_DOT_CENTER_X - (PAGE_DOT_DIAMETER / 2f)) * unit).dp,
        ),
        verticalArrangement = Arrangement.spacedBy(((PAGE_DOT_PITCH - PAGE_DOT_DIAMETER) * unit).dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        repeat(count) { index ->
            Box(
                modifier = Modifier
                    .size(diameter)
                    .clip(CircleShape)
                    .background(if (index == current) PageDotActive else PageDotIdle),
            )
        }
    }
}

/** The XMB categories waiting behind the tray, shown along the bottom edge as in the design. */
@Composable
private fun XmbCategoryRow(
    selectedIndex: Int,
    unit: Float,
    modifier: Modifier = Modifier,
) {
    val categories = XoraXmbCategory.entries
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(((XMB_ROW_PITCH - XMB_ROW_ICON) * unit).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        categories.forEachIndexed { index, category ->
            XmbVectorIcon(
                icon = category.toXmbIcon(),
                tint = if (index == selectedIndex) XmbRowActive else XmbRowIdle,
                size = (XMB_ROW_ICON * unit).dp,
                outlined = false,
            )
        }
    }
}

private sealed class VitaShortcutSlot {
    data class Filled(val shortcut: HomeShortcut) : VitaShortcutSlot()

    data object Add : VitaShortcutSlot()
}

private fun VitaShortcutSlot.label(editMode: Boolean): String = when (this) {
    is VitaShortcutSlot.Filled -> shortcut.title
    VitaShortcutSlot.Add -> if (editMode) "Add app or ROM" else "Add"
}

/** Pinned Android apps carry no artwork of their own; fall back to the launcher icon. */
private fun VitaShortcutSlot.Filled.artworkPath(): String? = shortcut.artPath
    ?: shortcut.target.takeIf { shortcut.kind == HomeShortcutKind.AndroidApp }
        ?.let { "${InstalledAppSync.ICON_SCHEME}$it" }

private fun buildVitaShortcutSlots(
    shortcuts: List<HomeShortcut>,
    includeAdd: Boolean,
): List<VitaShortcutSlot> {
    val items = shortcuts.map { VitaShortcutSlot.Filled(it) }
    return if (includeAdd) items + VitaShortcutSlot.Add else items
}

internal fun vitaTrayPageCount(slotCount: Int): Int =
    if (slotCount <= 0) 1 else ((slotCount + VITA_TRAY_PAGE_SIZE - 1) / VITA_TRAY_PAGE_SIZE)

/**
 * Slot indices for [page], grouped into the staggered rows the design uses. Indices are absolute
 * so callers can address a slot without knowing which page it lives on.
 */
internal fun vitaTrayPageRows(slotCount: Int, page: Int): List<List<Int>> {
    val start = page * VITA_TRAY_PAGE_SIZE
    val end = min(start + VITA_TRAY_PAGE_SIZE, slotCount)
    if (start >= end) return emptyList()
    val rows = mutableListOf<List<Int>>()
    var cursor = start
    for (capacity in VITA_TRAY_ROW_CAPACITIES) {
        if (cursor >= end) break
        val rowEnd = min(cursor + capacity, end)
        rows += (cursor until rowEnd).toList()
        cursor = rowEnd
    }
    return rows
}
