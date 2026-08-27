package com.arcadia.shell.libretro

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke

/**
 * System bezel / matte behind a fitted framebuffer. Drawn procedurally so we do not
 * ship large overlay PNGs; colors and frame shapes follow [platformId].
 */
@Composable
fun XoraSystemBezel(
    platformId: String,
    opacity: Float,
    contentAspect: Float,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val style = bezelStyleFor(platformId)
    val alpha = opacity.coerceIn(0f, 1f)

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val viewW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val viewH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val viewAspect = viewW / viewH
        val gameAspect = contentAspect.coerceIn(0.25f, 4f)

        // Same letterbox math as ContentScale.Fit.
        val (gameW, gameH) = if (gameAspect > viewAspect) {
            viewW to (viewW / gameAspect)
        } else {
            (viewH * gameAspect) to viewH
        }
        val left = (viewW - gameW) / 2f
        val top = (viewH - gameH) / 2f

        Canvas(modifier = Modifier.fillMaxSize()) {
            // Full-bleed matte.
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        style.matteCenter.copy(alpha = alpha * 0.55f),
                        style.matteEdge.copy(alpha = alpha),
                    ),
                    center = Offset(size.width / 2f, size.height / 2f),
                    radius = maxOf(size.width, size.height) * 0.72f,
                ),
            )

            val pad = style.framePadPx
            val frameL = (left - pad).coerceAtLeast(0f)
            val frameT = (top - pad).coerceAtLeast(0f)
            val frameW = (gameW + pad * 2f).coerceAtMost(size.width - frameL)
            val frameH = (gameH + pad * 2f).coerceAtMost(size.height - frameT)

            // Device / CRT shell around the game rect.
            drawRoundRect(
                color = style.shell.copy(alpha = alpha * 0.92f),
                topLeft = Offset(frameL, frameT),
                size = Size(frameW, frameH),
                cornerRadius = CornerRadius(style.cornerPx, style.cornerPx),
            )
            drawRoundRect(
                color = style.accent.copy(alpha = alpha * 0.55f),
                topLeft = Offset(frameL, frameT),
                size = Size(frameW, frameH),
                cornerRadius = CornerRadius(style.cornerPx, style.cornerPx),
                style = Stroke(width = style.strokePx),
            )

            // Inner screen cut — slightly darker lip.
            drawRoundRect(
                color = Color.Black.copy(alpha = alpha * 0.65f),
                topLeft = Offset(left - 2f, top - 2f),
                size = Size(gameW + 4f, gameH + 4f),
                cornerRadius = CornerRadius(style.innerCornerPx, style.innerCornerPx),
            )
        }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

@Composable
fun XoraBezelBackdrop(
    platformId: String,
    opacity: Float,
    modifier: Modifier = Modifier,
) {
    val style = bezelStyleFor(platformId)
    val alpha = opacity.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        style.matteCenter.copy(alpha = alpha * 0.5f),
                        style.matteEdge.copy(alpha = alpha),
                    ),
                ),
            ),
    )
}

private data class BezelStyle(
    val matteCenter: Color,
    val matteEdge: Color,
    val shell: Color,
    val accent: Color,
    val framePadPx: Float,
    val cornerPx: Float,
    val innerCornerPx: Float,
    val strokePx: Float,
)

private fun bezelStyleFor(platformId: String): BezelStyle = when (platformId) {
    "gb", "gbc" -> BezelStyle(
        matteCenter = Color(0xFF3A4450),
        matteEdge = Color(0xFF12161C),
        shell = Color(0xFFC5CCD6),
        accent = Color(0xFF6B7380),
        framePadPx = 28f,
        cornerPx = 36f,
        innerCornerPx = 8f,
        strokePx = 3f,
    )
    "gba" -> BezelStyle(
        matteCenter = Color(0xFF4A3A58),
        matteEdge = Color(0xFF140E1A),
        shell = Color(0xFF7B6B9A),
        accent = Color(0xFFD4C4F0),
        framePadPx = 24f,
        cornerPx = 28f,
        innerCornerPx = 6f,
        strokePx = 2.5f,
    )
    "nds", "3ds" -> BezelStyle(
        matteCenter = Color(0xFF2A3340),
        matteEdge = Color(0xFF0A0C10),
        shell = Color(0xFF1A1F28),
        accent = Color(0xFF5B8DEF),
        framePadPx = 18f,
        cornerPx = 20f,
        innerCornerPx = 4f,
        strokePx = 2f,
    )
    "nes", "snes", "n64", "gamecube", "wii", "genesis", "sega32x", "segacd",
    "mastersystem", "saturn", "ps1", "ps2", "dreamcast", "arcade", "neogeo",
    -> BezelStyle(
        matteCenter = Color(0xFF2C1810),
        matteEdge = Color(0xFF0A0604),
        shell = Color(0xFF1A1210),
        accent = Color(0xFF8B5A3C),
        framePadPx = 22f,
        cornerPx = 10f,
        innerCornerPx = 2f,
        strokePx = 3f,
    )
    "psp", "psvita" -> BezelStyle(
        matteCenter = Color(0xFF1E2430),
        matteEdge = Color(0xFF080A0E),
        shell = Color(0xFF0E1218),
        accent = Color(0xFF4A90D9),
        framePadPx = 20f,
        cornerPx = 24f,
        innerCornerPx = 4f,
        strokePx = 2f,
    )
    else -> BezelStyle(
        matteCenter = Color(0xFF1C222C),
        matteEdge = Color(0xFF080A0E),
        shell = Color(0xFF12161E),
        accent = Color(0xFF6A7380),
        framePadPx = 16f,
        cornerPx = 12f,
        innerCornerPx = 3f,
        strokePx = 2f,
    )
}
