package com.arcadia.shell.designsystem

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max

enum class XoraSwipeDirection { Left, Right, Up, Down }

/**
 * One-step flick navigation for controller-first menus. Nested scrollers (LazyColumn, etc.)
 * still win: a one-finger gesture only fires when no child consumed the drag, and clicks still
 * work under the touch slop.
 *
 * A two-finger flick is claimed even if a child started a one-finger scroll, so Home can open
 * the Vita shortcut tray without fighting the XMB.
 */
fun Modifier.xoraSwipeNavigate(
    enabled: Boolean = true,
    horizontal: Boolean = true,
    vertical: Boolean = true,
    threshold: Dp = 48.dp,
    onSwipe: (XoraSwipeDirection) -> Unit,
    onTwoFingerSwipe: ((XoraSwipeDirection) -> Unit)? = null,
): Modifier = composed {
    val onSwipeState = rememberUpdatedState(onSwipe)
    val onTwoFingerState = rememberUpdatedState(onTwoFingerSwipe)
    val thresholdPx = with(LocalDensity.current) { threshold.toPx() }
    if (!enabled || (!horizontal && !vertical && onTwoFingerSwipe == null)) return@composed this
    pointerInput(horizontal, vertical, thresholdPx, onTwoFingerSwipe != null) {
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = true)
            var total = Offset.Zero
            var dragging = false
            var stolen = false
            var maxPointers = 1
            while (true) {
                val event = awaitPointerEvent()
                val pressed = event.changes.count { it.pressed }
                if (pressed > maxPointers) maxPointers = pressed
                val twoFinger = maxPointers >= 2 && onTwoFingerState.value != null
                val change = event.changes.firstOrNull { it.id == down.id }
                    ?: event.changes.firstOrNull { it.pressed }
                    ?: break
                if (!twoFinger && change.isConsumed) {
                    stolen = true
                    break
                }
                val delta = if (twoFinger) {
                    event.changes
                        .filter { it.pressed || it.id == down.id }
                        .fold(Offset.Zero) { acc, pointer -> acc + pointer.positionChange() } /
                        event.changes.size.coerceAtLeast(1).toFloat()
                } else {
                    change.positionChange()
                }
                total += delta
                if (!dragging && total.getDistance() > slop) {
                    val horizontalDrag = abs(total.x) >= abs(total.y)
                    val allowAxis = if (twoFinger) {
                        true
                    } else {
                        (horizontalDrag && horizontal) || (!horizontalDrag && vertical)
                    }
                    if (!allowAxis) {
                        stolen = true
                        break
                    }
                    dragging = true
                }
                if (dragging) {
                    if (twoFinger) {
                        event.changes.forEach { it.consume() }
                    } else {
                        change.consume()
                    }
                }
                if (event.changes.none { it.pressed }) break
            }
            if (stolen || !dragging) return@awaitEachGesture
            val absX = abs(total.x)
            val absY = abs(total.y)
            if (max(absX, absY) < thresholdPx) return@awaitEachGesture
            val direction = if (absX > absY) {
                if (total.x > 0f) XoraSwipeDirection.Right else XoraSwipeDirection.Left
            } else {
                if (total.y > 0f) XoraSwipeDirection.Down else XoraSwipeDirection.Up
            }
            val twoFinger = maxPointers >= 2
            val twoFingerHandler = onTwoFingerState.value
            if (twoFinger && twoFingerHandler != null) {
                twoFingerHandler(direction)
            } else if (!twoFinger) {
                onSwipeState.value(direction)
            }
        }
    }
}
