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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import com.arcadia.shell.designsystem.rememberReduceMotion
import com.arcadia.shell.designsystem.rememberThrottledAmbientUnit
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/**
 * PEEL_BOUNDARY.png footprint on the 1920x1080 artboard. The sheet is centred under the status
 * strip; everything outside it (the wallpaper margin, the strip itself) never moves.
 */
internal const val PEEL_BOUNDARY_X = 64f
internal const val PEEL_BOUNDARY_Y = 126f
internal const val PEEL_BOUNDARY_W = 1792f
internal const val PEEL_BOUNDARY_H = 888f
/** Corner radius measured off PEEL_BOUNDARY.png / PEEL_UNDER.png. */
internal const val PEEL_BOUNDARY_RADIUS = 43f

/**
 * PEEL_TIP.png is a 45° dog-ear whose fold cuts this far along the sheet's top and right edges
 * (measured off the asset, in sheet px). The rest pose shows the asset at its own size.
 */
internal const val PEEL_TIP_REST_DEPTH = 170f
/** Corner square that takes the pull, in design px — well past the tip so a thumb can find it. */
private const val PEEL_GRAB = 430f
private const val PeelCommitFraction = 0.32f
private const val PeelAutoMs = 620
private const val PeelSettleMs = 420
/** The tip lifts and settles at rest so it reads as something to pull. */
private const val PeelIdleDepth = 10f
private const val PeelIdleCycleMs = 2_600
/** Shadow the folded face throws onto the flat sheet, in design px. */
private const val PeelShadowDepth = 34f
/** Pulling this many rest-depths brings the shadow to full strength. */
private const val PeelShadowRamp = 1.5f
private const val PeelShadowAlpha = 0.32f

/** Unit diagonal — the fold is always 45°, like the Vita's. */
private const val R2 = 0.70710678f

/** Pure fold geometry, kept off the draw pass so it can be checked in isolation. */
internal object VitaPeelGeometry {
    /** Fold depth at which the far corner has gone: the whole sheet has come away. */
    fun sweep(width: Float, height: Float): Float = width + height

    /** Depth the player has to reach before letting go commits the peel. */
    fun commitDepth(width: Float, height: Float): Float =
        sweep(width, height) * PeelCommitFraction

    /** A pull toward the bottom-left turns the corner over: both drag terms add. */
    fun depthDelta(dragX: Float, dragY: Float): Float = dragY - dragX

    /**
     * Square the dog-ear asset fills for fold depth [k], in sheet px. Its NE half is the
     * transparent revealed corner, its SW half the turned-over face; scaling the asset to this
     * square keeps the face congruent with the fold however far it has been pulled.
     */
    fun faceSquare(k: Float, width: Float): Rect = Rect(width - k, 0f, width, k)

    /** How far past rest the pull is, 0..1, for shadows that stay off the untouched asset. */
    fun pullFraction(k: Float, restDepth: Float): Float =
        ((k - restDepth) / (restDepth * PeelShadowRamp)).coerceIn(0f, 1f)
}

/**
 * The LiveArea page with a peelable sheet inside [boundary].
 *
 * At rest the sheet shows the wallpaper under a faint outline (PEEL_BOUNDARY) with the asset
 * dog-ear (PEEL_TIP) turned down at its top-right corner. Dragging the tip toward the bottom-left
 * folds the sheet along a 45° line: the wallpaper inside the boundary comes away to show its
 * backing (PEEL_UNDER), while everything outside the boundary stays put. A runs the same pull on
 * its own. [onPeeled] fires once the whole sheet is off, so the launch takes over from a bare
 * backing rather than under a still-moving corner.
 */
