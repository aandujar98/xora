package com.arcadia.shell.feature.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arcadia.shell.designsystem.XoraFonts
import com.arcadia.shell.feature.home.component.ArtworkImage
import com.arcadia.shell.model.Game
import com.arcadia.shell.retroachievements.RaGameProgress
import com.arcadia.shell.feature.home.component.isCharging
import com.arcadia.shell.feature.home.component.isWifiConnected
import com.arcadia.shell.feature.home.component.readBatteryPercent
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Design px on the 1920x1080 artboard, measured off the reference LiveArea page.
internal const val LIVEAREA_STATUS_H = 60f
private const val STATUS_TEXT = 26f
private const val STATUS_PAD_X = 22f

/** Page sheet: a rounded outline over the artwork, whose top-right corner is the peel. */
internal const val LIVEAREA_FRAME_LEFT = 14f
internal const val LIVEAREA_FRAME_TOP = 119f
internal const val LIVEAREA_FRAME_RIGHT_INSET = 65f
internal const val LIVEAREA_FRAME_BOTTOM_INSET = 85f
internal const val LIVEAREA_FRAME_RADIUS = 54f

/** Info panel under the box art. */
internal const val PANEL_X = 97f
/** Sits clear of the existing box-art plate rather than the reference's higher one. */
internal const val PANEL_Y = 700f
internal const val PANEL_W = 985f
internal const val PANEL_H = 308f
private const val PANEL_RADIUS = 38f
private const val PANEL_PAD = 22f
private const val PANEL_ICON = 152f
private const val PANEL_TITLE_SIZE = 35f
private const val PANEL_BADGE_H = 34f
private const val PANEL_THUMB = 70f
private const val PANEL_THUMB_GAP = 11f
private const val PANEL_ROW_TEXT = 27f

private val StatusFill = Color(0xFF404040)
private val PanelFill = Color(0x59000000)
private val PanelBorder = Color(0x66FFFFFF)
private val PanelInk = Color(0xFFFFFFFF)
private val PanelTrack = Color(0x4DFFFFFF)
private val PanelTrackFill = Color(0xFFF3B463)
private val PanelChipFill = Color(0x40FFFFFF)

/** What the LiveArea status strip shows. */
internal data class VitaLiveAreaStatus(
    val backLabel: String = "PRESS B TO RETURN TO SHORTCUTS",
    val timeText: String = "",
    val dateText: String = "",
    val wifiConnected: Boolean = false,
    val batteryPercent: Int = 0,
    val charging: Boolean = false,
)

/** Clock, connection and battery, refreshed while the page is up. */
@Composable
internal fun rememberVitaLiveAreaStatus(
    backLabel: String = "PRESS B TO RETURN TO SHORTCUTS",
): VitaLiveAreaStatus {
    val context = LocalContext.current
    var status by remember {
        mutableStateOf(readLiveAreaStatus(context, backLabel))
    }
    LaunchedEffect(context, backLabel) {
        while (true) {
            status = readLiveAreaStatus(context, backLabel)
            delay(STATUS_POLL_MS)
        }
    }
    return status
}

private const val STATUS_POLL_MS = 20_000L

private fun readLiveAreaStatus(context: Context, backLabel: String): VitaLiveAreaStatus {
    val now = Date()
    return VitaLiveAreaStatus(
        backLabel = backLabel,
        timeText = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(now),
        dateText = SimpleDateFormat("MM/dd", Locale.getDefault()).format(now),
        wifiConnected = isWifiConnected(context),
        batteryPercent = readBatteryPercent(context),
        charging = isCharging(context),
    )
}

/**
 * LiveArea status strip: how to get back on the left, connection and clock on the right. The
 * Vita keeps this above the page, so the peel never touches it.
 */
@Composable
internal fun VitaLiveAreaStatusBar(
    backLabel: String,
    timeText: String,
    dateText: String,
    wifiConnected: Boolean,
    batteryPercent: Int,
    charging: Boolean,
    unit: Float,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val style = TextStyle(
        fontFamily = XoraFonts.XmbLabel,
        fontWeight = FontWeight.Normal,
        fontSize = with(density) { (STATUS_TEXT * unit).dp.toSp() },
        color = Color.White,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.Both,
        ),
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height((LIVEAREA_STATUS_H * unit).dp)
            .background(StatusFill)
            .padding(horizontal = (STATUS_PAD_X * unit).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = backLabel, style = style, maxLines = 1)
        Spacer(Modifier.weight(1f))
        LiveAreaWifi(
            connected = wifiConnected,
            modifier = Modifier.size((30f * unit).dp),
        )
        Spacer(Modifier.width((14f * unit).dp))
        Text(text = dateText, style = style, maxLines = 1)
        Spacer(Modifier.width((18f * unit).dp))
        Text(text = timeText, style = style, maxLines = 1)
        Spacer(Modifier.width((18f * unit).dp))
        Text(
            text = if (charging) "$batteryPercent%+" else "$batteryPercent%",
            style = style,
            maxLines = 1,
        )
        Spacer(Modifier.width((10f * unit).dp))
        LiveAreaBattery(
            percent = batteryPercent,
            charging = charging,
            modifier = Modifier.size(width = (44f * unit).dp, height = (24f * unit).dp),
        )
    }
}

