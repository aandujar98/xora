package com.arcadia.shell.display

import android.view.Window
import androidx.core.view.ViewCompat
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

    /**
     * Re-hides the bars only when they are actually showing.
     *
     * [apply] posts a fresh insets request every time it is called, so a per-frame pin that calls
     * it unconditionally keeps the window in a permanent insets animation. Callers on a vsync
     * callback want this one.
     */
    fun keepHidden(window: Window?) {
        if (window == null) return
        val visible = ViewCompat.getRootWindowInsets(window.decorView)
            ?.isVisible(WindowInsetsCompat.Type.systemBars())
            ?: true
        if (visible) apply(window)
    }
}
