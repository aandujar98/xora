package com.arcadia.shell.feature.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import com.arcadia.shell.designsystem.XoraFonts
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

private val StatusFill = Color(0xFF404040)

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