@Composable
internal fun VitaLiveAreaPeel(
    peelRequested: Boolean,
    unit: Float,
    boundary: DpRect,
    onRequestPeel: () -> Unit,
    onPeeled: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val density = LocalDensity.current
    val bounds = with(density) {
        Rect(
            boundary.left.toPx(),
            boundary.top.toPx(),
            boundary.right.toPx(),
            boundary.bottom.toPx(),
        )
    }
    val sweep = VitaPeelGeometry.sweep(bounds.width, bounds.height)
    // Depth, the shadow and the radius are compared against pixel geometry in the draw pass and
    // against pixel drag deltas, so they leave design units here.
    val restDepth = with(density) { (PEEL_TIP_REST_DEPTH * unit).dp.toPx() }
    val idleDepth = with(density) { (PeelIdleDepth * unit).dp.toPx() }
    val shadowPx = with(density) { (PeelShadowDepth * unit).dp.toPx() }
    val radiusPx = with(density) { (PEEL_BOUNDARY_RADIUS * unit).dp.toPx() }

    val under = painterResource(R.drawable.vita_peel_under)
    val tip = painterResource(R.drawable.vita_peel_tip)
    val outline = painterResource(R.drawable.vita_peel_boundary)

    val reduceMotion = rememberReduceMotion()
    val scope = rememberCoroutineScope()
    val depth = remember(restDepth, sweep) {
        Animatable(restDepth).apply { updateBounds(restDepth, sweep) }
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
        if (reduceMotion) {
            depth.snapTo(sweep)
        } else {
            depth.animateTo(sweep, tween(PeelAutoMs, easing = FastOutSlowInEasing))
        }
        peeled.value()
    }
    val inert = peelRequested || spent

    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawWithContent {
                    drawContent()
                    drawPeel(
                        bounds = bounds,
                        k = depthNow().coerceIn(0f, sweep),
                        restDepth = restDepth,
                        radius = radiusPx,
                        shadowPx = shadowPx,
                        under = under,
                        tip = tip,
                        outline = outline,
                    )
                },
        ) {
            content()
        }

        // Only the corner takes the pull, so a stray swipe across the artwork cannot launch.
        val grab = (PEEL_GRAB * unit).dp
        Box(
            modifier = Modifier
                .offset(x = boundary.right - grab, y = boundary.top)
                .size(grab)
                .pointerInput(inert) {
                    if (inert) return@pointerInput
                    detectTapGestures { onRequestPeel() }
                }
                .pointerInput(inert, sweep) {
                    if (inert) return@pointerInput
                    val commit = VitaPeelGeometry.commitDepth(bounds.width, bounds.height)
                    detectDragGestures(
                        onDragStart = { engaged = true },
                        onDrag = { change, drag ->
                            change.consume()
                            scope.launch {
                                depth.snapTo(
                                    depth.value + VitaPeelGeometry.depthDelta(drag.x, drag.y),
                                )
                            }
                        },
                        onDragEnd = {
                            scope.launch {
                                if (depth.value >= commit) {
                                    spent = true
                                    depth.animateTo(
                                        targetValue = sweep,
                                        animationSpec = tween(
                                            durationMillis = if (reduceMotion) 0 else PeelSettleMs,
                                            easing = FastOutSlowInEasing,
                                        ),
                                    )
                                    peeled.value()
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
 * Everything the peel adds on top of the page, all of it inside [bounds]: the backing where the
 * sheet has come away, the shadow the fold throws, the turned-over face, and the outline.
 */
private fun DrawScope.drawPeel(
    bounds: Rect,
    k: Float,
    restDepth: Float,
    radius: Float,
    shadowPx: Float,
    under: Painter,
    tip: Painter,
    outline: Painter,
) {
    val w = bounds.width
    val h = bounds.height
    val sheetSize = Size(w, h)
    val sheet = Path().apply {
        addRoundRect(RoundRect(Rect(0f, 0f, w, h), CornerRadius(radius)))
    }

    translate(bounds.left, bounds.top) {
        // The corner that has come away shows the sheet's backing. The asset carries its own
        // rounded corners, so nothing outside the boundary is touched.
        if (k > 0.5f) {
            clipPath(halfPlane(k, w, h, lifted = true)) {
                with(under) { draw(sheetSize) }
            }
        }

        clipPath(sheet) {
            if (k > 0.5f) {
                val face = VitaPeelGeometry.faceSquare(k, w)
                drawFaceShadow(
                    face = face,
                    shadowPx = shadowPx,
                    alpha = PeelShadowAlpha * VitaPeelGeometry.pullFraction(k, restDepth),
                )
                // The dog-ear asset stretched to the fold: its SW half is the face lying on the
                // flat sheet, its NE half is clear so the backing shows through the corner.
                translate(face.left, face.top) {
                    with(tip) { draw(face.size) }
                }
            }
        }

        with(outline) { draw(sheetSize) }
    }
}

/** Soft band along the face's two straight edges, cast onto the sheet still lying flat. */
private fun DrawScope.drawFaceShadow(face: Rect, shadowPx: Float, alpha: Float) {
    if (alpha <= 0.005f) return
    val ink = Color.Black.copy(alpha = alpha)
    // Left edge of the face, running down to just past its bottom corner.
    drawRect(
        brush = Brush.horizontalGradient(
            colors = listOf(Color.Transparent, ink),
            startX = face.left - shadowPx,
            endX = face.left,
        ),
        topLeft = Offset(face.left - shadowPx, face.top),
        size = Size(shadowPx, face.height + shadowPx),
    )
    // Bottom edge of the face.
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(ink, Color.Transparent),
            startY = face.bottom,
            endY = face.bottom + shadowPx,
        ),
        topLeft = Offset(face.left, face.bottom),
        size = Size(face.width, shadowPx),
    )
}

/**
 * One side of the fold, as a quad big enough to cover the sheet whatever the fold has reached:
 * [lifted] true is the corner that has come away, false is everything still stuck down.
 */
private fun halfPlane(k: Float, w: Float, h: Float, lifted: Boolean): Path {
    val big = (w + h) * 2f
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
