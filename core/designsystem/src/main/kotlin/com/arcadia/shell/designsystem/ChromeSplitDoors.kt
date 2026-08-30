package com.arcadia.shell.designsystem

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.IntSize
import kotlin.math.roundToInt

/**
 * Split-door wipe for **foreground chrome only** (icons, titles, capsules).
 *
 * Records this node’s Compose contents and slides the left/right halves apart.
 * Wallpaper / hero / trailer must sit *under* the modified node so they stay on
 * screen as the cinematic plate into the emulator.
 */
fun Modifier.xoraChromeSplitDoors(progress: Float): Modifier = composed {
    val layer = rememberGraphicsLayer()
    val p = progress.coerceIn(0f, 1f)
    Modifier.drawWithContent {
        if (p <= 0.001f) {
            drawContent()
            return@drawWithContent
        }
        val w = size.width
        val h = size.height
        if (w <= 1f || h <= 1f) {
            drawContent()
            return@drawWithContent
        }
        layer.record(
            size = IntSize(w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1)),
        ) {
            this@drawWithContent.drawContent()
        }
        val eased = FastOutSlowInEasing.transform(p)
        val slide = w * 0.5f * eased
        val mid = w / 2f
        layer.alpha = 1f - eased
        clipRect(left = 0f, top = 0f, right = mid, bottom = h) {
            translate(left = -slide) {
                drawLayer(layer)
            }
        }
        clipRect(left = mid, top = 0f, right = w, bottom = h) {
            translate(left = slide) {
                drawLayer(layer)
            }
        }
        layer.alpha = 1f
    }
}

/** Extra scale the wallpaper / hero plate eases to while chrome splits out. */
fun launchBackdropScale(holdProgress: Float): Float =
    1f + (holdProgress.coerceIn(0f, 1f) * ArcadiaMotion.LaunchBackdropZoom)
