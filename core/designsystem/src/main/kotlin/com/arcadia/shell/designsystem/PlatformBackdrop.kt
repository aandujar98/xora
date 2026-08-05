package com.arcadia.shell.designsystem

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.min

/**
 * Stylized, era-tinted atmosphere for a library platform tab.
 * Hand-drawn silhouettes / watermark marks only — no scraped official logo assets.
 */
data class PlatformBackdropStyle(
    val wash: List<Color>,
    val mark: Color,
    val accent: Color,
    val markKind: PlatformMarkKind,
)

enum class PlatformMarkKind {
    None,
    NesBrick,
    SnesPad,
    N64Trident,
    HandheldPortrait,
    Clamshell,
    Cube,
    DiscRing,
    JoyRail,
    PsShapes,
    DualShock,
    HandheldWide,
    Swirl,
    ArcadeCab,
    Genesis,
    GenericPad,
}

fun platformBackdropStyle(platformId: String?): PlatformBackdropStyle? {
    if (platformId.isNullOrBlank()) return null
    return when (platformId) {
        "nes" -> PlatformBackdropStyle(
            wash = listOf(Color(0xFF6B1D1D), Color(0xFF2A1010), Color.Transparent),
            mark = Color(0xFFE8D4D4).copy(alpha = 0.14f),
            accent = Color(0xFFC44B4B).copy(alpha = 0.18f),
            markKind = PlatformMarkKind.NesBrick,
        )
        "snes" -> PlatformBackdropStyle(
            wash = listOf(Color(0xFF4A3A78), Color(0xFF1E1638), Color.Transparent),
            mark = Color(0xFFD8CFF0).copy(alpha = 0.16f),
            accent = Color(0xFF9B7ED9).copy(alpha = 0.20f),
            markKind = PlatformMarkKind.SnesPad,
        )
        "n64" -> PlatformBackdropStyle(
            wash = listOf(Color(0xFF1A4A2E), Color(0xFF0C2418), Color.Transparent),
            mark = Color(0xFFC8E8D4).copy(alpha = 0.14f),
            accent = Color(0xFF3D9B5F).copy(alpha = 0.18f),
            markKind = PlatformMarkKind.N64Trident,
        )
        "gb", "gbc" -> PlatformBackdropStyle(
            wash = listOf(Color(0xFF3A4A28), Color(0xFF1A2214), Color.Transparent),
            mark = Color(0xFFD4E0C0).copy(alpha = 0.14f),
            accent = Color(0xFF8FA86A).copy(alpha = 0.16f),
            markKind = PlatformMarkKind.HandheldPortrait,
        )
        "gba" -> PlatformBackdropStyle(
            wash = listOf(Color(0xFF3A2A68), Color(0xFF181230), Color.Transparent),
            mark = Color(0xFFD0C8F0).copy(alpha = 0.14f),
            accent = Color(0xFF7A6AB8).copy(alpha = 0.18f),
            markKind = PlatformMarkKind.HandheldPortrait,
        )
        "nds", "3ds" -> PlatformBackdropStyle(
            wash = listOf(Color(0xFF1A3A68), Color(0xFF0C1A30), Color.Transparent),
            mark = Color(0xFFC8D8F0).copy(alpha = 0.14f),
            accent = Color(0xFF4A8AD0).copy(alpha = 0.18f),
            markKind = PlatformMarkKind.Clamshell,
        )
        "gamecube" -> PlatformBackdropStyle(
            wash = listOf(Color(0xFF3A2868), Color(0xFF181230), Color.Transparent),
            mark = Color(0xFFD4C8F0).copy(alpha = 0.14f),
            accent = Color(0xFF6B4FC8).copy(alpha = 0.20f),
            markKind = PlatformMarkKind.Cube,
        )
        "wii", "wiiu" -> PlatformBackdropStyle(
            wash = listOf(Color(0xFF2A3A58), Color(0xFF121A28), Color.Transparent),
            mark = Color(0xFFE0E8F0).copy(alpha = 0.12f),
            accent = Color(0xFF6A9AD0).copy(alpha = 0.16f),
            markKind = PlatformMarkKind.DiscRing,
        )
        "switch" -> PlatformBackdropStyle(
            wash = listOf(Color(0xFF1A2840), Color(0xFF0C1420), Color.Transparent),
            mark = Color(0xFFE0E8F4).copy(alpha = 0.12f),
            accent = Color(0xFFE60012).copy(alpha = 0.14f),
            markKind = PlatformMarkKind.JoyRail,
        )
        "ps1" -> PlatformBackdropStyle(
            wash = listOf(Color(0xFF1A2848), Color(0xFF0C1424), Color.Transparent),
            mark = Color(0xFFD0DCF0).copy(alpha = 0.14f),
            accent = Color(0xFF4A90D0).copy(alpha = 0.18f),
            markKind = PlatformMarkKind.PsShapes,
        )
        "ps2" -> PlatformBackdropStyle(
            wash = listOf(Color(0xFF0C1840), Color(0xFF060C20), Color.Transparent),
            mark = Color(0xFFA8C0F0).copy(alpha = 0.14f),
            accent = Color(0xFF2A5AD0).copy(alpha = 0.20f),
            markKind = PlatformMarkKind.PsShapes,
        )
        "ps3" -> PlatformBackdropStyle(
            wash = listOf(Color(0xFF1A1A28), Color(0xFF0C0C14), Color.Transparent),
            mark = Color(0xFFE0E0E8).copy(alpha = 0.12f),
            accent = Color(0xFF808090).copy(alpha = 0.16f),
            markKind = PlatformMarkKind.DualShock,
        )
        "psp", "psvita" -> PlatformBackdropStyle(
            wash = listOf(Color(0xFF1A2438), Color(0xFF0C121C), Color.Transparent),
            mark = Color(0xFFC8D4E8).copy(alpha = 0.14f),
            accent = Color(0xFF5A7AA8).copy(alpha = 0.16f),
            markKind = PlatformMarkKind.HandheldWide,
        )
        "dreamcast" -> PlatformBackdropStyle(
            wash = listOf(Color(0xFF284058), Color(0xFF142028), Color.Transparent),
            mark = Color(0xFFE0ECF4).copy(alpha = 0.14f),
            accent = Color(0xFF4A90C0).copy(alpha = 0.18f),
            markKind = PlatformMarkKind.Swirl,
        )
        "genesis", "sega32x", "segacd", "mastersystem", "gamegear" -> PlatformBackdropStyle(
            wash = listOf(Color(0xFF1A2848), Color(0xFF0C1424), Color.Transparent),
            mark = Color(0xFFC0D0F0).copy(alpha = 0.14f),
            accent = Color(0xFF3A60C0).copy(alpha = 0.18f),
            markKind = PlatformMarkKind.Genesis,
        )
        "saturn" -> PlatformBackdropStyle(
            wash = listOf(Color(0xFF283848), Color(0xFF141C24), Color.Transparent),
            mark = Color(0xFFD0DCE8).copy(alpha = 0.14f),
            accent = Color(0xFF5A7A98).copy(alpha = 0.16f),
            markKind = PlatformMarkKind.DiscRing,
        )
        "arcade", "neogeo" -> PlatformBackdropStyle(
            wash = listOf(Color(0xFF3A1820), Color(0xFF1A0C10), Color.Transparent),
            mark = Color(0xFFF0D0D0).copy(alpha = 0.12f),
            accent = Color(0xFFC04040).copy(alpha = 0.18f),
            markKind = PlatformMarkKind.ArcadeCab,
        )
        else -> PlatformBackdropStyle(
            wash = listOf(Color(0xFF1A3048), Color(0xFF0C1824), Color.Transparent),
            mark = Color(0xFFD0E0F0).copy(alpha = 0.10f),
            accent = Color(0xFF4A8AD0).copy(alpha = 0.12f),
            markKind = PlatformMarkKind.GenericPad,
        )
    }
}

