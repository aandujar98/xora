package com.arcadia.shell.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.arcadia.shell.designsystem.rememberReduceMotion
import kotlinx.coroutines.delay

/** Hold still this long before titles, hero art, and sound bites follow a new focus. */
const val XMB_FOCUS_SETTLE_MS = 500L

/** Game Select waits a full second of idle so hero / titles do not chase a held d-pad. */
const val XMB_GAME_SELECT_SETTLE_MS = 1000L

/**
 * Returns [key] only after it has stayed put for [settleMs].
 * While the cursor is still moving, the value is null so titles can vanish.
 */
@Composable
fun <T> rememberXmbSettledFocus(
    key: T,
    settleMs: Long = XMB_FOCUS_SETTLE_MS,
): T? {
    val reduceMotion = rememberReduceMotion()
    var settled by remember { mutableStateOf(key) }
    LaunchedEffect(key, reduceMotion, settleMs) {
        if (key == settled) return@LaunchedEffect
        delay(if (reduceMotion) 0L else settleMs)
        settled = key
    }
    return key.takeIf { it == settled }
}
