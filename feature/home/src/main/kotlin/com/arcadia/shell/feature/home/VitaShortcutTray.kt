package com.arcadia.shell.feature.home

import androidx.compose.animation.AnimatedContent
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
import androidx.compose.animation.togetherWith
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import com.arcadia.shell.designsystem.rememberReduceMotion
import com.arcadia.shell.designsystem.supportsGlassBlurEffect
import com.arcadia.shell.input.UiOneShot
import kotlin.math.exp
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.arcadia.shell.designsystem.ArcadiaMotion
import com.arcadia.shell.designsystem.XoraFonts
import com.arcadia.shell.designsystem.XoraSwipeDirection
import com.arcadia.shell.designsystem.arcadiaTween
import com.arcadia.shell.designsystem.inverted
import com.arcadia.shell.designsystem.rememberAmbientMotionActive
import com.arcadia.shell.designsystem.rememberThrottledAmbientUnit
import com.arcadia.shell.designsystem.xoraSwipeNavigate
import com.arcadia.shell.feature.home.component.ArtworkImage
import com.arcadia.shell.feature.home.component.THUMB_DECODE_MAX_EDGE_PX
import com.arcadia.shell.launcher.InstalledAppSync
import com.arcadia.shell.model.HomeShortcut
import com.arcadia.shell.model.HomeShortcutKind
import kotlin.math.min
import kotlin.math.roundToInt

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

/** How far a page-turn bounce hops, as a fraction of the bubble diameter. */
private const val BUBBLE_JIGGLE_LIFT = 0.10f

private val RingGradientTop = Color.White
private val RingGradientBottom = Color(0xFFB5EFFF)
private val SelectionHalo = Color(0xB3E4FAFF)
private val NamePillFill = Color(0xA6000000)
private val NamePillBorder = Color(0x59FFFFFF)
private val NamePillInnerGlow = Color(0x80FFFFFF)
private val PageDotIdle = Color(0x33FFFFFF)
private val PageDotActive = Color(0xD9FFFFFF)

/** Sits under icons with transparent corners so every slot still reads as a glass bubble. */
private val BubbleFill = Color(0x4D0E2230)

/** A page holds three staggered rows, matching the bubble grid in the design. */
private val VITA_TRAY_ROW_CAPACITIES = intArrayOf(3, 4, 3)
internal const val VITA_TRAY_PAGE_SIZE = 10
private const val VitaBubbleFlipDeg = 360f
internal const val VitaBubbleDepartMs = 1_000
/** End scale so a 233u bubble covers a 1920u panel and keeps going into the wallpaper. */
private const val VitaBubbleZoom = 11f
/** Flip occupies the first half-second; zoom occupies the second. */
private const val VitaTwirlEnd = 0.5f
private const val VitaZoomStart = 0.5f
private const val VitaBubbleFadeStart = 0.75f
private val VitaBubbleDepartEasing = LinearEasing
private val VitaBubbleEchoLags = FloatArray(24) { i ->
    val t = (i + 1f) / 24f
    t * t * 0.28f
}

private fun vitaTwirl(t: Float): Float =
    FastOutSlowInEasing.transform((t / VitaTwirlEnd).coerceIn(0f, 1f))

private fun vitaZoom(t: Float): Float =
    FastOutSlowInEasing.transform(
        ((t - VitaZoomStart) / (1f - VitaZoomStart)).coerceIn(0f, 1f),
    )

