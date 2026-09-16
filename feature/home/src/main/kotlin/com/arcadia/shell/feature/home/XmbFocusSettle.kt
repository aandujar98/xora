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

/** Game Select waits a full second of idle so hero art does not chase a held d-pad. */
const val XMB_GAME_SELECT_SETTLE_MS = 1000L

/**
 * Non-game copy (systems, music, settings) can follow sooner than hero art. Matching the
 * card scroll keeps the outgoing line on screen while the wheel is still moving, then
 * crossfades as it lands. ROM title / partition / playtime use [rememberXmbSettledFocus]
 * at [XMB_GAME_SELECT_SETTLE_MS] so the new line waits for wallpaper and the sound bite.
 */
const val XMB_COPY_SETTLE_MS = 280L

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

/**
 * Same settle delay as [rememberXmbSettledFocus], but keeps the last value on screen while
 * the cursor is still moving so title / playtime can crossfade instead of vanishing.
 */
@Composable
fun <T> rememberXmbHeldFocus(
    key: T,
    settleMs: Long = XMB_FOCUS_SETTLE_MS,
): T {
    val reduceMotion = rememberReduceMotion()
    var settled by remember { mutableStateOf(key) }
    LaunchedEffect(key, reduceMotion, settleMs) {
        if (key == settled) return@LaunchedEffect
        delay(if (reduceMotion) 0L else settleMs)
        settled = key
    }
    return settled
}