@Composable
private fun LiveAreaWifi(connected: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val tint = if (connected) Color.White else Color.White.copy(alpha = 0.35f)
        val stroke = Stroke(width = size.minDimension * 0.11f, cap = StrokeCap.Round)
        val cx = size.width / 2f
        val cy = size.height * 0.74f
        drawCircle(color = tint, radius = size.minDimension * 0.075f, center = Offset(cx, cy))
        if (!connected) return@Canvas
        for (i in 1..3) {
            val r = size.minDimension * (0.17f + i * 0.17f)
            drawArc(
                color = tint.copy(alpha = 1f - i * 0.14f),
                startAngle = 210f,
                sweepAngle = 120f,
                useCenter = false,
                topLeft = Offset(cx - r, cy - r),
                size = Size(r * 2, r * 2),
                style = stroke,
            )
        }
    }
}

@Composable
private fun LiveAreaBattery(percent: Int, charging: Boolean, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val bodyW = size.width * 0.84f
        val bodyH = size.height * 0.8f
        val top = (size.height - bodyH) / 2f
        val r = CornerRadius(size.height * 0.16f, size.height * 0.16f)
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(0f, top),
            size = Size(bodyW, bodyH),
            cornerRadius = r,
            style = Stroke(width = size.height * 0.1f),
        )
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(bodyW, size.height * 0.32f),
            size = Size(size.width - bodyW, size.height * 0.36f),
            cornerRadius = CornerRadius(size.height * 0.08f, size.height * 0.08f),
        )
        val pad = size.height * 0.2f
        drawRoundRect(
            color = if (charging || percent > 20) Color.White else Color(0xFFFF5C6C),
            topLeft = Offset(pad, top + pad),
            size = Size(
                ((bodyW - pad * 2) * (percent / 100f).coerceIn(0f, 1f)),
                bodyH - pad * 2,
            ),
            cornerRadius = CornerRadius(size.height * 0.06f, size.height * 0.06f),
        )
    }
}

/**
 * The LiveArea info panel: the game's own icon and name, its system badge, the trophy strip and
 * count with a completion bar, and what has been played lately.
 */
@Composable
internal fun VitaLiveAreaPanel(
    title: String,
    iconPath: String?,
    systemLabel: String,
    progress: RaGameProgress?,
    recentGames: List<Game>,
    recentOverflow: Int,
    unit: Float,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    fun sp(design: Float) = with(density) { (design * unit).dp.toSp() }
    val ink = TextStyle(
        fontFamily = XoraFonts.XmbLabel,
        fontWeight = FontWeight.Normal,
        color = PanelInk,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.Both,
        ),
    )
    Box(
        modifier = modifier
            .requiredSize((PANEL_W * unit).dp, (PANEL_H * unit).dp)
            .clip(RoundedCornerShape((PANEL_RADIUS * unit).dp))
            .background(PanelFill)
            .border(
                width = (2f * unit).dp,
                color = PanelBorder,
                shape = RoundedCornerShape((PANEL_RADIUS * unit).dp),
            )
            .padding((PANEL_PAD * unit).dp),
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            ArtworkImage(
                path = iconPath,
                contentDescription = title,
                fallbackText = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .requiredSize((PANEL_ICON * unit).dp)
                    .clip(RoundedCornerShape((12f * unit).dp)),
            )
            Spacer(Modifier.width((16f * unit).dp))
            Column(modifier = Modifier.fillMaxHeight().weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = ink.copy(fontSize = sp(PANEL_TITLE_SIZE)),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    PanelChip(text = systemLabel, unit = unit, style = ink)
                }
                Spacer(Modifier.height((10f * unit).dp))
                TrophyStrip(progress = progress, unit = unit)
                Spacer(Modifier.weight(1f))
                TrophyCount(progress = progress, unit = unit, style = ink)
                Spacer(Modifier.height((12f * unit).dp))
                RecentlyPlayedRow(
                    games = recentGames,
                    overflow = recentOverflow,
                    unit = unit,
                    style = ink,
                )
            }
        }
    }
}

@Composable
private fun PanelChip(text: String, unit: Float, style: TextStyle) {
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .height((PANEL_BADGE_H * unit).dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(PanelChipFill)
            .border(
                width = (1.5f * unit).dp,
                color = PanelBorder,
                shape = RoundedCornerShape(percent = 50),
            )
            .padding(horizontal = (14f * unit).dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = style.copy(fontSize = with(density) { (22f * unit).dp.toSp() }),
            maxLines = 1,
        )
    }
}

