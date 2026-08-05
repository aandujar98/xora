package com.arcadia.shell.role

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Home screen",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
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
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
