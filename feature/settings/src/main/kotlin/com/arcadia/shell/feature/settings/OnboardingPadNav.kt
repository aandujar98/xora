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

private val OnboardingChromeIdSet = setOf(
    OnboardingPadIds.Back,
    OnboardingPadIds.Skip,
    OnboardingPadIds.Next,
)

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

internal fun onboardingChromeIds(
    canGoBack: Boolean,
    optional: Boolean,
): List<String> = buildList {
    if (canGoBack) add(OnboardingPadIds.Back)
    if (optional) add(OnboardingPadIds.Skip)
    add(OnboardingPadIds.Next)
}

/** Step fields only. Back / Skip / Next live in the chrome zone so they are one Up away. */
internal fun onboardingControlsLayout(layout: SettingsPadLayout): SettingsPadLayout =
    SettingsPadLayout(
        layout.rows.map { row -> row.filter { it !in OnboardingChromeIdSet } }
            .filter { it.isNotEmpty() },
    )

internal fun onboardingPadFocusId(
    state: SettingsPadNavState,
    layout: SettingsPadLayout,
    chromeIds: List<String>,
): String? {
    val coerced = onboardingPadCoerce(state, layout, chromeIds)
    return if (coerced.zone != SettingsPadZone.Controls) {
        chromeIds.getOrNull(coerced.colIndex)
    } else {
        val cell = layout.clamp(coerced.rowIndex, coerced.colIndex) ?: return null
        layout.idAt(cell.first, cell.second)
    }
}

internal fun onboardingPadCoerce(
    state: SettingsPadNavState,
    layout: SettingsPadLayout,
    chromeIds: List<String>,
): SettingsPadNavState {
    fun chrome(col: Int): SettingsPadNavState {
        if (chromeIds.isEmpty()) {
            return state.copy(zone = SettingsPadZone.Controls, rowIndex = 0, colIndex = 0)
        }
        return state.copy(
            zone = SettingsPadZone.Done,
            rowIndex = 0,
            colIndex = col.coerceIn(0, chromeIds.lastIndex),
        )
    }
    if (state.zone != SettingsPadZone.Controls) {
        return chrome(state.colIndex)
    }
    val cell = layout.clamp(state.rowIndex, state.colIndex) ?: return chrome(
        chromeIds.indexOf(OnboardingPadIds.Next).takeIf { it >= 0 }
            ?: chromeIds.lastIndex.coerceAtLeast(0),
    )
    return state.copy(
        zone = SettingsPadZone.Controls,
        rowIndex = cell.first,
        colIndex = cell.second,
    )
}

/**
 * Form rows walk like Setup. Up from the first field and Down from the last field jump to
 * Next so the footer is not a long march through every chip and text field.
 */
internal fun onboardingPadAfterAction(
    state: SettingsPadNavState,
    action: NavAction,
    layout: SettingsPadLayout,
    chromeIds: List<String>,
): SettingsPadNavState {
    val current = onboardingPadCoerce(state, layout, chromeIds)
    val nextChromeCol = chromeIds.indexOf(OnboardingPadIds.Next).takeIf { it >= 0 }
        ?: chromeIds.lastIndex.coerceAtLeast(0)
    fun atControl(row: Int, col: Int): SettingsPadNavState =
        onboardingPadCoerce(
            current.copy(zone = SettingsPadZone.Controls, rowIndex = row, colIndex = col),
            layout,
            chromeIds,
        )
    fun atChrome(col: Int): SettingsPadNavState =
        onboardingPadCoerce(
            current.copy(zone = SettingsPadZone.Done, rowIndex = 0, colIndex = col),
            layout,
            chromeIds,
        )
    if (current.zone != SettingsPadZone.Controls) {
        return when (action) {
            NavAction.Left -> atChrome(current.colIndex - 1)
            NavAction.Right -> atChrome(current.colIndex + 1)
            NavAction.Down -> if (layout.rowCount == 0) {
                current
            } else {
                atControl(0, 0)
            }
            NavAction.Up -> if (layout.rowCount == 0) {
                current
            } else {
                atControl(layout.rowCount - 1, current.colIndex)
            }
            else -> current
        }
    }
    return when (action) {
        NavAction.Left -> atControl(current.rowIndex, current.colIndex - 1)
        NavAction.Right -> atControl(current.rowIndex, current.colIndex + 1)
        NavAction.Down -> if (current.rowIndex < layout.rowCount - 1) {
            atControl(current.rowIndex + 1, current.colIndex)
        } else {
            atChrome(nextChromeCol)
        }
        NavAction.Up -> if (current.rowIndex > 0) {
            atControl(current.rowIndex - 1, current.colIndex)
        } else {
            atChrome(nextChromeCol)
        }
        else -> current
    }
}
