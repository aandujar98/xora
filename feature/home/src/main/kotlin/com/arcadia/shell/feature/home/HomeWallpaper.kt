package com.arcadia.shell.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.request.maxBitmapSize
import coil3.size.Size
import com.arcadia.shell.designsystem.ArcadiaMotion
import com.arcadia.shell.designsystem.LocalLiteVisuals
import com.arcadia.shell.designsystem.LocalShellTheme
import com.arcadia.shell.designsystem.ShellThemeBackdrop
import com.arcadia.shell.designsystem.XoraLoopingVideo
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
    dimBlendMode: BlendMode = BlendMode.Hardlight,
    alignX: Float = 0f,
    alignY: Float = 0f,
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
    val lite = LocalLiteVisuals.current

    // Offscreen so DIM samples the wallpaper, not whatever sits behind this box.
    // Lite skips the extra offscreen target — a full-screen layer is expensive on Mali-G68.
    Box(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (lite) {
                    Modifier
                } else {
                    Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                },
            ),
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
                alignX = alignX,
                alignY = alignY,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (!lite) {
            // Releases/DIM — 8% over the wallpaper; Game Select passes Multiply.
            Image(
                painter = painterResource(R.drawable.wallpaper_dim),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = 0.08f
                        blendMode = dimBlendMode
                    },
            )
        }
    }
}

@Composable
private fun WallpaperLayerContent(
    layer: WallpaperLayer,
    modifier: Modifier = Modifier,
    alignX: Float = 0f,
    alignY: Float = 0f,
) {
    val platformContext = LocalPlatformContext.current
    val androidContext = LocalContext.current
    val customFile = remember(layer.customPath) {
        layer.customPath?.takeIf { it.isNotBlank() }?.let { File(it) }
            ?.takeIf { it.isFile && it.length() > 0L }
    }
    val alignment = BiasAlignment(
        horizontalBias = alignX.coerceIn(-1f, 1f),
        verticalBias = alignY.coerceIn(-1f, 1f),
    )
    val panMedia = kotlin.math.abs(alignX) > 0.001f || kotlin.math.abs(alignY) > 0.001f
    val lite = LocalLiteVisuals.current

    Box(modifier = modifier.fillMaxSize().clipToBounds()) {
        when {
            !lite && customFile != null && customFile.isVideoWallpaper() -> {
                LoopingWallpaperVideo(
                    uri = "file://${customFile.absolutePath}",
                    alignment = alignment,
                    pan = panMedia,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            customFile != null -> {
                val edge = if (lite) LITE_WALLPAPER_DECODE_EDGE else WALLPAPER_DECODE_EDGE
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
                    alignment = alignment,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            !lite &&
                !layer.assetPath.isNullOrBlank() &&
                layer.assetPath.isVideoWallpaperPath() &&
                assetExists(androidContext, layer.assetPath) -> {
                LoopingWallpaperVideo(
                    uri = "asset:///${layer.assetPath}",
                    speed = layer.assetSpeed,
                    alignment = alignment,
                    pan = panMedia,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            !layer.assetPath.isNullOrBlank() && assetExists(androidContext, layer.assetPath) -> {
                val edge = if (lite) LITE_WALLPAPER_DECODE_EDGE else WALLPAPER_DECODE_EDGE
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
                    alignment = alignment,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            else -> {
                ShellThemeBackdrop(
                    style = layer.style,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (panMedia) {
                                Modifier.graphicsLayer {
                                    scaleX = 1.24f
                                    scaleY = 1.24f
                                    translationX = -alignX * size.width * 0.12f
                                    translationY = -alignY * size.height * 0.12f
                                }
                            } else {
                                Modifier
                            },
                        ),
                )
            }
        }
    }
}

/**
 * [uri] is already fully qualified (`file://…` for picked media, `asset:///…` for theme packs).
 *
 * Thin alias over [XoraLoopingVideo]; the player itself lives in the design system so onboarding
 * can show the same theme loop without a second copy of the lifecycle handling.
 */
@Composable
internal fun LoopingWallpaperVideo(
    uri: String,
    modifier: Modifier = Modifier,
    speed: Float = 1f,
    alignment: Alignment = Alignment.Center,
    pan: Boolean = false,
) {
    XoraLoopingVideo(
        uri = uri,
        modifier = modifier,
        speed = speed,
        alignment = alignment,
        pan = pan,
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

internal fun String.isVideoMediaPath(): Boolean =
    substringAfterLast('.', "").lowercase() in VIDEO_WALLPAPER_EXTS

private fun String.isVideoWallpaperPath(): Boolean = isVideoMediaPath()

internal fun assetExists(context: android.content.Context, path: String): Boolean =
    runCatching {
        context.assets.open(path).use { true }
    }.getOrDefault(false)

/** Cap wallpaper decode for handheld RAM; crop still fills the viewport. */
private const val WALLPAPER_DECODE_EDGE = 1280
private const val LITE_WALLPAPER_DECODE_EDGE = 960
private val VIDEO_WALLPAPER_EXTS = setOf("mp4", "webm", "mkv", "mov")
