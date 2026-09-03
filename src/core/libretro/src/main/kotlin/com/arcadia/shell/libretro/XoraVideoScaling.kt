package com.arcadia.shell.libretro

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import com.arcadia.shell.datastore.XoraAspectMode
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Fits [content] inside the parent according to [mode] and the native framebuffer size.
 */
@Composable
fun XoraScaledGameFrame(
    contentWidthPx: Int,
    contentHeightPx: Int,
    mode: XoraAspectMode,
    integerScaleCap: Int,
    modifier: Modifier = Modifier,
    content: @Composable (contentScale: ContentScale) -> Unit,
) {
    val w = contentWidthPx.coerceAtLeast(1)
    val h = contentHeightPx.coerceAtLeast(1)
    when (mode) {
        XoraAspectMode.Stretch -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                content(ContentScale.FillBounds)
            }
        }
        XoraAspectMode.Core -> {
            Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                content(ContentScale.Fit)
            }
        }
        XoraAspectMode.Integer -> {
            BoxWithConstraints(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val density = LocalDensity.current
                val maxW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                val maxH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
                val fit = min(maxW / w, maxH / h)
                val auto = max(1, floor(fit.toDouble()).toInt())
                val capped = if (integerScaleCap > 0) min(auto, integerScaleCap) else auto
                val scale = capped.coerceAtLeast(1)
                val boxW = with(density) { (w * scale).toDp() }
                val boxH = with(density) { (h * scale).toDp() }
                Box(
                    modifier = Modifier.size(DpSize(boxW, boxH)),
                    contentAlignment = Alignment.Center,
                ) {
                    content(ContentScale.FillBounds)
                }
            }
        }
    }
}

fun XoraAspectMode.toContentScale(): ContentScale = when (this) {
    XoraAspectMode.Core -> ContentScale.Fit
    XoraAspectMode.Integer -> ContentScale.Fit
    XoraAspectMode.Stretch -> ContentScale.FillBounds
}
