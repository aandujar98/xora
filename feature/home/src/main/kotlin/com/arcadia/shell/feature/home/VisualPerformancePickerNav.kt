package com.arcadia.shell.feature.home

import com.arcadia.shell.datastore.VisualPerformanceChoices
import com.arcadia.shell.datastore.VisualPerformanceMode
import com.arcadia.shell.input.NavAction

internal fun visualPerformancePickerIndex(
    action: NavAction,
    current: Int,
    count: Int = VisualPerformanceChoices.size,
): Int {
    if (count <= 0) return 0
    return when (action) {
        NavAction.Up -> (current - 1).coerceAtLeast(0)
        NavAction.Down -> (current + 1).coerceAtMost(count - 1)
        else -> current.coerceIn(0, count - 1)
    }
}

internal fun visualPerformancePickerMode(index: Int): VisualPerformanceMode =
    VisualPerformanceChoices.getOrElse(index.coerceAtLeast(0)) { VisualPerformanceMode.Auto }

internal fun visualPerformancePickerFocusIndex(mode: VisualPerformanceMode): Int =
    VisualPerformanceChoices.indexOf(mode).takeIf { it >= 0 } ?: 0
