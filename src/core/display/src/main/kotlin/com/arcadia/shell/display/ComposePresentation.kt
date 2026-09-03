package com.arcadia.shell.display

import android.app.Presentation
import android.content.Context
import android.os.Bundle
import android.view.Display
import android.view.WindowManager
import androidx.activity.OnBackPressedDispatcherOwner
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.setViewTreeOnBackPressedDispatcherOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Hosts Compose content on a second physical display.
 *
 * [Presentation] is a [android.app.Dialog] bound to a specific [Display], and it predates Compose
 * by years: its view tree carries none of the owners Compose requires, so a bare `ComposeView`
 * placed inside it crashes on first composition. Grafting the hosting Activity's lifecycle,
 * ViewModel store, saved-state, back-pressed, and activity-result owners onto the view is what
 * makes this work, and it has the useful side effect that content here can resolve the very same
 * ViewModel instance the Activity uses. That shared instance is the whole mechanism behind hero
 * art on one screen tracking grid movement on the other.
 *
 * Without [ActivityResultRegistryOwner] / [OnBackPressedDispatcherOwner], any sheet that calls
 * `rememberLauncherForActivityResult` or Material3 dialog back handling crashes immediately when
 * opened on the secondary pane ("No ActivityResultRegistryOwner was provided").
 *
 * The lifecycle is the one owner deliberately *not* borrowed from the Activity. Compose drives
 * recomposition from a frame clock that its window recomposer pauses as soon as the view tree's
 * lifecycle owner stops, so grafting the Activity's lifecycle here froze this window the instant a
 * game took the foreground: the last frame stayed on screen, taps still ran their handlers, and
 * nothing they changed was ever drawn. This window's own visibility is what matters — it lives on a
 * different display and keeps showing while another app owns the primary one — so it carries a
 * registry that is resumed for exactly as long as the presentation is up.
 */
class ComposePresentation(
    outerContext: Context,
    display: Display,
    private val viewModelStoreOwner: ViewModelStoreOwner,
    private val savedStateRegistryOwner: SavedStateRegistryOwner,
    private val activityResultRegistryOwner: ActivityResultRegistryOwner?,
    private val onBackPressedDispatcherOwner: OnBackPressedDispatcherOwner?,
    private val content: @Composable () -> Unit,
) : Presentation(outerContext, display), LifecycleOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleRegistry.currentState = Lifecycle.State.CREATED

        // The second pane is decoration, never something the user taps to dismiss, and it must not
        // steal input focus from the pane driving navigation.
        window?.apply {
            setFlags(
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            )
            setBackgroundDrawableResource(android.R.color.black)
        }
        ImmersiveMode.apply(window)
        setCancelable(false)

        val composeView = ComposeView(context).apply {
            setViewTreeLifecycleOwner(this@ComposePresentation)
            setViewTreeViewModelStoreOwner(viewModelStoreOwner)
            setViewTreeSavedStateRegistryOwner(savedStateRegistryOwner)
            onBackPressedDispatcherOwner?.let { setViewTreeOnBackPressedDispatcherOwner(it) }
            setContent {
                // ActivityResult has no setViewTree* helper in androidx.activity 1.13 — provide
                // the host Activity's registry via CompositionLocal so pickers on this pane work.
                val locals = buildList {
                    activityResultRegistryOwner?.let {
                        add(LocalActivityResultRegistryOwner provides it)
                    }
                    onBackPressedDispatcherOwner?.let {
                        add(LocalOnBackPressedDispatcherOwner provides it)
                    }
                }
                if (locals.isEmpty()) {
                    content()
                } else {
                    CompositionLocalProvider(*locals.toTypedArray(), content = content)
                }
            }
        }

        setContentView(composeView)
    }

    /** Dialog start/stop bracket `show()` and `dismiss()`, which is this window's real visibility. */
    override fun onStart() {
        super.onStart()
        lifecycleRegistry.currentState = Lifecycle.State.RESUMED
    }

    override fun onStop() {
        super.onStop()
        // A dismissed presentation is never re-shown; SecondaryDisplayPane builds a new one per
        // display, so tearing the composition down here is what releases it.
        lifecycleRegistry.currentState = Lifecycle.State.DESTROYED
    }
}
