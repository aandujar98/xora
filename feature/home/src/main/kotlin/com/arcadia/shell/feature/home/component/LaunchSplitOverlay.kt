package com.arcadia.shell.feature.home.component

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import android.view.Window
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import com.arcadia.shell.designsystem.ArcadiaMotion
import com.arcadia.shell.designsystem.rememberReduceMotion
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Game-launch doors: snapshot the live window, then slide the left half left and the right half
 * right. The overlay stays invisible until the copy is ready so PixelCopy does not photograph
 * a black plate. Capture failure still covers with black so launch is never a hard cut.
 */
@Composable
fun LaunchSplitOverlay(
    launching: Boolean,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val reduceMotion = rememberReduceMotion()
    var snapshot by remember { mutableStateOf<ImageBitmap?>(null) }
    var split by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(
        targetValue = if (split) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (reduceMotion) 0 else ArcadiaMotion.Launch,
            easing = FastOutSlowInEasing,
        ),
        label = "launchSplit",
    )

    LaunchedEffect(launching, reduceMotion) {
        if (!launching) {
            split = false
            snapshot = null
            return@LaunchedEffect
        }
        if (reduceMotion) {
            split = true
            return@LaunchedEffect
        }
        withFrameNanos { }
        withFrameNanos { }
        snapshot = captureWindowBitmap(view)?.asImageBitmap()
        split = true
    }

    if (!launching || !split) return

    val image = snapshot
    val p = progress
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (image == null) return@BoxWithConstraints
        val density = LocalDensity.current
        val fullWidth = maxWidth
        val half = fullWidth / 2
        val slidePx = with(density) { (half * p).toPx() }.roundToInt()
        Box(
            modifier = Modifier
                .width(half)
                .fillMaxHeight()
                .align(Alignment.CenterStart)
                .offset { IntOffset(-slidePx, 0) }
                .clipToBounds(),
        ) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .width(fullWidth)
                    .fillMaxHeight()
                    .align(Alignment.CenterStart),
            )
        }
        Box(
            modifier = Modifier
                .width(half)
                .fillMaxHeight()
                .align(Alignment.CenterEnd)
                .offset { IntOffset(slidePx, 0) }
                .clipToBounds(),
        ) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier
                    .width(fullWidth)
                    .fillMaxHeight()
                    .align(Alignment.CenterEnd),
            )
        }
    }
}

private suspend fun captureWindowBitmap(view: View): Bitmap? {
    val width = view.width
    val height = view.height
    if (width <= 0 || height <= 0) return null
    val window = view.findWindow() ?: return null
    return suspendCancellableCoroutine { cont ->
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val loc = IntArray(2)
        view.getLocationInWindow(loc)
        val src = Rect(loc[0], loc[1], loc[0] + width, loc[1] + height)
        val handler = Handler(Looper.getMainLooper())
        try {
            PixelCopy.request(window, src, bitmap, { result ->
                if (!cont.isActive) {
                    bitmap.recycle()
                    return@request
                }
                if (result == PixelCopy.SUCCESS) {
                    cont.resume(bitmap)
                } else {
                    bitmap.recycle()
                    cont.resume(null)
                }
            }, handler)
        } catch (_: RuntimeException) {
            bitmap.recycle()
            cont.resume(null)
        }
    }
}

private fun View.findWindow(): Window? {
    var ctx: Context? = context
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx.window
        val dialogWindow = (ctx as? Dialog)?.window
        if (dialogWindow != null) return dialogWindow
        ctx = ctx.baseContext
    }
    return null
}
