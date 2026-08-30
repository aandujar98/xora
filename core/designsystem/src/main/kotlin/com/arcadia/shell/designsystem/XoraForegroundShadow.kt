package com.arcadia.shell.designsystem

import android.graphics.BlurMaskFilter
import android.graphics.RectF
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max

/**
 * Soft hover drop shadow for XMB foreground assets (icons, box art, plates).
 *
 * A light, wide blur sits a couple of pixels down/right so glyphs float without a hard stamp.
 */
object XoraForegroundShadow {
    val OffsetX: Dp = 2.dp
    val OffsetY: Dp = 3.dp
    val Spread: Dp = 0.dp
    val Blur: Dp = 28.dp
    const val Alpha: Float = 0.32f
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
 * Soft silhouette pass for vector glyphs using the same offset / blur as the tile shadow.
 * Callers draw the real asset on top. Draws outside the current size on purpose.
 */
fun DrawScope.drawXoraForegroundSilhouette(drawGlyph: DrawScope.() -> Unit) {
    val ox = XoraForegroundShadow.OffsetX.toPx()
    val oy = XoraForegroundShadow.OffsetY.toPx()
    val blurPx = XoraForegroundShadow.Blur.toPx()
    val pad = blurPx + max(ox, oy)
    val layer = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
    }
    val native = drawContext.canvas.nativeCanvas
    native.saveLayer(
        RectF(-pad, -pad, size.width + pad, size.height + pad),
        layer,
    )
    translate(ox, oy, drawGlyph)
    native.restore()
}
