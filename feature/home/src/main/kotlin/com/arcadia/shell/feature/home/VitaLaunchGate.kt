package com.arcadia.shell.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import com.arcadia.shell.designsystem.XoraFonts
import com.arcadia.shell.designsystem.rememberReduceMotion
import com.arcadia.shell.designsystem.rememberThrottledAmbientUnit
import com.arcadia.shell.designsystem.xmbAssetShadow
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max

// Design-px on the 1920x1080 artboard, scaled by `unit` like the rest of the launch page.
internal const val GATE_W = 462f
internal const val GATE_H = 138f
/** Same column centre as the launch plate, so gate and box art share one edge. */
internal const val GATE_CENTER_X = 407f
internal const val GATE_TOP_Y = 772f
private const val GATE_RADIUS = 30f
private const val GATE_BORDER = 3f
private const val GATE_LABEL_SIZE = 46f

/** Release above this much peel and the sticker comes off; below it, it falls back down. */
private const val PeelCommitFraction = 0.42f
private const val PeelAutoMs = 520
private const val PeelSettleMs = 240
/** Peel past this and the sticker lets go of the page entirely. */
private const val PeelReleaseAt = 0.8f
/** Idle breath at the free edge so it reads as something you can grab. */
private const val PeelIdleFraction = 0.05f
private const val PeelIdleCycleMs = 2_600

private val GateFillTop = Color(0xFFFFFFFF)
private val GateFillBottom = Color(0xFFB5EFFF)
private val GateBorder = Color(0x8CFFFFFF)
private val GateInk = Color(0xFF0B2A3A)
/** Underside of the sticker: bright where it has lifted flat, shaded into the crease. */
private val PeelBackFace = Color(0xFFEDF4F8)
private val PeelFoldShade = Color(0xFF9EB4C0)

/**
 * PS Vita LiveArea start gate: a sticker over the game's page that has to be peeled off before
 * the title boots. Pull it up with a finger, or press A — [peelRequested] runs the same peel on
 * its own — and [onPeeled] fires the moment the sticker clears the page.
 *
 * The peel is a fold, not a slide: the strip below the fold line is mirrored back over the part
 * still stuck down, so the sticker's underside is what covers the label as it comes away.
 */
@Composable
internal fun VitaLaunchGate(
    peelRequested: Boolean,
    unit: Float,
    onRequestPeel: () -> Unit,
    onPeeled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = rememberReduceMotion()
    val scope = rememberCoroutineScope()
    val peel = remember { Animatable(0f) }
    val peeled = rememberUpdatedState(onPeeled)
    // True from the first touch (or the A press) until the sticker is either off or back down;
    // the idle breath stays out of the way for that whole stretch.
    var engaged by remember { mutableStateOf(false) }
    // Once the sticker is off, the gate stops taking touches — the launch owns the screen.
    var spent by remember { mutableStateOf(false) }
    val breath = rememberThrottledAmbientUnit(cycleMs = PeelIdleCycleMs)
    // Read from inside the draw lambdas, so a peel invalidates the drawing and not the
    // composition — the gate redraws at frame rate for half a second on every launch.
    val peelNow = {
        val base = peel.value
        if (engaged || reduceMotion) {
            base
        } else {
            val idle = PeelIdleFraction * (1f - cos(breath.floatValue * 2f * PI.toFloat())) / 2f
            max(base, idle)
        }
    }

    LaunchedEffect(peelRequested) {
        if (!peelRequested) return@LaunchedEffect
        engaged = true
        if (reduceMotion) {
            peel.snapTo(1f)
        } else {
            peel.animateTo(1f, tween(PeelAutoMs, easing = FastOutSlowInEasing))
        }
        spent = true
        peeled.value()
    }
    val inert = peelRequested || spent

    val density = LocalDensity.current
    val radiusPx = with(density) { (GATE_RADIUS * unit).dp.toPx() }
    val borderPx = with(density) { (GATE_BORDER * unit).dp.toPx() }

    Box(
        modifier = modifier
            .requiredSize((GATE_W * unit).dp, (GATE_H * unit).dp)
            .drawWithContent {
                val lift = peelNow()
                val foldY = size.height * (1f - lift)
                if (lift < 1f) {
                    clipRect(0f, 0f, size.width, foldY) {
                        this@drawWithContent.drawContent()
                    }
                }
                drawPeelFlap(peel = lift, foldY = foldY, radius = radiusPx)
            }
            // A tap is the touch shorthand for "peel it for me"; the drag below is the real thing.
            .pointerInput(inert) {
                if (inert) return@pointerInput
                detectTapGestures { onRequestPeel() }
            }
            .pointerInput(inert) {
                if (inert) return@pointerInput
                val height = size.height.toFloat().coerceAtLeast(1f)
                detectVerticalDragGestures(
                    onDragStart = { engaged = true },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        scope.launch {
                            // Up is negative, and up is the direction that lifts the sticker.
                            peel.snapTo((peel.value - dragAmount / height).coerceIn(0f, 1f))
                        }
                    },
                    onDragEnd = {
                        scope.launch {
                            if (peel.value >= PeelCommitFraction) {
                                peel.animateTo(
                                    targetValue = 1f,
                                    animationSpec = tween(
                                        durationMillis = if (reduceMotion) 0 else PeelSettleMs,
                                        easing = FastOutSlowInEasing,
                                    ),
                                )
                                spent = true
                                peeled.value()
                            } else {
                                peel.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = 0.55f,
                                        stiffness = Spring.StiffnessMedium,
                                    ),
                                )
                                engaged = false
                            }
                        }
                    },
                    onDragCancel = {
                        scope.launch {
                            peel.animateTo(0f, spring(dampingRatio = 0.55f))
                            engaged = false
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        // The shadow is drawn as part of the content, so the clip at the fold takes it away
        // with the strip that has already lifted off the page.
        Canvas(
            Modifier
                .requiredSize((GATE_W * unit).dp, (GATE_H * unit).dp)
                .xmbAssetShadow(
                    unit = unit,
                    shape = RoundedCornerShape((GATE_RADIUS * unit).dp),
                ),
        ) {
            drawGatePlate(radius = radiusPx, borderPx = borderPx)
            drawGrabChevrons(unit = unit, lift = peelNow())
        }
        Text(
            text = "Start",
            color = GateInk,
            style = TextStyle(
                fontFamily = XoraFonts.XmbLabel,
                fontWeight = FontWeight.Normal,
                fontSize = with(density) { (GATE_LABEL_SIZE * unit).dp.toSp() },
                lineHeight = with(density) { (GATE_LABEL_SIZE * unit).dp.toSp() },
                platformStyle = PlatformTextStyle(includeFontPadding = false),
                lineHeightStyle = LineHeightStyle(
                    alignment = LineHeightStyle.Alignment.Center,
                    trim = LineHeightStyle.Trim.Both,
                ),
            ),
        )
    }
}

private fun DrawScope.drawGatePlate(radius: Float, borderPx: Float) {
    val corner = CornerRadius(radius, radius)
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(GateFillTop, GateFillBottom)),
        cornerRadius = corner,
    )
    // Glass sheen over the top half, matching the tray bubbles.
    drawRoundRect(
        brush = Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.7f),
            0.55f to Color.White.copy(alpha = 0.04f),
            1f to Color.Transparent,
        ),
        cornerRadius = corner,
    )
    drawRoundRect(
        color = GateBorder,
        topLeft = Offset(borderPx / 2f, borderPx / 2f),
        size = Size(size.width - borderPx, size.height - borderPx),
        cornerRadius = corner,
        style = Stroke(width = borderPx),
    )
}

