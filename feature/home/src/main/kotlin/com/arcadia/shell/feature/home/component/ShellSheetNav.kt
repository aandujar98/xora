package com.arcadia.shell.feature.home.component

import androidx.compose.runtime.staticCompositionLocalOf
import com.arcadia.shell.input.NavAction
import kotlinx.coroutines.flow.Flow

/**
 * Controller input for an overlay that opens from inside a pane.
 *
 * The Activity's `dispatchKeyEvent` sees gamepad keys before any view does and routes them through
 * the Home ViewModel, so an overlay cannot get them by taking Compose focus. The ViewModel already
 * has the mechanism — while something claims capture, every action is diverted to it instead of
 * moving the library underneath — but the overlays that need it are nested several panes deep.
 *
 * Providing it once rather than threading a flow and a toggle through every pane keeps the panes
 * unaware of it, the same way [com.arcadia.shell.feature.home.LocalInGameXmbController] does for
 * the emulator menu.
 */
interface ShellSheetNav {
    val actions: Flow<NavAction>

    /** True while an overlay wants the D-pad; false hands it back to the pane underneath. */
    fun setCapturing(capturing: Boolean)
}

val LocalShellSheetNav = staticCompositionLocalOf<ShellSheetNav?> { null }
