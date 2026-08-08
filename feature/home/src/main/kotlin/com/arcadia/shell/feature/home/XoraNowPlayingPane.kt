package com.arcadia.shell.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arcadia.shell.designsystem.ArcadiaGlass
import com.arcadia.shell.designsystem.GlassIntensity
import com.arcadia.shell.designsystem.GlassTone
import com.arcadia.shell.designsystem.XoraFonts
import com.arcadia.shell.designsystem.liquidGlass
import com.arcadia.shell.feature.home.component.CoverArt
import com.arcadia.shell.feature.home.component.ProgressTrack
import com.arcadia.shell.launcher.music.NowPlayingState
import java.util.concurrent.TimeUnit

// Concept geometry (1920x1080 frame, node 301:1495).
private const val DESIGN_WIDTH = 1920f
private const val DESIGN_HEIGHT = 1080f
private const val CARD_LEFT = 583f
private const val CARD_TOP = 830f
private const val CARD_WIDTH = 753f
private const val CARD_HEIGHT = 234f
private const val CARD_RADIUS = 31f
private const val ART_SIZE = 183f
private const val ART_LEFT = 603f
private const val ART_TOP = 853f

/**
 * Music → Now Playing, drawn over the track's cover art (the pane's backdrop supplies the art).
 *
 * Transport controls are laid out here but inert until the audio engine lands; the state they
 * render comes from [NowPlayingState] so wiring the player later is a data change, not a redesign.
 */
@Composable
fun XoraNowPlayingPane(
    state: NowPlayingState,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val unit = minOf(
            maxWidth.value / DESIGN_WIDTH,
            maxHeight.value / DESIGN_HEIGHT,
        )
        val originX = (maxWidth.value - (DESIGN_WIDTH * unit)) / 2f
        val originY = (maxHeight.value - (DESIGN_HEIGHT * unit)) / 2f
        val track = state.track

        Box(
            modifier = Modifier
                .offset(
                    x = (originX + (CARD_LEFT * unit)).dp,
                    y = (originY + (CARD_TOP * unit)).dp,
                )
                .size(
                    width = (CARD_WIDTH * unit).dp,
                    height = (CARD_HEIGHT * unit).dp,
                )
                .liquidGlass(
                    shape = RoundedCornerShape((CARD_RADIUS * unit).dp),
                    tone = GlassTone.OverMedia,
                    intensity = GlassIntensity.Strong,
                ),
        )

        CoverArt(
            path = track?.albumArtUri,
            fallback = track?.title,
            size = (ART_SIZE * unit).toInt().coerceAtLeast(24),
            corner = (25f * unit).toInt().coerceAtLeast(4),
            modifier = Modifier.offset(
                x = (originX + (ART_LEFT * unit)).dp,
                y = (originY + (ART_TOP * unit)).dp,
            ),
        )

        Column(
            verticalArrangement = Arrangement.spacedBy((6f * unit).dp),
            modifier = Modifier
                .offset(
                    x = (originX + (801f * unit)).dp,
                    y = (originY + (846f * unit)).dp,
                )
                .width((489f * unit).dp),
        ) {
            Text(
                text = track?.title ?: "Nothing playing",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = XoraFonts.Title,
                    fontWeight = FontWeight.Medium,
                    fontSize = (32f * unit).sp,
                    lineHeight = (34f * unit).sp,
                ),
                color = NowPlayingInk,
            )
            Text(
                text = track?.artist ?: "Pick a song from Music",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontFamily = XoraFonts.Secondary,
                    fontSize = (16f * unit).sp,
                ),
                color = NowPlayingInk.copy(alpha = 0.85f),
            )
            ProgressTrack(
                fraction = state.progress,
                height = (12f * unit).toInt().coerceAtLeast(3),
                modifier = Modifier
                    .padding(top = (10f * unit).dp)
                    .fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                TimeLabel(text = formatTrackTime(state.positionMs), unit = unit)
                TimeLabel(
                    text = remainingLabel(state),
                    unit = unit,
                )
            }
            TransportRow(state = state, unit = unit)
        }
    }
}

@Composable
private fun TimeLabel(text: String, unit: Float) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(
            fontFamily = XoraFonts.Secondary,
            fontSize = (16f * unit).sp,
        ),
        color = NowPlayingInk.copy(alpha = 0.8f),
    )
}

/** Shuffle / previous / play / next / repeat, spaced as the concept lays them out. */
@Composable
private fun TransportRow(state: NowPlayingState, unit: Float) {
    Row(
        modifier = Modifier
            .padding(top = (8f * unit).dp)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TransportGlyph(XmbIcon.Shuffle, unit, active = state.shuffle)
        TransportGlyph(XmbIcon.PreviousTrack, unit)
        TransportGlyph(
            icon = if (state.isPlaying) XmbIcon.Pause else XmbIcon.Play,
            unit = unit,
            size = 46f,
        )
        TransportGlyph(XmbIcon.NextTrack, unit)
        TransportGlyph(XmbIcon.Repeat, unit, active = state.repeat)
    }
}

@Composable
private fun TransportGlyph(
    icon: XmbIcon,
    unit: Float,
    active: Boolean = false,
    size: Float = 34f,
) {
    XmbVectorIcon(
        icon = icon,
        tint = if (active) Color.White else NowPlayingInk.copy(alpha = 0.9f),
        size = (size * unit).dp,
        outlined = false,
    )
}

private val NowPlayingInk = Color(0xFFEDEDED)

private fun remainingLabel(state: NowPlayingState): String {
    val duration = state.track?.durationMs ?: return "-0:00"
    val remaining = (duration - state.positionMs).coerceAtLeast(0)
    return "-${formatTrackTime(remaining)}"
}

internal fun formatTrackTime(millis: Long): String {
    val safe = millis.coerceAtLeast(0)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(safe)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(safe) % 60
    return "%d:%02d".format(minutes, seconds)
}
