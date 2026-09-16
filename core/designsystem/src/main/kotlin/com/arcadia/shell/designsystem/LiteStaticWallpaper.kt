package com.arcadia.shell.designsystem

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource

/**
 * Still used when Performance / Auto-lite skips looping video, so Home is never a black plate.
 * Art is the GitHub `static-default-WP` drop (`Static.Default.Wallpaper.png`).
 */
@Composable
fun LiteStaticWallpaper(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.static_default_wallpaper),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = modifier.fillMaxSize(),
    )
}
