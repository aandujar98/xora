package com.arcadia.shell.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arcadia.shell.designsystem.ArcadiaMotion
import com.arcadia.shell.designsystem.arcadiaTween
import com.arcadia.shell.designsystem.rememberReduceMotion
import com.arcadia.shell.feature.home.component.ArtworkImage
import com.arcadia.shell.feature.home.component.THUMB_DECODE_MAX_EDGE_PX
import com.arcadia.shell.model.HomeShortcut
import com.arcadia.shell.model.HomeShortcutKind
import kotlin.math.PI
import kotlin.math.sin

/** Soft Vita-inspired palette for the shortcut tray atmosphere. */
private val TraySkyTop = Color(0xFF1A3A5C)
private val TraySkyMid = Color(0xFF0E2438)
private val TraySkyBottom = Color(0xFF071018)
private val BubbleRim = Color(0xE6F2F7FF)
private val BubbleGlow = Color(0x886EB8FF)
private val FocusRing = Color(0xFF7EC8FF)
private val EditAccent = Color(0xFFE8A85C)
private val EmptyFill = Color(0x33182028)
private val LabelColor = Color(0xF2FFFFFF)

/**
 * PS Vita–style floating shortcut bubbles.
 *
 * Slides down over the XMB when opened (Y). Bubbles hold pinned Android apps / ROMs.
 * Select enters edit mode so empty slots can be filled from the app/ROM picker.
 */
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
    val reduceMotion = rememberReduceMotion()
    val enterFade = fadeIn(arcadiaTween(ArcadiaMotion.Slow))
    val exitFade = fadeOut(arcadiaTween(ArcadiaMotion.Medium))
    val enterSlide = slideInVertically(
        animationSpec = arcadiaTween(ArcadiaMotion.Slow),
        initialOffsetY = { -it },
    )
    val exitSlide = slideOutVertically(
        animationSpec = arcadiaTween(ArcadiaMotion.Medium),
        targetOffsetY = { -it },
    )

    AnimatedVisibility(
        visible = visible,
        enter = enterSlide + enterFade,
        exit = exitSlide + exitFade,
        modifier = modifier,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val trayMaxWidth = maxWidth
            // Atmospheric plate — soft Vita night-sky wash over wallpaper.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                TraySkyTop.copy(alpha = 0.88f),
                                TraySkyMid.copy(alpha = 0.82f),
                                TraySkyBottom.copy(alpha = 0.92f),
                            ),
                        ),
                    ),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0x3348A0D8),
                                Color.Transparent,
                            ),
                            center = Offset(constraints.maxWidth * 0.5f, constraints.maxHeight * 0.38f),
                            radius = constraints.maxWidth * 0.55f,
                        ),
                    ),
            )

            val includeAdd = editMode || shortcuts.isEmpty()
            val slots = remember(shortcuts, includeAdd) {
                buildVitaShortcutSlots(shortcuts, includeAdd)
            }
            val focusIndex = selectedIndex.coerceIn(0, (slots.lastIndex).coerceAtLeast(0))
            val listState = rememberLazyListState()

            LaunchedEffect(focusIndex, slots.size, visible) {
                if (!visible || slots.isEmpty()) return@LaunchedEffect
                val target = focusIndex.coerceIn(0, slots.lastIndex)
                runCatching { listState.animateScrollToItem(target) }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 56.dp, bottom = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = if (editMode) "Edit shortcuts" else "Shortcuts",
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.2.sp,
                    ),
                    color = LabelColor,
                )
                Text(
                    text = if (editMode) {
                        "A add / remove · Select done · Y close"
                    } else {
                        "A open · Select edit · Y close"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = LabelColor.copy(alpha = 0.55f),
                    modifier = Modifier.padding(top = 4.dp, bottom = 28.dp),
                )

                val bubbleSize = when {
                    trayMaxWidth < 480.dp -> 84.dp
                    trayMaxWidth < 720.dp -> 96.dp
                    else -> 108.dp
                }

                LazyRow(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    itemsIndexed(
                        items = slots,
                        key = { _, slot -> slot.key },
                    ) { index, slot ->
                        val wave = rememberWaveOffset(
                            index = index,
                            enabled = !reduceMotion && visible,
                        )
                        VitaShortcutBubble(
                            slot = slot,
                            selected = index == focusIndex,
                            editMode = editMode,
                            bubbleSize = bubbleSize,
                            waveOffsetY = wave,
                            onClick = {
                                onSelect(index)
                                when (slot) {
                                    is VitaShortcutSlot.Filled -> onActivate(index)
                                    is VitaShortcutSlot.Add -> onAddSlot()
                                }
                            },
                        )
                    }
                }

                val focused = slots.getOrNull(focusIndex)
                Text(
                    text = when (focused) {
                        is VitaShortcutSlot.Filled -> focused.shortcut.title
                        is VitaShortcutSlot.Add -> if (editMode) "Add app or ROM" else "Add shortcut"
                        null -> ""
                    },
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium),
                    color = LabelColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(horizontal = 32.dp)
                        .widthIn(max = 420.dp),
                )

                if (focused is VitaShortcutSlot.Filled) {
                    Text(
                        text = when (focused.shortcut.kind) {
                            HomeShortcutKind.AndroidApp -> "Android app"
                            HomeShortcutKind.Game -> "ROM"
                            HomeShortcutKind.Picture -> "Picture"
                            HomeShortcutKind.Gif -> "GIF"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = LabelColor.copy(alpha = 0.5f),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                } else {
                    Spacer(modifier = Modifier.height(18.dp))
                }
            }
        }
    }
}

