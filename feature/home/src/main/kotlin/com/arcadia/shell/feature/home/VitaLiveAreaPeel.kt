package com.arcadia.shell.feature.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.arcadia.shell.designsystem.rememberReduceMotion
import com.arcadia.shell.designsystem.rememberThrottledAmbientUnit
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/**
 * Resting dog-ear, in design px: how far the fold already cuts along the top and right edges.
 * Taken from the reference page, where the corner is turned down before you touch it.
 */
private const val PEEL_REST_DEPTH = 190f
/** Corner square that takes the pull, in design px. */
private const val PEEL_GRAB = 430f
/** Let go past this fraction of the page's full sweep and the sheet comes away. */
private const val PeelCommitFraction = 0.32f
private const val PeelAutoMs = 620
private const val PeelSettleMs = 420
/** The turned corner breathes at rest so it reads as something to pull. */
private const val PeelIdleDepth = 26f
private const val PeelIdleCycleMs = 2_600
private const val PeelCreaseWidth = 3f
private const val PeelShadowDepth = 26f
/** Fraction of the sweep after which the departing sheet starts thinning out. */
private const val PeelFadeFrom = 0.45f

/** Underside of the page: light where it turns over, shading off toward the tip. */
private val PeelBackNear = Color(0xFFE9E9E9)
private val PeelBackFar = Color(0xFF8E8E8E)

/** Unit diagonal — the fold is always 45°, like the Vita's. */
private const val R2 = 0.70710678f

/**
 * The LiveArea page, with the Vita's turned-down corner at its top right.
 *
 * Pull the corner toward the bottom-left and the sheet comes off the screen; A runs the same
 * pull on its own. The fold is a 45° line sweeping down the page's diagonal, so
 * the bare corner grows the way a peeled sticker does rather than sliding away in one piece.
 * [onPeeled] fires as the page commits, so the launch cinematic starts underneath while the last
 * of the sheet is still travelling.
 */
@Composable
internal fun VitaLiveAreaPeel(
    peelRequested: Boolean,
    unit: Float,
    onRequestPeel: () -> Unit,
    onPeeled: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    BoxWithConstraints(modifier = modifier) {
        val density = LocalDensity.current
        val pageW = with(density) { maxWidth.toPx() }
        val pageH = with(density) { maxHeight.toPx() }
        // Depth runs along the fold's normal: the fold is every point where (w - x) + y equals
        // it, so 0 is the bare corner and w + h is the far corner, the whole page gone.
        val sweep = pageW + pageH
        // Depth, the crease and the shadow are all compared against pixel geometry in the draw
        // pass and against pixel drag deltas, so they leave design units here.
        val restDepth = with(density) { (PEEL_REST_DEPTH * unit).dp.toPx() }
        val idleDepth = with(density) { (PeelIdleDepth * unit).dp.toPx() }
        val creasePx = with(density) { (PeelCreaseWidth * unit).dp.toPx() }
        val shadowPx = with(density) { (PeelShadowDepth * unit).dp.toPx() }

        val reduceMotion = rememberReduceMotion()
        val scope = rememberCoroutineScope()
        val depth = remember(restDepth, sweep) {
            Animatable(restDepth).apply { updateBounds(0f, sweep) }
        }
        val peeled = rememberUpdatedState(onPeeled)
        var engaged by remember { mutableStateOf(false) }
        var spent by remember { mutableStateOf(false) }
        val breath = rememberThrottledAmbientUnit(cycleMs = PeelIdleCycleMs)

        // Read inside the draw lambda so a peel invalidates the drawing, not the composition.
        val depthNow = {
            val base = depth.value
            if (engaged || reduceMotion) {
                base
            } else {
                base + idleDepth * (1f - sin(breath.floatValue * 2f * PI.toFloat())) / 2f
            }
        }

        LaunchedEffect(peelRequested) {
            if (!peelRequested) return@LaunchedEffect
            engaged = true
            spent = true
            peeled.value()
            if (reduceMotion) {
                depth.snapTo(sweep)
            } else {
                depth.animateTo(sweep, tween(PeelAutoMs, easing = FastOutSlowInEasing))
            }
        }
        val inert = peelRequested || spent

        Box(
            modifier = Modifier
                .matchParentSize()
                .drawWithContent {
                    val k = depthNow().coerceIn(0f, sweep)
                    clipPath(halfPlane(k, lifted = false)) {
                        this@drawWithContent.drawContent()
                    }
                    drawTurnedCorner(k = k, creasePx = creasePx, shadowPx = shadowPx)
                },
        ) {
            content()
        }

        // Only the corner takes the pull, so a stray swipe across the artwork cannot launch.
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size((PEEL_GRAB * unit).dp)
                .pointerInput(inert) {
                    if (inert) return@pointerInput
                    detectTapGestures { onRequestPeel() }
                }
                .pointerInput(inert, sweep) {
                    if (inert) return@pointerInput
                    val commit = sweep * PeelCommitFraction
                    detectDragGestures(
                        onDragStart = { engaged = true },
                        onDrag = { change, drag ->
                            change.consume()
                            // Pulling down-left turns the corner over: both terms add.
                            scope.launch { depth.snapTo(depth.value + (drag.y - drag.x)) }
                        },
                        onDragEnd = {
                            scope.launch {
                                if (depth.value >= commit) {
                                    spent = true
                                    peeled.value()
                                    depth.animateTo(
                                        targetValue = sweep,
                                        animationSpec = tween(
                                            durationMillis = if (reduceMotion) 0 else PeelSettleMs,
                                            easing = FastOutSlowInEasing,
                                        ),
                                    )
                                } else {
                                    depth.animateTo(
                                        targetValue = restDepth,
                                        animationSpec = spring(
                                            dampingRatio = 0.62f,
                                            stiffness = Spring.StiffnessMedium,
                                        ),
                                    )
                                    engaged = false
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                depth.animateTo(restDepth, spring(dampingRatio = 0.62f))
                                engaged = false
                            }
                        },
                    )
                },
        )
    }
}

