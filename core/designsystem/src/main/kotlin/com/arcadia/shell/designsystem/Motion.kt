package com.arcadia.shell.designsystem

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlinx.coroutines.delay

/** Short, intentional shell motion (~200–350ms). */
object ArcadiaMotion {
    const val Fast = 180
    const val Medium = 240
    const val Slow = 320
    /** Theme wallpaper / BGM soft mix when switching packs. */
    const val ThemeCrossfade = THEME_CROSSFADE_MS
    /** UI chrome fade. The backdrop is already drifting under it. */
    const val Launch = 500
    /**
     * Backdrop zoom. Long and linear so it reads as a slow push rather than a move that starts,
     * accelerates and lands — a short eased zoom of any size reads as a punch.
     */
    const val LaunchZoom = 2000
    /** The drift starts under the chrome fade, so the whole plate is one continuous move. */
    const val LaunchZoomAt = 200
    /** Wallpaper starts dissolving this many ms after launch begins. */
    const val LaunchWallpaperFadeAt = 2100
    /** Wallpaper dissolve into black before the emulator Activity takes over. */
    const val LaunchWallpaperFade = 700
    /**
     * Whole cinematic plate before the emulator Activity is started: the backdrop drifts in, the
     * chrome clears, then the wallpaper dissolves from [LaunchWallpaperFadeAt]. Ends as that
     * dissolve does, so the emulator's own fade-in continues one movement instead of following a
     * beat of dead black.
     */
    const val LaunchHold = 2800
    /**
     * Ken Burns push on wallpaper / hero art. Small on purpose: enough to feel the screen breathe
     * into the game, not enough to crop the art or read as a lurch.
     */
    const val LaunchBackdropZoom = 0.05f
    /** White plate dissolve from the boot clip into the XMB. */
    const val BootWhiteFade = 560
    /**
     * Hero backdrop dissolve as the focused ROM changes. Long enough that the incoming art has
     * decoded before the outgoing has finished leaving — a short fade reads as a dip to empty.
     */
    const val HeroCrossfade = 460
    /**
     * Extra scale on the incoming ROM hero while it crossfades. A light drift only —
     * well under [LaunchBackdropZoom] so a launch still reads as a bigger move.
     */
    const val HeroBrowseZoom = 0.025f
    /** Ken Burns while the new hero fades in — short so it does not linger. */
    const val HeroBrowseZoomMs = 520
    /** Title / playtime fade+slide when the focused ROM changes. */
    const val HeroCopy = 520
    const val HeroCopyExit = 340
}

/**
 * Wallpaper / dust / wave loops. Compose infinite transitions still tick every vsync;
 * dual 1080p AMOLED handhelds (AYN Thor) cannot afford that on both panels.
 * 30 fps is the floor that still reads as water; vsync (90–120) is what drained the battery.
 */
const val AMBIENT_DRAW_FPS = 30

/** True when system animator duration scale is zero (common reduce-motion signal). */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) { context.isReduceMotionPreferred() }
}

/**
 * True while this composition is actually being looked at.
 *
 * XOrA is a home app, so its chrome stays composed long after the player has put the device down.
 * Anything that repeats on a timer should hang off this rather than running for the life of the
 * process, which on a handheld shows up as heat, fan noise, and flat batteries.
 */
@Composable
fun rememberShellResumed(): Boolean {
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    return lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
}

/** Process-level foreground — secondary [android.app.Presentation] panes stay locally RESUMED. */
@Composable
fun rememberProcessForeground(): Boolean {
    val lifecycle = remember { ProcessLifecycleOwner.get().lifecycle }
    val state by lifecycle.currentStateAsState()
    return state.isAtLeast(Lifecycle.State.STARTED)
}

/** System battery saver. Live: flipping the tile stops wallpaper clocks without a restart. */
@Composable
fun rememberPowerSaveMode(): Boolean {
    val context = LocalContext.current
    val power = remember(context) { context.getSystemService(Context.POWER_SERVICE) as PowerManager }
    var saving by remember { mutableStateOf(power.isPowerSaveMode) }
    DisposableEffect(context, power) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                saving = power.isPowerSaveMode
            }
        }
        val filter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }
        saving = power.isPowerSaveMode
        onDispose { runCatching { context.unregisterReceiver(receiver) } }
    }
    return saving
}

/**
 * Whether looping shell *decoration* should be running: on screen, the process in front,
 * motion not reduced, and battery saver off. Gated loops hold a still frame instead of animating.
 */
