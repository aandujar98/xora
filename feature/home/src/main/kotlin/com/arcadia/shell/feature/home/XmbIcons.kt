package com.arcadia.shell.feature.home

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas as ComposeCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.arcadia.shell.designsystem.XoraForegroundShadow
import kotlin.math.roundToInt

/** How much wider than the glyph box the XOrA wordmark is allowed to run. */
private const val XORA_MARK_WIDTH_SCALE = 1.7f

/** Stable glyph ids for XMB rows that are not ROM box art. */
enum class XmbIcon {
    Profiles,
    Settings,
    Games,
    Media,
    Music,
    Network,
    User,
    Guest,
    General,
    Display,
    Themes,
    Sound,
    Scrape,
    Social,
    Notifications,
    Trophy,
    Continue,
    Favorite,
    Folder,
    Photo,
    Video,
    NowPlaying,
    Playlist,
    Dsp,
    Spotify,
    AppleMusic,
    YoutubeMusic,
    Shuffle,
    Repeat,
    PreviousTrack,
    NextTrack,
    Play,
    Pause,
    Friends,
    Store,
    News,
    System,
    GamePad,
    Emulator,
    /** XOrA wordmark (X O Γ Δ) — the Network category on the cross bar. */
    Xora,
}

fun XoraXmbCategory.toXmbIcon(): XmbIcon = when (this) {
    XoraXmbCategory.Profiles -> XmbIcon.Profiles
    XoraXmbCategory.Settings -> XmbIcon.Settings
    XoraXmbCategory.Games -> XmbIcon.Games
    XoraXmbCategory.Media -> XmbIcon.Media
    XoraXmbCategory.Music -> XmbIcon.Music
    // The brand mark closes the cross bar; the globe stays available as [XmbIcon.Network].
    XoraXmbCategory.Network -> XmbIcon.Xora
}

@Composable
fun XmbVectorIcon(
    icon: XmbIcon,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    size: Dp = 28.dp,
    /** Soft black halo so glyphs stay readable over bright hero art (no glass / reflection). */
    outlined: Boolean = true,
    /** When false, a parent plate / tile already casts [XoraForegroundShadow]. */
    castShadow: Boolean = true,
    /** PS3-style frosted glass body (white→ice-blue gradient + top gloss) instead of flat tint. */
    glass: Boolean = false,
) {
    val boxWidth = if (icon == XmbIcon.Xora) size * XORA_MARK_WIDTH_SCALE else size
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val context = LocalContext.current
    val shadow = remember(icon, boxWidth, size, glass, castShadow, density.density, density.fontScale) {
        if (!castShadow) {
            null
        } else {
            rasterizeXmbGlyphShadow(density, layoutDirection, context, icon, boxWidth, size, glass)
        }
    }
    Box(
        modifier = modifier
            .size(width = boxWidth, height = size)
            .graphicsLayer { clip = false },
        contentAlignment = Alignment.Center,
    ) {
        if (shadow != null) {
            // Size the layer to the blurred bitmap so Compose cannot crop the halo into a square.
            val shadowW = with(density) { shadow.image.width.toDp() }
            val shadowH = with(density) { shadow.image.height.toDp() }
            Canvas(
                modifier = Modifier
                    .requiredSize(shadowW, shadowH)
                    .align(Alignment.Center)
                    .graphicsLayer {
                        clip = false
                        translationX = XoraForegroundShadow.OffsetX.toPx()
                        translationY = XoraForegroundShadow.OffsetY.toPx()
                        alpha = XoraForegroundShadow.Alpha
                    },
            ) {
                drawImage(image = shadow.image)
            }
        }
        if (icon == XmbIcon.Xora) {
            Image(
                painter = painterResource(R.drawable.ic_xora_logo),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(width = boxWidth, height = size),
            )
            return@Box
        }
        Canvas(modifier = Modifier.size(size)) {
            val stroke = Stroke(
                width = size.toPx() * (if (glass) 0.095f else 0.075f),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            )
            if (glass) {
                drawGlassIcon(icon, stroke)
                return@Canvas
            }
            if (outlined) {
                val outline = size.toPx() * 0.055f
                val ink = Color.Black
                val offsets = arrayOf(
                    Offset(-outline, 0f),
                    Offset(outline, 0f),
                    Offset(0f, -outline),
                    Offset(0f, outline),
                    Offset(-outline, -outline),
                    Offset(outline, -outline),
                    Offset(-outline, outline),
                    Offset(outline, outline),
                )
                for (o in offsets) {
                    translate(o.x, o.y) {
                        drawXmbIconContent(icon, ink, stroke)
                    }
                }
            }
            drawXmbIconContent(icon, tint, stroke)
        }
    }
}

