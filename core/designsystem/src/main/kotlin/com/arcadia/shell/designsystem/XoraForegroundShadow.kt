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
 * XMB drop shadow: 10px right, 10px down, 15px blur (1080p design units), 50% opacity
 * (25% when the parent icon is already faded to 50%).
 */
object XoraForegroundShadow {
    const val DesignOffset = 10f
    const val DesignBlur = 15f
    /** Layout pad so an alpha offscreen layer still fits offset + blur on every side. */
    const val DesignExtent = DesignOffset + DesignBlur
    val OffsetX: Dp = DesignOffset.dp
    val OffsetY: Dp = DesignOffset.dp
    val Spread: Dp = 0.dp
    val Blur: Dp = DesignBlur.dp
    const val Alpha: Float = 0.50f
    const val InactiveAlpha: Float = 0.25f
    val PlateBlur: Dp = DesignBlur.dp
    const val PlateAlpha: Float = Alpha
    val Ink: Color = Color.Black
}

fun Modifier.xoraForegroundShadow(
    shape: Shape,
    alpha: Float = XoraForegroundShadow.Alpha,
    offset: Dp = XoraForegroundShadow.OffsetX,
    blur: Dp = XoraForegroundShadow.Blur,
): Modifier = dropShadow(shape) {
    radius = blur.toPx()
    spread = XoraForegroundShadow.Spread.toPx()
    this.offset = Offset(offset.toPx(), offset.toPx())
    color = XoraForegroundShadow.Ink
    this.alpha = alpha
}

/** 1080p-scaled XMB shadow (10 / 10 / 15) so it stays pixel-true at any density. */
fun Modifier.xmbAssetShadow(
    unit: Float,
    shape: Shape,
    alpha: Float = XoraForegroundShadow.Alpha,
): Modifier = xoraForegroundShadow(
    shape = shape,
    alpha = alpha,
    offset = (XoraForegroundShadow.DesignOffset * unit).dp,
    blur = (XoraForegroundShadow.DesignBlur * unit).dp,
)

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
