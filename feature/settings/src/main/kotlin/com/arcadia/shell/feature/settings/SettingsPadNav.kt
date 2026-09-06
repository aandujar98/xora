package com.arcadia.shell.feature.settings

import com.arcadia.shell.input.NavAction

/**
 * Gamepad focus inside Advanced Settings. Starts on a card so the cursor lands on an option,
 * not the chrome. Shoulders (and Left/Right) change tabs.
 */
internal data class SettingsPadNavState(
    val sectionIndex: Int,
    val onTabs: Boolean,
    val cardIndex: Int,
)

internal fun settingsPadAfterAction(
    state: SettingsPadNavState,
    action: NavAction,
    sectionCount: Int,
    cardCount: Int,
): SettingsPadNavState {
    val sections = sectionCount.coerceAtLeast(1)
    val cards = cardCount.coerceAtLeast(0)
    return when (action) {
        NavAction.PreviousPlatform,
        NavAction.Left,
        -> state.copy(
            sectionIndex = (state.sectionIndex - 1 + sections) % sections,
            onTabs = false,
            cardIndex = 0,
        )
        NavAction.NextPlatform,
        NavAction.Right,
        -> state.copy(
            sectionIndex = (state.sectionIndex + 1) % sections,
            onTabs = false,
            cardIndex = 0,
        )
        NavAction.Down -> when {
            state.onTabs -> state.copy(onTabs = false, cardIndex = 0)
            cards <= 0 -> state
            else -> state.copy(cardIndex = (state.cardIndex + 1).coerceAtMost(cards - 1))
        }
        NavAction.Up -> when {
            !state.onTabs && state.cardIndex > 0 -> state.copy(cardIndex = state.cardIndex - 1)
            else -> state.copy(onTabs = true, cardIndex = 0)
        }
        NavAction.Confirm -> if (state.onTabs && cards > 0) {
            state.copy(onTabs = false, cardIndex = 0)
        } else {
            state
        }
        else -> state
    }
}

/** LazyColumn index the pad should scroll to. Item 0 is the header; a status banner may follow. */
internal fun settingsLazyItemIndex(
    onTabs: Boolean,
    cardIndex: Int,
    hasStatusBanner: Boolean,
): Int {
    if (onTabs) return 0
    val banner = if (hasStatusBanner) 1 else 0
    return 1 + banner + cardIndex.coerceAtLeast(0)
}
