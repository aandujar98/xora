package com.arcadia.shell.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.sin

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
        ShellWallpaperStyle.UsagiPinkGlow -> UsagiPinkBackdrop(modifier)
        ShellWallpaperStyle.UsagiDarkVeil -> UsagiDarkBackdrop(modifier)
    }
}

@Composable
private fun UsagiPinkBackdrop(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(Color(0xFF2A1020))
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFF9AC8).copy(alpha = 0.72f),
                    Color(0xFFE070B0).copy(alpha = 0.38f),
                    Color(0xFF1A0C14).copy(alpha = 0.96f),
                ),
                center = Offset(size.width * 0.42f, size.height * 0.38f),
                radius = size.maxDimension * 0.72f,
            ),
        )
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFFFFD0EA).copy(alpha = 0.35f),
                    Color.Transparent,
                ),
                center = Offset(size.width * 0.78f, size.height * 0.72f),
                radius = size.maxDimension * 0.45f,
            ),
        )
    }
}

@Composable
private fun UsagiDarkBackdrop(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(Color(0xFF07070C))
        drawRect(
            brush = Brush.radialGradient(
                colors = listOf(
                    Color(0xFF6A2048).copy(alpha = 0.55f),
                    Color(0xFF1A1020).copy(alpha = 0.4f),
                    Color(0xFF050508).copy(alpha = 0.96f),
                ),
                center = Offset(size.width * 0.55f, size.height * 0.42f),
                radius = size.maxDimension * 0.7f,
            ),
        )
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color(0xFFFF7EB6).copy(alpha = 0.08f),
                    Color.Transparent,
                    Color(0xFFB080C8).copy(alpha = 0.12f),
                ),
            ),
        )
    }
}
