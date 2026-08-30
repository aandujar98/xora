package com.arcadia.shell.feature.home.component

import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.arcadia.shell.designsystem.ArcadiaMotion
import com.arcadia.shell.designsystem.rememberReduceMotion
import com.arcadia.shell.feature.home.assetExists

/** Bundled cold-start boot clip (from the `xora-boot` GitHub release). */
const val BOOT_INTRO_ASSET = "boot/bootup.mp4"

private const val BOOT_URI = "asset:///$BOOT_INTRO_ASSET"

/**
 * Cold-start boot: play [BOOT_INTRO_ASSET] once, hold the white last frame, then fade that white
 * out so the XMB can bounce in underneath. Tap / Back / B skips to the white fade.
 */
@Composable
fun BootIntroOverlay(
    visible: Boolean,
    skip: Boolean,
    onRevealHome: () -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!visible) return

    val context = LocalContext.current
    val reduceMotion = rememberReduceMotion()
    val whiteAlpha = remember { Animatable(1f) }
    var videoReady by remember { mutableStateOf(false) }
    var requestEnd by remember { mutableStateOf(false) }
    var fading by remember { mutableStateOf(false) }
    val revealHome = rememberUpdatedState(onRevealHome)
    val finished = rememberUpdatedState(onFinished)
    val hasAsset = remember(context) { assetExists(context, BOOT_INTRO_ASSET) }

    LaunchedEffect(reduceMotion, hasAsset) {
        if (reduceMotion || !hasAsset) requestEnd = true
    }
    LaunchedEffect(skip) {
        if (skip) requestEnd = true
    }

    LaunchedEffect(videoReady) {
        if (videoReady && !fading) {
            whiteAlpha.snapTo(0f)
        }
    }

    LaunchedEffect(requestEnd) {
        if (!requestEnd || fading) return@LaunchedEffect
        fading = true
        whiteAlpha.snapTo(1f)
        revealHome.value()
        whiteAlpha.animateTo(
            0f,
            tween(ArcadiaMotion.BootWhiteFade, easing = FastOutSlowInEasing),
        )
        finished.value()
    }

    BackHandler(enabled = !fading) { requestEnd = true }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { requestEnd = true },
            ),
    ) {
        if (hasAsset && !reduceMotion && !fading) {
            BootIntroPlayer(
                onReady = { videoReady = true },
                onEnded = { requestEnd = true },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = if (videoReady) 1f else 0f },
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = whiteAlpha.value }
                .background(Color.White)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { requestEnd = true },
                ),
        )
    }
}

@Composable
private fun BootIntroPlayer(
    onReady: () -> Unit,
    onEnded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val ready = rememberUpdatedState(onReady)
    val ended = rememberUpdatedState(onEnded)
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(BOOT_URI))
            repeatMode = Player.REPEAT_MODE_OFF
            volume = 1f
            prepare()
            playWhenReady = true
        }
    }

    DisposableEffect(player, lifecycleOwner) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> ready.value()
                    Player.STATE_ENDED -> ended.value()
                    else -> Unit
                }
            }
        }
        player.addListener(listener)
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE,
                androidx.lifecycle.Lifecycle.Event.ON_STOP,
                -> player.pause()
                androidx.lifecycle.Lifecycle.Event.ON_RESUME ->
                    if (player.playbackState != Player.STATE_ENDED) player.play()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            player.removeListener(listener)
            lifecycleOwner.lifecycle.removeObserver(observer)
            player.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                this.player = player
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                setShutterBackgroundColor(android.graphics.Color.WHITE)
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
        },
        update = { it.player = player },
        onRelease = { view -> view.player = null },
        modifier = modifier,
    )
}
