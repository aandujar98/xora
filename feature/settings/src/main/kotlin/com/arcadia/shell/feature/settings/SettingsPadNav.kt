package com.arcadia.shell.feature.settings

import com.arcadia.shell.input.NavAction

object SettingsPadIds {
    const val Done = "chrome_done"
    const val Tabs = "chrome_tabs"
    const val SystemOfferHome = "system_offer_home"
    const val SystemOpenHomeSettings = "system_open_home_settings"
}

internal enum class SettingsPadZone {
    Done,
    Tabs,
    Controls,
}

/**
 * Gamepad focus inside Advanced Settings. Shoulders change tabs. Up/Down (and Left/Right when
 * the control is not a slider) walk every button, switch, chip, and field. Done sits above the
 * tab strip so chrome stays reachable without eating the first Down.
 */
internal data class SettingsPadNavState(
    val sectionIndex: Int,
    val zone: SettingsPadZone,
    val controlIndex: Int,
)

internal fun settingsPadAfterAction(
    state: SettingsPadNavState,
    action: NavAction,
    sectionCount: Int,
    controlCount: Int,
): SettingsPadNavState {
    val sections = sectionCount.coerceAtLeast(1)
    val controls = controlCount.coerceAtLeast(0)
    fun atControl(index: Int): SettingsPadNavState {
        if (controls <= 0) {
            return state.copy(zone = SettingsPadZone.Tabs, controlIndex = 0)
        }
        return state.copy(
            zone = SettingsPadZone.Controls,
            controlIndex = index.coerceIn(0, controls - 1),
        )
    }
    fun changeSection(delta: Int) = SettingsPadNavState(
        sectionIndex = (state.sectionIndex + delta + sections) % sections,
        zone = SettingsPadZone.Controls,
        controlIndex = 0,
    )
    return when (action) {
        NavAction.PreviousPlatform -> changeSection(-1)
        NavAction.NextPlatform -> changeSection(1)
        NavAction.Left -> when (state.zone) {
            SettingsPadZone.Done,
            SettingsPadZone.Tabs,
            -> changeSection(-1)
            SettingsPadZone.Controls -> if (state.controlIndex > 0) {
                atControl(state.controlIndex - 1)
            } else {
                state.copy(zone = SettingsPadZone.Tabs, controlIndex = 0)
            }
        }
        NavAction.Right -> when (state.zone) {
            SettingsPadZone.Done,
            SettingsPadZone.Tabs,
            -> changeSection(1)
            SettingsPadZone.Controls -> atControl(state.controlIndex + 1)
        }
        NavAction.Down -> when (state.zone) {
            SettingsPadZone.Done -> state.copy(zone = SettingsPadZone.Tabs, controlIndex = 0)
            SettingsPadZone.Tabs -> atControl(0)
            SettingsPadZone.Controls -> atControl(state.controlIndex + 1)
        }
        NavAction.Up -> when (state.zone) {
            SettingsPadZone.Done -> state
            SettingsPadZone.Tabs -> state.copy(zone = SettingsPadZone.Done, controlIndex = 0)
            SettingsPadZone.Controls -> if (state.controlIndex > 0) {
                atControl(state.controlIndex - 1)
            } else {
                state.copy(zone = SettingsPadZone.Tabs, controlIndex = 0)
            }
        }
        NavAction.Confirm -> if (state.zone == SettingsPadZone.Tabs && controls > 0) {
            atControl(0)
        } else {
            state
        }
        else -> state
    }
}

internal fun settingsPadFocusId(
    state: SettingsPadNavState,
    controlIds: List<String>,
): String? = when (state.zone) {
    SettingsPadZone.Done -> SettingsPadIds.Done
    SettingsPadZone.Tabs -> SettingsPadIds.Tabs
    SettingsPadZone.Controls -> controlIds.getOrNull(
        state.controlIndex.coerceIn(0, (controlIds.size - 1).coerceAtLeast(0)),
    )
}

internal fun settingsPadCoerce(
    state: SettingsPadNavState,
    controlCount: Int,
): SettingsPadNavState {
    if (state.zone != SettingsPadZone.Controls) return state
    if (controlCount <= 0) return state.copy(zone = SettingsPadZone.Tabs, controlIndex = 0)
    return state.copy(controlIndex = state.controlIndex.coerceIn(0, controlCount - 1))
}
