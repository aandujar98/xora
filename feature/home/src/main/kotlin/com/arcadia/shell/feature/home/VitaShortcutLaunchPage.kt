package com.arcadia.shell.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.arcadia.shell.designsystem.ArcadiaMotion
import com.arcadia.shell.designsystem.XoraFonts
import com.arcadia.shell.designsystem.XoraForegroundShadow
import com.arcadia.shell.designsystem.arcadiaTween
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
private const val LAUNCH_SUBTITLE_SIZE = 28f
/** Figma X4 Y4 B4 S0 — same drop as Game Select hover titles. */
private const val LAUNCH_TITLE_SHADOW = 4f

@Composable
fun VitaShortcutLaunchPage(
    visible: Boolean,
    launch: VitaShortcutLaunchUi?,
    homeWallpaperPath: String?,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    achievementsContent: @Composable BoxScope.() -> Unit = {},
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
            XmbGamePlate(
                title = page.shortcut.title,
                artPath = page.iconPath,
                selected = true,
                width = cardW,
                height = cardH,
                unit = unit,
                onClick = onConfirm,
                modifier = Modifier.offset(
                    x = (originX + (LAUNCH_CARD_X - LAUNCH_CARD_W / 2f) * unit).dp,
                    y = (originY + LAUNCH_CARD_Y * unit).dp,
                ),
            )

            Column(
                modifier = Modifier
                    .offset(
                        x = (originX + LAUNCH_TITLE_X * unit).dp,
                        y = (originY + LAUNCH_TITLE_Y * unit).dp,
                    )
                    .widthIn(max = du(900f)),
            ) {
                LaunchSelectTitle(
                    text = page.shortcut.title,
                    fontSize = with(density) { du(LAUNCH_TITLE_SIZE).toSp() },
                    unit = unit,
                    maxLines = 2,
                )
                LaunchSelectTitle(
                    text = "A  Launch",
                    fontSize = with(density) { du(LAUNCH_SUBTITLE_SIZE).toSp() },
                    unit = unit,
                    maxLines = 1,
                )
            }

            achievementsContent()
        }
    }
}

/** Game Select / XMB hover title: NewRodin Pro DB, #FFFFFF, X4 Y4 B4 S0. */
@Composable
private fun LaunchSelectTitle(
    text: String,
    fontSize: TextUnit,
    unit: Float,
    maxLines: Int,
    modifier: Modifier = Modifier,
) {
    val shadowPx = with(LocalDensity.current) { (LAUNCH_TITLE_SHADOW * unit).dp.toPx() }
    Text(
        text = text,
        modifier = modifier,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        color = Color.White,
        style = TextStyle(
            fontFamily = XoraFonts.XmbLabel,
            fontWeight = FontWeight.Normal,
            fontSize = fontSize,
            lineHeight = fontSize,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = LineHeightStyle(
                alignment = LineHeightStyle.Alignment.Center,
                trim = LineHeightStyle.Trim.Both,
            ),
            shadow = Shadow(
                color = Color.Black.copy(alpha = XoraForegroundShadow.TitleAlpha),
                offset = Offset(shadowPx, shadowPx),
                blurRadius = shadowPx,
            ),
        ),
    )
}
