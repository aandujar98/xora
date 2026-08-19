package com.arcadia.shell.display

import android.os.Build
import android.view.Window
import android.view.WindowManager
import kotlin.math.abs

/**
 * Handhelds like the AYN Thor expose 90/120 Hz modes. Shell wallpaper and in-game ImageView
 * present do not need that; running both AMOLED panels at 120 Hz is a large part of why XOrA
 * runs hot and drains fast. Every window pins to 60 Hz.
 */
object DisplayRefresh {
    const val UI_HZ = 60f

    fun preferSixtyHertz(window: Window?) {
        if (window == null) return
        val apply = Runnable { applyToWindow(window) }
        if (window.decorView.isAttachedToWindow) {
            apply.run()
        } else {
            window.decorView.post(apply)
        }
    }

    fun applyToLayoutParams(params: WindowManager.LayoutParams) {
        params.preferredRefreshRate = UI_HZ
    }

    private fun applyToWindow(window: Window) {
        val attrs = window.attributes
        attrs.preferredRefreshRate = UI_HZ
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.decorView.display?.let { display ->
                val current = display.mode
                val match = display.supportedModes
                    .filter { mode ->
                        mode.physicalWidth == current.physicalWidth &&
                            mode.physicalHeight == current.physicalHeight &&
                            mode.refreshRate in 58f..61.5f
                    }
                    .minByOrNull { abs(it.refreshRate - UI_HZ) }
                if (match != null) {
                    attrs.preferredDisplayModeId = match.modeId
                }
            }
        }
        window.attributes = attrs
    }
}
