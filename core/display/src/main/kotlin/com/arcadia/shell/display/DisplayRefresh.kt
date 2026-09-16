package com.arcadia.shell.display

import android.os.Build
import android.view.Window
import android.view.WindowManager
import kotlin.math.abs

/** What the shell asks the panel for. Gameplay is handled separately — see [DisplayRefresh]. */
enum class RefreshRateMode {
    /** Pin 60 Hz. Coolest and longest-lasting; every animation lands on a 16.7ms grid. */
    Sixty,

    /** Take the fastest mode the panel offers at the current resolution. */
    Max,
}

/**
 * Refresh-rate policy.
 *
 * Handhelds like the AYN Thor expose 90/120 Hz modes. The shell used to pin every window to
 * 60 Hz because running two AMOLED panels flat out is a large part of why XOrA gets hot — but
 * that also capped how smooth the menus could ever feel, since a 60 Hz window cannot present
 * more than 60 frames no matter how little work each one costs.
 *
 * So the two cases are split. Shell windows follow [mode], which the player controls from
 * Display → Refresh rate. Gameplay stays pinned at 60: an emulated core runs at its own rate and
 * presenting its frames twice as often buys nothing but heat.
 */
object DisplayRefresh {
    const val UI_HZ = 60f

    /**
     * Process-wide because it is read from window callbacks that have no DI reach. Written once
     * when preferences load and on every change after.
     */
    @Volatile
    var mode: RefreshRateMode = RefreshRateMode.Max

    /** Gameplay and anything else that genuinely wants a 60 Hz grid. */
    fun preferSixtyHertz(window: Window?) {
        applyToWindow(window, RefreshRateMode.Sixty)
    }

    /** Shell windows: follows the player's Display → Refresh rate choice. */
    fun preferShellRefresh(window: Window?) {
        applyToWindow(window, mode)
    }

    /**
     * Drop the current mode, then re-apply. Sleep/wake clears a leftover wash because
     * SurfaceFlinger rebuilds the display pipeline; this is that rebuild without leaving.
     */
    fun rebind(window: Window?) {
        if (window == null) return
        val run = Runnable {
            val attrs = window.attributes
            attrs.preferredDisplayModeId = 0
            attrs.preferredRefreshRate = 0f
            window.attributes = attrs
            apply(window, RefreshRateMode.Sixty)
        }
        post(window, run)
    }

    fun applyToLayoutParams(params: WindowManager.LayoutParams) {
        params.preferredRefreshRate = if (mode == RefreshRateMode.Sixty) UI_HZ else 0f
    }

    private fun applyToWindow(window: Window?, target: RefreshRateMode) {
        if (window == null) return
        post(window) { apply(window, target) }
    }

    private fun post(window: Window, action: Runnable) {
        if (window.decorView.isAttachedToWindow) action.run() else window.decorView.post(action)
    }

    private fun apply(window: Window, target: RefreshRateMode) {
        val attrs = window.attributes
        attrs.preferredRefreshRate = if (target == RefreshRateMode.Sixty) UI_HZ else 0f
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.decorView.display?.let { display ->
                val current = display.mode
                val sameSize = display.supportedModes.filter { candidate ->
                    candidate.physicalWidth == current.physicalWidth &&
                        candidate.physicalHeight == current.physicalHeight
                }
                val match = when (target) {
                    RefreshRateMode.Sixty ->
                        sameSize.filter { it.refreshRate in 58f..61.5f }
                            .minByOrNull { abs(it.refreshRate - UI_HZ) }
                    // Never below 60: a panel whose "fastest" mode is 50 Hz would be a downgrade.
                    RefreshRateMode.Max ->
                        sameSize.filter { it.refreshRate >= UI_HZ - 1f }
                            .maxByOrNull { it.refreshRate }
                }
                if (match != null) {
                    attrs.preferredDisplayModeId = match.modeId
                    if (target == RefreshRateMode.Max) {
                        attrs.preferredRefreshRate = match.refreshRate
                    }
                }
            }
        }
        window.attributes = attrs
    }
}
