package com.arcadia.shell.display

import android.app.Activity
import android.content.pm.ActivityInfo
import android.os.Build
import android.util.DisplayMetrics
import kotlin.math.max
import kotlin.math.min

/**
 * Long/short ceiling for "this panel is square." RG Rotate is 720×720; a few Cube-class
 * handhelds report 1.04–1.1 after rotation compensation.
 */
const val SQUARE_PANEL_RATIO_CEILING = 1.15f

fun isNearSquarePanel(
    widthPx: Int,
    heightPx: Int,
    ratioCeiling: Float = SQUARE_PANEL_RATIO_CEILING,
): Boolean {
    if (widthPx <= 0 || heightPx <= 0) return false
    val longPx = max(widthPx, heightPx).toFloat()
    val shortPx = min(widthPx, heightPx).toFloat()
    return longPx / shortPx <= ratioCeiling
}

/**
 * Square / rotatable panels (RG Rotate) must follow gravity on all four sides. Locking those
 * devices to landscape makes Android letterbox the window into a 16:9 strip, and a 1:1 XMB
 * plate then swallows the LT/RT chrome.
 *
 * Wide handhelds and TVs stay sensor-landscape so the XMB never goes portrait.
 */
fun xoraScreenOrientation(widthPx: Int, heightPx: Int): Int =
    if (isNearSquarePanel(widthPx, heightPx)) {
        ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR
    } else {
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

fun Activity.applyXoraScreenOrientation() {
    val (widthPx, heightPx) = physicalPanelSize()
    val next = xoraScreenOrientation(widthPx, heightPx)
    if (requestedOrientation != next) {
        requestedOrientation = next
    }
}

/**
 * Native panel size, not the activity window. `sensorLandscape` on a square device can already
 * have letterboxed the window to 16:9 by the time [android.util.DisplayMetrics] is read.
 */
internal fun Activity.physicalPanelSize(): Pair<Int, Int> {
    val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        display
    } else {
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay
    }
    if (display != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        val mode = display.mode
        if (mode.physicalWidth > 0 && mode.physicalHeight > 0) {
            return mode.physicalWidth to mode.physicalHeight
        }
    }
    val metrics = DisplayMetrics()
    if (display != null) {
        @Suppress("DEPRECATION")
        display.getRealMetrics(metrics)
    } else {
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.getRealMetrics(metrics)
    }
    return metrics.widthPixels to metrics.heightPixels
}
