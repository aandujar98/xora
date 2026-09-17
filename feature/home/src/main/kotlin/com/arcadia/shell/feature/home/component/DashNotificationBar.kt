package com.arcadia.shell.feature.home.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.material3.Text
import com.arcadia.shell.designsystem.ArcadiaMotion
import com.arcadia.shell.designsystem.XoraFonts
import com.arcadia.shell.designsystem.arcadiaTween
import com.arcadia.shell.feature.home.R
import com.arcadia.shell.launcher.notifications.DashNotification
import com.arcadia.shell.launcher.notifications.DashNotificationKind

// 10% up on the original 32/18/15 — the line reads from a couch without becoming chrome.
private val DashBarHeight = 35.dp
private val DashIconSize = 20.dp
private val DashTextSize = 16.5.sp

/**
 * Figma 974:2199: near-black at the left edge fading out to the right, so the line reads over
 * whatever the shell is showing without drawing a box around itself.
 */
private val DashBarFill = Brush.horizontalGradient(
    0f to Color(0xFF0A0A0A),
    0.55f to Color(0xCC0E0E0E),
    1f to Color.Transparent,
)

/** The icon each kind carries (ICONS-2 from DNU-0.5.6), and nothing for a kind that should read as plain text. */
private fun DashNotificationKind.iconRes(): Int = when (this) {
    DashNotificationKind.Music -> R.drawable.dash_music
    DashNotificationKind.Scraping -> R.drawable.dash_download
    DashNotificationKind.Update -> R.drawable.dash_update
    DashNotificationKind.Playtime -> R.drawable.dash_time
    DashNotificationKind.Scanning -> R.drawable.dash_search_device
    DashNotificationKind.Error -> R.drawable.dash_power
}

/**
 * The bottom-left Dash line. Slides in from the left as it fades up, holds, then slides back out
 * the way it came. Capped at half the screen so a long line ellipsizes rather than crossing the
 * whole dash.
 */
@Composable
fun BoxScope.DashNotificationBar(
    notification: DashNotification?,
    modifier: Modifier = Modifier,
) {
    // Held through the fade-out so the bar does not blank on the frame it starts leaving.
    val last = remember { mutableStateOf(notification) }
    if (notification != null) last.value = notification
    val shown = notification ?: last.value
    val maxWidth = LocalConfiguration.current.screenWidthDp.dp / 2
    AnimatedVisibility(
        visible = notification != null,
        enter = slideInHorizontally(arcadiaTween(ArcadiaMotion.Medium)) { -it } +
            fadeIn(arcadiaTween(ArcadiaMotion.Medium)),
        exit = slideOutHorizontally(arcadiaTween(ArcadiaMotion.Medium)) { -it } +
            fadeOut(arcadiaTween(ArcadiaMotion.Medium)),
        modifier = modifier.align(Alignment.BottomStart),
    ) {
        if (shown != null) {
            Row(
                modifier = Modifier
                    .widthIn(max = maxWidth)
                    .height(DashBarHeight)
                    .background(DashBarFill)
                    .padding(start = 16.dp, end = 32.dp)
                    .semantics { contentDescription = shown.text },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Image(
                    painter = painterResource(shown.kind.iconRes()),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(Color.White),
                    modifier = Modifier.size(DashIconSize),
                )
                Text(
                    text = shown.text,
                    fontFamily = XoraFonts.XmbLabel,
                    fontSize = DashTextSize,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Standalone host for hosts that are not already inside a [Box]. */
@Composable
fun DashNotificationHost(
    notification: DashNotification?,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth()) {
        DashNotificationBar(notification = notification)
    }
}