private data class XmbGlyphShadow(
    val image: ImageBitmap,
    val padPx: Float,
)

/**
 * Blur the glyph's own alpha so the drop shadow follows the icon, not the square slot.
 * [Bitmap.extractAlpha] runs the mask filter in software, where it actually applies.
 */
private fun rasterizeXmbGlyphShadow(
    density: Density,
    layoutDirection: LayoutDirection,
    context: android.content.Context,
    icon: XmbIcon,
    width: Dp,
    height: Dp,
    glass: Boolean,
): XmbGlyphShadow {
    val widthPx = with(density) { width.toPx() }
    val heightPx = with(density) { height.toPx() }
    val blurPx = with(density) { XoraForegroundShadow.Blur.toPx() }
    val padPx = blurPx * 2.5f
    val bmpW = (widthPx + padPx * 2f).roundToInt().coerceAtLeast(1)
    val bmpH = (heightPx + padPx * 2f).roundToInt().coerceAtLeast(1)
    val src = Bitmap.createBitmap(bmpW, bmpH, Bitmap.Config.ARGB_8888)
    val androidCanvas = android.graphics.Canvas(src)
    if (icon == XmbIcon.Xora) {
        context.getDrawable(R.drawable.ic_xora_logo)?.let { drawable ->
            drawable.setBounds(
                padPx.roundToInt(),
                padPx.roundToInt(),
                (padPx + widthPx).roundToInt(),
                (padPx + heightPx).roundToInt(),
            )
            drawable.draw(androidCanvas)
        }
    } else {
        CanvasDrawScope().draw(
            density = density,
            layoutDirection = layoutDirection,
            canvas = ComposeCanvas(androidCanvas),
            size = Size(bmpW.toFloat(), bmpH.toFloat()),
        ) {
            translate(padPx, padPx) {
                val stroke = Stroke(
                    width = heightPx * (if (glass) 0.095f else 0.075f),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                )
                drawXmbIconContent(icon, Color.Black, stroke)
            }
        }
    }
    val blurPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
        maskFilter = BlurMaskFilter(blurPx, BlurMaskFilter.Blur.NORMAL)
    }
    val alpha = src.extractAlpha(blurPaint, null)
    src.recycle()
    val tinted = Bitmap.createBitmap(alpha.width, alpha.height, Bitmap.Config.ARGB_8888)
    android.graphics.Canvas(tinted).drawBitmap(
        alpha,
        0f,
        0f,
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.BLACK
        },
    )
    alpha.recycle()
    return XmbGlyphShadow(image = tinted.asImageBitmap(), padPx = padPx)
}

/**
 * Frosted-glass pass: the glyph is drawn in white and then masked (SrcIn) with a vertical
 * white→ice-blue gradient, plus a second masked pass that leaves a bright gloss on the top half.
 * The shape itself is the mask, so every stroke glyph gets the PS3 glass body without new paths.
 */
