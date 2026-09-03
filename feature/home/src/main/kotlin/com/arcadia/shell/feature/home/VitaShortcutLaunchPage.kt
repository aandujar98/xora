package com.arcadia.shell.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.pointerInput
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
import com.arcadia.shell.designsystem.launchBackdropScale
import com.arcadia.shell.designsystem.rememberLaunchCinematic
import com.arcadia.shell.designsystem.XoraFonts
import com.arcadia.shell.designsystem.XoraForegroundShadow
import com.arcadia.shell.designsystem.arcadiaTween
import com.arcadia.shell.designsystem.rememberReduceMotion
import com.arcadia.shell.designsystem.xmbAssetShadow
import com.arcadia.shell.feature.home.component.ArtworkImage
import kotlin.math.min

/** Figma node 178:2798 — isolated game plate + title over the title's wallpaper. */
private const val LAUNCH_CARD_W = 462f
private const val LAUNCH_CARD_H = 248f
private const val LAUNCH_CARD_X = 407f
private const val LAUNCH_CARD_Y = 416f
private const val LAUNCH_TITLE_X = 683f
private const val LAUNCH_TITLE_SIZE = 48f
private const val LAUNCH_SUBTITLE_SIZE = 40f
/** Matches Game Select: title → 4px rule → playtime. */
private const val LAUNCH_TITLE_TO_RULE = 91f
private const val LAUNCH_RULE_TO_SUBTITLE = 25f
/** Design-px inset from the screen edge so the rule nearly spans the panel. */
private const val LAUNCH_RULE_EDGE_INSET = 85f
private const val LAUNCH_RULE_THICKNESS = 4f
/** Figma X4 Y4 B4 S0 — same drop as Game Select hover titles. */
private const val LAUNCH_TITLE_SHADOW = 4f
/** Background fade to white while the tapped bubble is still departing. */
private const val WhiteFadeInMs = 750
private const val WhiteHoldRevealMs = 420

@Composable
fun VitaShortcutLaunchPage(
    visible: Boolean,
    launch: VitaShortcutLaunchUi?,
    homeWallpaperPath: String?,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    holdWhite: Boolean = false,
    isLaunching: Boolean = false,
    wallpaperAlignX: Float = 0f,
    wallpaperAlignY: Float = 0f,
    achievementsContent: @Composable BoxScope.() -> Unit = {},
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(0)),
        exit = fadeOut(arcadiaTween(ArcadiaMotion.Fast)),
        modifier = modifier,
    ) {
        val page = launch
        val revealPlate = page != null && !holdWhite
        val reduceMotion = rememberReduceMotion()
        val cinematic = rememberLaunchCinematic(isLaunching)
        val artworkScale = launchBackdropScale(cinematic.zoom)
        val whiteAlpha = remember { Animatable(0f) }
        val fadeUp = holdWhite || page == null
        LaunchedEffect(visible, fadeUp, reduceMotion) {
            if (!visible) {
                whiteAlpha.snapTo(0f)
                return@LaunchedEffect
            }
            if (reduceMotion) {
                whiteAlpha.snapTo(if (fadeUp) 1f else 0f)
                return@LaunchedEffect
            }
            whiteAlpha.animateTo(
                targetValue = if (fadeUp) 1f else 0f,
                animationSpec = tween(
                    durationMillis = if (fadeUp) WhiteFadeInMs else WhiteHoldRevealMs,
                    easing = FastOutSlowInEasing,
                ),
            )
        }
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .then(if (revealPlate) Modifier.background(Color.Black) else Modifier)
                .pointerInput(Unit) {},
        ) {
            if (revealPlate && page != null) {
                val unit = min(
                    maxWidth.value / XORA_DESIGN_WIDTH,
                    maxHeight.value / XORA_DESIGN_HEIGHT,
                )
                val originX = (maxWidth.value - (XORA_DESIGN_WIDTH * unit)) / 2f
                val originY = (maxHeight.value - (XORA_DESIGN_HEIGHT * unit)) / 2f
                val density = LocalDensity.current
                fun du(v: Float) = (v * unit).dp

                val wallpaperMotion = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = artworkScale
                        scaleY = artworkScale
                        alpha = cinematic.wallpaperAlpha
                    }
                if (!page.wallpaperPath.isNullOrBlank()) {
                    ArtworkImage(
                        path = page.wallpaperPath,
                        contentDescription = page.shortcut.title,
                        fallbackText = "",
                        contentScale = ContentScale.Crop,
                        cacheInMemory = false,
                        decodeMaxEdgePx = 1920,
                        modifier = wallpaperMotion,
                    )
                } else {
                    HomeWallpaper(
                        customPath = homeWallpaperPath,
                        dim = false,
                        alignX = wallpaperAlignX,
                        alignY = wallpaperAlignY,
                        modifier = wallpaperMotion,
                    )
                }

                val cardW = du(LAUNCH_CARD_W)
                val cardH = du(LAUNCH_CARD_H)
                val titleX = originX + LAUNCH_TITLE_X * unit
                val ruleThickness = LAUNCH_RULE_THICKNESS * unit
                // Horizon through the screen's center; right end stops 85 design-px shy of the edge.
                val ruleY = maxHeight.value / 2f - ruleThickness / 2f
                val ruleWidth = (maxWidth.value - LAUNCH_RULE_EDGE_INSET * unit - titleX)
                    .coerceAtLeast(0f)
                val titleY = ruleY - LAUNCH_TITLE_TO_RULE * unit
                val subtitleY = ruleY + ruleThickness + LAUNCH_RULE_TO_SUBTITLE * unit

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = cinematic.chromeAlpha },
                ) {
                    XmbGamePlate(
                        title = page.shortcut.title,
                        artPath = page.iconPath,
                        selected = true,
                        width = cardW,
                        height = cardH,
                        unit = unit,
                        onClick = onConfirm,
                        trailer = HeroTrailerState(),
                        artAlignX = page.artAlignX,
                        artAlignY = page.artAlignY,
                        modifier = Modifier.offset(
                            x = (originX + (LAUNCH_CARD_X - LAUNCH_CARD_W / 2f) * unit).dp,
                            y = (originY + LAUNCH_CARD_Y * unit).dp,
                        ),
                    )

                    LaunchSelectTitle(
                        text = page.shortcut.title,
                        fontSize = with(density) { du(LAUNCH_TITLE_SIZE).toSp() },
                        unit = unit,
                        maxLines = 1,
                        modifier = Modifier
                            .offset(x = titleX.dp, y = titleY.dp)
                            .width(ruleWidth.dp),
                    )
                    Box(
                        modifier = Modifier
                            .offset(x = titleX.dp, y = ruleY.dp)
                            .width(ruleWidth.dp)
                            .height(ruleThickness.dp)
                            .xmbAssetShadow(
                                unit = unit,
                                shape = RectangleShape,
                                alpha = XoraForegroundShadow.TitleAlpha,
                            )
                            .background(Color.White),
                    )
                    LaunchSelectTitle(
                        text = "Playtime: ${formatXmbPlaytime(page.game?.playTimeMs ?: 0L)}",
                        fontSize = with(density) { du(LAUNCH_SUBTITLE_SIZE).toSp() },
                        unit = unit,
                        maxLines = 1,
                        modifier = Modifier
                            .offset(x = titleX.dp, y = subtitleY.dp)
                            .width(ruleWidth.dp),
                    )

                    achievementsContent()
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = whiteAlpha.value.coerceIn(0f, 1f))),
            )
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
        softWrap = false,
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
