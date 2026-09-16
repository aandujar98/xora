package com.arcadia.shell.feature.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import com.arcadia.shell.designsystem.rememberAmbientMotionActive
import kotlin.math.max

/** Held brightness when the pulse is parked. Mid-swing, so a still glow reads as intended. */
private const val GLOW_STILL = 0.72f

/**
 * Faint radial halo under a hovered XMB icon. Drawn behind the south-east drop shadow
 * so the glyph still sits on its usual silhouette.
 */
@Composable
fun XmbHoverGlow(
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    if (!enabled) return
    // Ambient motion rather than reduce-motion alone: something is hovered for as long as the
    // XMB is on screen, so this transition asks the compositor for a frame at display rate the
    // whole time. Battery saver, lite visuals, and a shell that is composed but not in front
    // now park it on a still frame the way the wallpaper clocks already do.
    val pulse: State<Float> = if (rememberAmbientMotionActive()) {
        rememberInfiniteTransition(label = "xmbHoverGlow").animateFloat(
            initialValue = 0.38f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "xmbHoverGlowPulse",
        )
    } else {
        remember { mutableFloatStateOf(GLOW_STILL) }
    }
    Canvas(
        modifier = modifier.graphicsLayer { clip = false },
    ) {
        val radius = max(size.width, size.height) * 0.56f
        if (radius <= 0f) return@Canvas
        // Read inside the draw scope so a swing repaints this one circle instead of
        // recomposing the icon that owns it.
        val level = pulse.value
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to Color.White.copy(alpha = 0.22f * level),
                    0.42f to Color.White.copy(alpha = 0.10f * level),
                    1f to Color.Transparent,
                ),
                center = Offset(size.width / 2f, size.height / 2f),
                radius = radius,
            ),
            radius = radius,
        )
    }
}