private fun DrawScope.drawGlassIcon(icon: XmbIcon, stroke: Stroke) {
    val canvas = drawContext.canvas
    val bounds = Rect(Offset.Zero, Size(size.width, size.height)).inflate(stroke.width)

    // Body: cool glass gradient, brightest at the top edge like backlit acrylic.
    canvas.saveLayer(bounds, Paint())
    drawXmbIconContent(icon, Color.White, stroke)
    drawRect(
        brush = Brush.verticalGradient(
            0f to Color.White,
            0.34f to Color(0xFFEAF5FE),
            0.66f to Color(0xFFB9DCF7),
            1f to Color(0xFF7FB4E6),
        ),
        topLeft = bounds.topLeft,
        size = bounds.size,
        blendMode = BlendMode.SrcIn,
    )
    canvas.restore()

    // Gloss: hard white sheen across the top third — the "reflection" on the glass.
    canvas.saveLayer(bounds, Paint())
    drawXmbIconContent(icon, Color.White, stroke)
    drawRect(
        brush = Brush.verticalGradient(
            0f to Color.White.copy(alpha = 0.85f),
            0.30f to Color.White.copy(alpha = 0.18f),
            0.52f to Color.Transparent,
            1f to Color.Transparent,
        ),
        topLeft = bounds.topLeft,
        size = bounds.size,
        blendMode = BlendMode.SrcIn,
    )
    canvas.restore()

    // Rim light: refraction catching the bottom edge, which is what sells solid glyphs as glass.
    canvas.saveLayer(bounds, Paint())
    drawXmbIconContent(icon, Color.White, stroke)
    drawRect(
        brush = Brush.verticalGradient(
            0f to Color.Transparent,
            0.74f to Color.Transparent,
            1f to Color.White.copy(alpha = 0.6f),
        ),
        topLeft = bounds.topLeft,
        size = bounds.size,
        blendMode = BlendMode.SrcIn,
    )
    canvas.restore()
}

private fun DrawScope.drawXmbIconContent(icon: XmbIcon, tint: Color, stroke: Stroke) {
    when (icon) {
        XmbIcon.Profiles -> drawProfiles(tint, stroke)
        XmbIcon.Settings -> drawFigmaGlyph(FigmaGlyph.SETTINGS, tint)
        XmbIcon.Games -> drawFigmaGlyph(FigmaGlyph.GAMES, tint)
        XmbIcon.Media -> drawFigmaGlyph(FigmaGlyph.PHOTO, tint)
        XmbIcon.Music -> drawFigmaGlyph(FigmaGlyph.MUSIC, tint)
        XmbIcon.Network -> drawFigmaGlyph(FigmaGlyph.NETWORK, tint)
        XmbIcon.User -> drawUser(tint, stroke)
        XmbIcon.Guest -> drawGuest(tint, stroke)
        XmbIcon.General -> drawGear(tint, stroke)
        XmbIcon.Display -> drawMonitor(tint, stroke)
        XmbIcon.Themes -> drawPalette(tint, stroke)
        XmbIcon.Sound -> drawSpeaker(tint, stroke)
        XmbIcon.Scrape -> drawScrape(tint, stroke)
        XmbIcon.Social -> drawChat(tint, stroke)
        XmbIcon.Notifications -> drawBell(tint, stroke)
        XmbIcon.Trophy -> drawTrophy(tint, stroke)
        XmbIcon.Emulator -> drawFigmaGlyph(FigmaGlyph.GAMES, tint)
        XmbIcon.Continue -> drawPlay(tint, stroke)
        XmbIcon.Favorite -> drawStar(tint, stroke)
        XmbIcon.Folder -> drawFolder(tint, stroke)
        XmbIcon.Photo -> drawFigmaGlyph(FigmaGlyph.PHOTO, tint)
        XmbIcon.Video -> drawFigmaGlyph(FigmaGlyph.VIDEO, tint)
        XmbIcon.NowPlaying -> drawFigmaGlyph(FigmaGlyph.MUSIC, tint)
        XmbIcon.Playlist -> drawList(tint, stroke)
        XmbIcon.Dsp -> drawWave(tint, stroke)
        XmbIcon.Spotify -> drawSpotify(tint, stroke)
        XmbIcon.AppleMusic -> drawAppleMusic(tint, stroke)
        XmbIcon.YoutubeMusic -> drawYoutubeMusic(tint, stroke)
        XmbIcon.Shuffle -> drawShuffle(tint, stroke)
        XmbIcon.Repeat -> drawRepeat(tint, stroke)
        XmbIcon.PreviousTrack -> drawSkip(tint, stroke, forward = false)
        XmbIcon.NextTrack -> drawSkip(tint, stroke, forward = true)
        XmbIcon.Play -> drawPlay(tint, stroke)
        XmbIcon.Pause -> drawPause(tint, stroke)
        XmbIcon.Friends -> drawFriends(tint, stroke)
        XmbIcon.Store -> drawBag(tint, stroke)
        XmbIcon.News -> drawNews(tint, stroke)
        XmbIcon.System -> drawSystemCube(tint, stroke)
        XmbIcon.GamePad -> drawFigmaGlyph(FigmaGlyph.GAMES, tint)
        XmbIcon.Xora -> drawXoraWordmark(tint, stroke)
    }
}