/** Trophy badges for this game — earned in colour, the rest greyed, as on the Vita. */
@Composable
private fun TrophyStrip(progress: RaGameProgress?, unit: Float) {
    val slots = 7
    val badges = progress?.achievements.orEmpty().take(slots)
    Row(horizontalArrangement = Arrangement.spacedBy((PANEL_THUMB_GAP * unit).dp)) {
        repeat(slots) { i ->
            val badge = badges.getOrNull(i)
            Box(
                modifier = Modifier
                    .requiredSize((PANEL_THUMB * unit).dp)
                    .clip(RoundedCornerShape((8f * unit).dp))
                    .background(Color.White.copy(alpha = 0.14f))
                    .border(
                        width = (1.5f * unit).dp,
                        color = PanelBorder,
                        shape = RoundedCornerShape((8f * unit).dp),
                    ),
            ) {
                if (badge != null) {
                    ArtworkImage(
                        path = badge.badgeUrl,
                        contentDescription = badge.title,
                        fallbackText = "",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    if (!badge.earned) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xB3202020)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TrophyCount(progress: RaGameProgress?, unit: Float, style: TextStyle) {
    val density = LocalDensity.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        TrophyGlyph(modifier = Modifier.size((36f * unit).dp))
        Spacer(Modifier.width((10f * unit).dp))
        Text(
            text = "${progress?.numAwardedToUser ?: 0}",
            style = style.copy(fontSize = with(density) { (38f * unit).dp.toSp() }),
            maxLines = 1,
        )
        Text(
            text = "/${progress?.numAchievements ?: 0}",
            style = style.copy(
                fontSize = with(density) { (24f * unit).dp.toSp() },
                color = PanelInk.copy(alpha = 0.75f),
            ),
            maxLines = 1,
        )
        Spacer(Modifier.width((18f * unit).dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height((22f * unit).dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(PanelTrack),
        ) {
            val fraction = progress?.completionFraction ?: 0f
            if (fraction > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction.coerceAtLeast(0.04f))
                        .clip(RoundedCornerShape(percent = 50))
                        .background(
                            Brush.horizontalGradient(
                                listOf(PanelTrackFill, PanelTrackFill.copy(alpha = 0.85f)),
                            ),
                        ),
                )
            }
        }
    }
}

@Composable
private fun TrophyGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cup = Size(w * 0.52f, h * 0.42f)
        drawArc(
            color = Color.White,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(w * 0.24f, h * 0.14f),
            size = cup,
        )
        drawRect(
            color = Color.White,
            topLeft = Offset(w * 0.46f, h * 0.52f),
            size = Size(w * 0.08f, h * 0.2f),
        )
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(w * 0.28f, h * 0.72f),
            size = Size(w * 0.44f, h * 0.12f),
            cornerRadius = CornerRadius(h * 0.04f, h * 0.04f),
        )
        val handle = Stroke(width = w * 0.06f)
        for (side in listOf(-1f, 1f)) {
            val cx = w * 0.5f + side * w * 0.3f
            drawArc(
                color = Color.White,
                startAngle = if (side < 0) 90f else 270f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(cx - w * 0.09f, h * 0.16f),
                size = Size(w * 0.18f, h * 0.24f),
                style = handle,
            )
        }
    }
}

/** "Recently played" — the Vita shows other players here; a launcher has the last games run. */
@Composable
private fun RecentlyPlayedRow(
    games: List<Game>,
    overflow: Int,
    unit: Float,
    style: TextStyle,
) {
    val density = LocalDensity.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        ClockGlyph(modifier = Modifier.size((34f * unit).dp))
        Spacer(Modifier.width((10f * unit).dp))
        Text(
            text = "RECENTLY PLAYED:",
            style = style.copy(
                fontSize = with(density) { (PANEL_ROW_TEXT * unit).dp.toSp() },
                color = PanelInk.copy(alpha = 0.85f),
            ),
            maxLines = 1,
        )
        Spacer(Modifier.width((14f * unit).dp))
        Row(horizontalArrangement = Arrangement.spacedBy((8f * unit).dp)) {
            games.forEach { game ->
                ArtworkImage(
                    path = game.gridArt,
                    contentDescription = game.title,
                    fallbackText = "",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .requiredSize((56f * unit).dp)
                        .clip(CircleShape)
                        .border(width = (2f * unit).dp, color = PanelBorder, shape = CircleShape),
                )
            }
        }
        if (overflow > 0) {
            Spacer(Modifier.width((10f * unit).dp))
            PanelChip(text = "+$overflow", unit = unit, style = style)
        }
    }
}

@Composable
private fun ClockGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val r = size.minDimension / 2f
        val c = Offset(size.width / 2f, size.height / 2f)
        drawCircle(color = Color.White, radius = r, center = c)
        drawCircle(color = Color(0xFF303030), radius = r * 0.84f, center = c)
        val hand = Stroke(width = r * 0.12f, cap = StrokeCap.Round)
        drawLine(
            color = Color.White,
            start = c,
            end = Offset(c.x, c.y - r * 0.55f),
            strokeWidth = hand.width,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = Color.White,
            start = c,
            end = Offset(c.x + r * 0.42f, c.y),
            strokeWidth = hand.width,
            cap = StrokeCap.Round,
        )
    }
}
