package com.arcadia.shell.feature.home.component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import com.arcadia.shell.designsystem.LocalLiteVisuals
import com.arcadia.shell.feature.home.LoopingWallpaperVideo

/** Bundled PS5-style particle matte (GitHub tag `XOrA-particle-effects`). */
internal const val XMB_PARTICLE_ASSET = "particles/xmb_particles.mp4"

internal const val XMB_PARTICLE_URI = "asset:///$XMB_PARTICLE_ASSET"

/**
 * Ambient particles drifting over the XMB wallpaper.
 *
 * The source is a luma matte — bright motes on pure black — so it composites [BlendMode.Screen]:
 * black contributes nothing and drops out, leaving only the particles. (Multiply is the mode for
 * the Music wave, whose matte is inverted: dark motes on *white*.)
 *
 * Screen only lands on the wallpaper if the two share an offscreen group, so the caller must
 * place this inside the backdrop's [androidx.compose.ui.graphics.CompositingStrategy.Offscreen]
 * layer rather than over it.
 *
 * Lite devices fall back to the drawn star field — a full-screen video decode is exactly the kind
 * of per-frame cost the lite budget exists to avoid.
 */
@Composable
fun XmbParticleFieldLayer(
    modifier: Modifier = Modifier,
) {
    if (LocalLiteVisuals.current) {
        XmbStarFieldLayer(modifier = modifier)
        return
    }
    LoopingWallpaperVideo(
        uri = XMB_PARTICLE_URI,
        speed = XMB_PARTICLE_SPEED,
        modifier = modifier.graphicsLayer {
            alpha = XMB_PARTICLE_ALPHA
            blendMode = BlendMode.Screen
            // Alpha must modulate the matte *before* it blends, not after.
            compositingStrategy = CompositingStrategy.Offscreen
        },
    )
}

/** Half strength — the matte at full brightness reads as weather, not ambience. */
private const val XMB_PARTICLE_ALPHA = 0.5f

/** Slower than the source render so the drift sits behind the menu rather than pulling focus. */
private const val XMB_PARTICLE_SPEED = 0.75f
