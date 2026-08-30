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
 * A wide, light blur sits a few pixels down/right so glyphs float without a hard stamp.
 */
object XoraForegroundShadow {
    val OffsetX: Dp = 4.dp
    val OffsetY: Dp = 6.dp
    val Spread: Dp = 0.dp
    val Blur: Dp = 40.dp
    const val Alpha: Float = 0.45f
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
 * Offset silhouette for callers that still draw a shadow in-canvas.
 * Prefer [Modifier.blur] on a dedicated shadow layer for vector glyphs.
 */
fun DrawScope.drawXoraForegroundSilhouette(drawGlyph: DrawScope.() -> Unit) {
    translate(
        XoraForegroundShadow.OffsetX.toPx(),
        XoraForegroundShadow.OffsetY.toPx(),
        drawGlyph,
    )
}
