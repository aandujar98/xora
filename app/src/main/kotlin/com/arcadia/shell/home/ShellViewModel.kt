package com.arcadia.shell.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.arcadia.shell.datastore.DEFAULT_SHELL_THEME_ID
import com.arcadia.shell.datastore.DEFAULT_UI_TEXT_SCALE
import com.arcadia.shell.datastore.DisplayMode
import com.arcadia.shell.datastore.ShellPreferences
import com.arcadia.shell.datastore.ThemeMode
import com.arcadia.shell.datastore.UiFitMode
import com.arcadia.shell.display.DisplayTopologyMonitor
import com.arcadia.shell.display.computeUiLayoutScale
import com.arcadia.shell.display.formatDisplayResolution
import com.arcadia.shell.role.HomeRoleController
import com.arcadia.shell.role.HomeRoleState
import com.arcadia.shell.model.DisplayTopology
import com.arcadia.shell.model.ScreenRole
import com.arcadia.shell.model.ShellDisplay
import com.arcadia.shell.model.swapped
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ShellUiState(
    val topology: DisplayTopology = DisplayTopology.Empty,
    val homeRole: HomeRoleState = HomeRoleState(),
    val secondaryDisplayRole: ScreenRole = ScreenRole.Hero,
    val displayMode: DisplayMode = DisplayMode.Dual,
    val themeMode: ThemeMode = ThemeMode.Dark,
    /** Active launcher theme pack id (colors / wallpaper / optional BGM). */
    val shellThemeId: String = DEFAULT_SHELL_THEME_ID,
    /** Shell / XMB text size multiplier from Display settings. */
    val uiTextScale: Float = DEFAULT_UI_TEXT_SCALE,
    /** Auto-fit vs system density (Display settings). */
    val uiFitMode: UiFitMode = UiFitMode.Auto,
    /** False until the first DataStore emission so we do not flash Home before onboarding. */
    val prefsReady: Boolean = false,
    val onboardingComplete: Boolean = false,
    /** Session flag from Settings → Go to Onboarding (also clears the prefs flag). */
    val forceOnboarding: Boolean = false,
) {
    /** True when two public displays are present (hardware), regardless of [displayMode]. */
    val isDualScreen: Boolean get() = topology.isDualScreen

    /** Layout scale for the primary panel (1f when fit is off). */
    val primaryUiLayoutScale: Float
        get() = layoutScaleFor(topology.primary)

    /** Layout scale for the secondary panel when present. */
    val secondaryUiLayoutScale: Float
        get() = layoutScaleFor(topology.presentationDisplay ?: topology.primary)

    fun layoutScaleFor(display: ShellDisplay?): Float =
        if (uiFitMode == UiFitMode.Auto) computeUiLayoutScale(display) else 1f

    fun resolutionLabel(display: ShellDisplay? = topology.primary): String =
        formatDisplayResolution(display)

    /**
     * Whether the shell should open a secondary Presentation and split Hero / Library.
     * Requires Dual preference **and** a second physical display; otherwise falls back to
     * the single-screen composed host.
     */
    val useDualLayout: Boolean
        get() = displayMode == DisplayMode.Dual && topology.isDualScreen

    /** Full-screen onboarding instead of the Home hub. */
    val showOnboarding: Boolean
        get() = prefsReady && (!onboardingComplete || forceOnboarding)

    /** The pane the built-in display shows, which is always the opposite of the secondary one. */
    val primaryDisplayRole: ScreenRole get() = secondaryDisplayRole.swapped()

    val secondaryDisplayId: Int? get() = topology.presentationDisplay?.displayId

    /** Where the grid currently lives, since that is the screen the user is navigating on. */
    val gridDisplayId: Int?
        get() = when {
            !useDualLayout -> topology.primary?.displayId
            secondaryDisplayRole == ScreenRole.Grid -> topology.secondary?.displayId
            else -> topology.primary?.displayId
        }

    val otherDisplayId: Int?
        get() = when {
            !useDualLayout -> topology.secondary?.displayId
            secondaryDisplayRole == ScreenRole.Grid -> topology.primary?.displayId
            else -> topology.secondary?.displayId
        }
}

@HiltViewModel
class ShellViewModel @Inject constructor(
    topologyMonitor: DisplayTopologyMonitor,
    private val homeRoleController: HomeRoleController,
    private val preferences: ShellPreferences,
) : ViewModel() {

    private val forceOnboarding = MutableStateFlow(false)

    val uiState: StateFlow<ShellUiState> = combine(
        topologyMonitor.topology(),
        homeRoleController.state(),
        preferences.settings,
        preferences.onboardingComplete,
        forceOnboarding,
    ) { topology, homeRole, settings, onboardingDone, force ->
        ShellUiState(
            topology = topology,
            homeRole = homeRole,
            secondaryDisplayRole = settings.secondaryDisplayRole,
            displayMode = settings.displayMode,
            themeMode = settings.themeMode,
            shellThemeId = settings.shellThemeId,
            uiTextScale = settings.uiTextScale,
            uiFitMode = settings.uiFitMode,
            prefsReady = true,
            onboardingComplete = onboardingDone,
            forceOnboarding = force,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
        initialValue = ShellUiState(),
    )

    fun setHomeCandidate(enabled: Boolean) {
        homeRoleController.setHomeCandidate(enabled)
    }

    fun openHomeSettings() {
        homeRoleController.openHomeSettings()
    }

    /** Called when the shell regains focus, since the user may have changed the home app in Settings. */
    fun refresh() {
        homeRoleController.refresh()
    }

    /**
     * Settings → Go to Onboarding: clear the completed flag and force the full-screen flow for
     * this session until Finish.
     */
    fun restartOnboarding() {
        viewModelScope.launch {
            preferences.setOnboardingComplete(false)
            forceOnboarding.value = true
        }
    }

    /** Cleared when the user finishes onboarding (including a Settings redo). */
    fun clearForceOnboarding() {
        forceOnboarding.value = false
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
