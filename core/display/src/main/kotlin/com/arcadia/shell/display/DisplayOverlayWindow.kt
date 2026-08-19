package com.arcadia.shell.display

import android.content.Context
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.os.Build
import android.view.Gravity
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Compose content on a specific display through a `TYPE_APPLICATION_OVERLAY` window.
 *
 * [SecondaryDisplayPane] covers the case where the shell itself is in front. It cannot cover the
 * case this class exists for: every window hanging off the shell Activity — including a
 * [android.app.Presentation] on the second screen — is torn down when that Activity is destroyed,
 * which the system is free to do at any point while an emulator owns the foreground. An overlay
 * window belongs to the process rather than to an Activity, so it survives. The price is the
 * `SYSTEM_ALERT_WINDOW` permission, which the user must grant by hand (see [OverlayPermission]).
 *
 * The window is not focusable, so key and gamepad events continue on to the running game. Touches
 * that land inside it are still delivered here, which is what makes the panel usable while a
 * controller is busy elsewhere.
 *
 * Owners have to be supplied by hand for the same reason [ComposePresentation] does it, except
 * there is no Activity to borrow them from here — this class *is* the owner, which also means the
 * content gets a ViewModel store scoped to the overlay rather than to the shell.
 */
class DisplayOverlayWindow(
    private val outerContext: Context,
) : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore = ViewModelStore()
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private var windowManager: WindowManager? = null
    private var composeView: ComposeView? = null

    val isShowing: Boolean get() = composeView != null

    /**
     * Adds the window to [displayId]. Returns false when the display is gone, the overlay
     * permission is missing, or the window manager rejects the token — all of which are normal and
     * must leave the caller in a usable state.
     */
    fun show(displayId: Int, content: @Composable () -> Unit): Boolean {
        if (isShowing) return true
        if (!OverlayPermission.isGranted(outerContext)) return false

        val display = outerContext.getSystemService(DisplayManager::class.java)
            ?.getDisplay(displayId)
            ?.takeIf { it.isValid }
            ?: return false

        val displayContext = outerContext.createDisplayContext(display)
        // A window context binds the window to this display for good; without it the platform is
        // free to reparent the overlay onto the default display on API 30+.
        val hostContext = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            displayContext.createWindowContext(OVERLAY_TYPE, null)
        } else {
            displayContext
        }
        val manager = hostContext.getSystemService(WindowManager::class.java) ?: return false

        savedStateController.performRestore(null)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        val view = ComposeView(hostContext).apply {
            setViewTreeLifecycleOwner(this@DisplayOverlayWindow)
            setViewTreeViewModelStoreOwner(this@DisplayOverlayWindow)
            setViewTreeSavedStateRegistryOwner(this@DisplayOverlayWindow)
            setContent(content)
        }

        val added = runCatching { manager.addView(view, layoutParams()) }.isSuccess
        if (!added) {
            lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
            return false
        }

        windowManager = manager
        composeView = view
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
        return true
    }

    fun dismiss() {
        val view = composeView
        val manager = windowManager
        composeView = null
        windowManager = null
        if (view != null && manager != null) {
            runCatching { manager.removeViewImmediate(view) }
        }
        if (lifecycleRegistry.currentState != Lifecycle.State.DESTROYED) {
            lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
        }
        viewModelStore.clear()
    }

    private fun layoutParams() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        OVERLAY_TYPE,
        // NOT_FOCUSABLE keeps keys and gamepad input flowing to the game while touch still lands
        // here. LAYOUT_NO_LIMITS lets the panel run under the second screen's cutouts and bars.
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        DisplayRefresh.applyToLayoutParams(this)
    }

    private companion object {
        const val OVERLAY_TYPE = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    }
}
