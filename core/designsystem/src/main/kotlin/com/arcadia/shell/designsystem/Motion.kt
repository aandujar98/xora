package com.arcadia.shell.designsystem

import android.content.Context
import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Short, intentional shell motion (~200–350ms). */
object ArcadiaMotion {
    const val Fast = 180
    const val Medium = 240
    const val Slow = 320
    /** Theme wallpaper / BGM soft mix when switching packs. */
    const val ThemeCrossfade = THEME_CROSSFADE_MS
    /** Library / pill chrome exit when launching into an emulator. */
    const val Launch = 420
    /**
     * Hero artwork hold as the cinematic transition plate before the emulator starts.
     * Chrome may finish sliding out earlier; the ROM launch waits for this full beat.
     */
    const val LaunchHold = 3_000
}

/** True when system animator duration scale is zero (common reduce-motion signal). */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) { context.isReduceMotionPreferred() }
}

fun Context.isReduceMotionPreferred(): Boolean {
    val scale = Settings.Global.getFloat(
        contentResolver,
        Settings.Global.ANIMATOR_DURATION_SCALE,
        1f,
    )
    return scale == 0f
}

/** Duration that collapses to zero when reduced motion is preferred. */
@Composable
fun motionMillis(durationMillis: Int): Int {
    val reduce = rememberReduceMotion()
    return if (reduce) 0 else durationMillis
}

@Composable
fun <T> arcadiaTween(durationMillis: Int = ArcadiaMotion.Medium): TweenSpec<T> =
    tween(
        durationMillis = motionMillis(durationMillis),
        easing = FastOutSlowInEasing,
    )
