package com.arcadia.shell.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arcadia.shell.designsystem.XoraFonts
import com.arcadia.shell.designsystem.xoraModalGlass
import com.arcadia.shell.feature.home.component.CoverArt
import com.arcadia.shell.launcher.music.MusicSource
import com.arcadia.shell.launcher.music.NowPlayingState
import com.arcadia.shell.launcher.music.NowPlayingVolume

private val HudShape = RoundedCornerShape(18.dp)
private val HudInk = Color(0xFFEDEDED)
private val VolumeFill = Color(0xFF50D9FF)
private val VolumeRail = Color.White.copy(alpha = 0.22f)

/**
 * Bottom-right music HUD on the XOrA emulator overlay.
 *
 * Wrap-content only — a full-screen Compose host over the framebuffer washes the game.
 */
@Composable
fun EmulatorNowPlayingHud(
    state: NowPlayingState,
    onTogglePlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onVolumeDown: () -> Unit,
    onVolumeUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!state.hasTrack) return
    val track = state.track ?: return
    val showVolume = track.source == MusicSource.Device

    Column(
        modifier = modifier
            .width(300.dp)
            .xoraModalGlass(HudShape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverArt(
                path = track.albumArtUri,
                fallback = track.title,
                size = 48,
                corner = 8,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = HudInk,
                    fontFamily = XoraFonts.Title,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                )
                Text(
                    text = track.artist,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = HudInk.copy(alpha = 0.72f),
                    fontFamily = XoraFonts.Secondary,
                    fontSize = 11.sp,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HudGlyph(
                icon = XmbIcon.PreviousTrack,
                onClick = onSkipPrevious,
            )
            HudGlyph(
                icon = if (state.isPlaying) XmbIcon.Pause else XmbIcon.Play,
                size = 36f,
                onClick = onTogglePlayPause,
            )
            HudGlyph(
                icon = XmbIcon.NextTrack,
                onClick = onSkipNext,
            )
        }
        if (showVolume) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HudTextButton(label = "−", onClick = onVolumeDown)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(VolumeRail),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(NowPlayingVolume.coerce(state.volume))
                            .height(6.dp)
                            .clip(CircleShape)
                            .background(VolumeFill),
                    )
                }
                HudTextButton(label = "+", onClick = onVolumeUp)
                Text(
                    text = NowPlayingVolume.percentLabel(state.volume),
                    color = HudInk.copy(alpha = 0.8f),
                    fontFamily = XoraFonts.Secondary,
                    fontSize = 11.sp,
                    modifier = Modifier.width(36.dp),
                )
            }
        }
    }
}

@Composable
private fun HudGlyph(
    icon: XmbIcon,
    onClick: () -> Unit,
    size: Float = 26f,
) {
    val hit = (size + 14f).dp
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(hit)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = hit / 2),
                role = Role.Button,
                onClick = onClick,
            ),
    ) {
        XmbVectorIcon(
            icon = icon,
            tint = HudInk,
            size = size.dp,
            outlined = false,
        )
    }
}

@Composable
private fun HudTextButton(
    label: String,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(28.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = 16.dp),
                role = Role.Button,
                onClick = onClick,
            ),
    ) {
        Text(
            text = label,
            color = HudInk,
            fontFamily = XoraFonts.Title,
            fontWeight = FontWeight.Bold,
            fontSize = 18.sp,
        )
    }
}
