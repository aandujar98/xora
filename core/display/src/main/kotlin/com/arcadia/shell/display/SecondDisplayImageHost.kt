package com.arcadia.shell.display

import android.app.Activity
import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ImageView

enum class SecondDisplayAttachResult {
    Hidden,
    ShownPresentation,
    ShownOverlay,
    NoDisplay,
    NeedsOverlayPermission,
    Failed,
}

/**
 * Puts a live [ImageView] on a second physical display.
 *
 * This must not live inside a Compose host that gameplay pinning can dispose. The emulator
 * banner overlay used to do that: [syncBannerHost] tore the composition down, so Expand
 * dual display wrote the preference and never attached a window.
 */
class SecondDisplayImageHost(private val activity: Activity) {

    private var presentation: ImagePresentation? = null
    private var overlayView: ImageView? = null
    private var overlayManager: WindowManager? = null
    private var attachedDisplayId: Int? = null

    var imageView: ImageView? = null
        private set

    val isAttached: Boolean get() = imageView != null

    fun show(displayId: Int, restart: Boolean = false): SecondDisplayAttachResult {
        if (!restart && attachedDisplayId == displayId && imageView != null) {
            return if (presentation != null) {
                SecondDisplayAttachResult.ShownPresentation
            } else {
                SecondDisplayAttachResult.ShownOverlay
            }
        }
        dismiss()
        val display = activity.getSystemService(DisplayManager::class.java)
            ?.resolveDisplay(displayId)
            ?: return SecondDisplayAttachResult.NoDisplay

        if (showPresentation(display)) {
            attachedDisplayId = displayId
            return SecondDisplayAttachResult.ShownPresentation
        }
        if (!OverlayPermission.isGranted(activity)) {
            return SecondDisplayAttachResult.NeedsOverlayPermission
        }
        return if (showOverlay(display)) {
            attachedDisplayId = displayId
            SecondDisplayAttachResult.ShownOverlay
        } else {
            SecondDisplayAttachResult.Failed
        }
    }

    fun dismiss() {
        val shownPresentation = presentation
        presentation = null
        val view = overlayView
        val manager = overlayManager
        overlayView = null
        overlayManager = null
        imageView = null
        attachedDisplayId = null
        if (shownPresentation != null) {
            runCatching { shownPresentation.dismiss() }
        }
        if (view != null && manager != null) {
            runCatching { manager.removeViewImmediate(view) }
        }
    }

    private fun showPresentation(display: Display): Boolean {
        val presented = ImagePresentation(activity, display)
        val ok = runCatching {
            presented.show()
            presented.isShowing
        }.getOrDefault(false)
        if (!ok) {
            runCatching { presented.dismiss() }
            return false
        }
        presentation = presented
        imageView = presented.imageView
        return true
    }

    private fun showOverlay(display: Display): Boolean {
        val displayContext = activity.createDisplayContext(display)
        val hostContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            displayContext.createWindowContext(
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                null,
            )
        } else {
            displayContext
        }
        val manager = hostContext.getSystemService(WindowManager::class.java) ?: return false
        val view = ImageView(hostContext).apply {
            scaleType = ImageView.ScaleType.FIT_XY
            setBackgroundColor(Color.BLACK)
            adjustViewBounds = false
        }
        val added = runCatching { manager.addView(view, overlayLayoutParams()) }.isSuccess
        if (!added) return false
        overlayView = view
        overlayManager = manager
        imageView = view
        return true
    }

    private fun overlayLayoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        DisplayRefresh.applyToLayoutParams(this)
    }

    private class ImagePresentation(
        outerContext: Context,
        display: Display,
    ) : Presentation(outerContext, display) {
        val imageView = ImageView(context).apply {
            scaleType = ImageView.ScaleType.FIT_XY
            setBackgroundColor(Color.BLACK)
            adjustViewBounds = false
        }

        override fun onCreate(savedInstanceState: Bundle?) {
            super.onCreate(savedInstanceState)
            window?.apply {
                setFlags(
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                )
                setBackgroundDrawableResource(android.R.color.black)
            }
            ImmersiveMode.apply(window)
            DisplayRefresh.preferSixtyHertz(window)
            setCancelable(false)
            setContentView(
                imageView,
                ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }
    }
}

object ExpandDualDisplayMessages {
    fun forResult(result: SecondDisplayAttachResult): String = when (result) {
        SecondDisplayAttachResult.Hidden -> "Both screens on this display"
        SecondDisplayAttachResult.ShownPresentation,
        SecondDisplayAttachResult.ShownOverlay,
        -> "Bottom screen on the other display"
        SecondDisplayAttachResult.NoDisplay ->
            "No second display found — plug in HDMI or use a clamshell bottom panel"
        SecondDisplayAttachResult.NeedsOverlayPermission ->
            "Allow Display over other apps so the bottom screen can leave this panel"
        SecondDisplayAttachResult.Failed -> "Could not open the second display"
    }
}