/** XOrA wordmark — X O Γ Δ, matching the brand logo's geometric letterforms. */
private fun DrawScope.drawXoraWordmark(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    val top = h * 0.34f
    val bottom = h * 0.66f
    val sw = stroke.width
    // X
    drawLine(tint, Offset(w * 0.04f, top), Offset(w * 0.22f, bottom), strokeWidth = sw, cap = StrokeCap.Round)
    drawLine(tint, Offset(w * 0.22f, top), Offset(w * 0.04f, bottom), strokeWidth = sw, cap = StrokeCap.Round)
    // O
    drawCircle(tint, radius = w * 0.105f, center = Offset(w * 0.395f, h * 0.5f), style = stroke)
    // Γ
    drawLine(tint, Offset(w * 0.565f, top), Offset(w * 0.565f, bottom), strokeWidth = sw, cap = StrokeCap.Round)
    drawLine(tint, Offset(w * 0.565f, top), Offset(w * 0.70f, top), strokeWidth = sw, cap = StrokeCap.Round)
    // Δ
    val tri = Path().apply {
        moveTo(w * 0.865f, top)
        lineTo(w * 0.97f, bottom)
        lineTo(w * 0.76f, bottom)
        close()
    }
    drawPath(tri, tint, style = stroke)
}

private fun DrawScope.drawProfiles(tint: Color, stroke: Stroke) {
    // House + user silhouette
    val w = size.width
    val h = size.height
    val path = Path().apply {
        moveTo(w * 0.18f, h * 0.48f)
        lineTo(w * 0.5f, h * 0.18f)
        lineTo(w * 0.82f, h * 0.48f)
        lineTo(w * 0.82f, h * 0.82f)
        lineTo(w * 0.18f, h * 0.82f)
        close()
    }
    drawPath(path, tint, style = stroke)
    drawCircle(tint, radius = w * 0.08f, center = Offset(w * 0.5f, h * 0.52f))
    drawArc(
        color = tint,
        startAngle = 0f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(w * 0.36f, h * 0.58f),
        size = Size(w * 0.28f, h * 0.18f),
        style = stroke,
    )
}

private fun DrawScope.drawNote(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    drawLine(tint, Offset(w * 0.38f, h * 0.22f), Offset(w * 0.38f, h * 0.68f), strokeWidth = stroke.width * 1.2f)
    drawLine(tint, Offset(w * 0.38f, h * 0.22f), Offset(w * 0.72f, h * 0.16f), strokeWidth = stroke.width * 1.2f)
    drawLine(tint, Offset(w * 0.72f, h * 0.16f), Offset(w * 0.72f, h * 0.58f), strokeWidth = stroke.width * 1.2f)
    drawCircle(tint, radius = w * 0.09f, center = Offset(w * 0.3f, h * 0.72f))
    drawCircle(tint, radius = w * 0.09f, center = Offset(w * 0.64f, h * 0.62f))
}

