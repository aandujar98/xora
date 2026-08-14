package com.arcadia.shell.feature.home.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.arcadia.shell.designsystem.rememberAmbientMotionActive
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/**
 * PS5-style ambient dust over the XMB wallpaper: tiny white motes rising slowly with a gentle
 * sideways wobble and twinkle, plus a few brighter 4-point sparkles with a soft glow.
 *
 * One linear phase drives everything; per-star loop counts are integers so the cycle wraps
 * seamlessly. When ambient motion is off (reduce motion / shell paused) the field renders as a
 * static scatter instead of animating.
 */
@Composable
fun XmbStarFieldLayer(
    modifier: Modifier = Modifier,
) {
    val stars = remember { buildXmbStars() }
    val phase = if (rememberAmbientMotionActive()) {
        val transition = rememberInfiniteTransition(label = "xmbStarField")
        val animated by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(STAR_CYCLE_MS, easing = LinearEasing),
            ),
            label = "xmbStarPhase",
        )
        animated
    } else {
        0.35f
    }

    Canvas(modifier = modifier) {
        val twoPi = (2.0 * PI).toFloat()
        for (star in stars) {
            val yFrac = (star.y - phase * star.riseLoops).mod(1f)
            val xFrac = star.x +
                star.wobbleAmp * sin(twoPi * (phase * star.wobbleLoops + star.twinklePhase))
            val twinkle = 0.62f + 0.38f * sin(twoPi * (phase * star.twinkleLoops + star.twinklePhase))
            val alpha = (star.baseAlpha * twinkle).coerceIn(0f, 1f)
            val center = Offset(xFrac * size.width, yFrac * size.height)
            val radius = star.radiusDp.dp.toPx()
            if (star.sparkle) {
                val glowRadius = radius * 3.2f
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.White.copy(alpha = alpha * 0.45f),
                            Color.Transparent,
                        ),
                        center = center,
                        radius = glowRadius,
                    ),
                    radius = glowRadius,
                    center = center,
                )
                val arm = radius * 2.1f
                val strokeWidth = radius * 0.55f
                drawLine(
                    color = Color.White.copy(alpha = alpha),
                    start = Offset(center.x - arm, center.y),
                    end = Offset(center.x + arm, center.y),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
                drawLine(
                    color = Color.White.copy(alpha = alpha),
                    start = Offset(center.x, center.y - arm),
                    end = Offset(center.x, center.y + arm),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            } else {
                drawCircle(
                    color = Color.White.copy(alpha = alpha),
                    radius = radius,
                    center = center,
                )
            }
        }
    }
}

private class XmbStar(
    val x: Float,
    val y: Float,
    val radiusDp: Float,
    val baseAlpha: Float,
    /** Integer screen traversals per cycle so the loop wraps without a jump. */
    val riseLoops: Int,
    val twinkleLoops: Int,
    val twinklePhase: Float,
    /** Sideways wobble amplitude as a fraction of the pane width. */
    val wobbleAmp: Float,
    val wobbleLoops: Int,
    val sparkle: Boolean,
)

private fun buildXmbStars(count: Int = 56): List<XmbStar> {
    // Fixed seed: the same sky every boot, no per-frame allocation.
    val random = Random(0x50355)
    return List(count) {
        val sparkle = random.nextFloat() < 0.16f
        XmbStar(
            x = random.nextFloat(),
            y = random.nextFloat(),
            radiusDp = if (sparkle) 1.6f + random.nextFloat() * 1.4f else 0.7f + random.nextFloat() * 1.1f,
            baseAlpha = 0.22f + random.nextFloat() * 0.42f,
            riseLoops = 1 + random.nextInt(2),
            twinkleLoops = 6 + random.nextInt(10),
            twinklePhase = random.nextFloat(),
            wobbleAmp = 0.004f + random.nextFloat() * 0.010f,
            wobbleLoops = 2 + random.nextInt(3),
            sparkle = sparkle,
        )
    }
}

/** One full drift cycle — slow enough to read as ambient dust, not weather. */
private const val STAR_CYCLE_MS = 90_000