private sealed class VitaShortcutSlot {
    abstract val key: String

    data class Filled(val shortcut: HomeShortcut) : VitaShortcutSlot() {
        override val key: String get() = shortcut.id
    }

    data object Add : VitaShortcutSlot() {
        override val key: String = "vita-add"
    }
}

private fun buildVitaShortcutSlots(
    shortcuts: List<HomeShortcut>,
    includeAdd: Boolean,
): List<VitaShortcutSlot> {
    val filled = shortcuts.map { VitaShortcutSlot.Filled(it) }
    return if (includeAdd) filled + VitaShortcutSlot.Add else filled
}

@Composable
private fun rememberWaveOffset(index: Int, enabled: Boolean): Dp {
    if (!enabled) {
        // Static Vita-like stagger even without motion.
        val phase = (index % 5) - 2
        return (phase * 6).dp
    }
    val transition = rememberInfiniteTransition(label = "vitaBubbleWave")
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2f * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4200 + (index % 3) * 280, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "vitaWavePhase",
    )
    val base = sin(t + index * 0.85f)
    return (base * 10f).dp
}

@Composable
private fun VitaShortcutBubble(
    slot: VitaShortcutSlot,
    selected: Boolean,
    editMode: Boolean,
    bubbleSize: Dp,
    waveOffsetY: Dp,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (selected) 1.12f else 1f,
        animationSpec = tween(durationMillis = ArcadiaMotion.Medium, easing = FastOutSlowInEasing),
        label = "vitaBubbleScale",
    )
    val rim = when {
        selected && editMode -> EditAccent
        selected -> FocusRing
        else -> BubbleRim.copy(alpha = 0.55f)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .offset(y = waveOffsetY)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            ),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(bubbleSize + 18.dp)
                .drawBehind {
                    if (selected) {
                        drawCircle(
                            color = BubbleGlow,
                            radius = size.minDimension * 0.48f,
                        )
                    }
                    // Soft ground shadow under the bubble (Vita silhouette).
                    drawOval(
                        color = Color.Black.copy(alpha = 0.28f),
                        topLeft = Offset(size.width * 0.18f, size.height * 0.78f),
                        size = androidx.compose.ui.geometry.Size(
                            size.width * 0.64f,
                            size.height * 0.14f,
                        ),
                    )
                },
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(bubbleSize)
                    .shadow(elevation = if (selected) 16.dp else 8.dp, shape = CircleShape)
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xFF2A3A4C),
                                Color(0xFF121820),
                            ),
                        ),
                    )
                    .border(
                        width = if (selected) 3.dp else 2.dp,
                        color = rim,
                        shape = CircleShape,
                    ),
            ) {
                when (slot) {
                    is VitaShortcutSlot.Filled -> {
                        ArtworkImage(
                            path = slot.shortcut.artPath,
                            contentDescription = slot.shortcut.title,
                            fallbackText = slot.shortcut.title.take(2).uppercase(),
                            contentScale = ContentScale.Crop,
                            decodeMaxEdgePx = THUMB_DECODE_MAX_EDGE_PX,
                            modifier = Modifier.fillMaxSize(),
                        )
                        // Soft top gloss like Vita icon glass.
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            Color.White.copy(alpha = 0.22f),
                                            Color.Transparent,
                                            Color.Black.copy(alpha = 0.18f),
                                        ),
                                    ),
                                ),
                        )
                    }
                    VitaShortcutSlot.Add -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(EmptyFill),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = "+",
                                color = LabelColor.copy(alpha = 0.85f),
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Light,
                            )
                        }
                    }
                }
            }

            if (selected) {
                Box(
                    modifier = Modifier
                        .size(bubbleSize + 10.dp)
                        .border(
                            width = 2.dp,
                            brush = Brush.sweepGradient(
                                listOf(
                                    FocusRing.copy(alpha = 0.15f),
                                    FocusRing,
                                    FocusRing.copy(alpha = 0.15f),
                                    FocusRing,
                                ),
                            ),
                            shape = CircleShape,
                        ),
                )
            }
        }
    }
}
