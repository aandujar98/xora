package com.arcadia.shell.designsystem

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.sin

private val FlowSkyTop = Color(0xFF2ACBFD)
private val FlowSkyBottom = Color.White

// Long, unequal cycles so the two bands never line back up into an obvious loop.
private const val FRONT_WAVE_CYCLE_MS = 96_000
private const val BACK_WAVE_CYCLE_MS = 138_000
private const val FRONT_WAVE_TRAVEL = 74f
private const val BACK_WAVE_TRAVEL = 52f
private const val WAVE_RISE = 20f
private const val TWO_PI = (Math.PI * 2).toFloat()

/**
 * Full-bleed theme backdrop when no custom wallpaper file is set.
 * Patterns are original geometric treatments — not ripped game UI.
 */
@Composable
fun ShellThemeBackdrop(
    style: ShellWallpaperStyle,
    modifier: Modifier = Modifier,
) {
    when (style) {
        ShellWallpaperStyle.XoraFlowWave -> XoraFlowBackdrop(modifier)
        ShellWallpaperStyle.Persona3Tartan -> Persona3TartanBackdrop(modifier)
        ShellWallpaperStyle.MidnightGradient -> MidnightBackdrop(modifier)
        ShellWallpaperStyle.ClassicXmbWave -> ClassicXmbBackdrop(modifier)
        ShellWallpaperStyle.WarmArcadeGlow -> WarmArcadeBackdrop(modifier)
    }
}

/**
 * Default shell wallpaper: the authored HOME bands over a `#2ACBFD` → white sky.
 *
 * The two bands travel on separate long cycles, so the crossing point drifts instead of the whole
 * picture sliding as one slab. Both cycles are full sine periods, which start and end at zero
 * travel — the loop can restart forever with no seam. Amplitudes stay well inside the slack the
 * artwork has beyond the artboard, so an edge can never swim into frame.
 */
@Composable
private fun XoraFlowBackdrop(modifier: Modifier = Modifier) {
    val drift = rememberWaveDrift()
    WaveSky(
        topColor = FlowSkyTop,
        bottomColor = FlowSkyBottom,
        field = HomeWaveField,
        modifier = modifier.fillMaxSize(),
        layerDrift = drift,
        driftMargin = maxOf(FRONT_WAVE_TRAVEL, BACK_WAVE_TRAVEL, WAVE_RISE) + 8f,
    )
}

@Composable
private fun rememberWaveDrift(): (Int) -> Offset {
    if (!rememberAmbientMotionActive()) return { Offset.Zero }
    val transition = rememberInfiniteTransition(label = "xoraFlowWave")
    val front by transition.animateFloat(
        initialValue = 0f,
        targetValue = TWO_PI,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = FRONT_WAVE_CYCLE_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "xoraFlowFront",
    )
    val back by transition.animateFloat(
        initialValue = 0f,
        targetValue = TWO_PI,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = BACK_WAVE_CYCLE_MS, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "xoraFlowBack",
    )
    return { index ->
        val phase = if (index == 0) back else front
        val amplitude = if (index == 0) BACK_WAVE_TRAVEL else FRONT_WAVE_TRAVEL
        Offset(
            x = sin(phase) * amplitude,
            y = cos(phase) * WAVE_RISE,
        )
    }
}

@Composable
private fun Persona3TartanBackdrop(modifier: Modifier = Modifier) {
    val navy = Color(0xFF07101F)
    val navyMid = Color(0xFF0E1A32)
    val stripeBlue = Color(0xFF1A3A6B)
    val stripeGold = Color(0xFFE8C547)
    val stripeWine = Color(0xFF5A2040)
    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.verticalGradient(listOf(navy, navyMid, navy)),
        )
        val step = size.minDimension * 0.07f
        var x = -size.height
        while (x < size.width + size.height) {
            drawLine(
                color = stripeBlue.copy(alpha = 0.35f),
                start = Offset(x, 0f),
                end = Offset(x + size.height, size.height),
                strokeWidth = step * 0.55f,
            )
            drawLine(
                color = stripeWine.copy(alpha = 0.18f),
                start = Offset(x + step * 0.5f, 0f),
                end = Offset(x + step * 0.5f + size.height, size.height),
                strokeWidth = step * 0.22f,
            )
            x += step
        }
        var y = -size.width
        while (y < size.height + size.width) {
            drawLine(
                color = stripeBlue.copy(alpha = 0.22f),
                start = Offset(0f, y),
                end = Offset(size.width, y + size.width * 0.15f),
                strokeWidth = step * 0.18f,
            )
            y += step * 1.15f
        }
        // Subtle gold accent grid (tartan cross)
        val goldStep = step * 2.4f
        var gx = 0f
        while (gx < size.width) {
            drawLine(
                color = stripeGold.copy(alpha = 0.12f),
                start = Offset(gx, 0f),
                end = Offset(gx, size.height),
                strokeWidth = 1.5f,
            )
            gx += goldStep
        }
        var gy = 0f
        while (gy < size.height) {
            drawLine(
                color = stripeGold.copy(alpha = 0.10f),
                start = Offset(0f, gy),
                end = Offset(size.width, gy),
                strokeWidth = 1.5f,
            )
            gy += goldStep
        }
        // Soft vignette
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.45f)),
                center = Offset(size.width * 0.5f, size.height * 0.4f),
                radius = size.maxDimension * 0.75f,
            ),
        )
        // Thin gold horizon bar
        drawLine(
            color = stripeGold.copy(alpha = 0.55f),
            start = Offset(0f, size.height * 0.78f),
            end = Offset(size.width, size.height * 0.78f),
            strokeWidth = 2.5f,
        )
    }
}

