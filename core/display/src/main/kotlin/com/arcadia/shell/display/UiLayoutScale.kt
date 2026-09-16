package com.arcadia.shell.display

import com.arcadia.shell.model.ShellDisplay
import kotlin.math.max
import kotlin.math.min

/**
 * Computes a layout scale so the shell UI fits the physical display.
 *
 * The XMB / chrome were authored around a ~1080p reference. Smaller panels shrink chrome so
 * everything stays on-screen; larger panels grow it so controls are not tiny on a TV.
 */
fun computeUiLayoutScale(display: ShellDisplay?): Float {
    if (display == null || display.widthPx <= 0 || display.heightPx <= 0) {
        return 1f
    }
    val shortPx = min(display.widthPx, display.heightPx).toFloat()
    val longPx = max(display.widthPx, display.heightPx).toFloat()
    val byShort = shortPx / REF_SHORT_PX
    val byLong = longPx / REF_LONG_PX
    // Square panels (RG Rotate, Cube) are not a 16:9 strip. Scaling by the missing long edge
    // would shrink chrome as if the device were a tiny 720×1280.
    val resolutionScale = if (isNearSquarePanel(display.widthPx, display.heightPx)) {
        byShort
    } else {
        min(byShort, byLong)
    }

    // Soften extreme DPI so a sharp 1080p handheld does not blow up chrome vs a 1080p TV.
    val density = (display.densityDpi / 160f).coerceIn(1f, 4f)
    val densityFactor = (REF_DENSITY / density).coerceIn(0.7f, 1.2f)

    return (resolutionScale * densityFactor).coerceIn(MIN_UI_LAYOUT_SCALE, MAX_UI_LAYOUT_SCALE)
}

fun formatDisplayResolution(display: ShellDisplay?): String {
    if (display == null || display.widthPx <= 0 || display.heightPx <= 0) {
        return "Unknown"
    }
    val w = max(display.widthPx, display.heightPx)
    val h = min(display.widthPx, display.heightPx)
    return "${w}×${h}"
}

private const val REF_SHORT_PX = 1080f
private const val REF_LONG_PX = 1920f
/** Authored roughly around xhdpi. */
private const val REF_DENSITY = 2f
const val MIN_UI_LAYOUT_SCALE = 0.7f
const val MAX_UI_LAYOUT_SCALE = 1.35f