private fun DrawScope.drawUser(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    drawCircle(tint, radius = w * 0.14f, center = Offset(w * 0.5f, h * 0.34f), style = stroke)
    drawArc(
        color = tint,
        startAngle = 0f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(w * 0.22f, h * 0.48f),
        size = Size(w * 0.56f, h * 0.4f),
        style = stroke,
    )
}

private fun DrawScope.drawGuest(tint: Color, stroke: Stroke) {
    drawUser(tint.copy(alpha = 0.85f), stroke)
    val w = size.width
    drawLine(tint, Offset(w * 0.22f, w * 0.22f), Offset(w * 0.78f, w * 0.78f), strokeWidth = stroke.width)
}

private fun DrawScope.drawGear(tint: Color, stroke: Stroke) {
    val w = size.width
    val c = Offset(w * 0.5f, size.height * 0.5f)
    drawCircle(tint, radius = w * 0.14f, center = c, style = stroke)
    drawCircle(tint, radius = w * 0.28f, center = c, style = stroke)
    for (i in 0 until 6) {
        val a = Math.toRadians(i * 60.0)
        val inner = w * 0.28f
        val outer = w * 0.4f
        drawLine(
            tint,
            Offset(c.x + inner * kotlin.math.cos(a).toFloat(), c.y + inner * kotlin.math.sin(a).toFloat()),
            Offset(c.x + outer * kotlin.math.cos(a).toFloat(), c.y + outer * kotlin.math.sin(a).toFloat()),
            strokeWidth = stroke.width * 1.4f,
            cap = StrokeCap.Round,
        )
    }
}

private fun DrawScope.drawMonitor(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    drawRoundRect(
        color = tint,
        topLeft = Offset(w * 0.16f, h * 0.2f),
        size = Size(w * 0.68f, h * 0.48f),
        cornerRadius = CornerRadius(w * 0.05f),
        style = stroke,
    )
    drawLine(tint, Offset(w * 0.5f, h * 0.68f), Offset(w * 0.5f, h * 0.78f), strokeWidth = stroke.width)
    drawLine(tint, Offset(w * 0.32f, h * 0.78f), Offset(w * 0.68f, h * 0.78f), strokeWidth = stroke.width)
}

private fun DrawScope.drawPalette(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    drawOval(
        color = tint,
        topLeft = Offset(w * 0.18f, h * 0.18f),
        size = Size(w * 0.64f, h * 0.64f),
        style = stroke,
    )
    drawCircle(tint, radius = w * 0.05f, center = Offset(w * 0.38f, h * 0.4f))
    drawCircle(tint, radius = w * 0.05f, center = Offset(w * 0.55f, h * 0.34f))
    drawCircle(tint, radius = w * 0.05f, center = Offset(w * 0.62f, h * 0.5f))
}

private fun DrawScope.drawSpeaker(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    val path = Path().apply {
        moveTo(w * 0.22f, h * 0.4f)
        lineTo(w * 0.38f, h * 0.4f)
        lineTo(w * 0.55f, h * 0.26f)
        lineTo(w * 0.55f, h * 0.74f)
        lineTo(w * 0.38f, h * 0.6f)
        lineTo(w * 0.22f, h * 0.6f)
        close()
    }
    drawPath(path, tint, style = stroke)
    drawArc(
        tint, -40f, 80f, false,
        Offset(w * 0.58f, h * 0.34f), Size(w * 0.22f, h * 0.32f),
        style = stroke,
    )
}

