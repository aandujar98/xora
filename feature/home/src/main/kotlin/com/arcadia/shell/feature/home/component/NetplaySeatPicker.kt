package com.arcadia.shell.feature.home.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arcadia.shell.designsystem.ArcadiaGlass
import com.arcadia.shell.designsystem.ArcadiaMotion
import com.arcadia.shell.designsystem.GlassIntensity
import com.arcadia.shell.designsystem.GlassTone
import com.arcadia.shell.designsystem.arcadiaTween
import com.arcadia.shell.designsystem.liquidGlass
import com.arcadia.shell.designsystem.rememberGlassTokens

private val FocusSeat = Color(0xFF4AE39A)

/** One selectable seat row in the picker. */
data class NetplaySeatOption(
    val slot: Int,
    /** Username occupying the seat, empty when free. */
    val takenBy: String = "",
    val isCurrent: Boolean = false,
) {
    val taken: Boolean get() = takenBy.isNotBlank() && !isCurrent
}

/**
 * Pop-up seat picker for online netplay joiners: lists Players 2–4, greys out seats that are
 * already taken, and marks the seat this device currently holds. Host is always Player 1.
 */
@Composable
fun NetplaySeatPickerDialog(
    visible: Boolean,
    options: List<NetplaySeatOption>,
    focusIndex: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = rememberGlassTokens(GlassTone.Surface)
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(arcadiaTween(ArcadiaMotion.Medium)) +
            scaleIn(arcadiaTween(ArcadiaMotion.Medium), initialScale = 0.94f),
        exit = fadeOut(arcadiaTween(ArcadiaMotion.Fast)) +
            scaleOut(arcadiaTween(ArcadiaMotion.Fast), targetScale = 0.98f),
        modifier = modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.55f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(min = 300.dp, max = 440.dp)
                    .liquidGlass(
                        shape = ArcadiaGlass.PanelShape,
                        tone = GlassTone.Surface,
                        intensity = GlassIntensity.Strong,
                        shimmer = true,
                    )
                    .clickable(enabled = false, onClick = {})
                    .padding(horizontal = 22.dp, vertical = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Choose your player",
                    style = MaterialTheme.typography.labelLarge,
                    color = FocusSeat,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "The host is Player 1 — taken seats are greyed out",
                    style = MaterialTheme.typography.bodyMedium,
                    color = glass.contentMuted,
                )
                options.forEachIndexed { index, option ->
                    SeatRow(
                        option = option,
                        focused = index == focusIndex,
                        onClick = { if (!option.taken) onPick(option.slot) },
                    )
                }
                Text(
                    text = "A choose · B keep current seat",
                    style = MaterialTheme.typography.labelSmall,
                    color = glass.contentMuted,
                )
            }
        }
    }
}

@Composable
private fun SeatRow(
    option: NetplaySeatOption,
    focused: Boolean,
    onClick: () -> Unit,
) {
    val shape = ArcadiaGlass.PillShape
    val enabled = !option.taken
    val contentAlpha = if (enabled) 1f else 0.35f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                when {
                    focused && enabled -> FocusSeat.copy(alpha = 0.22f)
                    else -> Color.White.copy(alpha = if (enabled) 0.08f else 0.03f)
                },
            )
            .border(
                width = 1.dp,
                color = if (focused && enabled) FocusSeat.copy(alpha = 0.8f) else Color.Transparent,
                shape = shape,
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = "Player ${option.slot}",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = Color.White.copy(alpha = contentAlpha),
        )
        Text(
            text = when {
                option.isCurrent -> "You"
                option.taken -> "Taken · ${option.takenBy}"
                else -> "Free"
            },
            style = MaterialTheme.typography.labelMedium,
            color = when {
                option.isCurrent -> FocusSeat
                option.taken -> Color.White.copy(alpha = 0.4f)
                else -> Color.White.copy(alpha = 0.75f)
            },
        )
    }
}