/**
 * PS Vita LiveArea-style shortcut field: staggered bubbles over the live wallpaper (no tray
 * backdrop), the focused bubble ringed and named, page dots down the left edge. Opens with a
 * slide-down; each bubble then lands on its own slightly staggered bounce. Closes by sliding
 * up. Pages move vertically. Bubbles sway with the device's gyroscope.
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
    onPageSwipe: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
    departingIndex: Int? = null,
    suppressIdleBubbles: Boolean = false,
    /** Bubble picked up for repositioning, or null when nothing is being moved. */
    moveIndex: Int? = null,
    onBeginMove: (Int) -> Unit = {},
    onMoveTo: (Int) -> Unit = {},
    onDropMove: () -> Unit = {},
) {
    val enter = slideInVertically(
        animationSpec = arcadiaTween(ArcadiaMotion.Medium),
        initialOffsetY = { -it },
    ) + fadeIn(arcadiaTween(ArcadiaMotion.Medium))
    val exit = slideOutVertically(
        animationSpec = arcadiaTween(ArcadiaMotion.Medium),
        targetOffsetY = { -it },
    ) + fadeOut(arcadiaTween(ArcadiaMotion.Medium))

    val includeAdd = editMode || shortcuts.isEmpty()
    val slots = remember(shortcuts, includeAdd) { buildVitaShortcutSlots(shortcuts, includeAdd) }
    val focus = selectedIndex.coerceIn(0, slots.lastIndex.coerceAtLeast(0))
    val pageCount = vitaTrayPageCount(slots.size)
    val page = (focus / VITA_TRAY_PAGE_SIZE).coerceIn(0, pageCount - 1)

    AnimatedVisibility(
        visible = visible,
        enter = enter,
        exit = exit,
        modifier = modifier,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .xoraSwipeNavigate(
                    enabled = departingIndex == null && !suppressIdleBubbles,
                    horizontal = false,
                    vertical = true,
                    onSwipe = { direction ->
                        when (direction.inverted()) {
                            XoraSwipeDirection.Down -> onPageSwipe(1)
                            XoraSwipeDirection.Up -> onPageSwipe(-1)
                            else -> Unit
                        }
                    },
                ),
        ) {
            val unit = min(
                maxWidth.value / XORA_DESIGN_WIDTH,
                maxHeight.value / XORA_DESIGN_HEIGHT,
            )
            val bubbleDiameter = (BUBBLE_DIAMETER * unit).dp
            val glass = ImageBitmap.imageResource(R.drawable.vita_bubble_glass)
            // Also drops the sensor when the shell is backgrounded with the tray still open.
            val sway = visible && rememberAmbientMotionActive()
            val tilt = rememberDeviceTilt(active = sway)
            val density = LocalDensity.current
            val bubblePx = with(density) { bubbleDiameter.toPx() }
            val motion = rememberVitaBubbleMotion(
                count = slots.size,
                tilt = tilt,
                maxShiftPx = bubblePx * VITA_BUBBLE_TILT_SHIFT_FRACTION,
                enabled = sway,
            )
            val landing = rememberVitaBubbleLanding(
                count = slots.size,
                dropPx = bubblePx * 0.42f,
            )
            // Page turns hop the field up and down. Keyed on the focused page, so moving the
            // cursor inside a page leaves the bubbles alone.
            val jiggle = rememberVitaBubbleJiggle(
                count = slots.size,
                page = page,
                enabled = sway,
            )
            val idleRock = rememberThrottledAmbientUnit(cycleMs = VITA_BUBBLE_ROCK_CYCLE_MS)

            // Finger travel for the bubble currently picked up; dropped back to zero on release
            // and whenever the move ends from the controller.
            var dragOffset by remember { mutableStateOf(Offset.Zero) }
            LaunchedEffect(moveIndex) {
                if (moveIndex == null) dragOffset = Offset.Zero
            }

            val pageSlide = tween<IntOffset>(ArcadiaMotion.Medium)
            val pageFade = tween<Float>(ArcadiaMotion.Fast)
            val crowdAlpha by animateFloatAsState(
                targetValue = if (departingIndex != null || suppressIdleBubbles) 0f else 1f,
                animationSpec = arcadiaTween(ArcadiaMotion.Fast),
                label = "vitaCrowdHide",
            )
            AnimatedContent(
                targetState = page,
                transitionSpec = {
                    val down = targetState > initialState
                    val enter = slideInVertically(pageSlide) { if (down) it else -it } +
                        fadeIn(pageFade)
                    val exit = slideOutVertically(pageSlide) { if (down) -it else it } +
                        fadeOut(pageFade)
                    enter togetherWith exit
                },
                label = "vitaTrayPage",
                modifier = Modifier.fillMaxSize(),
            ) { shownPage ->
                val rows = vitaTrayPageRows(slots.size, shownPage)
                // Slot centres relative to the field centre, in pixels — used to work out which
                // slot a dragged bubble is currently over.
                val slotCentres = remember(rows, unit, density) {
                    buildMap {
                        rows.forEachIndexed { rowIndex, row ->
                            val rowShift = (rowIndex - ((rows.size - 1) / 2f)) * ROW_PITCH
                            row.forEachIndexed { column, slotIndex ->
                                val columnShift = (column - ((row.size - 1) / 2f)) * COLUMN_PITCH
                                put(
                                    slotIndex,
                                    Offset(
                                        x = columnShift * unit * density.density,
                                        y = rowShift * unit * density.density,
                                    ),
                                )
                            }
                        }
                    }
                }
                Box(modifier = Modifier.fillMaxSize()) {
                    rows.forEachIndexed { rowIndex, row ->
                        val rowShift = (rowIndex - ((rows.size - 1) / 2f)) * ROW_PITCH
                        row.forEachIndexed { column, slotIndex ->
                            val columnShift = (column - ((row.size - 1) / 2f)) * COLUMN_PITCH
                            val moving = slotIndex == moveIndex
                            VitaBubble(
                                slot = slots[slotIndex],
                                selected = slotIndex == focus,
                                departing = slotIndex == departingIndex,
                                moving = moving,
                                diameter = bubbleDiameter,
                                glass = glass,
                                offsetProvider = {
                                    val tiltShift = motion.offsetAt(slotIndex)
                                    val bounce = jiggle.liftAt(slotIndex) * bubblePx * BUBBLE_JIGGLE_LIFT
                                    val drag = if (moving) dragOffset else Offset.Zero
                                    Offset(
                                        tiltShift.x + drag.x,
                                        tiltShift.y + landing.offsetY(slotIndex) + bounce + drag.y,
                                    )
                                },
                                leanProvider = {
                                    val lean = motion.leanAt(slotIndex)
                                    val idle = if (sway) {
                                        vitaBubbleIdleLean(slotIndex, idleRock.floatValue)
                                    } else {
                                        0f
                                    }
                                    Offset(lean.x + idle, lean.y)
                                },
                                interactive = departingIndex == null && !suppressIdleBubbles,
                                onClick = {
                                    onSelect(slotIndex)
                                    when (slots[slotIndex]) {
                                        is VitaShortcutSlot.Filled -> onActivate(slotIndex)
                                        VitaShortcutSlot.Add -> onAddSlot()
                                    }
                                },
                                onLongPress = {
                                    if (slots[slotIndex] is VitaShortcutSlot.Filled) {
                                        onBeginMove(slotIndex)
                                    }
                                },
                                onDrag = if (moving) {
                                    { delta ->
                                        dragOffset += delta
                                        val here = slotCentres[slotIndex] ?: Offset.Zero
                                        val pointer = here + dragOffset
                                        val target = nearestVitaSlot(
                                            centres = slotCentres,
                                            point = pointer,
                                            limit = shortcuts.size,
                                        )
                                        if (target != null && target != slotIndex) {
                                            // Keep the bubble under the finger as the field reflows.
                                            val there = slotCentres[target] ?: here
                                            dragOffset += here - there
                                            onMoveTo(target)
                                        }
                                    }
                                } else {
                                    null
                                },
                                onDragEnd = {
                                    dragOffset = Offset.Zero
                                    onDropMove()
                                },
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .offset(x = (columnShift * unit).dp, y = (rowShift * unit).dp)
                                    .zIndex(if (moving) 2f else 0f)
                                    .graphicsLayer {
                                        if (slotIndex != departingIndex) alpha = crowdAlpha
                                        clip = false
                                    },
                            )
                        }
                    }

                    // Drawn after every bubble so the focused name always reads over its neighbours.
                    rows.forEachIndexed { rowIndex, row ->
                        val focusColumn = row.indexOf(focus)
                        if (focusColumn < 0 ||
                            departingIndex == focus ||
                            suppressIdleBubbles
                        ) {
                            return@forEachIndexed
                        }
                        val rowShift = (rowIndex - ((rows.size - 1) / 2f)) * ROW_PITCH
                        val columnShift = (focusColumn - ((row.size - 1) / 2f)) * COLUMN_PITCH
                        val pillCentre = rowShift + (BUBBLE_DIAMETER / 2f) + NAME_PILL_GAP +
                            (NAME_PILL_HEIGHT / 2f)
                        SoftwareNamePill(
                            label = if (moveIndex == focus) {
                                "Place with A · B cancels"
                            } else {
                                slots[focus].label(editMode)
                            },
                            unit = unit,
                            minWidth = bubbleDiameter,
                            offsetProvider = {
                                val tiltShift = motion.offsetAt(focus)
                                Offset(tiltShift.x, tiltShift.y + landing.offsetY(focus))
                            },
                            modifier = Modifier
                                .align(Alignment.Center)
                                .offset(x = (columnShift * unit).dp, y = (pillCentre * unit).dp),
                        )
                    }
                }
            }

            PageDots(
                count = pageCount,
                current = page,
                unit = unit,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .graphicsLayer { alpha = crowdAlpha },
            )
        }
    }
}

