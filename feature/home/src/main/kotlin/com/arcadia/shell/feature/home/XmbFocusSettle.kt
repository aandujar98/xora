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

/**
 * Returns [key] only after it has stayed put for [XMB_FOCUS_SETTLE_MS].
 * While the cursor is still moving, the value is null so titles can vanish.
 */
@Composable
fun <T> rememberXmbSettledFocus(key: T): T? {
    val reduceMotion = rememberReduceMotion()
    var settled by remember { mutableStateOf(key) }
    LaunchedEffect(key, reduceMotion) {
        if (key == settled) return@LaunchedEffect
        delay(if (reduceMotion) 0L else XMB_FOCUS_SETTLE_MS)
        settled = key
    }
    return key.takeIf { it == settled }
}
