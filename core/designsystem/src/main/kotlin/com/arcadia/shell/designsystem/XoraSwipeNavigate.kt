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
 * still win: this gesture only fires when no child consumed the drag, and clicks still work
 * under the touch slop.
 */
fun Modifier.xoraSwipeNavigate(
    enabled: Boolean = true,
    horizontal: Boolean = true,
    vertical: Boolean = true,
    threshold: Dp = 48.dp,
    onSwipe: (XoraSwipeDirection) -> Unit,
): Modifier = composed {
    val onSwipeState = rememberUpdatedState(onSwipe)
    val thresholdPx = with(LocalDensity.current) { threshold.toPx() }
    if (!enabled || (!horizontal && !vertical)) return@composed this
    pointerInput(horizontal, vertical, thresholdPx) {
        val slop = viewConfiguration.touchSlop
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = true)
            var total = Offset.Zero
            var dragging = false
            var stolen = false
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (change.isConsumed) {
                    stolen = true
                    break
                }
                total += change.positionChange()
                if (!dragging && total.getDistance() > slop) {
                    val horizontalDrag = abs(total.x) >= abs(total.y)
                    if (horizontalDrag && !horizontal || !horizontalDrag && !vertical) {
                        stolen = true
                        break
                    }
                    dragging = true
                }
                if (dragging) change.consume()
                if (!change.pressed) break
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
            onSwipeState.value(direction)
        }
    }
}
