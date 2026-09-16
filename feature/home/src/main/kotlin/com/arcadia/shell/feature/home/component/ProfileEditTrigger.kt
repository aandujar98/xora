package com.arcadia.shell.feature.home.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue

/**
 * Fires [onOpen] once per *new* profile-edit request.
 *
 * The request is a monotonic counter, so `LaunchedEffect(request) { if (request > 0) … }` also
 * runs on first composition — which meant any pane remount replayed the last request. Closing a
 * settings overlay does exactly that, so B out of Customize (or Start settings, or the pin
 * picker) popped the profile editor open. Seeding the watermark with the value present at mount
 * makes an existing request stale by definition; only a later increment counts.
 */
@Composable
fun ProfileEditRequestEffect(request: Int, onOpen: () -> Unit) {
    var handled by remember { mutableIntStateOf(request) }
    val open by rememberUpdatedState(onOpen)
    LaunchedEffect(request) {
        if (request > handled) {
            handled = request
            open()
        }
    }
}
