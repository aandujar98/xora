package com.arcadia.shell.feature.home

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.maxBitmapSize
import coil3.size.Size
import com.arcadia.shell.designsystem.ArcadiaMotion
import com.arcadia.shell.designsystem.LocalShellTheme
import com.arcadia.shell.designsystem.ShellThemeBackdrop
import com.arcadia.shell.designsystem.ShellWallpaperStyle
import com.arcadia.shell.designsystem.arcadiaTween
import java.io.File

/**
 * Full-bleed Home hub wallpaper. Uses a user-picked still / GIF / MP4 when [customPath] resolves,
 * otherwise the active launcher theme asset wallpaper (if any), else the authored theme backdrop.
 *
 * Theme / custom media changes crossfade (~[ArcadiaMotion.ThemeCrossfade] ms).
 */
@Composable
fun HomeWallpaper(
    customPath: String?,
    modifier: Modifier = Modifier,
    @Suppress("UNUSED_PARAMETER") dim: Boolean = false,
) {
    val shellTheme = LocalShellTheme.current
    val layer = remember(
        customPath,
        shellTheme.id,
        shellTheme.wallpaperAssetPath,
        shellTheme.wallpaperStyle,
        shellTheme.wallpaperPlaybackSpeed,
    ) {
        WallpaperLayer(
            customPath = customPath,
            themeId = shellTheme.id.id,
            assetPath = shellTheme.wallpaperAssetPath,
            style = shellTheme.wallpaperStyle,
            assetSpeed = shellTheme.wallpaperPlaybackSpeed,
        )
    }
    val fade = arcadiaTween<Float>(ArcadiaMotion.ThemeCrossfade)

    // Offscreen so Hard Light samples the wallpaper, not whatever sits behind this box.
    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
    ) {
        AnimatedContent(
            targetState = layer,
            transitionSpec = { fadeIn(fade) togetherWith fadeOut(fade) },
            contentKey = { "${it.themeId}|${it.customPath.orEmpty()}|${it.assetPath.orEmpty()}|${it.style}" },
            label = "homeWallpaperCrossfade",
            modifier = Modifier.fillMaxSize(),
        ) { target ->
            WallpaperLayerContent(
                layer = target,
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Releases/DIM — always 10% Hard Light; wallpaper itself stays full opacity.
        Image(
            painter = painterResource(R.drawable.wallpaper_dim),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = 0.10f
                    blendMode = BlendMode.Hardlight
                },
        )
    }
}

@Composable
private fun WallpaperLayerContent(
    layer: WallpaperLayer,
    modifier: Modifier = Modifier,
) {
    val platformContext = LocalPlatformContext.current
    val androidContext = LocalContext.current
    val customFile = remember(layer.customPath) {
        layer.customPath?.takeIf { it.isNotBlank() }?.let { File(it) }
            ?.takeIf { it.isFile && it.length() > 0L }
    }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            customFile != null && customFile.isVideoWallpaper() -> {
                LoopingWallpaperVideo(
                    uri = "file://${customFile.absolutePath}",
                    modifier = Modifier.fillMaxSize(),
                )
            }
            customFile != null -> {
                val edge = WALLPAPER_DECODE_EDGE
                val request = remember(customFile.absolutePath) {
                    ImageRequest.Builder(platformContext)
                        .data(customFile)
                        .crossfade(false)
                        .size(edge, edge)
                        .maxBitmapSize(Size(edge, edge))
                        .memoryCachePolicy(CachePolicy.DISABLED)
                        .build()
                }
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            !layer.assetPath.isNullOrBlank() &&
                layer.assetPath.isVideoWallpaperPath() &&
                assetExists(androidContext, layer.assetPath) -> {
                LoopingWallpaperVideo(
                    uri = "asset:///${layer.assetPath}",
                    speed = layer.assetSpeed,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            !layer.assetPath.isNullOrBlank() && assetExists(androidContext, layer.assetPath) -> {
                val edge = WALLPAPER_DECODE_EDGE
                val request = remember(layer.assetPath) {
                    ImageRequest.Builder(platformContext)
                        .data("file:///android_asset/${layer.assetPath}")
                        .crossfade(false)
                        .size(edge, edge)
                        .maxBitmapSize(Size(edge, edge))
                        .build()
                }
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            else -> {
                ShellThemeBackdrop(
                    style = layer.style,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

/** [uri] is already fully qualified (`file://…` for picked media, `asset:///…` for theme packs). */
@Composable
internal fun LoopingWallpaperVideo(
    uri: String,
    modifier: Modifier = Modifier,
    speed: Float = 1f,
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
            lifecycleOwner.lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)
        var processStarted = androidx.lifecycle.ProcessLifecycleOwner.get()
            .lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.STARTED)
        fun syncPlayback() {
            val shouldPlay = localResumed && processStarted
            player.playWhenReady = shouldPlay
            if (!shouldPlay) player.pause()
        }
        val localObserver = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE,
                androidx.lifecycle.Lifecycle.Event.ON_STOP,
                -> {
                    localResumed = false
                    syncPlayback()
                }
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    localResumed = true
                    syncPlayback()
                }
                else -> Unit
            }
        }
        val processObserver = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> {
                    processStarted = false
                    syncPlayback()
                }
                androidx.lifecycle.Lifecycle.Event.ON_START -> {
                    processStarted = true
                    syncPlayback()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(localObserver)
        androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.addObserver(processObserver)
        syncPlayback()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(localObserver)
            androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.removeObserver(processObserver)
            player.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            (LayoutInflater.from(ctx).inflate(R.layout.xora_backdrop_player, null) as PlayerView)
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

private data class WallpaperLayer(
    val customPath: String?,
    val themeId: String,
    val assetPath: String?,
    val style: ShellWallpaperStyle,
    val assetSpeed: Float,
)

private fun File.isVideoWallpaper(): Boolean =
    extension.lowercase() in VIDEO_WALLPAPER_EXTS

private fun String.isVideoWallpaperPath(): Boolean =
    substringAfterLast('.', "").lowercase() in VIDEO_WALLPAPER_EXTS

internal fun assetExists(context: android.content.Context, path: String): Boolean =
    runCatching {
        context.assets.open(path).use { true }
    }.getOrDefault(false)

/** Cap wallpaper decode for handheld RAM; crop still fills the viewport. */
private const val WALLPAPER_DECODE_EDGE = 1280
private val VIDEO_WALLPAPER_EXTS = setOf("mp4", "webm", "mkv", "mov")
