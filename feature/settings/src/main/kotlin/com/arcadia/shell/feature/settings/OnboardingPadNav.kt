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

/**
 * Hat/stick auto-repeat from [com.arcadia.shell.input.GamepadDispatcher] is 70ms after a 350ms
 * delay. A wizard should not skip several steps while Right is held, so Left/Right are gated.
 */
internal const val ONBOARDING_PAD_DIRECTION_DEBOUNCE_MS = 400L

internal fun shouldAcceptOnboardingPadRepeat(
    action: NavAction,
    nowMs: Long,
    lastDirectionalMs: Long?,
): Boolean {
    if (action != NavAction.Left && action != NavAction.Right) return true
    val previous = lastDirectionalMs ?: return true
    return nowMs - previous >= ONBOARDING_PAD_DIRECTION_DEBOUNCE_MS
}

/**
 * Maps controller actions onto the first-run wizard.
 *
 * Handhelds report D-pad as a hat/stick, which never becomes Compose focus, so this is the
 * path that actually walks the steps. A / Right / RB advance, B / Left / LB go back, Y skips
 * an optional step.
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
        return if (action == NavAction.Cancel) OnboardingPadCommand.DismissPicker
        else OnboardingPadCommand.None
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
