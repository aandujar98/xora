package com.arcadia.shell.feature.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

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
    Friends,
    Store,
    News,
    System,
    GamePad,
}

fun XoraXmbCategory.toXmbIcon(): XmbIcon = when (this) {
    XoraXmbCategory.Profiles -> XmbIcon.Profiles
    XoraXmbCategory.Settings -> XmbIcon.Settings
    XoraXmbCategory.Games -> XmbIcon.Games
    XoraXmbCategory.Media -> XmbIcon.Media
    XoraXmbCategory.Music -> XmbIcon.Music
    XoraXmbCategory.Network -> XmbIcon.Network
}

@Composable
fun XmbVectorIcon(
    icon: XmbIcon,
    modifier: Modifier = Modifier,
    tint: Color = Color.White,
    size: Dp = 28.dp,
    /** Soft black halo so glyphs stay readable over bright hero art (no glass / reflection). */
    outlined: Boolean = true,
) {
    Canvas(modifier = modifier.size(size)) {
        val stroke = Stroke(
            width = size.toPx() * 0.075f,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        if (outlined) {
            val outline = size.toPx() * 0.055f
            val ink = Color.Black
            // 8-direction outline so strokes and fills both get a solid black edge.
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

private fun DrawScope.drawXmbIconContent(icon: XmbIcon, tint: Color, stroke: Stroke) {
    when (icon) {
        XmbIcon.Profiles -> drawProfiles(tint, stroke)
        XmbIcon.Settings -> drawSettingsToolbox(tint, stroke)
        XmbIcon.Games -> drawController(tint, stroke)
        XmbIcon.Media -> drawCamera(tint, stroke)
        XmbIcon.Music -> drawNote(tint, stroke)
        XmbIcon.Network -> drawGlobe(tint, stroke)
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
        XmbIcon.Continue -> drawPlay(tint, stroke)
        XmbIcon.Favorite -> drawStar(tint, stroke)
        XmbIcon.Folder -> drawFolder(tint, stroke)
        XmbIcon.Photo -> drawCamera(tint, stroke)
        XmbIcon.Video -> drawFilm(tint, stroke)
        XmbIcon.NowPlaying -> drawNote(tint, stroke)
        XmbIcon.Playlist -> drawList(tint, stroke)
        XmbIcon.Dsp -> drawWave(tint, stroke)
        XmbIcon.Friends -> drawFriends(tint, stroke)
        XmbIcon.Store -> drawBag(tint, stroke)
        XmbIcon.News -> drawNews(tint, stroke)
        XmbIcon.System -> drawSystemCube(tint, stroke)
        XmbIcon.GamePad -> drawController(tint, stroke)
    }
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

private fun DrawScope.drawSettingsToolbox(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    drawRoundRect(
        color = tint,
        topLeft = Offset(w * 0.18f, h * 0.38f),
        size = Size(w * 0.64f, h * 0.42f),
        cornerRadius = CornerRadius(w * 0.06f),
        style = stroke,
    )
    drawRoundRect(
        color = tint,
        topLeft = Offset(w * 0.32f, h * 0.22f),
        size = Size(w * 0.36f, h * 0.2f),
        cornerRadius = CornerRadius(w * 0.04f),
        style = stroke,
    )
    drawLine(tint, Offset(w * 0.18f, h * 0.52f), Offset(w * 0.82f, h * 0.52f), strokeWidth = stroke.width)
}

private fun DrawScope.drawController(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    drawRoundRect(
        color = tint,
        topLeft = Offset(w * 0.12f, h * 0.32f),
        size = Size(w * 0.76f, h * 0.4f),
        cornerRadius = CornerRadius(w * 0.18f),
        style = stroke,
    )
    drawCircle(tint, radius = w * 0.055f, center = Offset(w * 0.32f, h * 0.52f))
    drawCircle(tint, radius = w * 0.04f, center = Offset(w * 0.68f, h * 0.46f), style = stroke)
    drawCircle(tint, radius = w * 0.04f, center = Offset(w * 0.76f, h * 0.54f), style = stroke)
}

private fun DrawScope.drawCamera(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    drawRoundRect(
        color = tint,
        topLeft = Offset(w * 0.16f, h * 0.32f),
        size = Size(w * 0.68f, h * 0.44f),
        cornerRadius = CornerRadius(w * 0.08f),
        style = stroke,
    )
    drawCircle(tint, radius = w * 0.12f, center = Offset(w * 0.5f, h * 0.54f), style = stroke)
    drawCircle(tint, radius = w * 0.04f, center = Offset(w * 0.72f, h * 0.42f))
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

private fun DrawScope.drawGlobe(tint: Color, stroke: Stroke) {
    val w = size.width
    val c = Offset(w * 0.5f, size.height * 0.5f)
    val r = w * 0.32f
    drawCircle(tint, radius = r, center = c, style = stroke)
    drawOval(
        color = tint,
        topLeft = Offset(c.x - r * 0.45f, c.y - r),
        size = Size(r * 0.9f, r * 2f),
        style = stroke,
    )
    drawLine(tint, Offset(c.x - r, c.y), Offset(c.x + r, c.y), strokeWidth = stroke.width)
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

private fun DrawScope.drawFilm(tint: Color, stroke: Stroke) {
    val w = size.width
    val h = size.height
    drawRoundRect(
        tint,
        Offset(w * 0.22f, h * 0.18f),
        Size(w * 0.56f, h * 0.64f),
        CornerRadius(w * 0.04f),
        style = stroke,
    )
    for (i in 0 until 4) {
        val y = h * (0.28f + i * 0.14f)
        drawLine(tint, Offset(w * 0.28f, y), Offset(w * 0.36f, y), strokeWidth = stroke.width)
        drawLine(tint, Offset(w * 0.64f, y), Offset(w * 0.72f, y), strokeWidth = stroke.width)
    }
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
