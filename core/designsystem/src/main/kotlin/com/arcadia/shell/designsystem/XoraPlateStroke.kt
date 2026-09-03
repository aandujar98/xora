package com.arcadia.shell.designsystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** White → ice glass rim shared by Game Icons and Platform / Game Select cards. */
val XoraPlateStrokeBrush = Brush.verticalGradient(
    0f to Color.White,
    0.34f to Color(0xFFEAF5FE),
    0.66f to Color(0xFFB9DCF7),
    1f to Color(0xFF7FB4E6),
)

/**
 * Draws the glass plate stroke after the clipped fill so the rim sits on the rounded
 * edge rather than being cropped by [androidx.compose.ui.draw.clip].
 */
fun Modifier.xoraPlateStroke(
    unit: Float,
    radiusDesign: Float,
    borderDesign: Float,
    alpha: Float = 1f,
): Modifier = drawWithContent {
    drawContent()
    val stroke = (borderDesign * unit).dp.toPx()
    val inset = stroke / 2f
    val radius = ((radiusDesign * unit).dp.toPx() - inset).coerceAtLeast(0f)
    drawRoundRect(
        brush = XoraPlateStrokeBrush,
        alpha = alpha,
        topLeft = Offset(inset, inset),
        size = Size(size.width - stroke, size.height - stroke),
        cornerRadius = CornerRadius(radius, radius),
        style = Stroke(width = stroke),
    )
}
