package com.arcadia.shell.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.arcadia.shell.designsystem.GlassTone
import com.arcadia.shell.designsystem.XoraModalGlass
import com.arcadia.shell.designsystem.rememberGlassTokens
import com.arcadia.shell.designsystem.xoraModalGlass
import kotlin.math.roundToInt

private val WindowFill = Color(0xE6101218)
private val FocusFill = Color(0x33FFFFFF)

internal const val VOLUME_MIXER_MUSIC = 0
internal const val VOLUME_MIXER_BGM = 1

data class VolumeMixerUiState(
    val open: Boolean = false,
    val focusedIndex: Int = VOLUME_MIXER_MUSIC,
    val musicVolume: Float = 0.7f,
    val bgmVolume: Float = 0.35f,
)

internal fun nudgeMixerVolume(current: Float, delta: Float): Float =
    (current + delta).coerceIn(0f, 1f)

/**
 * XMB overlay: library / Now Playing volume vs looping XMB background music.
 * Opened with face X (and Options when not in a ROM folder).
 */
@Composable
fun XmbVolumeMixer(
    state: VolumeMixerUiState,
    onMusicVolume: (Float) -> Unit,
    onBgmVolume: (Float) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!state.open) return
    val glass = rememberGlassTokens(GlassTone.OverMedia)
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .widthIn(min = 420.dp, max = 520.dp)
                .fillMaxWidth(0.5f)
                .xoraModalGlass()
                .background(WindowFill, XoraModalGlass.Shape)
                .padding(horizontal = 24.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Volume mix",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = glass.content,
            )
            Text(
                text = "Library music is the song that is playing. XMB music is the theme loop " +
                    "behind the menu.",
                style = MaterialTheme.typography.bodyMedium,
                color = glass.contentMuted,
            )
            MixerSliderRow(
                title = "Playing music",
                subtitle = "Now Playing / Music category",
                volume = state.musicVolume,
                focused = state.focusedIndex == VOLUME_MIXER_MUSIC,
                onVolume = onMusicVolume,
            )
            MixerSliderRow(
                title = "XMB music",
                subtitle = "Menu background soundtrack",
                volume = state.bgmVolume,
                focused = state.focusedIndex == VOLUME_MIXER_BGM,
                onVolume = onBgmVolume,
            )
            Text(
                text = "U/D · Mix   L/R · Level   B · Close",
                style = MaterialTheme.typography.labelMedium,
                color = glass.contentMuted,
            )
        }
    }
}

@Composable
private fun MixerSliderRow(
    title: String,
    subtitle: String,
    volume: Float,
    focused: Boolean,
    onVolume: (Float) -> Unit,
) {
    val glass = rememberGlassTokens(GlassTone.OverMedia)
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (focused) {
                    Modifier
                        .background(FocusFill, shape)
                        .border(2.dp, glass.content, shape)
                } else {
                    Modifier.border(1.dp, Color.White.copy(alpha = 0.12f), shape)
                },
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = glass.content,
            )
            Text(
                text = "${(volume * 100f).roundToInt()}%",
                style = MaterialTheme.typography.titleSmall,
                color = glass.content,
            )
        }
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = glass.contentMuted,
        )
        Slider(
            value = volume,
            onValueChange = onVolume,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = glass.content,
                activeTrackColor = glass.content,
                inactiveTrackColor = Color.White.copy(alpha = 0.18f),
            ),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
