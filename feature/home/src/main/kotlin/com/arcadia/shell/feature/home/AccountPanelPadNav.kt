package com.arcadia.shell.feature.home

import com.arcadia.shell.datastore.CIRCLE_FRIEND_LIMIT
import com.arcadia.shell.input.NavAction

/** Outcome of one D-pad / stick step inside the LT social card. */
data class AccountPanelPadResult(
    val index: Int,
    val tabDelta: Int = 0,
)

/**
 * 2D pad geometry for the LT social card.
 *
 * Notifications is a single-cell header. Pinned friends are one horizontal row
 * ([CIRCLE_FRIEND_LIMIT] slots). Everything after that is a vertical list. Left/Right on the
 * pin row walk the pins; Left/Right elsewhere cycle tabs. Up/Down leave a row instead of
 * scrubbing through it.
 */
internal fun accountPanelAfterAction(
    index: Int,
    action: NavAction,
    rowCount: Int,
    notificationsOpen: Boolean,
    pinCount: Int = CIRCLE_FRIEND_LIMIT,
): AccountPanelPadResult {
    val size = rowCount.coerceAtLeast(0)
    if (size == 0) return AccountPanelPadResult(0)
    val pins = pinCount.coerceAtLeast(0)
    val pinStart = 1
    val pinEnd = pins
    val bodyStart = pins + 1
    val current = index.coerceIn(0, size - 1)
    val onPins = pins > 0 && current in pinStart..pinEnd.coerceAtMost(size - 1)
    val onBody = current >= bodyStart

    fun at(i: Int) = AccountPanelPadResult(i.coerceIn(0, size - 1))
    fun tab(delta: Int) = if (notificationsOpen) {
        AccountPanelPadResult(current)
    } else {
        AccountPanelPadResult(current, tabDelta = delta)
    }

    return when (action) {
        NavAction.PreviousPlatform -> tab(-1)
        NavAction.NextPlatform -> tab(1)
        NavAction.Left -> if (onPins) at((current - 1).coerceAtLeast(pinStart)) else tab(-1)
        NavAction.Right -> if (onPins) at((current + 1).coerceAtMost(pinEnd.coerceAtMost(size - 1))) else tab(1)
        NavAction.Up -> when {
            onBody && current > bodyStart -> at(current - 1)
            onBody && pins > 0 && pinStart < size -> at(pinStart)
            onBody -> at(0)
            onPins -> at(0)
            else -> at(current)
        }
        NavAction.Down -> when {
            current == 0 && pins > 0 && pinStart < size -> at(pinStart)
            current == 0 && bodyStart < size -> at(bodyStart)
            onPins && bodyStart < size -> at(bodyStart)
            onBody -> at(current + 1)
            else -> at(current)
        }
        else -> AccountPanelPadResult(current)
    }
}