/**
 * One side of the fold, as a quad big enough to cover the page whatever the fold has reached:
 * [lifted] false is everything still stuck down, true is everything already turned over.
 */
private fun DrawScope.halfPlane(k: Float, lifted: Boolean): Path {
    val w = size.width
    val big = (w + size.height) * 2f
    // A point on the fold, the fold's own direction, and the normal into the chosen side.
    val px = w - k
    val nx = if (lifted) R2 else -R2
    val ny = if (lifted) -R2 else R2
    return Path().apply {
        moveTo(px + R2 * big, R2 * big)
        lineTo(px - R2 * big, -R2 * big)
        lineTo(px - R2 * big + nx * big, -R2 * big + ny * big)
        lineTo(px + R2 * big + nx * big, R2 * big + ny * big)
        close()
    }
}

/**
 * The corner that has turned over: the page's own underside, lit along the crease and shading
 * off toward the tip, with the shadow it throws back onto the part still lying flat.
 */
private fun DrawScope.drawTurnedCorner(k: Float, creasePx: Float, shadowPx: Float) {
    if (k <= 0.5f) return
    val w = size.width
    val h = size.height
    val sweep = w + h
    // The sheet thins as it leaves, so the page dissolves into the launch rather than ending on
    // a screen of flat grey.
    val fade = (1f - ((k / sweep) - PeelFadeFrom) / (1f - PeelFadeFrom)).coerceIn(0f, 1f)
    if (fade <= 0.01f) return

    val corner = Offset(w, 0f)
    // Foot of the perpendicular from the corner to the fold — where the crease runs deepest.
    val foldMid = Offset(w - k / 2f, k / 2f)

    clipPath(halfPlane(k, lifted = true)) {
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(PeelBackNear, PeelBackFar),
                start = foldMid,
                end = corner,
            ),
            alpha = fade,
        )
    }

    clipPath(halfPlane(k, lifted = false)) {
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color.Black.copy(alpha = 0.34f), Color.Transparent),
                start = foldMid,
                end = Offset(foldMid.x - R2 * shadowPx, foldMid.y + R2 * shadowPx),
            ),
            alpha = fade,
        )
    }

    clipRect(0f, 0f, w, h) {
        drawLine(
            color = Color.White.copy(alpha = 0.8f * fade),
            start = Offset(w - k, 0f),
            end = Offset(w, k),
            strokeWidth = creasePx,
        )
    }
}
