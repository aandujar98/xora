package com.arcadia.shell.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arcadia.shell.designsystem.ArcadiaMotion
import com.arcadia.shell.designsystem.GlassIntensity
import com.arcadia.shell.designsystem.GlassTone
import com.arcadia.shell.designsystem.XoraFonts
import com.arcadia.shell.designsystem.arcadiaTween
import com.arcadia.shell.designsystem.liquidGlass
import com.arcadia.shell.feature.home.component.ArtworkImage
import com.arcadia.shell.feature.home.component.THUMB_DECODE_MAX_EDGE_PX
import com.arcadia.shell.model.HomeShortcut
import com.arcadia.shell.model.HomeShortcutKind

private val TraySkyTop = Color(0xFF2ACBFD)
private val TraySkyBottom = Color(0xFFDEF9FF)
private val CursorGlow = Color(0xA6D6F6FF)
private val BubbleRim = Color(0xCCF2F7FF)
private val BubbleRimSelected = Color(0xFFFFFFFF)
private val BubbleShadow = Color(0x66000000)
private val BubbleTagText = Color(0xFFFFFFFF)

@Composable
fun VitaShortcutTray(
    visible: Boolean,
    shortcuts: List<HomeShortcut>,
    selectedIndex: Int,
    editMode: Boolean,
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
    val focus = selectedIndex.coerceIn(0, (slots.lastIndex).coerceAtLeast(0))
    val rows = remember(slots) { buildVitaRows(slots) }

    AnimatedVisibility(
        visible = visible,
        enter = enter,
        exit = exit,
        modifier = modifier,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            TraySkyTop.copy(alpha = 0.95f),
                            TraySkyBottom.copy(alpha = 0.92f),
                        ),
                    ),
                ),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.26f),
                                Color.Transparent,
                            ),
                            center = Offset(640f, 280f),
                            radius = 820f,
                        ),
                    ),
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 118.dp, bottom = 128.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                rows.forEachIndexed { rowIndex, row ->
                    val yOffset = when (rowIndex) {
                        0 -> 0.dp
                        1 -> (-2).dp
                        else -> 2.dp
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = yOffset),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        row.forEach { entry ->
                            VitaBubble(
                                entry = entry,
                                selected = entry.index == focus,
                                editMode = editMode,
                                onClick = {
                                    onSelect(entry.index)
                                    when (entry.slot) {
                                        is VitaShortcutSlot.Filled -> onActivate(entry.index)
                                        VitaShortcutSlot.Add -> onAddSlot()
                                    }
                                },
                            )
                        }
                    }
                }
            }

            Text(
                text = if (editMode) "A add/remove · Select done · Y close" else "A open · Select edit · Y close",
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = XoraFonts.Secondary),
                color = Color.White.copy(alpha = 0.75f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 26.dp),
            )
        }
    }
}

private sealed class VitaShortcutSlot {
    abstract val key: String

    data class Filled(val shortcut: HomeShortcut) : VitaShortcutSlot() {
        override val key: String = shortcut.id
    }

    data object Add : VitaShortcutSlot() {
        override val key: String = "vita-add"
    }
}

private data class VitaTrayEntry(
    val index: Int,
    val slot: VitaShortcutSlot,
)

private fun buildVitaShortcutSlots(
    shortcuts: List<HomeShortcut>,
    includeAdd: Boolean,
): List<VitaShortcutSlot> {
    val items = shortcuts.map { VitaShortcutSlot.Filled(it) }
    return if (includeAdd) items + VitaShortcutSlot.Add else items
}

