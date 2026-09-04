package com.arcadia.shell.role

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arcadia.shell.designsystem.ArcadiaGlass
import com.arcadia.shell.designsystem.GlassIntensity
import com.arcadia.shell.designsystem.GlassTone
import com.arcadia.shell.designsystem.LiquidGlassSurface
import com.arcadia.shell.designsystem.XoraFonts
import com.arcadia.shell.designsystem.R as DsR

/**
 * Opt-in for replacing the device home screen, in two deliberate steps.
 *
 * Enabling the alias only puts SORA in the home-app chooser; the user still has to pick it in
 * system settings. Keeping those separate means a half-finished setup can never strand someone on a
 * launcher that cannot open their library.
 */
@Composable
fun HomeRoleCard(
    state: HomeRoleState,
    onSetHomeCandidate: (Boolean) -> Unit,
    onOpenHomeSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LiquidGlassSurface(
        modifier = modifier.fillMaxWidth(),
        shape = ArcadiaGlass.CardShape,
        tone = GlassTone.OverMedia,
        intensity = GlassIntensity.Standard,
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Image(
                    painter = painterResource(DsR.drawable.xmb_figma_device),
                    contentDescription = null,
                    modifier = Modifier.size(26.dp),
                )
                Text(
                    text = "Home screen",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontFamily = XoraFonts.XmbLabel,
                    ),
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                )
            }
            Text(
                text = when {
                    state.isDefaultHome ->
                        "XOrA is your home screen. Turning this off returns you to your " +
                            "previous launcher."
                    state.isHomeCandidate ->
                        "XOrA can be chosen as the home screen. Pick it in system settings to " +
                            "finish, or turn this off to hide it again."
                    else ->
                        "XOrA can take over as the home screen so the device boots straight " +
                            "into your library. It stays hidden from the launcher chooser until " +
                            "you enable it here."
                },
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = XoraFonts.Secondary,
                ),
                color = Color.White.copy(alpha = 0.72f),
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                FilterChip(
                    selected = state.isHomeCandidate,
                    onClick = { onSetHomeCandidate(!state.isHomeCandidate) },
                    label = { Text(text = "Offer as home screen") },
                )
                if (state.isHomeCandidate && !state.isDefaultHome) {
                    OutlinedButton(onClick = onOpenHomeSettings) {
                        Text(text = "Open home settings")
                    }
                }
            }
        }
    }
}