@Composable
private fun MidnightBackdrop(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF05060C),
                    Color(0xFF0A1020),
                    Color(0xFF12182E),
                    Color(0xFF080A12),
                ),
            ),
        )
        val sparkle = Color(0xFFC8D4FF)
        val seeds = listOf(
            0.12f to 0.18f, 0.28f to 0.08f, 0.45f to 0.22f, 0.62f to 0.12f,
            0.78f to 0.20f, 0.88f to 0.09f, 0.18f to 0.35f, 0.55f to 0.32f,
            0.72f to 0.40f, 0.35f to 0.48f, 0.08f to 0.55f, 0.92f to 0.45f,
        )
        seeds.forEachIndexed { i, (nx, ny) ->
            val r = if (i % 3 == 0) 1.8f else 1.1f
            drawCircle(
                color = sparkle.copy(alpha = if (i % 2 == 0) 0.55f else 0.28f),
                radius = r,
                center = Offset(size.width * nx, size.height * ny),
            )
        }
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f)),
                startY = size.height * 0.55f,
                endY = size.height,
            ),
        )
    }
}

@Composable
private fun ClassicXmbBackdrop(modifier: Modifier = Modifier) {
    val animate = rememberAmbientMotionActive()
    val drift = if (animate) {
        val phase by rememberInfiniteTransition(label = "classicXmbWave").animateFloat(
            initialValue = 0f,
            targetValue = (Math.PI * 2.0).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 16_000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
            label = "classicXmbWavePhase",
        )
        phase
    } else {
        0f
    }
    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF041018),
                    Color(0xFF0A2838),
                    Color(0xFF124858),
                    Color(0xFF0A2030),
                ),
            ),
        )
        val waveColor = Color(0xFF5EC8E8)
        for (band in 0 until 5) {
            val baseY = size.height * (0.35f + band * 0.1f)
            val xShift = cos(drift + band * 0.55f) * (18f + band * 3f)
            val path = Path()
            path.moveTo(-48f + xShift, baseY)
            var x = -48f
            while (x <= size.width + 48f) {
                val y = baseY + sin((x / size.width) * Math.PI * 2.0 + band + drift).toFloat() *
                    (18f + band * 4f)
                path.lineTo(x + xShift, y)
                x += 8f
            }
            drawPath(
                path = path,
                color = waveColor.copy(alpha = 0.08f + band * 0.03f),
                style = Stroke(width = 2.5f),
            )
        }
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Black.copy(alpha = 0.25f),
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.25f),
                ),
            ),
        )
    }
}

@Composable
private fun WarmArcadeBackdrop(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(Color(0xFF120A06))
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFFA040).copy(alpha = 0.55f),
                    Color(0xFFE06020).copy(alpha = 0.28f),
                    Color(0xFF140C08).copy(alpha = 0.95f),
                ),
                center = Offset(size.width * 0.5f, size.height * 0.62f),
                radius = size.maxDimension * 0.7f,
            ),
        )
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color(0xFF1A0C08).copy(alpha = 0.65f),
                    Color.Transparent,
                    Color(0xFF0A0604).copy(alpha = 0.8f),
                ),
            ),
        )
        // Soft scanline suggestion
        val step = 6f
        var y = 0f
        while (y < size.height) {
            drawLine(
                color = Color.Black.copy(alpha = 0.12f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f,
            )
            y += step
        }
    }
}
