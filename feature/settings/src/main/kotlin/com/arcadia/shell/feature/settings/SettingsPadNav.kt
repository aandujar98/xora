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

/** Row-major pad geometry. Each inner list is one visual row, left to right. */
internal data class SettingsPadLayout(
    val rows: List<List<String>> = emptyList(),
) {
    val rowCount: Int get() = rows.size

    fun cols(row: Int): Int = rows.getOrNull(row)?.size ?: 0

    fun idAt(row: Int, col: Int): String? = rows.getOrNull(row)?.getOrNull(col)

    fun clamp(row: Int, col: Int): Pair<Int, Int>? {
        if (rows.isEmpty()) return null
        val r = row.coerceIn(0, rows.lastIndex)
        val lastCol = rows[r].lastIndex.coerceAtLeast(0)
        return r to col.coerceIn(0, lastCol)
    }
}

/**
 * Gamepad focus inside Advanced Settings. Shoulders change tabs. Left/Right stay on a visual
 * row; Up/Down change rows. Done sits above the tab strip so chrome stays reachable without
 * eating the first Down.
 */
internal data class SettingsPadNavState(
    val sectionIndex: Int,
    val zone: SettingsPadZone,
    val rowIndex: Int = 0,
    val colIndex: Int = 0,
)

internal fun settingsPadAfterAction(
    state: SettingsPadNavState,
    action: NavAction,
    sectionCount: Int,
    layout: SettingsPadLayout,
): SettingsPadNavState {
    val sections = sectionCount.coerceAtLeast(1)
    fun atCell(row: Int, col: Int): SettingsPadNavState {
        val cell = layout.clamp(row, col) ?: return state.copy(
            zone = SettingsPadZone.Tabs,
            rowIndex = 0,
            colIndex = 0,
        )
        return state.copy(zone = SettingsPadZone.Controls, rowIndex = cell.first, colIndex = cell.second)
    }
    fun changeSection(delta: Int) = SettingsPadNavState(
        sectionIndex = (state.sectionIndex + delta + sections) % sections,
        zone = SettingsPadZone.Controls,
        rowIndex = 0,
        colIndex = 0,
    )
    return when (action) {
        NavAction.PreviousPlatform -> changeSection(-1)
        NavAction.NextPlatform -> changeSection(1)
        NavAction.Left -> when (state.zone) {
            SettingsPadZone.Done,
            SettingsPadZone.Tabs,
            -> changeSection(-1)
            SettingsPadZone.Controls -> atCell(state.rowIndex, state.colIndex - 1)
        }
        NavAction.Right -> when (state.zone) {
            SettingsPadZone.Done,
            SettingsPadZone.Tabs,
            -> changeSection(1)
            SettingsPadZone.Controls -> atCell(state.rowIndex, state.colIndex + 1)
        }
        NavAction.Down -> when (state.zone) {
            SettingsPadZone.Done -> state.copy(zone = SettingsPadZone.Tabs, rowIndex = 0, colIndex = 0)
            SettingsPadZone.Tabs -> atCell(0, 0)
            SettingsPadZone.Controls -> if (state.rowIndex < layout.rowCount - 1) {
                atCell(state.rowIndex + 1, state.colIndex)
            } else {
                state
            }
        }
        NavAction.Up -> when (state.zone) {
            SettingsPadZone.Done -> state
            SettingsPadZone.Tabs -> state.copy(zone = SettingsPadZone.Done, rowIndex = 0, colIndex = 0)
            SettingsPadZone.Controls -> if (state.rowIndex > 0) {
                atCell(state.rowIndex - 1, state.colIndex)
            } else {
                state.copy(zone = SettingsPadZone.Tabs, rowIndex = 0, colIndex = 0)
            }
        }
        NavAction.Confirm -> if (state.zone == SettingsPadZone.Tabs && layout.rowCount > 0) {
            atCell(0, 0)
        } else {
            state
        }
        else -> state
    }
}

internal fun settingsPadFocusId(
    state: SettingsPadNavState,
    layout: SettingsPadLayout,
): String? = when (state.zone) {
    SettingsPadZone.Done -> SettingsPadIds.Done
    SettingsPadZone.Tabs -> SettingsPadIds.Tabs
    SettingsPadZone.Controls -> {
        val cell = layout.clamp(state.rowIndex, state.colIndex)
        if (cell == null) null else layout.idAt(cell.first, cell.second)
    }
}

internal fun settingsPadCoerce(
    state: SettingsPadNavState,
    layout: SettingsPadLayout,
): SettingsPadNavState {
    if (state.zone != SettingsPadZone.Controls) return state
    val cell = layout.clamp(state.rowIndex, state.colIndex)
        ?: return state.copy(zone = SettingsPadZone.Tabs, rowIndex = 0, colIndex = 0)
    return state.copy(rowIndex = cell.first, colIndex = cell.second)
}

/** True when Up/Down cannot move the cursor, so Setup should scroll the page instead. */
internal fun settingsPadShouldScrollPage(
    before: SettingsPadNavState,
    after: SettingsPadNavState,
    action: NavAction,
): Boolean {
    if (action != NavAction.Up && action != NavAction.Down) return false
    return after == before
}