fun DrawScope.drawPlatformBackdrop(style: PlatformBackdropStyle, alpha: Float = 1f) {
    if (alpha <= 0.01f) return
    val a = alpha.coerceIn(0f, 1f)
    drawRect(
        brush = Brush.radialGradient(
            colors = style.wash.map { it.copy(alpha = it.alpha * a) },
            center = Offset(size.width * 0.72f, size.height * 0.38f),
            radius = size.maxDimension * 0.85f,
        ),
    )
    drawRect(
        brush = Brush.linearGradient(
            colors = listOf(
                style.accent.copy(alpha = style.accent.alpha * a),
                Color.Transparent,
            ),
            start = Offset(0f, 0f),
            end = Offset(size.width * 0.55f, size.height),
        ),
    )

    val markSize = min(size.width, size.height) * 0.42f
    translate(
        left = size.width * 0.58f,
        top = size.height * 0.18f,
    ) {
        drawPlatformMark(
            kind = style.markKind,
            color = style.mark.copy(alpha = style.mark.alpha * a),
            accent = style.accent.copy(alpha = style.accent.alpha * a * 0.85f),
            extent = markSize,
        )
    }
}

private fun DrawScope.drawPlatformMark(
    kind: PlatformMarkKind,
    color: Color,
    accent: Color,
    extent: Float,
) {
    when (kind) {
        PlatformMarkKind.None -> Unit
        PlatformMarkKind.NesBrick -> {
            drawRoundRect(
                color = color,
                topLeft = Offset(extent * 0.05f, extent * 0.28f),
                size = Size(extent * 0.9f, extent * 0.38f),
                cornerRadius = CornerRadius(extent * 0.04f),
            )
            drawRect(
                color = accent,
                topLeft = Offset(extent * 0.18f, extent * 0.40f),
                size = Size(extent * 0.22f, extent * 0.12f),
            )
            drawCircle(color = accent, radius = extent * 0.06f, center = Offset(extent * 0.72f, extent * 0.46f))
        }
        PlatformMarkKind.SnesPad -> {
            drawRoundRect(
                color = color,
                topLeft = Offset(extent * 0.08f, extent * 0.30f),
                size = Size(extent * 0.84f, extent * 0.36f),
                cornerRadius = CornerRadius(extent * 0.12f),
            )
            drawCircle(color = accent, radius = extent * 0.05f, center = Offset(extent * 0.28f, extent * 0.48f))
            drawCircle(color = accent, radius = extent * 0.05f, center = Offset(extent * 0.72f, extent * 0.44f))
            drawCircle(color = accent, radius = extent * 0.05f, center = Offset(extent * 0.78f, extent * 0.52f))
        }
        PlatformMarkKind.N64Trident -> {
            val path = Path().apply {
                moveTo(extent * 0.5f, extent * 0.12f)
                lineTo(extent * 0.62f, extent * 0.42f)
                lineTo(extent * 0.88f, extent * 0.46f)
                lineTo(extent * 0.68f, extent * 0.62f)
                lineTo(extent * 0.74f, extent * 0.88f)
                lineTo(extent * 0.5f, extent * 0.72f)
                lineTo(extent * 0.26f, extent * 0.88f)
                lineTo(extent * 0.32f, extent * 0.62f)
                lineTo(extent * 0.12f, extent * 0.46f)
                lineTo(extent * 0.38f, extent * 0.42f)
                close()
            }
            drawPath(path, color = color)
            drawCircle(color = accent, radius = extent * 0.08f, center = Offset(extent * 0.5f, extent * 0.52f))
        }
        PlatformMarkKind.HandheldPortrait -> {
            drawRoundRect(
                color = color,
                topLeft = Offset(extent * 0.28f, extent * 0.08f),
                size = Size(extent * 0.44f, extent * 0.84f),
                cornerRadius = CornerRadius(extent * 0.08f),
            )
            drawRoundRect(
                color = accent,
                topLeft = Offset(extent * 0.34f, extent * 0.16f),
                size = Size(extent * 0.32f, extent * 0.28f),
                cornerRadius = CornerRadius(extent * 0.03f),
            )
        }
        PlatformMarkKind.Clamshell -> {
            drawRoundRect(
                color = color,
                topLeft = Offset(extent * 0.12f, extent * 0.10f),
                size = Size(extent * 0.76f, extent * 0.34f),
                cornerRadius = CornerRadius(extent * 0.04f),
            )
            drawRoundRect(
                color = color,
                topLeft = Offset(extent * 0.12f, extent * 0.52f),
                size = Size(extent * 0.76f, extent * 0.34f),
                cornerRadius = CornerRadius(extent * 0.04f),
            )
            drawRoundRect(
                color = accent,
                topLeft = Offset(extent * 0.20f, extent * 0.16f),
                size = Size(extent * 0.60f, extent * 0.20f),
                cornerRadius = CornerRadius(extent * 0.02f),
            )
        }
        PlatformMarkKind.Cube -> {
            rotate(degrees = 18f, pivot = Offset(extent * 0.5f, extent * 0.5f)) {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(extent * 0.18f, extent * 0.18f),
                    size = Size(extent * 0.64f, extent * 0.64f),
                    cornerRadius = CornerRadius(extent * 0.06f),
                )
                drawCircle(color = accent, radius = extent * 0.12f, center = Offset(extent * 0.5f, extent * 0.5f))
            }
        }
        PlatformMarkKind.DiscRing -> {
            drawCircle(
                color = color,
                radius = extent * 0.38f,
                center = Offset(extent * 0.5f, extent * 0.5f),
                style = Stroke(width = extent * 0.08f),
            )
            drawCircle(
                color = accent,
                radius = extent * 0.14f,
                center = Offset(extent * 0.5f, extent * 0.5f),
            )
        }
        PlatformMarkKind.JoyRail -> {
            drawRoundRect(
                color = accent,
                topLeft = Offset(extent * 0.08f, extent * 0.22f),
                size = Size(extent * 0.18f, extent * 0.56f),
                cornerRadius = CornerRadius(extent * 0.08f),
            )
            drawRoundRect(
                color = color,
                topLeft = Offset(extent * 0.30f, extent * 0.28f),
                size = Size(extent * 0.40f, extent * 0.44f),
                cornerRadius = CornerRadius(extent * 0.04f),
            )
            drawRoundRect(
                color = accent,
                topLeft = Offset(extent * 0.74f, extent * 0.22f),
                size = Size(extent * 0.18f, extent * 0.56f),
                cornerRadius = CornerRadius(extent * 0.08f),
            )
        }
        PlatformMarkKind.PsShapes -> {
            // Abstract △ ○ ✕ □ silhouette cluster — evocative, not a trademark lockup.
            val stroke = Stroke(width = extent * 0.045f, cap = StrokeCap.Round)
            val tri = Path().apply {
                moveTo(extent * 0.28f, extent * 0.62f)
                lineTo(extent * 0.40f, extent * 0.38f)
                lineTo(extent * 0.52f, extent * 0.62f)
                close()
            }
            drawPath(tri, color = color, style = stroke)
            drawCircle(
                color = accent,
                radius = extent * 0.10f,
                center = Offset(extent * 0.68f, extent * 0.42f),
                style = stroke,
            )
            drawLine(
                color = color,
                start = Offset(extent * 0.58f, extent * 0.58f),
                end = Offset(extent * 0.78f, extent * 0.78f),
                strokeWidth = extent * 0.045f,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = color,
                start = Offset(extent * 0.78f, extent * 0.58f),
                end = Offset(extent * 0.58f, extent * 0.78f),
                strokeWidth = extent * 0.045f,
                cap = StrokeCap.Round,
            )
            drawRoundRect(
                color = accent,
                topLeft = Offset(extent * 0.22f, extent * 0.70f),
                size = Size(extent * 0.16f, extent * 0.16f),
                cornerRadius = CornerRadius(extent * 0.02f),
                style = stroke,
            )
        }
        PlatformMarkKind.DualShock -> {
            drawRoundRect(
                color = color,
                topLeft = Offset(extent * 0.06f, extent * 0.32f),
                size = Size(extent * 0.88f, extent * 0.36f),
                cornerRadius = CornerRadius(extent * 0.16f),
            )
            drawCircle(color = accent, radius = extent * 0.07f, center = Offset(extent * 0.30f, extent * 0.52f))
            drawCircle(color = accent, radius = extent * 0.07f, center = Offset(extent * 0.70f, extent * 0.48f))
        }
        PlatformMarkKind.HandheldWide -> {
            drawRoundRect(
                color = color,
                topLeft = Offset(extent * 0.04f, extent * 0.30f),
                size = Size(extent * 0.92f, extent * 0.40f),
                cornerRadius = CornerRadius(extent * 0.10f),
            )
            drawRoundRect(
                color = accent,
                topLeft = Offset(extent * 0.28f, extent * 0.38f),
                size = Size(extent * 0.44f, extent * 0.24f),
                cornerRadius = CornerRadius(extent * 0.03f),
            )
        }
        PlatformMarkKind.Swirl -> {
            drawCircle(
                color = color,
                radius = extent * 0.36f,
                center = Offset(extent * 0.5f, extent * 0.5f),
                style = Stroke(width = extent * 0.07f),
            )
            drawArc(
                color = accent,
                startAngle = -40f,
                sweepAngle = 220f,
                useCenter = false,
                topLeft = Offset(extent * 0.22f, extent * 0.22f),
                size = Size(extent * 0.56f, extent * 0.56f),
                style = Stroke(width = extent * 0.05f, cap = StrokeCap.Round),
            )
        }
        PlatformMarkKind.ArcadeCab -> {
            drawRoundRect(
                color = color,
                topLeft = Offset(extent * 0.22f, extent * 0.08f),
                size = Size(extent * 0.56f, extent * 0.78f),
                cornerRadius = CornerRadius(extent * 0.04f),
            )
            drawRoundRect(
                color = accent,
                topLeft = Offset(extent * 0.30f, extent * 0.16f),
                size = Size(extent * 0.40f, extent * 0.28f),
                cornerRadius = CornerRadius(extent * 0.02f),
            )
            drawCircle(color = accent, radius = extent * 0.06f, center = Offset(extent * 0.42f, extent * 0.62f))
            drawCircle(color = accent, radius = extent * 0.05f, center = Offset(extent * 0.58f, extent * 0.62f))
        }
        PlatformMarkKind.Genesis -> {
            drawRoundRect(
                color = color,
                topLeft = Offset(extent * 0.06f, extent * 0.30f),
                size = Size(extent * 0.88f, extent * 0.40f),
                cornerRadius = CornerRadius(extent * 0.08f),
            )
            drawRect(
                color = accent,
                topLeft = Offset(extent * 0.20f, extent * 0.42f),
                size = Size(extent * 0.16f, extent * 0.16f),
            )
            drawCircle(color = accent, radius = extent * 0.05f, center = Offset(extent * 0.68f, extent * 0.46f))
            drawCircle(color = accent, radius = extent * 0.05f, center = Offset(extent * 0.78f, extent * 0.54f))
        }
        PlatformMarkKind.GenericPad -> {
            drawRoundRect(
                color = color,
                topLeft = Offset(extent * 0.10f, extent * 0.32f),
                size = Size(extent * 0.80f, extent * 0.36f),
                cornerRadius = CornerRadius(extent * 0.14f),
            )
            drawCircle(color = accent, radius = extent * 0.06f, center = Offset(extent * 0.32f, extent * 0.50f))
            drawCircle(color = accent, radius = extent * 0.06f, center = Offset(extent * 0.68f, extent * 0.50f))
        }
    }
}
