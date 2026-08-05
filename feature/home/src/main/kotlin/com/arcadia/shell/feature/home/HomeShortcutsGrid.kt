package com.arcadia.shell.feature.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.arcadia.shell.datastore.DEFAULT_HOME_SHORTCUT_GRID_COLUMNS
import com.arcadia.shell.datastore.DEFAULT_HOME_SHORTCUT_GRID_ROWS
import com.arcadia.shell.datastore.MAX_HOME_SHORTCUT_GRID_COLUMNS
import com.arcadia.shell.datastore.MAX_HOME_SHORTCUT_GRID_ROWS
import com.arcadia.shell.datastore.MIN_HOME_SHORTCUT_GRID_COLUMNS
import com.arcadia.shell.datastore.MIN_HOME_SHORTCUT_GRID_ROWS
import com.arcadia.shell.designsystem.ArcadiaMotion
import com.arcadia.shell.designsystem.arcadiaTween
import com.arcadia.shell.feature.home.component.ArtworkImage
import com.arcadia.shell.feature.home.component.THUMB_DECODE_MAX_EDGE_PX
import com.arcadia.shell.launcher.InstalledAppSync
import com.arcadia.shell.model.HomeShortcut
import com.arcadia.shell.model.HomeShortcutKind
import kotlin.math.max
import kotlin.math.roundToInt

private val PanelShape = RoundedCornerShape(28.dp)
private val TileShape = RoundedCornerShape(18.dp)
private val FocusBorder = Color(0xFF6EB8FF)
private val EditBorder = Color(0xFFE8A85C)
private val PanelFill = Color(0xE6181E28)
private val Gap = 10.dp
private val PanelPadding = 12.dp
private val MinCell = 72.dp

/**
 * Smash/Switch-inspired customizable shortcut grid (Home page 2).
 *
 * Controllers navigate with D-pad; A opens; Select opens customize (cols/rows + tile span).
 * Cell size = hub area ÷ [columns] / [rows] so coarser boards yield larger icons.
 */
