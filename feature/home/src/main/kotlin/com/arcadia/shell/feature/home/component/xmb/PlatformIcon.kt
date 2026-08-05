package com.arcadia.shell.feature.home.component.xmb

import androidx.annotation.DrawableRes
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.arcadia.shell.designsystem.ArcadiaGlass
import com.arcadia.shell.designsystem.GlassIntensity
import com.arcadia.shell.designsystem.GlassTone
import com.arcadia.shell.designsystem.liquidGlass
import com.arcadia.shell.feature.home.LibraryTab
import com.arcadia.shell.feature.home.R
import com.arcadia.shell.feature.home.TabKind

/**
 * Recognizable system / console logo marks for XMB category icons.
 * Monochrome vector drawables — tinted by selection state.
 */
@Composable
fun PlatformIcon(
    tab: LibraryTab,
    selected: Boolean,
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
) {
    val iconRes = remember(tab.id, tab.kind, tab.platformId) { drawableResForTab(tab) }
    val tint = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
    }
    val shape = ArcadiaGlass.DockShape
    val borderModifier = if (selected) {
        Modifier.border(
            width = 2.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.primary,
                    MaterialTheme.colorScheme.tertiary,
                    MaterialTheme.colorScheme.primary,
                ),
            ),
            shape = shape,
        )
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .size(size)
            .liquidGlass(
                shape = shape,
                tone = GlassTone.Surface,
                intensity = if (selected) GlassIntensity.Standard else GlassIntensity.Subtle,
            )
            .then(borderModifier)
            .padding(if (selected) 6.dp else 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = tab.label,
            tint = tint,
            modifier = Modifier.size(size * 0.72f),
        )
    }
}

@DrawableRes
fun drawableResForTab(tab: LibraryTab): Int = when (tab.kind) {
    TabKind.All -> R.drawable.ic_tab_all
    TabKind.Favorites -> R.drawable.ic_tab_favorites
    TabKind.Recent -> R.drawable.ic_tab_recent
    TabKind.Apps -> R.drawable.ic_tab_apps
    TabKind.Platform -> drawableResForPlatformId(tab.platformId)
}

@DrawableRes
fun drawableResForPlatformId(platformId: String?): Int = when (platformId) {
    "nes" -> R.drawable.ic_platform_nes
    "snes" -> R.drawable.ic_platform_snes
    "n64" -> R.drawable.ic_platform_n64
    "gb" -> R.drawable.ic_platform_gb
    "gbc" -> R.drawable.ic_platform_gbc
    "gba" -> R.drawable.ic_platform_gba
    "nds" -> R.drawable.ic_platform_nds
    "3ds" -> R.drawable.ic_platform_3ds
    "gamecube" -> R.drawable.ic_platform_gamecube
    "wii" -> R.drawable.ic_platform_wii
    "wiiu" -> R.drawable.ic_platform_wiiu
    "switch" -> R.drawable.ic_platform_switch
    "ps1" -> R.drawable.ic_platform_ps1
    "ps2" -> R.drawable.ic_platform_ps2
    "psp" -> R.drawable.ic_platform_psp
    "psvita" -> R.drawable.ic_platform_psvita
    "ps3" -> R.drawable.ic_platform_ps3
    "dreamcast" -> R.drawable.ic_platform_dreamcast
    "genesis", "sega32x", "segacd", "mastersystem", "gamegear" -> R.drawable.ic_platform_genesis
    "saturn" -> R.drawable.ic_platform_saturn
    "arcade", "neogeo" -> R.drawable.ic_platform_arcade
    else -> R.drawable.ic_platform_fallback
}

@DrawableRes
fun iconForPlatformId(platformId: String?): Int = drawableResForPlatformId(platformId)
