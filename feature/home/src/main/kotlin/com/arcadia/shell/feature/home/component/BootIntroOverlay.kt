package com.arcadia.shell.feature.home.component

import android.view.LayoutInflater
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.arcadia.shell.designsystem.ArcadiaMotion
import com.arcadia.shell.designsystem.rememberReduceMotion
import com.arcadia.shell.feature.home.R
import com.arcadia.shell.feature.home.assetExists
import kotlinx.coroutines.delay

/** Bundled cold-start boot clip (from the `xora-boot` GitHub release). */
const val BOOT_INTRO_ASSET = "boot/bootup.mp4"

private const val BOOT_URI = "asset:///$BOOT_INTRO_ASSET"

/**
 * Cold-start boot: play [BOOT_INTRO_ASSET] once (TextureView, so Compose can actually show it),
 * then fade the white last frame into the XMB. Tap / Back / B skips after a short grace period.
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
    var playVideo by remember { mutableStateOf(false) }
    var requestEnd by remember { mutableStateOf(false) }
    var fading by remember { mutableStateOf(false) }
    var allowSkip by remember { mutableStateOf(false) }
    var firstFrame by remember { mutableStateOf(false) }
    val revealHome = rememberUpdatedState(onRevealHome)
    val finished = rememberUpdatedState(onFinished)
    val hasAsset = remember(context) { assetExists(context, BOOT_INTRO_ASSET) }

    LaunchedEffect(Unit) {
        delay(500)
        allowSkip = true
    }

    LaunchedEffect(reduceMotion, hasAsset) {
        if (reduceMotion || !hasAsset) requestEnd = true
    }
    LaunchedEffect(skip, allowSkip) {
        if (skip && allowSkip) requestEnd = true
    }

    LaunchedEffect(requestEnd) {
        if (!requestEnd || fading) return@LaunchedEffect
        fading = true
        whiteAlpha.snapTo(1f)
        revealHome.value()
        withFrameNanos { }
        playVideo = false
        whiteAlpha.animateTo(
            0f,
            tween(ArcadiaMotion.BootWhiteFade, easing = FastOutSlowInEasing),
        )
        finished.value()
    }

    LaunchedEffect(firstFrame, fading) {
        if (firstFrame && !fading) whiteAlpha.snapTo(0f)
    }

    LaunchedEffect(hasAsset, reduceMotion) {
        if (!reduceMotion && hasAsset) playVideo = true
    }

    BackHandler(enabled = allowSkip && !fading) { requestEnd = true }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.White)
            .clickable(
                enabled = allowSkip && !fading,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = { requestEnd = true },
            ),
    ) {
        if (playVideo) {
            BootIntroPlayer(
                onFirstFrame = { firstFrame = true },
                onEnded = { requestEnd = true },
                onError = { requestEnd = true },
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = whiteAlpha.value }
                .background(Color.White),
        )
    }
}

@Composable
private fun BootIntroPlayer(
    onFirstFrame: () -> Unit,
    onEnded: () -> Unit,
    onError: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val firstFrame = rememberUpdatedState(onFirstFrame)
    val ended = rememberUpdatedState(onEnded)
    val failed = rememberUpdatedState(onError)
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
            volume = 1f
            playWhenReady = true
        }
    }

    DisposableEffect(player, lifecycleOwner) {
        val listener = object : Player.Listener {
            override fun onRenderedFirstFrame() {
                firstFrame.value()
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) ended.value()
            }
            override fun onPlayerError(error: PlaybackException) {
                failed.value()
            }
        }
        // Attach before prepare — a local asset can reach READY synchronously.
        player.addListener(listener)
        player.setMediaItem(MediaItem.fromUri(BOOT_URI))
        player.prepare()
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
            (LayoutInflater.from(ctx).inflate(R.layout.xora_texture_player, null) as PlayerView)
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
        modifier = modifier,
    )
}
