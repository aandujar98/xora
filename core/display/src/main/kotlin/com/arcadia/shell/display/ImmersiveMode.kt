package com.arcadia.shell.display

import android.view.Window
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Hides the status and navigation/gesture bars while drawing edge-to-edge behind them.
 * Swipe temporarily reveals the bars ([BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE]).
 */
object ImmersiveMode {
    fun apply(window: Window?) {
        if (window == null) return

        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }
}
