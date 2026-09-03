package com.arcadia.shell.designsystem

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

/** True when [path] resolves inside the merged APK asset tree. */
fun xoraAssetExists(context: Context, path: String): Boolean =
    runCatching { context.assets.open(path).use { true } }.getOrDefault(false)

/**
 * Muted, looping video backdrop shared by the home wallpaper and first-run onboarding, so both
 * surfaces show the same theme loop from one implementation.
 *
 * [uri] is already fully qualified (`file://…` for picked media, `asset:///…` for theme packs).
 */
@Composable
fun XoraLoopingVideo(
    uri: String,
    modifier: Modifier = Modifier,
    speed: Float = 1f,
    alignment: Alignment = Alignment.Center,
    pan: Boolean = false,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            repeatMode = Player.REPEAT_MODE_ONE
            volume = 0f
            setPlaybackSpeed(speed.coerceIn(0.25f, 2f))
            prepare()
            playWhenReady = true
        }
    }

    // Speed is applied outside the player factory so a rate change never restarts the loop.
    LaunchedEffect(player, speed) {
        player.setPlaybackSpeed(speed.coerceIn(0.25f, 2f))
    }

    DisposableEffect(player, lifecycleOwner) {
        // Watch BOTH lifecycles: the local owner (Activity) and the whole process. A secondary
        // display Presentation stays RESUMED from show() to dismiss(), so on dual-screen devices
        // this player used to keep decoding video all night while the device slept — fans + battery.
        // Process ON_STOP (screen off / another app owning every screen) now always pauses it.
        var localResumed =
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        var processStarted = ProcessLifecycleOwner.get()
            .lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        fun syncPlayback() {
            val shouldPlay = localResumed && processStarted
            player.playWhenReady = shouldPlay
            if (!shouldPlay) player.pause()
        }
        val localObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP,
                -> {
                    localResumed = false
                    syncPlayback()
                }
                Lifecycle.Event.ON_RESUME -> {
                    localResumed = true
                    syncPlayback()
                }
                else -> Unit
            }
        }
        val processObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    processStarted = false
                    syncPlayback()
                }
                Lifecycle.Event.ON_START -> {
                    processStarted = true
                    syncPlayback()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(localObserver)
        ProcessLifecycleOwner.get().lifecycle.addObserver(processObserver)
        syncPlayback()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(localObserver)
            ProcessLifecycleOwner.get().lifecycle.removeObserver(processObserver)
            player.release()
        }
    }

    val videoModifier = if (pan) {
        modifier.graphicsLayer {
            scaleX = 1.24f
            scaleY = 1.24f
            val bias = alignment as? BiasAlignment
            translationX = -(bias?.horizontalBias ?: 0f) * size.width * 0.12f
            translationY = -(bias?.verticalBias ?: 0f) * size.height * 0.12f
        }
    } else {
        modifier
    }

    AndroidView(
        factory = { ctx ->
            (LayoutInflater.from(ctx).inflate(R.layout.xora_video_backdrop, null) as PlayerView)
                .apply {
                    this.player = player
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                }
        },
        update = { it.player = player },
        onRelease = { view -> view.player = null },
        modifier = videoModifier,
    )
}

/**
 * The default theme's looping wallpaper, for surfaces that sit outside the themed home shell
 * (first-run onboarding). Falls back to the procedural [XoraFlowBackdrop] when the theme pack's
 * video is not bundled, matching what the home wallpaper does.
 *
 * [scrim] darkens the loop so foreground copy stays legible over its brighter frames.
 */
@Composable
fun DefaultThemeBackdrop(
    modifier: Modifier = Modifier,
    scrim: Float = 0.45f,
) {
    val context = LocalContext.current
    val hasVideo = remember { xoraAssetExists(context, DEFAULT_WALLPAPER_ASSET) }
    Box(modifier = modifier.fillMaxSize()) {
        if (hasVideo) {
            XoraLoopingVideo(
                uri = "asset:///$DEFAULT_WALLPAPER_ASSET",
                speed = DEFAULT_WALLPAPER_SPEED,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            XoraFlowBackdrop(Modifier.fillMaxSize())
        }
        if (scrim > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrim.coerceIn(0f, 1f))),
            )
        }
    }
}
