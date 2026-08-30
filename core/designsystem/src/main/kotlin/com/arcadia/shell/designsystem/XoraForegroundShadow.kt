package com.arcadia.shell.designsystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Soft hover drop shadow for XMB foreground assets (icons, box art, plates).
 *
 * Glyphs rasterize a shape-following blur; plates use [xoraForegroundShadow] with a lighter halo
 * so capsules do not sit in a dark fog.
 */
object XoraForegroundShadow {
    val OffsetX: Dp = 2.dp
    val OffsetY: Dp = 3.dp
    val Spread: Dp = 0.dp
    /** Glyph silhouette blur — wide enough to read as a halo, tight enough to follow the shape. */
    val Blur: Dp = 12.dp
    const val Alpha: Float = 0.40f
    val PlateBlur: Dp = 16.dp
    const val PlateAlpha: Float = 0.22f
    val Ink: Color = Color.Black
}

/** Shape-based drop shadow for plates / pills (not vector glyphs). */
fun Modifier.xoraForegroundShadow(shape: Shape): Modifier = dropShadow(shape) {
    radius = XoraForegroundShadow.PlateBlur.toPx()
    spread = XoraForegroundShadow.Spread.toPx()
    offset = Offset(
        XoraForegroundShadow.OffsetX.toPx(),
        XoraForegroundShadow.OffsetY.toPx(),
    )
    color = XoraForegroundShadow.Ink
    alpha = XoraForegroundShadow.PlateAlpha
}

/**
 * Offset silhouette for callers that still draw a shadow in-canvas.
 * Vector XMB glyphs rasterize a shape-following blur instead.
 */
fun DrawScope.drawXoraForegroundSilhouette(drawGlyph: DrawScope.() -> Unit) {
    translate(
        XoraForegroundShadow.OffsetX.toPx(),
        XoraForegroundShadow.OffsetY.toPx(),
        drawGlyph,
    )
}
