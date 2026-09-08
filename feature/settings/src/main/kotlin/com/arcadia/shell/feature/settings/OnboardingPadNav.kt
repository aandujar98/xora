package com.arcadia.shell.feature.settings

import com.arcadia.shell.input.NavAction

/** Gamepad outcome for one onboarding input. */
internal enum class OnboardingPadCommand {
    None,
    Next,
    Back,
    Skip,
    DismissPicker,
    Activate,
}

internal object OnboardingPadIds {
    const val Back = "onboarding_back"
    const val Skip = "onboarding_skip"
    const val Next = "onboarding_next"
}

/**
 * Hat/stick auto-repeat from [com.arcadia.shell.input.GamepadDispatcher] is 70ms after a 350ms
 * delay. Shoulders still change steps, so LB/RB are gated. D-pad Left/Right move the cursor
 * and must not be throttled.
 */
internal const val ONBOARDING_PAD_DIRECTION_DEBOUNCE_MS = 400L

internal fun shouldAcceptOnboardingPadRepeat(
    action: NavAction,
    nowMs: Long,
    lastDirectionalMs: Long?,
): Boolean {
    if (action != NavAction.NextPlatform && action != NavAction.PreviousPlatform) return true
    val previous = lastDirectionalMs ?: return true
    return nowMs - previous >= ONBOARDING_PAD_DIRECTION_DEBOUNCE_MS
}

/**
 * Maps controller actions onto the first-run wizard.
 *
 * Handhelds report D-pad as a hat/stick, which never becomes Compose focus, so this is the
 * path that actually walks the steps. A activates the focused control (same as Advanced
 * Settings), RB / LB change steps, B goes back, Y skips an optional step.
 */
internal fun onboardingPadCommand(
    action: NavAction,
    canGoBack: Boolean,
    canAdvance: Boolean,
    optional: Boolean,
    pickerOpen: Boolean,
    intraForm: Boolean = false,
): OnboardingPadCommand {
    if (pickerOpen) {
        return when (action) {
            NavAction.Cancel -> OnboardingPadCommand.DismissPicker
            NavAction.Confirm ->
                if (intraForm) OnboardingPadCommand.Activate else OnboardingPadCommand.None
            else -> OnboardingPadCommand.None
        }
    }
    if (intraForm) {
        return when (action) {
            NavAction.Confirm -> OnboardingPadCommand.Activate
            NavAction.NextPlatform ->
                if (canAdvance) OnboardingPadCommand.Next else OnboardingPadCommand.None
            NavAction.Cancel,
            NavAction.PreviousPlatform,
            -> if (canGoBack) OnboardingPadCommand.Back else OnboardingPadCommand.None
            NavAction.SwapScreens,
            NavAction.Options,
            -> if (optional) OnboardingPadCommand.Skip else OnboardingPadCommand.None
            else -> OnboardingPadCommand.None
        }
    }
    return when (action) {
        NavAction.Confirm,
        NavAction.Right,
        NavAction.NextPlatform,
        -> if (canAdvance) OnboardingPadCommand.Next else OnboardingPadCommand.None
        NavAction.Cancel,
        NavAction.Left,
        NavAction.PreviousPlatform,
        -> if (canGoBack) OnboardingPadCommand.Back else OnboardingPadCommand.None
        NavAction.SwapScreens,
        NavAction.Options,
        -> if (optional) OnboardingPadCommand.Skip else OnboardingPadCommand.None
        else -> OnboardingPadCommand.None
    }
}

/** Keep the wizard cursor on live controls. Never promote it into Setup's Tabs/Done chrome. */
internal fun onboardingPadCoerce(
    state: SettingsPadNavState,
    layout: SettingsPadLayout,
): SettingsPadNavState {
    val cell = layout.clamp(state.rowIndex, state.colIndex)
        ?: return state.copy(zone = SettingsPadZone.Controls, rowIndex = 0, colIndex = 0)
    return state.copy(
        zone = SettingsPadZone.Controls,
        rowIndex = cell.first,
        colIndex = cell.second,
    )
}

/**
 * D-pad walks every registered button and field. Left/Right stay on a visual row; Up/Down
 * change rows. Edges stay put so the host can scroll the window.
 */
internal fun onboardingPadAfterAction(
    state: SettingsPadNavState,
    action: NavAction,
    layout: SettingsPadLayout,
): SettingsPadNavState {
    val current = onboardingPadCoerce(state, layout)
    fun atCell(row: Int, col: Int): SettingsPadNavState {
        val cell = layout.clamp(row, col) ?: return current
        return current.copy(
            zone = SettingsPadZone.Controls,
            rowIndex = cell.first,
            colIndex = cell.second,
        )
    }
    return when (action) {
        NavAction.Left -> atCell(current.rowIndex, current.colIndex - 1)
        NavAction.Right -> atCell(current.rowIndex, current.colIndex + 1)
        NavAction.Down -> if (current.rowIndex < layout.rowCount - 1) {
            atCell(current.rowIndex + 1, current.colIndex)
        } else {
            current
        }
        NavAction.Up -> if (current.rowIndex > 0) {
            atCell(current.rowIndex - 1, current.colIndex)
        } else {
            current
        }
        else -> current
    }
}