/** Nearest slot to [point] among [centres], considering only slots below [limit]. */
private fun nearestVitaSlot(
    centres: Map<Int, Offset>,
    point: Offset,
    limit: Int,
): Int? = centres.entries
    .filter { it.key < limit }
    .minByOrNull { (_, centre) -> (centre - point).getDistanceSquared() }
    ?.key

@Composable
private fun VitaBubble(
    slot: VitaShortcutSlot,
    selected: Boolean,
    departing: Boolean,
    diameter: Dp,
    glass: ImageBitmap,
    offsetProvider: () -> Offset,
    /** Sway, idle rock and page-turn wobble, combined; `x` turns the dome, `y` pitches it. */
    leanProvider: () -> Offset,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    /** True while this bubble is the one being repositioned. */
    moving: Boolean = false,
    onLongPress: () -> Unit = {},
    /** Non-null only while [moving]; receives finger deltas in pixels. */
    onDrag: ((Offset) -> Unit)? = null,
    onDragEnd: () -> Unit = {},
) {
    val ringWidth = diameter * (SELECTION_RING_WIDTH / BUBBLE_DIAMETER)
    val interaction = remember { MutableInteractionSource() }
    val depart by animateFloatAsState(
        targetValue = if (departing) 1f else 0f,
        animationSpec = tween(
            durationMillis = VitaBubbleDepartMs,
            easing = VitaBubbleDepartEasing,
        ),
        label = "vitaBubbleDepart",
    )
    val hovered by interaction.collectIsHoveredAsState()
    val reduceMotion = rememberReduceMotion()
    val highlighted = (selected || hovered) && depart < 0.05f && interactive
    // A picked-up bubble lifts off the field so it reads as held rather than merely focused.
    val liftScale by animateFloatAsState(
        targetValue = if (moving) 1.16f else 1f,
        animationSpec = arcadiaTween(ArcadiaMotion.Fast),
        label = "vitaBubbleLift",
    )
    val pulse = rememberInfiniteTransition(label = "vitaBubblePulse")
    val pulseScale by pulse.animateFloat(
        initialValue = 1f,
        targetValue = if (highlighted && !reduceMotion) 1.045f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(640, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "vitaBubblePulseScale",
    )
    val pulseLift by pulse.animateFloat(
        initialValue = 0f,
        targetValue = if (highlighted && !reduceMotion) -0.035f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(640, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "vitaBubblePulseLift",
    )
    val canBlur = supportsGlassBlurEffect()
    Box(
        modifier = modifier
            .size(diameter + ringWidth)
            .graphicsLayer {
                val shift = offsetProvider()
                translationX = shift.x
                translationY = shift.y + (diameter.toPx() * pulseLift)
                scaleX = pulseScale * liftScale
                scaleY = pulseScale * liftScale
                // Perspective, so a lean reads as a dome turning rather than an ellipse.
                cameraDistance = VITA_BUBBLE_CAMERA_DISTANCE * density
                // Handed back to the depart flip, which owns the rotation once a bubble launches.
                val settle = 1f - depart
                val lean = leanProvider()
                rotationY = (lean.x * VITA_BUBBLE_TILT_DEG * settle)
                    .coerceIn(-VITA_BUBBLE_TILT_DEG, VITA_BUBBLE_TILT_DEG)
                rotationX = (-lean.y * VITA_BUBBLE_TILT_DEG * settle)
                    .coerceIn(-VITA_BUBBLE_TILT_DEG, VITA_BUBBLE_TILT_DEG)
                clip = false
            }
            .then(
                if (interactive && onDrag != null) {
                    // While held, the bubble follows the finger and the field reflows under it.
                    Modifier.pointerInput(onDrag) {
                        detectDragGestures(
                            onDragEnd = onDragEnd,
                            onDragCancel = onDragEnd,
                        ) { change, delta ->
                            change.consume()
                            onDrag(delta)
                        }
                    }
                } else if (interactive) {
                    Modifier
                        .clickable(
                            interactionSource = interaction,
                            indication = null,
                            onClick = onClick,
                        )
                        .pointerInput(onLongPress) {
                            detectTapGestures(onLongPress = { onLongPress() })
                        }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (depart > 0.02f) {
            val echoCount = VitaBubbleEchoLags.size
            for (index in echoCount - 1 downTo 0) {
                val lag = VitaBubbleEchoLags[index]
                val echoT = (depart - lag).coerceIn(0f, 1f)
                if (echoT <= 0.001f) continue
                val trailT = index / (echoCount - 1f).coerceAtLeast(1f)
                val twirl = vitaTwirl(echoT)
                val zoom = vitaZoom(echoT)
                val fade = (exp(-2.8f * trailT * trailT) * 0.34f * (1f - echoT * 0.22f))
                    .coerceAtLeast(0.02f)
                val liveScale = 1f + VitaBubbleZoom * zoom
                val feather = 1.08f + 0.22f * trailT
                val echoScale = liveScale * feather
                Box(
                    modifier = Modifier
                        .size(diameter)
                        .graphicsLayer {
                            rotationY = VitaBubbleFlipDeg * twirl
                            scaleX = echoScale
                            scaleY = echoScale
                            alpha = fade
                            cameraDistance = 8f * density
                            transformOrigin = TransformOrigin.Center
                            clip = false
                            compositingStrategy = CompositingStrategy.Offscreen
                            if (canBlur) {
                                renderEffect = BlurEffect(
                                    6f + 16f * trailT,
                                    6f + 16f * trailT,
                                    TileMode.Decal,
                                )
                            }
                        }
                        .clip(CircleShape)
                        .background(Color.White),
                )
            }
        }
        Box(
            modifier = Modifier
                .size(diameter)
                .graphicsLayer {
                    val twirl = vitaTwirl(depart)
                    val zoom = vitaZoom(depart)
                    rotationY = VitaBubbleFlipDeg * twirl
                    scaleX = 1f + VitaBubbleZoom * zoom
                    scaleY = 1f + VitaBubbleZoom * zoom
                    alpha = 1f - ((depart - VitaBubbleFadeStart) / (1f - VitaBubbleFadeStart))
                        .coerceIn(0f, 1f)
                    cameraDistance = 8f * density
                    transformOrigin = TransformOrigin.Center
                    clip = false
                }
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
                    // The highlight slides against the lean, the way a fixed light source would
                    // travel across real glass as it turns.
                    val lean = leanProvider()
                    val travel = size.minDimension * VITA_BUBBLE_SHEEN_TRAVEL
                    withTransform({
                        translate(
                            -lean.x.coerceIn(-1f, 1f) * travel,
                            -lean.y.coerceIn(-1f, 1f) * travel,
                        )
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

        if (selected && depart < 0.2f) {
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

/**
 * Slot next to [from] in the staggered tray grid, or null at an edge.
 *
 * Rows hold 3 / 4 / 3, so a vertical step keeps the horizontal position proportionally — the same
 * mapping the focus cursor uses — and steps onto the next page when it runs out of rows.
 */
internal fun vitaTrayNeighbourSlot(slotCount: Int, from: Int, dx: Int, dy: Int): Int? {
    if (slotCount <= 0 || from !in 0 until slotCount) return null
    val page = from / VITA_TRAY_PAGE_SIZE
    val rows = vitaTrayPageRows(slotCount, page)
    val rowIndex = rows.indexOfFirst { from in it }
    if (rowIndex < 0) return null
    val row = rows[rowIndex]
    val column = row.indexOf(from).coerceAtLeast(0)

    if (dx != 0) {
        val next = column + dx
        return row.getOrNull(next)
    }
    if (dy == 0) return null

    fun land(targetRows: List<List<Int>>, targetRowIndex: Int): Int? {
        val targetRow = targetRows.getOrNull(targetRowIndex) ?: return null
        val mapped = if (row.size <= 1 || targetRow.size <= 1) {
            0
        } else {
            ((column.toFloat() / (row.size - 1)) * (targetRow.size - 1)).roundToInt()
        }
        return targetRow.getOrNull(mapped.coerceIn(0, targetRow.lastIndex))
    }

    val samePage = land(rows, rowIndex + dy)
    if (samePage != null) return samePage
    val nextPage = page + dy
    if (nextPage !in 0 until vitaTrayPageCount(slotCount)) return null
    val nextRows = vitaTrayPageRows(slotCount, nextPage)
    if (nextRows.isEmpty()) return null
    return land(nextRows, if (dy > 0) 0 else nextRows.lastIndex)
}

internal fun vitaTrayPageCount(slotCount: Int): Int =
    if (slotCount <= 0) 1 else ((slotCount + VITA_TRAY_PAGE_SIZE - 1) / VITA_TRAY_PAGE_SIZE)

/** Page turns play the GitHub `vita-page-navigate` sample; same-page row steps stay on the cursor tick. */
internal fun vitaTrayVerticalOneShot(crossedPage: Boolean): UiOneShot =
    if (crossedPage) UiOneShot.VitaPageNavigate else UiOneShot.Cursor

/** Opening the tray plays `vita_open.wav`; already-open (edit / swipe while open) stays silent. */
internal fun vitaTrayOpenOneShot(alreadyOpen: Boolean): UiOneShot? =
    if (alreadyOpen) null else UiOneShot.VitaOpen

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
