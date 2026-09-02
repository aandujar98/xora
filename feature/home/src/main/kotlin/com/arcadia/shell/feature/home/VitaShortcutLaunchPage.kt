package com.arcadia.shell.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.arcadia.shell.designsystem.ArcadiaMotion
import com.arcadia.shell.designsystem.XoraForegroundShadow
import com.arcadia.shell.designsystem.XoraSecondaryText
import com.arcadia.shell.designsystem.XoraTitleText
import com.arcadia.shell.designsystem.arcadiaTween
import com.arcadia.shell.designsystem.xmbAssetShadow
import com.arcadia.shell.feature.home.component.ArtworkImage
import kotlin.math.min

/** Figma node 178:2798 — isolated game plate + title over the title's wallpaper. */
private const val LAUNCH_CARD_W = 462f
private const val LAUNCH_CARD_H = 248f
private const val LAUNCH_CARD_X = 407f
private const val LAUNCH_CARD_Y = 416f
private const val LAUNCH_TITLE_X = 683f
private const val LAUNCH_TITLE_Y = 475f
private const val LAUNCH_TITLE_SIZE = 48f
private const val LAUNCH_CARD_RADIUS = 30f

@Composable
fun VitaShortcutLaunchPage(
    visible: Boolean,
    launch: VitaShortcutLaunchUi?,
    homeWallpaperPath: String?,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible && launch != null,
        enter = fadeIn(arcadiaTween(ArcadiaMotion.Medium)),
        exit = fadeOut(arcadiaTween(ArcadiaMotion.Fast)),
        modifier = modifier,
    ) {
        val page = launch ?: return@AnimatedVisibility
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val unit = min(
                maxWidth.value / XORA_DESIGN_WIDTH,
                maxHeight.value / XORA_DESIGN_HEIGHT,
            )
            val originX = (maxWidth.value - (XORA_DESIGN_WIDTH * unit)) / 2f
            val originY = (maxHeight.value - (XORA_DESIGN_HEIGHT * unit)) / 2f
            val density = LocalDensity.current
            fun du(v: Float) = (v * unit).dp

            if (!page.wallpaperPath.isNullOrBlank()) {
                ArtworkImage(
                    path = page.wallpaperPath,
                    contentDescription = page.shortcut.title,
                    fallbackText = "",
                    contentScale = ContentScale.Crop,
                    cacheInMemory = false,
                    decodeMaxEdgePx = 1920,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                HomeWallpaper(
                    customPath = homeWallpaperPath,
                    dim = false,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            val cardW = du(LAUNCH_CARD_W)
            val cardH = du(LAUNCH_CARD_H)
            val shape = RoundedCornerShape(du(LAUNCH_CARD_RADIUS))
            Box(
                modifier = Modifier
                    .offset(
                        x = (originX + (LAUNCH_CARD_X - LAUNCH_CARD_W / 2f) * unit).dp,
                        y = (originY + LAUNCH_CARD_Y * unit).dp,
                    )
                    .requiredSize(cardW, cardH)
                    .xmbAssetShadow(
                        unit = unit,
                        shape = shape,
                        alpha = XoraForegroundShadow.Alpha,
                    )
                    .clip(shape)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onConfirm,
                    ),
            ) {
                ArtworkImage(
                    path = page.iconPath,
                    contentDescription = page.shortcut.title,
                    fallbackText = page.shortcut.title.take(2).uppercase(),
                    contentScale = ContentScale.Crop,
                    cacheInMemory = true,
                    decodeMaxEdgePx = 512,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Column(
                modifier = Modifier
                    .offset(
                        x = (originX + LAUNCH_TITLE_X * unit).dp,
                        y = (originY + LAUNCH_TITLE_Y * unit).dp,
                    )
                    .widthIn(max = du(900f)),
            ) {
                XoraTitleText(
                    text = page.shortcut.title,
                    fontSize = with(density) { du(LAUNCH_TITLE_SIZE).toSp() },
                    maxLines = 2,
                )
                XoraSecondaryText(
                    text = "A  Launch",
                    fontSize = with(density) { du(28f).toSp() },
                    fillColor = Color.White,
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.Start),
                )
            }
        }
    }
}