/** Swipe-up chevrons either side of the label; they fade out once the peel is under way. */
private fun DrawScope.drawGrabChevrons(unit: Float, lift: Float) {
    val alpha = (0.45f * (1f - lift * 4f)).coerceAtLeast(0f)
    if (alpha <= 0.01f) return
    val armW = 26f * unit
    val armH = 13f * unit
    val stroke = 5f * unit
    val cy = size.height / 2f
    for (cx in floatArrayOf(70f * unit, size.width - 70f * unit)) {
        for (i in 0..1) {
            val y = cy - 9f * unit + (i * 18f * unit)
            val ink = GateInk.copy(alpha = alpha * (1f - i * 0.45f))
            drawLine(ink, Offset(cx - armW / 2f, y + armH), Offset(cx, y), stroke, StrokeCap.Round)
            drawLine(ink, Offset(cx, y), Offset(cx + armW / 2f, y + armH), stroke, StrokeCap.Round)
        }
    }
}

/**
 * The lifted half of the sticker, mirrored about the fold line so it lies back over the part
 * still stuck down — with a crease highlight along the fold and contact shadow under the free
 * edge, which is what sells it as paper rather than a panel sliding away.
 */
private fun DrawScope.drawPeelFlap(peel: Float, foldY: Float, radius: Float) {
    if (peel <= 0.002f) return
    val w = size.width
    val h = size.height
    val strip = h - foldY
    val freeEdge = foldY - strip
    // Past [PeelReleaseAt] the sticker has nothing left holding it: it lets go, carries on up
    // and fades, so nothing of the gate is left lying over the page when the game takes over.
    val release = ((peel - PeelReleaseAt) / (1f - PeelReleaseAt)).coerceIn(0f, 1f)
    val fade = 1f - release
    if (fade <= 0.002f) return
    val flyUp = release * release * h * 1.4f

    translate(top = -flyUp) {
        withTransform({
            scale(scaleX = 1f, scaleY = -1f, pivot = Offset(w / 2f, foldY))
            clipRect(left = 0f, top = foldY, right = w, bottom = h)
        }) {
            drawRoundRect(
                brush = Brush.verticalGradient(
                    colors = listOf(PeelFoldShade, PeelBackFace),
                    startY = foldY,
                    endY = h,
                ),
                cornerRadius = CornerRadius(radius, radius),
                alpha = fade,
            )
        }

        // Crease: a bright rolled edge sitting on the fold line.
        val crease = (strip * 0.12f).coerceIn(1f, 10f)
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.85f)),
                startY = foldY - crease,
                endY = foldY,
            ),
            topLeft = Offset(0f, foldY - crease),
            size = Size(w, crease),
            alpha = fade,
        )
    }

    // Contact shadow the raised flap drops on the page above its free edge. It stays put while
    // the flap flies off, fading with it rather than travelling along.
    val shadowDepth = (strip * 0.22f).coerceAtMost(28f)
    if (shadowDepth > 0.5f && freeEdge > 0f) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.3f)),
                startY = freeEdge - shadowDepth,
                endY = freeEdge,
            ),
            topLeft = Offset(0f, (freeEdge - shadowDepth).coerceAtLeast(0f)),
            size = Size(w, shadowDepth.coerceAtMost(freeEdge)),
            alpha = fade,
        )
    }
}
