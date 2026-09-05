package com.arcadia.shell.libretro

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import com.arcadia.shell.datastore.XoraAspectMode
import com.arcadia.shell.datastore.forcedRatio
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Letterboxed game rectangle inside a panel. Used by [XoraEmulatorStage] and unit tests.
 *
 * [XoraAspectMode.Core] (Auto) keeps the framebuffer aspect. Forced ratios (16:9, 1:1, …)
 * fit that box in the panel; the framebuffer is stretched into it. Integer scale stays
 * pixel-perfect. Stretch fills the panel unless bezels are on (then Auto, so the overlay hole
 * still fits).
 */
fun computeXoraGameRect(
    viewW: Int,
    viewH: Int,
    contentWidthPx: Int,
    contentHeightPx: Int,
    aspectMode: XoraAspectMode,
    integerScaleCap: Int = 0,
    bezelsEnabled: Boolean = false,
): IntArray {
    if (viewW <= 0 || viewH <= 0) return intArrayOf(0, 0, viewW, viewH)
    val layoutMode =
        if (bezelsEnabled && aspectMode == XoraAspectMode.Stretch) XoraAspectMode.Core
        else aspectMode
    if (layoutMode == XoraAspectMode.Stretch) {
        return intArrayOf(0, 0, viewW, viewH)
    }
    val fw = contentWidthPx.coerceAtLeast(1).toFloat()
    val fh = contentHeightPx.coerceAtLeast(1).toFloat()
    val (gameW, gameH) = when (layoutMode) {
        XoraAspectMode.Integer -> {
            val fit = min(viewW / fw, viewH / fh)
            val auto = max(1, floor(fit.toDouble()).toInt())
            val scale = if (integerScaleCap > 0) min(auto, integerScaleCap) else auto
            (fw * scale) to (fh * scale)
        }
        else -> {
            val gameAspect = layoutMode.forcedRatio() ?: (fw / fh)
            val viewAspect = viewW / viewH.toFloat()
            if (gameAspect > viewAspect) {
                viewW.toFloat() to (viewW / gameAspect)
            } else {
                (viewH * gameAspect) to viewH.toFloat()
            }
        }
    }
    val left = ((viewW - gameW) / 2f).toInt().coerceAtLeast(0)
    val top = ((viewH - gameH) / 2f).toInt().coerceAtLeast(0)
    val right = (left + gameW).toInt().coerceAtMost(viewW)
    val bottom = (top + gameH).toInt().coerceAtMost(viewH)
    return intArrayOf(left, top, right, bottom)
}

/**
 * XMB / wallpaper plate. Aspect mode is an emulator-only setting — the launcher always
 * fills the physical panel so a 4:3 or 1:1 game ratio never crops the menu backdrop.
 */
fun computeXoraLauncherRect(
    viewW: Int,
    viewH: Int,
    @Suppress("UNUSED_PARAMETER") aspectMode: XoraAspectMode,
): IntArray {
    if (viewW <= 0 || viewH <= 0) return intArrayOf(0, 0, viewW, viewH)
    return intArrayOf(0, 0, viewW, viewH)
}

/**
 * Full-bleed launcher plate. [mode] is ignored — aspect ratio belongs to the in-game
 * framebuffer via [computeXoraGameRect] / [XoraScaledGameFrame], not the XMB.
 *
 * [hud] is laid out in the physical panel so LT Social and RT Profile stay on the real
 * corners even on OEM landscape-locked square panels (RG Rotate).
 */
@Composable
fun XoraAspectLetterbox(
    mode: XoraAspectMode,
    modifier: Modifier = Modifier,
    hud: @Composable BoxScope.() -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    BoxWithConstraints(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        val density = LocalDensity.current
        val rect = computeXoraLauncherRect(constraints.maxWidth, constraints.maxHeight, mode)
        val boxW = with(density) { (rect[2] - rect[0]).coerceAtLeast(1).toDp() }
        val boxH = with(density) { (rect[3] - rect[1]).coerceAtLeast(1).toDp() }
        Box(
            modifier = Modifier
                .size(DpSize(boxW, boxH))
                .clipToBounds(),
            content = content,
        )
        Box(
            modifier = Modifier.fillMaxSize(),
            content = hud,
        )
    }
}

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
        else -> {
            BoxWithConstraints(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                val density = LocalDensity.current
                val rect = computeXoraGameRect(
                    viewW = constraints.maxWidth,
                    viewH = constraints.maxHeight,
                    contentWidthPx = w,
                    contentHeightPx = h,
                    aspectMode = mode,
                    integerScaleCap = integerScaleCap,
                )
                val boxW = with(density) { (rect[2] - rect[0]).toDp() }
                val boxH = with(density) { (rect[3] - rect[1]).toDp() }
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
    else -> ContentScale.FillBounds
}
