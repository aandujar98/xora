package com.arcadia.shell.designsystem

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

private val FlowSkyTop = Color(0xFF2ACBFD)
private val FlowSkyBottom = Color.White

/** Artboard the wave layers were exported against. */
private const val ART_WIDTH = 1920f
private const val ART_HEIGHT = 1080f

/**
 * Bleed baked into the drawables on every side, in artboard units. The bands continue past the
 * artboard, so drifting them can never pull a cut edge into frame.
 */
private const val WAVE_BLEED = 160f

// Unequal periods keep the two bands from lining back up, so the flow never looks like a loop.
private const val BACK_PERIOD_MS = 92_000
private const val FRONT_PERIOD_MS = 66_000

// Each band travels an ellipse. Speed is sqrt((A·cos)² + (B·sin)²), which never reaches zero, so
// the drift reads as constant — a hold at the turnaround is what would give the loop away. Both
// amplitudes stay under WAVE_BLEED.
private const val BACK_TRAVEL_X = 58f
private const val BACK_TRAVEL_Y = 46f
private const val FRONT_TRAVEL_X = 74f
private const val FRONT_TRAVEL_Y = 58f

private const val TWO_PI = (Math.PI * 2).toFloat()

/**
 * Default shell wallpaper: the authored HOME bands over a cyan → white sky, drifting slowly.
 *
 * The bands ship as pre-rendered drawables rather than paths. Each one carries a white inner
 * shadow in the source art, which is what gives the edges their glow, and Compose has no
 * inner-shadow primitive — so the layers are baked from the authored vectors and composited here
 * with [BlendMode.Lighten], exactly as authored. Lighten also means the bands can only brighten
 * the sky, so the pale bottom of the gradient never turns muddy.
 */
@Composable
fun XoraFlowBackdrop(modifier: Modifier = Modifier) {
    val back = ImageBitmap.imageResource(R.drawable.xora_wave_back)
    val front = ImageBitmap.imageResource(R.drawable.xora_wave_front)
    val animate = rememberAmbientMotionActive()

    val backPhase = rememberWavePhase(BACK_PERIOD_MS, animate, "xoraFlowBack")
    val frontPhase = rememberWavePhase(FRONT_PERIOD_MS, animate, "xoraFlowFront")

    Spacer(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(brush = Brush.verticalGradient(listOf(FlowSkyTop, FlowSkyBottom)))
                // Cover the pane the way the artboard would crop, then bleed outwards.
                val scale = max(size.width / ART_WIDTH, size.height / ART_HEIGHT)
                val originX = (size.width - ART_WIDTH * scale) / 2f - WAVE_BLEED * scale
                val originY = (size.height - ART_HEIGHT * scale) / 2f - WAVE_BLEED * scale
                val spread = WAVE_BLEED * 2f
                val dstSize = IntSize(
                    ((ART_WIDTH + spread) * scale).roundToInt(),
                    ((ART_HEIGHT + spread) * scale).roundToInt(),
                )
                drawWaveLayer(
                    image = back,
                    originX = originX,
                    originY = originY,
                    dstSize = dstSize,
                    scale = scale,
                    phase = backPhase,
                    travelX = BACK_TRAVEL_X,
                    travelY = BACK_TRAVEL_Y,
                )
                drawWaveLayer(
                    image = front,
                    originX = originX,
                    originY = originY,
                    dstSize = dstSize,
                    scale = scale,
                    // Quarter-turn apart, so the bands are never at the same point of their orbits.
                    phase = frontPhase + TWO_PI / 4f,
                    travelX = FRONT_TRAVEL_X,
                    travelY = FRONT_TRAVEL_Y,
                )
            },
    )
}

private fun DrawScope.drawWaveLayer(
    image: ImageBitmap,
    originX: Float,
    originY: Float,
    dstSize: IntSize,
    scale: Float,
    phase: Float,
    travelX: Float,
    travelY: Float,
) {
    drawImage(
        image = image,
        dstOffset = IntOffset(
            (originX + sin(phase) * travelX * scale).roundToInt(),
            (originY + cos(phase) * travelY * scale).roundToInt(),
        ),
        dstSize = dstSize,
        blendMode = BlendMode.Lighten,
    )
}

/**
 * A full turn per cycle. Sine and cosine both close on themselves at 2π, so restarting the ramp
 * is seamless no matter how long the shell stays open.
 */
@Composable
private fun rememberWavePhase(periodMs: Int, animate: Boolean, label: String): Float {
    if (!animate) return 0f
    val transition = rememberInfiniteTransition(label = label)
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = TWO_PI,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = periodMs, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "${label}Phase",
    )
    return phase
}