@Composable
fun HomeShortcutsGrid(
    shortcuts: List<HomeShortcut>,
    selectedIndex: Int,
    editMode: Boolean,
    onSelect: (Int) -> Unit,
    onActivate: (Int) -> Unit,
    onAddSlot: () -> Unit,
    onCycleSpan: ((Int) -> Unit)? = null,
    columns: Int = DEFAULT_HOME_SHORTCUT_GRID_COLUMNS,
    rows: Int = DEFAULT_HOME_SHORTCUT_GRID_ROWS,
    customizeChrome: ShortcutCustomizeChrome = ShortcutCustomizeChrome.Tiles,
    onAdjustColumns: ((Int) -> Unit)? = null,
    onAdjustRows: ((Int) -> Unit)? = null,
    onFocusCustomizeChrome: ((ShortcutCustomizeChrome) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val gridColumns = columns.coerceIn(MIN_HOME_SHORTCUT_GRID_COLUMNS, MAX_HOME_SHORTCUT_GRID_COLUMNS)
    val preferredRows = rows.coerceIn(MIN_HOME_SHORTCUT_GRID_ROWS, MAX_HOME_SHORTCUT_GRID_ROWS)
    val includeAdd = editMode || shortcuts.isEmpty()
    val totalSlots = (shortcuts.size + if (includeAdd) 1 else 0).coerceAtLeast(1)
    val focusIndex = selectedIndex.coerceIn(0, (totalSlots - 1).coerceAtLeast(0))
    val tilesFocused = !editMode || customizeChrome == ShortcutCustomizeChrome.Tiles

    val placements = remember(shortcuts, includeAdd, gridColumns) {
        packShortcutPlacements(shortcuts, includeAddSlot = includeAdd, columns = gridColumns)
    }
    val packedRows = remember(placements) {
        placements.maxOfOrNull { it.row + it.rowSpan }?.coerceAtLeast(1) ?: 1
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = when {
                editMode -> "Customize · L/R adjust · Select cycles size · A removes · B done"
                else -> "Shortcuts · Select to customize"
            },
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.padding(bottom = 8.dp, start = 4.dp),
        )

        if (editMode) {
            ShortcutCustomizeChromeBar(
                columns = gridColumns,
                rows = preferredRows,
                focused = customizeChrome,
                onFocus = { onFocusCustomizeChrome?.invoke(it) },
                onAdjustColumns = { delta -> onAdjustColumns?.invoke(delta) },
                onAdjustRows = { delta -> onAdjustRows?.invoke(delta) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
            )
        }

        // Constraints are read here, so the scroll modifier must live on the inner viewport: a
        // scrollable BoxWithConstraints reports an infinite maxHeight, which makes every derived
        // cell size infinite and crashes when the board asks for that height.
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(PanelShape)
                .background(PanelFill)
                .padding(PanelPadding),
        ) {
            val density = LocalDensity.current
            val gapPx = with(density) { Gap.toPx() }
            val availableHeight = if (maxHeight == Dp.Infinity) {
                MinCell * preferredRows + Gap * (preferredRows - 1).coerceAtLeast(0)
            } else {
                maxHeight
            }
            val cellWidth = ((maxWidth - Gap * (gridColumns - 1)) / gridColumns)
                .coerceAtLeast(MinCell)
            val cellHeight = ((availableHeight - Gap * (preferredRows - 1)) / preferredRows)
                .coerceAtLeast(MinCell)
            val cellWPx = with(density) { cellWidth.toPx() }
            val cellHPx = with(density) { cellHeight.toPx() }
            val boardHeight = cellHeight * packedRows + Gap * (packedRows - 1).coerceAtLeast(0)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(boardHeight),
                ) {
                    placements.forEach { placement ->
                        val w = cellWidth * placement.colSpan +
                            Gap * (placement.colSpan - 1).coerceAtLeast(0)
                        val h = cellHeight * placement.rowSpan +
                            Gap * (placement.rowSpan - 1).coerceAtLeast(0)
                        val xPx = placement.col * (cellWPx + gapPx)
                        val yPx = placement.row * (cellHPx + gapPx)
                        val focused = tilesFocused && placement.index == focusIndex

                        Box(
                            modifier = Modifier
                                .offset { IntOffset(xPx.roundToInt(), yPx.roundToInt()) }
                                .size(w, h),
                        ) {
                            when (val content = placement.content) {
                                is ShortcutSlotContent.Shortcut -> ShortcutTile(
                                    shortcut = content.shortcut,
                                    focused = focused,
                                    editMode = editMode,
                                    onClick = {
                                        onFocusCustomizeChrome?.invoke(ShortcutCustomizeChrome.Tiles)
                                        onSelect(placement.index)
                                        onActivate(placement.index)
                                    },
                                    onLongClickResize = onCycleSpan?.let { cycle ->
                                        { cycle(placement.index) }
                                    },
                                )
                                ShortcutSlotContent.Add -> AddShortcutTile(
                                    focused = focused || shortcuts.isEmpty(),
                                    onClick = {
                                        onFocusCustomizeChrome?.invoke(ShortcutCustomizeChrome.Tiles)
                                        onSelect(placement.index)
                                        onAddSlot()
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ShortcutCustomizeChromeBar(
    columns: Int,
    rows: Int,
    focused: ShortcutCustomizeChrome,
    onFocus: (ShortcutCustomizeChrome) -> Unit,
    onAdjustColumns: (Int) -> Unit,
    onAdjustRows: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CustomizeStepper(
            label = "Columns",
            value = columns,
            focused = focused == ShortcutCustomizeChrome.Columns,
            onFocus = { onFocus(ShortcutCustomizeChrome.Columns) },
            onMinus = { onAdjustColumns(-1) },
            onPlus = { onAdjustColumns(1) },
            modifier = Modifier.weight(1f),
        )
        CustomizeStepper(
            label = "Rows",
            value = rows,
            focused = focused == ShortcutCustomizeChrome.Rows,
            onFocus = { onFocus(ShortcutCustomizeChrome.Rows) },
            onMinus = { onAdjustRows(-1) },
            onPlus = { onAdjustRows(1) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CustomizeStepper(
    label: String,
    value: Int,
    focused: Boolean,
    onFocus: () -> Unit,
    onMinus: () -> Unit,
    onPlus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val border = if (focused) EditBorder else Color.White.copy(alpha = 0.18f)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xCC101820))
            .border(width = if (focused) 2.dp else 1.dp, color = border, shape = RoundedCornerShape(14.dp))
            .clickable(onClick = onFocus)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            color = Color.White.copy(alpha = 0.75f),
            style = MaterialTheme.typography.labelLarge,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "◂",
                color = if (focused) EditBorder else Color.White.copy(alpha = 0.55f),
                modifier = Modifier.clickable(onClick = onMinus),
            )
            Text(
                text = value.toString(),
                color = Color.White,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            )
            Text(
                text = "▸",
                color = if (focused) EditBorder else Color.White.copy(alpha = 0.55f),
                modifier = Modifier.clickable(onClick = onPlus),
            )
        }
    }
}

internal data class ShortcutPlacement(
    val index: Int,
    val col: Int,
    val row: Int,
    val colSpan: Int,
    val rowSpan: Int,
    val content: ShortcutSlotContent,
)

internal sealed class ShortcutSlotContent {
    data class Shortcut(val shortcut: HomeShortcut) : ShortcutSlotContent()
    data object Add : ShortcutSlotContent()
}

/**
 * Packs shortcuts (and optional add tile) into a [columns]-wide board.
 * Empty cells are left as negative space — Smash-style.
 */
internal fun packShortcutPlacements(
    shortcuts: List<HomeShortcut>,
    includeAddSlot: Boolean,
    columns: Int = DEFAULT_HOME_SHORTCUT_GRID_COLUMNS,
): List<ShortcutPlacement> {
    val occupied = mutableSetOf<Long>()
    fun key(col: Int, row: Int) = (row.toLong() shl 32) or (col.toLong() and 0xffffffffL)

    fun canPlace(col: Int, row: Int, colSpan: Int, rowSpan: Int): Boolean {
        if (col + colSpan > columns) return false
        for (r in row until row + rowSpan) {
            for (c in col until col + colSpan) {
                if (key(c, r) in occupied) return false
            }
        }
        return true
    }

    fun mark(col: Int, row: Int, colSpan: Int, rowSpan: Int) {
        for (r in row until row + rowSpan) {
            for (c in col until col + colSpan) {
                occupied += key(c, r)
            }
        }
    }

    fun findSlot(colSpan: Int, rowSpan: Int): Pair<Int, Int> {
        var row = 0
        while (true) {
            for (col in 0..(columns - colSpan)) {
                if (canPlace(col, row, colSpan, rowSpan)) return col to row
            }
            row++
            if (row > 256) return 0 to 0
        }
    }

    val result = ArrayList<ShortcutPlacement>(shortcuts.size + if (includeAddSlot) 1 else 0)
    shortcuts.forEachIndexed { index, shortcut ->
        val span = shortcut.span
        val colSpan = span.colSpan.coerceIn(1, columns)
        val rowSpan = span.rowSpan.coerceAtLeast(1)
        val (col, row) = findSlot(colSpan, rowSpan)
        mark(col, row, colSpan, rowSpan)
        result += ShortcutPlacement(
            index = index,
            col = col,
            row = row,
            colSpan = colSpan,
            rowSpan = rowSpan,
            content = ShortcutSlotContent.Shortcut(shortcut),
        )
    }
    if (includeAddSlot) {
        val (col, row) = findSlot(1, 1)
        mark(col, row, 1, 1)
        result += ShortcutPlacement(
            index = shortcuts.size,
            col = col,
            row = row,
            colSpan = 1,
            rowSpan = 1,
            content = ShortcutSlotContent.Add,
        )
    }
    return result
}

/**
 * Finds the nearest packed tile in [direction] from [fromIndex] (Smash-style spatial nav).
 * Returns [fromIndex] when no neighbor exists in that direction.
 */
internal fun findNeighborShortcutIndex(
    placements: List<ShortcutPlacement>,
    fromIndex: Int,
    direction: ShortcutNavDirection,
): Int {
    val from = placements.firstOrNull { it.index == fromIndex } ?: return fromIndex
    val fromCx = from.col + from.colSpan / 2f
    val fromCy = from.row + from.rowSpan / 2f
    val fromLeft = from.col.toFloat()
    val fromRight = (from.col + from.colSpan).toFloat()
    val fromTop = from.row.toFloat()
    val fromBottom = (from.row + from.rowSpan).toFloat()

    var bestIndex = fromIndex
    var bestScore = Float.MAX_VALUE

    placements.forEach { candidate ->
        if (candidate.index == fromIndex) return@forEach
        val cx = candidate.col + candidate.colSpan / 2f
        val cy = candidate.row + candidate.rowSpan / 2f
        val left = candidate.col.toFloat()
        val right = (candidate.col + candidate.colSpan).toFloat()
        val top = candidate.row.toFloat()
        val bottom = (candidate.row + candidate.rowSpan).toFloat()

        val inDirection = when (direction) {
            ShortcutNavDirection.Left -> right <= fromLeft + 0.01f
            ShortcutNavDirection.Right -> left >= fromRight - 0.01f
            ShortcutNavDirection.Up -> bottom <= fromTop + 0.01f
            ShortcutNavDirection.Down -> top >= fromBottom - 0.01f
        }
        if (!inDirection) return@forEach

        val primary = when (direction) {
            ShortcutNavDirection.Left -> fromCx - cx
            ShortcutNavDirection.Right -> cx - fromCx
            ShortcutNavDirection.Up -> fromCy - cy
            ShortcutNavDirection.Down -> cy - fromCy
        }
        if (primary < 0f) return@forEach
        val secondary = when (direction) {
            ShortcutNavDirection.Left, ShortcutNavDirection.Right -> kotlin.math.abs(cy - fromCy)
            ShortcutNavDirection.Up, ShortcutNavDirection.Down -> kotlin.math.abs(cx - fromCx)
        }
        val score = primary * 1000f + secondary
        if (score < bestScore) {
            bestScore = score
            bestIndex = candidate.index
        }
    }
    return bestIndex
}

internal enum class ShortcutNavDirection { Left, Right, Up, Down }

@Composable
private fun ShortcutTile(
    shortcut: HomeShortcut,
    focused: Boolean,
    editMode: Boolean,
    onClick: () -> Unit,
    onLongClickResize: (() -> Unit)? = null,
) {
    val borderColor by animateColorAsState(
        targetValue = when {
            editMode && focused -> EditBorder
            focused -> FocusBorder
            else -> Color.White.copy(alpha = 0.14f)
        },
        animationSpec = arcadiaTween(ArcadiaMotion.Fast),
        label = "shortcutBorder",
    )
    val art = shortcut.artPath ?: shortcut.target.takeIf {
        shortcut.kind == HomeShortcutKind.Picture || shortcut.kind == HomeShortcutKind.Gif
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(TileShape)
            .background(Color(0xCC141A22))
            .then(
                if (focused && !editMode) {
                    Modifier.drawBehind {
                        val stroke = 3.dp.toPx()
                        val glow = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF5CFFE7),
                                Color(0xFFFF5CE7),
                                Color(0xFFFFE75C),
                                Color(0xFF5CFFE7),
                            ),
                        )
                        drawRoundRect(
                            brush = glow,
                            topLeft = Offset(stroke / 2f, stroke / 2f),
                            size = Size(size.width - stroke, size.height - stroke),
                            cornerRadius = CornerRadius(18.dp.toPx(), 18.dp.toPx()),
                            style = Stroke(width = stroke),
                        )
                    }
                } else {
                    Modifier.border(
                        width = if (focused) 2.5.dp else 1.dp,
                        color = borderColor,
                        shape = TileShape,
                    )
                },
            )
            .clickable(onClick = onClick),
    ) {
        ArtworkImage(
            path = when (shortcut.kind) {
                HomeShortcutKind.AndroidApp ->
                    "${InstalledAppSync.ICON_SCHEME}${shortcut.target}"
                HomeShortcutKind.Game -> art
                HomeShortcutKind.Picture, HomeShortcutKind.Gif -> art
            },
            contentDescription = shortcut.title,
            fallbackText = shortcut.title,
            contentScale = ContentScale.Crop,
            decodeMaxEdgePx = max(
                THUMB_DECODE_MAX_EDGE_PX,
                THUMB_DECODE_MAX_EDGE_PX * max(shortcut.span.colSpan, shortcut.span.rowSpan) / 2,
            ),
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.55f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.72f),
                    ),
                ),
        )
        Text(
            text = shortcut.title,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp),
        )
        if (editMode) {
            Text(
                text = shortcut.span.label,
                color = EditBorder.copy(alpha = 0.95f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.45f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .then(
                        if (onLongClickResize != null) {
                            Modifier.clickable(onClick = onLongClickResize)
                        } else {
                            Modifier
                        },
                    ),
            )
            Text(
                text = "✕",
                color = EditBorder,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp),
            )
        }
    }
}

@Composable
private fun AddShortcutTile(
    focused: Boolean,
    onClick: () -> Unit,
) {
    val border = if (focused) FocusBorder else Color.White.copy(alpha = 0.2f)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(TileShape)
            .background(Color(0x99101820))
            .border(width = if (focused) 2.5.dp else 1.dp, color = border, shape = TileShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "+",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White.copy(alpha = 0.85f),
            )
            Text(
                text = "Add",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.65f),
            )
        }
    }
}

/** @suppress Kept for call sites that still reference the old constant name. */
@Deprecated("Use DEFAULT_HOME_SHORTCUT_GRID_COLUMNS", ReplaceWith("DEFAULT_HOME_SHORTCUT_GRID_COLUMNS"))
internal const val HOME_SHORTCUT_GRID_COLUMNS = DEFAULT_HOME_SHORTCUT_GRID_COLUMNS