private fun DrawScope.drawScrape(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    drawCircle(tint, radius = w * 0.22f, center = Offset(w * 0.42f, h * 0.42f), style = stroke)
    drawLine(
        tint,
        Offset(w * 0.58f, h * 0.58f),
        Offset(w * 0.78f, h * 0.78f),
        strokeWidth = stroke.width * 1.5f,
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.drawChat(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    drawRoundRect(
        tint,
        Offset(w * 0.18f, h * 0.22f),
        Size(w * 0.64f, h * 0.48f),
        CornerRadius(w * 0.1f),
        style = stroke,
    )
    val tail = Path().apply {
        moveTo(w * 0.32f, h * 0.7f)
        lineTo(w * 0.28f, h * 0.84f)
        lineTo(w * 0.48f, h * 0.7f)
    }
    drawPath(tail, tint, style = stroke)
}

private fun DrawScope.drawBell(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    drawArc(
        tint, 200f, 140f, false,
        Offset(w * 0.28f, h * 0.22f), Size(w * 0.44f, h * 0.5f),
        style = stroke,
    )
    drawLine(tint, Offset(w * 0.28f, h * 0.62f), Offset(w * 0.72f, h * 0.62f), strokeWidth = stroke.width)
    drawCircle(tint, radius = w * 0.05f, center = Offset(w * 0.5f, h * 0.74f))
}

private fun DrawScope.drawTrophy(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    drawArc(
        tint, 180f, 180f, false,
        Offset(w * 0.28f, h * 0.18f), Size(w * 0.44f, h * 0.42f),
        style = stroke,
    )
    drawLine(tint, Offset(w * 0.5f, h * 0.6f), Offset(w * 0.5f, h * 0.72f), strokeWidth = stroke.width)
    drawLine(tint, Offset(w * 0.34f, h * 0.72f), Offset(w * 0.66f, h * 0.72f), strokeWidth = stroke.width)
    drawLine(tint, Offset(w * 0.3f, h * 0.8f), Offset(w * 0.7f, h * 0.8f), strokeWidth = stroke.width)
}

private fun DrawScope.drawPlay(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    val path = Path().apply {
        moveTo(w * 0.34f, h * 0.24f)
        lineTo(w * 0.76f, h * 0.5f)
        lineTo(w * 0.34f, h * 0.76f)
        close()
    }
    drawPath(path, tint, style = stroke)
}

private fun DrawScope.drawStar(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    val cx = w * 0.5f
    val cy = h * 0.5f
    val outer = w * 0.34f
    val inner = w * 0.15f
    val path = Path()
    for (i in 0 until 10) {
        val r = if (i % 2 == 0) outer else inner
        val a = Math.toRadians(-90.0 + i * 36.0)
        val x = cx + r * kotlin.math.cos(a).toFloat()
        val y = cy + r * kotlin.math.sin(a).toFloat()
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    path.close()
    drawPath(path, tint, style = stroke)
}

private fun DrawScope.drawFolder(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    drawRoundRect(
        tint,
        Offset(w * 0.16f, h * 0.34f),
        Size(w * 0.68f, h * 0.46f),
        CornerRadius(w * 0.05f),
        style = stroke,
    )
    drawRoundRect(
        tint,
        Offset(w * 0.16f, h * 0.26f),
        Size(w * 0.32f, h * 0.14f),
        CornerRadius(w * 0.04f),
        style = stroke,
    )
}

private fun DrawScope.drawList(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    for (i in 0 until 3) {
        val y = h * (0.3f + i * 0.2f)
        drawCircle(tint, radius = w * 0.04f, center = Offset(w * 0.24f, y))
        drawLine(tint, Offset(w * 0.36f, y), Offset(w * 0.78f, y), strokeWidth = stroke.width)
    }
}

private fun DrawScope.drawWave(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    val path = Path().apply {
        moveTo(w * 0.15f, h * 0.55f)
        cubicTo(w * 0.3f, h * 0.25f, w * 0.4f, h * 0.85f, w * 0.55f, h * 0.5f)
        cubicTo(w * 0.68f, h * 0.25f, w * 0.78f, h * 0.75f, w * 0.88f, h * 0.45f)
    }
    drawPath(path, tint, style = stroke)
}

private fun DrawScope.drawFriends(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    drawCircle(tint, radius = w * 0.1f, center = Offset(w * 0.36f, h * 0.34f), style = stroke)
    drawCircle(tint, radius = w * 0.1f, center = Offset(w * 0.62f, h * 0.34f), style = stroke)
    drawArc(tint, 10f, 160f, false, Offset(w * 0.18f, h * 0.48f), Size(w * 0.36f, h * 0.32f), style = stroke)
    drawArc(tint, 10f, 160f, false, Offset(w * 0.46f, h * 0.48f), Size(w * 0.36f, h * 0.32f), style = stroke)
}

private fun DrawScope.drawBag(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    drawRoundRect(
        tint,
        Offset(w * 0.22f, h * 0.28f),
        Size(w * 0.56f, h * 0.5f),
        CornerRadius(w * 0.06f),
        style = stroke,
    )
    drawLine(tint, Offset(w * 0.22f, h * 0.42f), Offset(w * 0.78f, h * 0.42f), strokeWidth = stroke.width)
    drawLine(tint, Offset(w * 0.5f, h * 0.28f), Offset(w * 0.5f, h * 0.78f), strokeWidth = stroke.width)
}

private fun DrawScope.drawNews(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    drawRoundRect(
        tint,
        Offset(w * 0.18f, h * 0.2f),
        Size(w * 0.64f, h * 0.6f),
        CornerRadius(w * 0.05f),
        style = stroke,
    )
    drawLine(tint, Offset(w * 0.28f, h * 0.36f), Offset(w * 0.72f, h * 0.36f), strokeWidth = stroke.width)
    drawLine(tint, Offset(w * 0.28f, h * 0.5f), Offset(w * 0.72f, h * 0.5f), strokeWidth = stroke.width)
    drawLine(tint, Offset(w * 0.28f, h * 0.64f), Offset(w * 0.58f, h * 0.64f), strokeWidth = stroke.width)
}

private fun DrawScope.drawSystemCube(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    drawRoundRect(
        tint,
        Offset(w * 0.22f, h * 0.28f),
        Size(w * 0.56f, h * 0.52f),
        CornerRadius(w * 0.06f),
        style = stroke,
    )
    // Small wrench badge (top-left), PS3-style
    drawCircle(tint.copy(alpha = 0.95f), radius = w * 0.12f, center = Offset(w * 0.28f, h * 0.28f))
    drawLine(
        Color.Black.copy(alpha = 0.55f),
        Offset(w * 0.22f, h * 0.3f),
        Offset(w * 0.34f, h * 0.26f),
        strokeWidth = stroke.width,
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.drawShuffle(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    val upper = Path().apply {
        moveTo(w * 0.18f, h * 0.34f)
        lineTo(w * 0.36f, h * 0.34f)
        lineTo(w * 0.7f, h * 0.68f)
        lineTo(w * 0.84f, h * 0.68f)
    }
    val lower = Path().apply {
        moveTo(w * 0.18f, h * 0.68f)
        lineTo(w * 0.36f, h * 0.68f)
        lineTo(w * 0.7f, h * 0.34f)
        lineTo(w * 0.84f, h * 0.34f)
    }
    drawPath(upper, tint, style = stroke)
    drawPath(lower, tint, style = stroke)
    drawArrowHead(w * 0.84f, h * 0.34f, w * 0.08f, tint, stroke)
    drawArrowHead(w * 0.84f, h * 0.68f, w * 0.08f, tint, stroke)
}

private fun DrawScope.drawRepeat(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    val loop = Path().apply {
        moveTo(w * 0.3f, h * 0.3f)
        lineTo(w * 0.74f, h * 0.3f)
        lineTo(w * 0.74f, h * 0.7f)
        lineTo(w * 0.26f, h * 0.7f)
        lineTo(w * 0.26f, h * 0.3f)
    }
    drawPath(loop, tint, style = stroke)
    drawArrowHead(w * 0.3f, h * 0.3f, w * 0.08f, tint, stroke)
}

private fun DrawScope.drawSkip(tint: Color, stroke: Stroke, forward: Boolean) {
    val w = size.width
    val h = size.height
    val dir = if (forward) 1f else -1f
    val centerX = w * 0.5f
    val triangle = Path().apply {
        moveTo(centerX - (dir * w * 0.2f), h * 0.3f)
        lineTo(centerX + (dir * w * 0.16f), h * 0.5f)
        lineTo(centerX - (dir * w * 0.2f), h * 0.7f)
        close()
    }
    drawPath(triangle, tint, style = stroke)
    val barX = centerX + (dir * w * 0.24f)
    drawLine(
        tint,
        Offset(barX, h * 0.3f),
        Offset(barX, h * 0.7f),
        strokeWidth = stroke.width,
        cap = StrokeCap.Round,
    )
}

private fun DrawScope.drawPause(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    drawLine(
        tint,
        Offset(w * 0.4f, h * 0.28f),
        Offset(w * 0.4f, h * 0.72f),
        strokeWidth = stroke.width * 1.6f,
        cap = StrokeCap.Round,
    )
    drawLine(
        tint,
        Offset(w * 0.6f, h * 0.28f),
        Offset(w * 0.6f, h * 0.72f),
        strokeWidth = stroke.width * 1.6f,
        cap = StrokeCap.Round,
    )
}

/** Small chevron used as the head of the shuffle / repeat strokes. */
private fun DrawScope.drawArrowHead(
    x: Float,
    y: Float,
    length: Float,
    tint: Color,
    stroke: Stroke,
) {
    val head = Path().apply {
        moveTo(x - length, y - length)
        lineTo(x, y)
        lineTo(x - length, y + length)
    }
    drawPath(head, tint, style = stroke)
}

/** Circle with three arcs — recognisable as Spotify without brand fill. */
private fun DrawScope.drawSpotify(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    drawCircle(tint, radius = w * 0.36f, center = Offset(w * 0.5f, h * 0.5f), style = stroke)
    drawArc(
        color = tint,
        startAngle = 200f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(w * 0.28f, h * 0.32f),
        size = Size(w * 0.44f, h * 0.28f),
        style = stroke,
    )
    drawArc(
        color = tint,
        startAngle = 200f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(w * 0.32f, h * 0.44f),
        size = Size(w * 0.36f, h * 0.22f),
        style = stroke,
    )
    drawArc(
        color = tint,
        startAngle = 200f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(w * 0.36f, h * 0.54f),
        size = Size(w * 0.28f, h * 0.18f),
        style = stroke,
    )
}

/** Note with a simple apple-leaf curl. */
private fun DrawScope.drawAppleMusic(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    drawNote(tint, stroke)
    drawArc(
        color = tint,
        startAngle = 210f,
        sweepAngle = 120f,
        useCenter = false,
        topLeft = Offset(w * 0.52f, h * 0.12f),
        size = Size(w * 0.22f, h * 0.18f),
        style = stroke,
    )
}

/** Play triangle in a rounded rectangle. */
private fun DrawScope.drawYoutubeMusic(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    drawRoundRect(
        tint,
        Offset(w * 0.18f, h * 0.28f),
        Size(w * 0.64f, h * 0.44f),
        CornerRadius(w * 0.12f),
        style = stroke,
    )
    val play = Path().apply {
        moveTo(w * 0.42f, h * 0.38f)
        lineTo(w * 0.66f, h * 0.5f)
        lineTo(w * 0.42f, h * 0.62f)
        close()
    }
    drawPath(play, tint, style = stroke)
}
