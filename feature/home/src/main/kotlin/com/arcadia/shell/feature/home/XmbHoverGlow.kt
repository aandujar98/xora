package com.arcadia.shell.feature.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import com.arcadia.shell.designsystem.rememberReduceMotion
import kotlin.math.max

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
    val reduceMotion = rememberReduceMotion()
    val pulse = if (reduceMotion) {
        0.72f
    } else {
        val transition = rememberInfiniteTransition(label = "xmbHoverGlow")
        val animated by transition.animateFloat(
            initialValue = 0.38f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "xmbHoverGlowPulse",
        )
        animated
    }
    Canvas(
        modifier = modifier.graphicsLayer { clip = false },
    ) {
        val radius = max(size.width, size.height) * 0.56f
        if (radius <= 0f) return@Canvas
        drawCircle(
            brush = Brush.radialGradient(
                colorStops = arrayOf(
                    0f to Color.White.copy(alpha = 0.22f * pulse),
                    0.42f to Color.White.copy(alpha = 0.10f * pulse),
                    1f to Color.Transparent,
                ),
                center = Offset(size.width / 2f, size.height / 2f),
                radius = radius,
            ),
            radius = radius,
        )
    }
}
