package com.arcadia.shell.feature.home.component.xmb

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arcadia.shell.designsystem.ArcadiaMotion
import com.arcadia.shell.designsystem.arcadiaTween
import com.arcadia.shell.feature.home.component.ArtworkImage
import com.arcadia.shell.feature.home.component.THUMB_DECODE_MAX_EDGE_PX
import com.arcadia.shell.feature.home.preview.XoraPreview
import com.arcadia.shell.feature.home.preview.XoraPreviewTheme
import com.arcadia.shell.feature.home.preview.previewGame
import com.arcadia.shell.model.Game

/**
 * Tall PSP-case portrait tile for the horizontal XMB strip.
 * Focused: scale-up + glowing light rim; neighbors smaller and faded.
 */
@Composable
fun XmbGameTile(
    game: Game,
    focused: Boolean,
    distanceFromFocus: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    baseWidth: Dp = 118.dp,
) {
    val focusFloat = arcadiaTween<Float>(ArcadiaMotion.Medium)
    val focusDp = arcadiaTween<Dp>(ArcadiaMotion.Medium)
    val scale by animateFloatAsState(
        targetValue = when {
            focused -> 1.14f
            distanceFromFocus == 1 -> 0.94f
            distanceFromFocus == 2 -> 0.88f
            else -> 0.82f
        },
        animationSpec = focusFloat,
        label = "xmbTileScale",
    )
    val alpha by animateFloatAsState(
        targetValue = when {
            focused -> 1f
            distanceFromFocus == 1 -> 0.82f
            distanceFromFocus == 2 -> 0.55f
            else -> 0.36f
        },
        animationSpec = focusFloat,
        label = "xmbTileAlpha",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (focused) 2.5.dp else 0.dp,
        animationSpec = focusDp,
        label = "xmbTileBorder",
    )
    val elevation by animateFloatAsState(
        targetValue = if (focused) 14f else 0f,
        animationSpec = focusFloat,
        label = "xmbTileElevation",
    )
    val shape = RoundedCornerShape(14.dp)
    val glow = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
    val rim = Color.White.copy(alpha = 0.95f)

    Box(
        modifier = modifier
            .width(baseWidth)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
                shadowElevation = elevation
                this.shape = shape
                clip = false
            }
            .drawWithContent {
                drawContent()
                if (focused) {
                    val stroke = 3.5.dp.toPx()
                    val inset = stroke / 2f
                    // Soft outer glow (PS-style light rim).
                    drawRoundRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                glow.copy(alpha = 0.55f),
                                rim.copy(alpha = 0.75f),
                                glow.copy(alpha = 0.45f),
                            ),
                            start = Offset.Zero,
                            end = Offset(size.width, size.height),
                        ),
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - stroke, size.height - stroke),
                        cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()),
                        style = Stroke(width = stroke),
                    )
                }
            }
            .clip(shape)
            .border(
                width = borderWidth,
                color = if (focused) rim else Color.Transparent,
                shape = shape,
            )
            .clickable(onClick = onClick),
    ) {
        ArtworkImage(
            path = game.gridArt,
            contentDescription = game.title,
            fallbackText = game.title,
            decodeMaxEdgePx = THUMB_DECODE_MAX_EDGE_PX,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(CASE_ASPECT),
        )
    }
}

/** PSP / Vita case proportions — width:height ≈ 2:3. */
private const val CASE_ASPECT = 2f / 3f

@XoraPreview
@Composable
private fun XmbGameTilePreview() {
    XoraPreviewTheme {
        XmbGameTile(
            game = previewGame(),
            focused = true,
            distanceFromFocus = 0,
            onClick = {},
        )
    }
}
