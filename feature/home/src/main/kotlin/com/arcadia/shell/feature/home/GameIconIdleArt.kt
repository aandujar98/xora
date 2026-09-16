package com.arcadia.shell.feature.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import com.arcadia.shell.datastore.GameIconIdleMedia
import com.arcadia.shell.datastore.TrailerDisplayMode
import com.arcadia.shell.designsystem.ArcadiaMotion
import com.arcadia.shell.feature.home.component.ArtworkImage
import com.arcadia.shell.feature.home.component.HeroTrailerLayer
import com.arcadia.shell.feature.home.component.THUMB_DECODE_MAX_EDGE_PX
import kotlinx.coroutines.delay

private const val SCREENSHOT_HOLD_MS = 4_000L

/**
 * Cover, in-icon trailer, or cycling screenshots inside a focused Game Icon.
 * Screenshots replace the trailer when [GameIconIdleMedia.Screenshot] is selected.
 */
@Composable
fun GameIconIdleArt(
    coverPath: String?,
    title: String,
    focused: Boolean,
    trailer: HeroTrailerState,
    screenshotPaths: List<String> = emptyList(),
    artAlignX: Float = 0f,
    artAlignY: Float = 0f,
    modifier: Modifier = Modifier,
) {
    val shots = remember(trailer.screenshotPaths, screenshotPaths) {
        (trailer.screenshotPaths + screenshotPaths)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
    }
    val showScreenshots = focused &&
        trailer.iconIdleMedia == GameIconIdleMedia.Screenshot &&
        shots.isNotEmpty()
    val playTrailer = focused &&
        trailer.iconIdleMedia == GameIconIdleMedia.Trailer &&
        trailer.active &&
        trailer.displayMode == TrailerDisplayMode.InIcon &&
        !trailer.trailerUrl.isNullOrBlank()

    when {
        playTrailer -> HeroTrailerLayer(
            state = trailer.copy(displayMode = TrailerDisplayMode.FullBackground),
            modifier = modifier.fillMaxSize(),
        )
        showScreenshots -> CyclingScreenshotArt(
            paths = shots,
            title = title,
            artAlignX = artAlignX,
            artAlignY = artAlignY,
            modifier = modifier,
        )
        !coverPath.isNullOrBlank() -> ArtworkImage(
            path = coverPath,
            contentDescription = title,
            fallbackText = title,
            contentScale = ContentScale.Crop,
            decodeMaxEdgePx = THUMB_DECODE_MAX_EDGE_PX,
            alignment = BiasAlignment(
                horizontalBias = artAlignX.coerceIn(-1f, 1f),
                verticalBias = artAlignY.coerceIn(-1f, 1f),
            ),
            modifier = modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun CyclingScreenshotArt(
    paths: List<String>,
    title: String,
    artAlignX: Float,
    artAlignY: Float,
    modifier: Modifier = Modifier,
) {
    var index by remember(paths) { mutableIntStateOf(0) }
    LaunchedEffect(paths) {
        if (paths.size < 2) return@LaunchedEffect
        while (true) {
            delay(SCREENSHOT_HOLD_MS)
            index = (index + 1) % paths.size
        }
    }
    Crossfade(
        targetState = paths.getOrElse(index) { paths.first() },
        animationSpec = tween(ArcadiaMotion.Slow),
        label = "gameIconScreenshot",
        modifier = modifier.fillMaxSize(),
    ) { path ->
        ArtworkImage(
            path = path,
            contentDescription = title,
            fallbackText = title,
            contentScale = ContentScale.Crop,
            decodeMaxEdgePx = THUMB_DECODE_MAX_EDGE_PX,
            alignment = BiasAlignment(
                horizontalBias = artAlignX.coerceIn(-1f, 1f),
                verticalBias = artAlignY.coerceIn(-1f, 1f),
            ),
            modifier = Modifier.fillMaxSize(),
        )
    }
}
