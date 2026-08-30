package com.arcadia.shell.feature.home.component

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.arcadia.shell.designsystem.ArcadiaMotion
import com.arcadia.shell.designsystem.rememberReduceMotion
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Game-launch doors: a snapshot of the current UI splits down the middle and the halves slide
 * off to the left and right, leaving black for the emulator fade.
 */
@Composable
fun LaunchSplitOverlay(
    launching: Boolean,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val reduceMotion = rememberReduceMotion()
    var snapshot by remember { mutableStateOf<ImageBitmap?>(null) }
    val progress = remember { Animatable(0f) }

    LaunchedEffect(launching, reduceMotion) {
        if (!launching) {
            snapshot = null
            progress.snapTo(0f)
            return@LaunchedEffect
        }
        if (reduceMotion) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        withFrameNanos { }
        val captured = captureWindowBitmap(view)?.asImageBitmap()
        snapshot = captured
        progress.snapTo(0f)
        if (captured == null) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        progress.animateTo(
            1f,
            tween(ArcadiaMotion.Launch, easing = FastOutSlowInEasing),
        )
    }

    if (!launching) return
    if (reduceMotion) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black),
        )
        return
    }
    val image = snapshot ?: return

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        val p = progress.value.coerceIn(0f, 1f)
        val halfW = size.width / 2f
        val slide = p * (halfW + 8f)
        val srcHalf = (image.width / 2).coerceAtLeast(1)
        val dstHalf = halfW.roundToInt().coerceAtLeast(1)
        val height = size.height.roundToInt().coerceAtLeast(1)
        val srcH = image.height.coerceAtLeast(1)
        drawImage(
            image = image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(srcHalf, srcH),
            dstOffset = IntOffset((-slide).roundToInt(), 0),
            dstSize = IntSize(dstHalf, height),
            filterQuality = FilterQuality.Low,
        )
        drawImage(
            image = image,
            srcOffset = IntOffset(srcHalf, 0),
            srcSize = IntSize((image.width - srcHalf).coerceAtLeast(1), srcH),
            dstOffset = IntOffset((halfW + slide).roundToInt(), 0),
            dstSize = IntSize((size.width - halfW).roundToInt().coerceAtLeast(1), height),
            filterQuality = FilterQuality.Low,
        )
    }
}

private suspend fun captureWindowBitmap(view: View): Bitmap? {
    val width = view.width
    val height = view.height
    if (width <= 0 || height <= 0) return null
    val window = (view.context as? Activity)?.window
        ?: return drawViewFallback(view, width, height)
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
                    cont.resume(drawViewFallback(view, width, height))
                }
            }, handler)
        } catch (_: RuntimeException) {
            bitmap.recycle()
            cont.resume(drawViewFallback(view, width, height))
        }
    }
}

private fun drawViewFallback(view: View, width: Int, height: Int): Bitmap? {
    return runCatching {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        bitmap
    }.getOrNull()
}