private fun buildVitaRows(slots: List<VitaShortcutSlot>): List<List<VitaTrayEntry>> {
    if (slots.isEmpty()) return emptyList()
    val capacities = listOf(3, 4, 3)
    val rows = mutableListOf<List<VitaTrayEntry>>()
    var cursor = 0
    capacities.forEach { cap ->
        if (cursor >= slots.size) return@forEach
        val end = (cursor + cap).coerceAtMost(slots.size)
        rows += (cursor until end).map { idx -> VitaTrayEntry(idx, slots[idx]) }
        cursor = end
    }
    while (cursor < slots.size) {
        val end = (cursor + 4).coerceAtMost(slots.size)
        rows += (cursor until end).map { idx -> VitaTrayEntry(idx, slots[idx]) }
        cursor = end
    }
    return rows
}

@Composable
private fun VitaBubble(
    entry: VitaTrayEntry,
    selected: Boolean,
    editMode: Boolean,
    onClick: () -> Unit,
) {
    val pulse = rememberInfiniteTransition(label = "vitaCursorPulse")
    val glowAlpha by pulse.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "vitaCursorAlpha",
    )
    val bubbleSize = 98.dp
    val cursorSize = 112.dp

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(cursorSize)
                .drawBehind {
                    if (selected) {
                        drawCircle(
                            color = CursorGlow.copy(alpha = glowAlpha),
                            radius = size.minDimension * 0.50f,
                        )
                    }
                    drawCircle(
                        color = BubbleShadow,
                        radius = size.minDimension * 0.30f,
                        center = Offset(size.width * 0.5f, size.height * 0.78f),
                    )
                },
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(cursorSize)
                        .border(
                            width = 2.5.dp,
                            brush = Brush.sweepGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.22f),
                                    Color.White.copy(alpha = 0.95f),
                                    Color.White.copy(alpha = 0.22f),
                                ),
                            ),
                            shape = CircleShape,
                        ),
                )
            }

            Box(
                modifier = Modifier
                    .size(bubbleSize)
                    .clip(CircleShape)
                    .shadow(
                        elevation = if (selected) 14.dp else 8.dp,
                        shape = CircleShape,
                        clip = false,
                    )
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.26f),
                                Color(0x22001828),
                            ),
                        ),
                    )
                    .border(
                        width = if (selected) 2.8.dp else 1.8.dp,
                        color = if (selected) BubbleRimSelected else BubbleRim,
                        shape = CircleShape,
                    ),
            ) {
                when (val slot = entry.slot) {
                    is VitaShortcutSlot.Filled -> {
                        ArtworkImage(
                            path = slot.shortcut.artPath,
                            contentDescription = slot.shortcut.title,
                            fallbackText = slot.shortcut.title.take(2).uppercase(),
                            contentScale = ContentScale.Crop,
                            decodeMaxEdgePx = THUMB_DECODE_MAX_EDGE_PX,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    VitaShortcutSlot.Add -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0x44253040)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "+",
                                color = Color.White.copy(alpha = 0.9f),
                                style = MaterialTheme.typography.displaySmall.copy(
                                    fontFamily = XoraFonts.Secondary,
                                    fontWeight = FontWeight.SemiBold,
                                ),
                            )
                        }
                    }
                }
            }
        }

        if (selected) {
            val label = when (val slot = entry.slot) {
                is VitaShortcutSlot.Filled -> slot.shortcut.title
                VitaShortcutSlot.Add -> if (editMode) "Add app or ROM" else "Add"
            }
            SoftwareNamePill(label = label)
        }
    }
}

@Composable
private fun SoftwareNamePill(label: String) {
    Box(
        modifier = Modifier
            .padding(top = 6.dp)
            .widthIn(min = 120.dp, max = 210.dp)
            .liquidGlass(
                shape = RoundedCornerShape(60.dp),
                tone = GlassTone.OverMedia,
                intensity = GlassIntensity.Standard,
                shimmer = true,
            )
            .border(
                width = 1.5.dp,
                color = Color.White.copy(alpha = 0.35f),
                shape = RoundedCornerShape(60.dp),
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.titleMedium.copy(
                fontFamily = XoraFonts.Secondary,
                fontSize = 30.sp,
                lineHeight = 30.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            color = BubbleTagText,
        )
    }
}
