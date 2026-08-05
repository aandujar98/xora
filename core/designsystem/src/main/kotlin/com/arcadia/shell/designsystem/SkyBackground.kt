package com.arcadia.shell.designsystem

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Soft SORA sky atmosphere: pale blue mist / dusk gradient with subtle four-point PS5-style
 * sparkles. Optional [platformId] overlays a muted, era-tinted watermark that crossfades as
 * the XMB system tab changes (All / Favourites / Recent / Apps keep the default sky).
 *
 * Sparkle animation pauses when the composition lifecycle is not RESUMED (background / covered)
 * to cut CPU on handhelds.
 */
@Composable
fun SkyBackground(
    modifier: Modifier = Modifier,
    darkTheme: Boolean = isSystemInDarkTheme(),
    sparkle: Boolean = true,
    platformId: String? = null,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val reduceMotion = rememberReduceMotion()
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val animateSparkles = sparkle && !reduceMotion &&
        lifecycleState.isAtLeast(Lifecycle.State.RESUMED)

    val sparkles = remember(darkTheme) {
        List(if (darkTheme) SPARKLE_COUNT_DARK else SPARKLE_COUNT_LIGHT) { index ->
            SparkleSpec(
                xFrac = Random(index * 31 + 7).nextFloat(),
                yFrac = Random(index * 17 + 3).nextFloat() * 0.85f,
                sizePx = 4f + Random(index * 11).nextFloat() * (if (darkTheme) 7f else 9f),
                phase = Random(index * 23).nextFloat(),
                blueTint = index % 3 == 0,
            )
        }
    }

    val pulse = if (animateSparkles) {
        val transition = rememberInfiniteTransition(label = "skySparkle")
        val value by transition.animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 5_400, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "skySparklePulse",
        )
        value
    } else {
        0.72f
    }

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .arcadiaHazeSource(zIndex = 0f),
        ) {
            drawSkyGradient(darkTheme)
            drawSoftClouds(darkTheme)
            if (sparkle) {
                sparkles.forEachIndexed { index, spec ->
                    val alphaBoost = 0.55f + 0.45f * ((pulse + spec.phase) % 1f)
                    drawFourPointSparkle(
                        center = Offset(size.width * spec.xFrac, size.height * spec.yFrac),
                        radius = spec.sizePx,
                        color = (if (spec.blueTint) SparkleBlue else SparkleLight)
                            .copy(alpha = (if (darkTheme) 0.28f else 0.42f) * alphaBoost),
                        rotationDeg = index * 12f,
                    )
                }
            }
        }

        Crossfade(
            targetState = platformId,
            animationSpec = arcadiaTween(ArcadiaMotion.Medium),
            label = "platformBackdropCrossfade",
            modifier = Modifier.fillMaxSize(),
        ) { id ->
            val style = remember(id) { platformBackdropStyle(id) }
            if (style != null) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawPlatformBackdrop(style, alpha = 1f)
                }
            }
        }

        content()
    }
}

private data class SparkleSpec(
    val xFrac: Float,
    val yFrac: Float,
    val sizePx: Float,
    val phase: Float,
    val blueTint: Boolean,
)

private fun DrawScope.drawSkyGradient(darkTheme: Boolean) {
    val colors = if (darkTheme) {
        listOf(DuskTop, DuskMid, DuskHorizon)
    } else {
        listOf(SkyTop, SkyMid, SkyHorizon, SkyCloud)
    }
    drawRect(
        brush = Brush.verticalGradient(colors = colors),
    )
    // Soft diagonal light wash — ethereal depth without a second flat fill.
    drawRect(
        brush = Brush.linearGradient(
            colors = if (darkTheme) {
                listOf(
                    Accent.copy(alpha = 0.10f),
                    Color.Transparent,
                    AccentBright.copy(alpha = 0.06f),
                )
            } else {
                listOf(
                    Color.White.copy(alpha = 0.35f),
                    Color.Transparent,
                    SoraBlue.copy(alpha = 0.08f),
                )
            },
            start = Offset(0f, 0f),
            end = Offset(size.width, size.height),
        ),
    )
}

private fun DrawScope.drawSoftClouds(darkTheme: Boolean) {
    val cloudColor = if (darkTheme) {
        Color.White.copy(alpha = 0.04f)
    } else {
        Color.White.copy(alpha = 0.45f)
    }
    val bandY = size.height * 0.72f
    drawCircle(
        color = cloudColor,
        radius = size.width * 0.28f,
        center = Offset(size.width * 0.15f, bandY),
    )
    drawCircle(
        color = cloudColor.copy(alpha = cloudColor.alpha * 0.85f),
        radius = size.width * 0.22f,
        center = Offset(size.width * 0.38f, bandY + size.height * 0.04f),
    )
    drawCircle(
        color = cloudColor,
        radius = size.width * 0.32f,
        center = Offset(size.width * 0.72f, bandY + size.height * 0.02f),
    )
    drawCircle(
        color = cloudColor.copy(alpha = cloudColor.alpha * 0.7f),
        radius = size.width * 0.18f,
        center = Offset(size.width * 0.92f, bandY - size.height * 0.02f),
    )
}

private fun DrawScope.drawFourPointSparkle(
    center: Offset,
    radius: Float,
    color: Color,
    rotationDeg: Float,
) {
    rotate(degrees = rotationDeg, pivot = center) {
        val path = Path()
        val tips = 4
        for (i in 0 until tips * 2) {
            val angle = Math.PI * i / tips - Math.PI / 2
            val r = if (i % 2 == 0) radius else radius * 0.22f
            val x = center.x + (cos(angle) * r).toFloat()
            val y = center.y + (sin(angle) * r).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path, color = color)
    }
}

private const val SPARKLE_COUNT_LIGHT = 4
private const val SPARKLE_COUNT_DARK = 3
