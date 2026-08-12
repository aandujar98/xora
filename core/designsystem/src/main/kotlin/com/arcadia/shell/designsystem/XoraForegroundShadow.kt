package com.arcadia.shell.designsystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.min

/**
 * Shared drop shadow for XMB foreground assets (icons, box art, plates).
 *
 * Figma: X 10px, Y 10px, Spread 15px, Opacity 50%. Blur is 0 — only spread inflates the
 * silhouette, so every asset casts the same hard offset shadow.
 */
object XoraForegroundShadow {
    val OffsetX: Dp = 10.dp
    val OffsetY: Dp = 10.dp
    val Spread: Dp = 15.dp
    val Blur: Dp = 0.dp
    const val Alpha: Float = 0.5f
    val Ink: Color = Color.Black
}

/** Shape-based drop shadow that matches [XoraForegroundShadow] exactly. */
fun Modifier.xoraForegroundShadow(shape: Shape): Modifier = dropShadow(shape) {
    radius = XoraForegroundShadow.Blur.toPx()
    spread = XoraForegroundShadow.Spread.toPx()
    offset = Offset(
        XoraForegroundShadow.OffsetX.toPx(),
        XoraForegroundShadow.OffsetY.toPx(),
    )
    color = XoraForegroundShadow.Ink
    alpha = XoraForegroundShadow.Alpha
}

/**
 * Draws a silhouette pass for vector glyphs: offset by X/Y, inflated by spread, 50% black.
 * Callers then draw the real asset on top. Draws outside the current size on purpose.
 */
fun DrawScope.drawXoraForegroundSilhouette(drawGlyph: DrawScope.() -> Unit) {
    val ox = XoraForegroundShadow.OffsetX.toPx()
    val oy = XoraForegroundShadow.OffsetY.toPx()
    val spreadPx = XoraForegroundShadow.Spread.toPx()
    translate(ox, oy) {
        val dim = min(size.width, size.height)
        val scale = if (dim > 0f) (dim + spreadPx * 2f) / dim else 1f
        scale(scale, scale, pivot = Offset(size.width / 2f, size.height / 2f)) {
            drawGlyph()
        }
    }
}
