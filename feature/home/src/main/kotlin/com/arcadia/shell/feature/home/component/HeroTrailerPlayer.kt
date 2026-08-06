package com.arcadia.shell.feature.home.component

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.arcadia.shell.datastore.TrailerDisplayMode
import com.arcadia.shell.designsystem.ArcadiaMotion
import com.arcadia.shell.designsystem.arcadiaTween
import com.arcadia.shell.feature.home.HeroTrailerState
import com.arcadia.shell.feature.home.preview.XoraPreview
import com.arcadia.shell.feature.home.preview.XoraPreviewTheme
import com.arcadia.shell.model.TrailerRef
import com.arcadia.shell.model.TrailerRefs
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.PlayerConstants
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.options.IFramePlayerOptions
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView
import java.io.File

/**
 * Idle trailer surface for the hero pane.
 *
 * Direct media uses Media3; YouTube ids use PierfrancescoSoffritti's IFrame player with the app
 * package as `origin` (avoids YouTube embed errors 153 / 152-4). Chrome (pills / metadata) is
 * drawn by the parent above this layer.
 */
@Composable
fun HeroTrailerLayer(
    state: HeroTrailerState,
    modifier: Modifier = Modifier,
) {
    val ref = remember(state.trailerUrl) { TrailerRefs.parse(state.trailerUrl) }
    val enter = fadeIn(arcadiaTween(ArcadiaMotion.Slow))
    val exit = fadeOut(arcadiaTween(ArcadiaMotion.Medium))

    AnimatedVisibility(
        visible = state.active && ref != null,
        enter = enter,
        exit = exit,
        modifier = modifier,
    ) {
        val trailer = ref ?: return@AnimatedVisibility
        when (state.displayMode) {
            TrailerDisplayMode.FullBackground -> {
                Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                    TrailerSurface(
                        ref = trailer,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            TrailerDisplayMode.CornerPip -> {
                BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                    val pipWidth = maxWidth * 0.22f
                    val pipHeight = maxHeight * 0.28f
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            // Clear of bottom metadata / RA pill (~12dp chrome + bar).
                            .padding(end = 20.dp, bottom = 88.dp)
                            .width(pipWidth)
                            .height(pipHeight)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.Black),
                    ) {
                        TrailerSurface(
                            ref = trailer,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrailerSurface(
    ref: TrailerRef,
    modifier: Modifier = Modifier,
) {
    when (ref) {
        is TrailerRef.Direct -> DirectTrailerPlayer(uri = ref.uri, modifier = modifier)
        is TrailerRef.YouTube -> YouTubeTrailerEmbed(videoIds = ref.videoIds, modifier = modifier)
    }
}

@Composable
private fun DirectTrailerPlayer(
    uri: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val player = remember(uri) {
        val headers = buildMap {
            if (uri.contains("screenscraper", ignoreCase = true)) {
                put("Referer", "https://www.screenscraper.fr/")
                put("User-Agent", "arcadia")
            }
        }
        val httpFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setDefaultRequestProperties(headers)
        val mediaSourceFactory = DefaultMediaSourceFactory(context).setDataSourceFactory(httpFactory)

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                val mediaUri = when {
                    uri.startsWith("http", ignoreCase = true) -> uri
                    uri.startsWith("file:", ignoreCase = true) -> uri
                    File(uri).isFile -> "file://$uri"
                    else -> uri
                }
                Log.i(TAG, "ExoPlayer prepare $mediaUri")
                setMediaItem(MediaItem.fromUri(mediaUri))
                repeatMode = Player.REPEAT_MODE_ONE
                volume = 0f
                addListener(
                    object : Player.Listener {
                        override fun onPlayerError(error: PlaybackException) {
                            Log.e(TAG, "ExoPlayer error: ${error.message}", error)
                        }

                        override fun onPlaybackStateChanged(playbackState: Int) {
                            if (playbackState == Player.STATE_READY) {
                                Log.i(TAG, "ExoPlayer ready")
                            }
                        }
                    },
                )
                prepare()
                playWhenReady = true
            }
    }

    DisposableEffect(player, lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE,
                androidx.lifecycle.Lifecycle.Event.ON_STOP,
                -> {
                    player.playWhenReady = false
                    player.pause()
                }
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    player.playWhenReady = true
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
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
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
            }
        },
        update = { it.player = player },
        onRelease = { view ->
            // Detach player before release so PlayerView cannot keep a disposed ExoPlayer.
            view.player = null
        },
        modifier = modifier,
    )
}

/**
 * Muted autoplay via android-youtube-player. Origin is `https://<applicationId>` so YouTube
 * receives a valid embedder Referer (fixes 153 / 152-4). On embed / policy errors, advances
 * through discovery candidates before showing an "Open in YouTube" fallback.
 */
@Composable
private fun YouTubeTrailerEmbed(
    videoIds: List<String>,
    modifier: Modifier = Modifier,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val ids = remember(videoIds) { videoIds.filter { it.length == 11 }.distinct() }
    var candidateIndex by remember(ids) { mutableIntStateOf(0) }
    var exhausted by remember(ids) { mutableStateOf(ids.isEmpty()) }

    if (exhausted || ids.isEmpty()) {
        YouTubeUnavailableFallback(
            videoId = ids.firstOrNull(),
            modifier = modifier,
        )
        return
    }

    val videoId = ids[candidateIndex.coerceIn(0, ids.lastIndex)]

    key(videoId) {
        AndroidView(
            factory = { ctx ->
                YouTubePlayerView(ctx).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    enableAutomaticInitialization = false
                    lifecycleOwner.lifecycle.addObserver(this)

                    // Package origin is the default in 13.x and is required by current YouTube
                    // embed policy (error 152-4 when origin/referer is missing or youtube.com).
                    val options = IFramePlayerOptions.Builder(ctx)
                        .controls(0)
                        .autoplay(1)
                        .mute(1)
                        .rel(0)
                        .ivLoadPolicy(3)
                        .fullscreen(0)
                        .build()

                    initialize(
                        object : AbstractYouTubePlayerListener() {
                            override fun onReady(youTubePlayer: YouTubePlayer) {
                                Log.i(TAG, "YouTube IFrame ready; muted load $videoId")
                                youTubePlayer.mute()
                                youTubePlayer.loadVideo(videoId, 0f)
                            }

                            override fun onStateChange(
                                youTubePlayer: YouTubePlayer,
                                state: PlayerConstants.PlayerState,
                            ) {
                                when (state) {
                                    PlayerConstants.PlayerState.PLAYING -> {
                                        // Re-assert mute in case the IFrame unmuted after buffering.
                                        youTubePlayer.mute()
                                    }
                                    PlayerConstants.PlayerState.ENDED -> {
                                        youTubePlayer.mute()
                                        youTubePlayer.loadVideo(videoId, 0f)
                                    }
                                    else -> Unit
                                }
                            }

                            override fun onError(
                                youTubePlayer: YouTubePlayer,
                                error: PlayerConstants.PlayerError,
                            ) {
                                Log.w(TAG, "YouTube player error $error for $videoId")
                                val next = candidateIndex + 1
                                if (next < ids.size) {
                                    Log.i(TAG, "Trying next trailer candidate ${ids[next]}")
                                    candidateIndex = next
                                } else {
                                    Log.i(TAG, "All YouTube trailer candidates failed")
                                    exhausted = true
                                }
                            }
                        },
                        options,
                    )
                }
            },
            update = { /* keyed by videoId — recreate on candidate change */ },
            onRelease = { view ->
                lifecycleOwner.lifecycle.removeObserver(view)
                // Destroy the WebView immediately when the trailer leaves composition or
                // AnimatedVisibility finishes — avoids leaking Chromium processes.
                runCatching { view.release() }
            },
            modifier = modifier,
        )
    }
}

@Composable
private fun YouTubeUnavailableFallback(
    videoId: String?,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Trailer unavailable in-app",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
        if (videoId != null) {
            TextButton(
                onClick = {
                    val uri = Uri.parse("https://www.youtube.com/watch?v=$videoId")
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }.onFailure {
                        Log.w(TAG, "Could not open YouTube for $videoId", it)
                    }
                },
            ) {
                Text(text = "Open in YouTube", color = Color.White)
            }
        }
    }
}

private const val TAG = "HeroTrailer"

@XoraPreview
@Composable
private fun HeroTrailerLayerPreview() {
    XoraPreviewTheme {
        HeroTrailerLayer(state = HeroTrailerState())
    }
}
