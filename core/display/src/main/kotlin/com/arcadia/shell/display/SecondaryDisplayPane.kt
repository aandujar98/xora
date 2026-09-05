package com.arcadia.shell.display

import android.hardware.display.DisplayManager
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.savedstate.compose.LocalSavedStateRegistryOwner

/**
 * Shows [content] on the display with [displayId], or nothing at all when [displayId] is null.
 *
 * Passing a new id tears the old presentation down and builds a new one, which is exactly what
 * should happen when a screen is unplugged or the panes are swapped between displays.
 */
@Composable
fun SecondaryDisplayPane(
    displayId: Int?,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val viewModelStoreOwner = LocalViewModelStoreOwner.current
    val savedStateRegistryOwner = LocalSavedStateRegistryOwner.current
    val activityResultRegistryOwner = LocalActivityResultRegistryOwner.current
    val onBackPressedDispatcherOwner = LocalOnBackPressedDispatcherOwner.current

    // The effect below only re-runs when the target display changes, so the content lambda is read
    // through a state holder to keep it from going stale across recompositions.
    val currentContent by rememberUpdatedState(content)

    DisposableEffect(displayId, viewModelStoreOwner) {
        val display = displayId
            ?.let { context.getSystemService(DisplayManager::class.java)?.getDisplay(it) }
            ?.takeIf { it.isValid }

        if (display == null || viewModelStoreOwner == null) {
            return@DisposableEffect onDispose { }
        }

        val presentation = ComposePresentation(
            outerContext = context,
            display = display,
            viewModelStoreOwner = viewModelStoreOwner,
            savedStateRegistryOwner = savedStateRegistryOwner,
            activityResultRegistryOwner = activityResultRegistryOwner,
            onBackPressedDispatcherOwner = onBackPressedDispatcherOwner,
            content = { currentContent() },
        )

        // A display can disappear between the topology snapshot and this call, in which case the
        // window manager rejects the show outright. Internal presentation panels (AYN Thor
        // bottom screen) can also reject Presentation while the Activity is on the primary
        // display — fall back to an overlay window on that panel when overlay permission exists.
        val presented = runCatching { presentation.show() }.isSuccess
        val overlay = if (!presented) {
            DisplayOverlayWindow(context).takeIf { it.show(display.displayId) { currentContent() } }
        } else {
            null
        }

        onDispose {
            runCatching { presentation.dismiss() }
            overlay?.dismiss()
        }
    }
}