@Composable
fun rememberAmbientMotionActive(): Boolean =
    rememberShellResumed() &&
        rememberProcessForeground() &&
        !rememberReduceMotion() &&
        !rememberPowerSaveMode()

/**
 * Slow clock for wallpaper loops. Read [FloatState.floatValue] only inside `drawBehind` / `Canvas`
 * so ticks invalidate paint, not composition or layout.
 *
 * [floatValue] is seconds into a [loopSeconds] cycle (or [still] when ambient motion is off).
 */
@Composable
fun rememberThrottledAmbientSeconds(
    loopSeconds: Float,
    fps: Int = AMBIENT_DRAW_FPS,
    still: Float = 0f,
): FloatState {
    val active = rememberAmbientMotionActive()
    val clock = remember { mutableFloatStateOf(still) }
    LaunchedEffect(active, loopSeconds, fps, still) {
        if (!active) {
            clock.floatValue = still
            return@LaunchedEffect
        }
        val start = SystemClock.elapsedRealtime()
        val frameMs = (1000L / fps.coerceIn(4, 60))
        val loopMs = (loopSeconds.coerceAtLeast(0.001f) * 1000.0)
        while (true) {
            val elapsed = (SystemClock.elapsedRealtime() - start).toDouble()
            clock.floatValue = ((elapsed % loopMs) / 1000.0).toFloat()
            delay(frameMs)
        }
    }
    return clock
}

/**
 * Same clock as [rememberThrottledAmbientSeconds], normalized to 0..1 through [cycleMs].
 */
@Composable
fun rememberThrottledAmbientUnit(
    cycleMs: Int,
    fps: Int = AMBIENT_DRAW_FPS,
    still: Float = 0f,
): FloatState {
    val active = rememberAmbientMotionActive()
    val clock = remember { mutableFloatStateOf(still) }
    LaunchedEffect(active, cycleMs, fps, still) {
        if (!active) {
            clock.floatValue = still
            return@LaunchedEffect
        }
        val start = SystemClock.elapsedRealtime()
        val frameMs = (1000L / fps.coerceIn(4, 60))
        val period = cycleMs.coerceAtLeast(1).toDouble()
        while (true) {
            val elapsed = (SystemClock.elapsedRealtime() - start).toDouble()
            clock.floatValue = ((elapsed % period) / period).toFloat()
            delay(frameMs)
        }
    }
    return clock
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

/** Chrome fade, delayed wallpaper zoom, and wallpaper dissolve for a game launch. */
data class LaunchCinematicProgress(
    val chrome: Float,
    val zoom: Float,
    val wallpaperFade: Float,
) {
    val chromeAlpha: Float get() = (1f - chrome).coerceIn(0f, 1f)
    val wallpaperAlpha: Float get() = (1f - wallpaperFade).coerceIn(0f, 1f)
}

@Composable
fun rememberLaunchCinematic(isLaunching: Boolean): LaunchCinematicProgress {
    val reduce = rememberReduceMotion()
    val snapMs = if (reduce) 0 else ArcadiaMotion.Fast
    val chrome by animateFloatAsState(
        targetValue = if (isLaunching) 1f else 0f,
        animationSpec = if (isLaunching && !reduce) {
            tween(ArcadiaMotion.Launch, easing = FastOutSlowInEasing)
        } else {
            tween(snapMs, easing = FastOutSlowInEasing)
        },
        label = "launchChromeFade",
    )
    val zoom by animateFloatAsState(
        targetValue = if (isLaunching) 1f else 0f,
        animationSpec = if (isLaunching && !reduce) {
            tween(
                durationMillis = ArcadiaMotion.LaunchZoom,
                delayMillis = ArcadiaMotion.LaunchZoomAt,
                // Linear: an eased push spends its middle third moving fast, which is exactly the
                // lurch a slow zoom is trying to avoid.
                easing = LinearEasing,
            )
        } else {
            tween(snapMs, easing = FastOutSlowInEasing)
        },
        label = "launchZoom",
    )
    val wallpaperFade by animateFloatAsState(
        targetValue = if (isLaunching) 1f else 0f,
        animationSpec = if (isLaunching && !reduce) {
            tween(
                durationMillis = ArcadiaMotion.LaunchWallpaperFade,
                delayMillis = ArcadiaMotion.LaunchWallpaperFadeAt,
                easing = FastOutSlowInEasing,
            )
        } else {
            tween(snapMs, easing = FastOutSlowInEasing)
        },
        label = "launchWallpaperFade",
    )
    return LaunchCinematicProgress(
        chrome = chrome,
        zoom = zoom,
        wallpaperFade = wallpaperFade,
    )
}
