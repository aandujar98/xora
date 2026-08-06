package com.arcadia.shell.feature.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arcadia.shell.designsystem.ArcadiaGlass
import com.arcadia.shell.designsystem.GlassTone
import com.arcadia.shell.designsystem.rememberGlassTokens
import com.arcadia.shell.feature.home.preview.XoraPreview
import com.arcadia.shell.feature.home.preview.XoraPreviewTheme
import com.arcadia.shell.feature.home.preview.previewGame
import com.arcadia.shell.model.Game
import com.arcadia.shell.model.LaunchDisplayPreference
import com.arcadia.shell.model.Player

/**
 * Per-game overrides, reachable with X from the grid.
 *
 * The display target lives here rather than only in global settings because the right answer is
 * genuinely per-game on a dual-screen handheld: a DS title wants both panels, while a PSP title
 * belongs on whichever single screen is larger.
 */
@Composable
fun GameOptionsDialog(
    game: Game,
    isDualScreen: Boolean,
    loadPlayers: suspend (String) -> List<Player>,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onToggleFavorite: (Boolean) -> Unit,
    onSelectPlayer: (String?) -> Unit,
    onSelectDisplay: (LaunchDisplayPreference) -> Unit,
) {
    val players by produceState(initialValue = emptyList<Player>(), game.platformId) {
        value = loadPlayers(game.platformId)
    }

    val glass = rememberGlassTokens(GlassTone.Surface)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = glass.tintStrong,
        shape = ArcadiaGlass.CardShape,
        titleContentColor = glass.content,
        textContentColor = glass.content,
        title = {
            Text(
                text = game.title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = if (game.isAndroidApp) {
                        game.fileName
                    } else {
                        "${game.platform.displayName} · ${game.fileName}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = glass.contentMuted,
                )

                FilterChip(
                    selected = game.favorite,
                    onClick = { onToggleFavorite(!game.favorite) },
                    label = { Text(text = if (game.favorite) "Favourited" else "Add to favourites") },
                )

                if (!game.isAndroidApp && players.isNotEmpty()) {
                    Text(text = "Open with", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = game.playerIdOverride == null,
                            onClick = { onSelectPlayer(null) },
                            label = { Text(text = "Automatic") },
                        )
                        players.forEach { player ->
                            FilterChip(
                                selected = game.playerIdOverride == player.uniqueId,
                                onClick = { onSelectPlayer(player.uniqueId) },
                                label = { Text(text = player.name) },
                            )
                        }
                    }
                }

                if (isDualScreen) {
                    Text(text = "Launch on", style = MaterialTheme.typography.titleSmall)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LaunchDisplayPreference.entries.forEach { preference ->
                            FilterChip(
                                selected = game.launchDisplayPreference == preference,
                                onClick = { onSelectDisplay(preference) },
                                label = {
                                    Text(
                                        text = when (preference) {
                                            LaunchDisplayPreference.Inherit -> "System default"
                                            LaunchDisplayPreference.GridScreen -> "This screen"
                                            LaunchDisplayPreference.OtherScreen -> "Other screen"
                                        },
                                    )
                                },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onPlay) {
                Text(text = if (game.isAndroidApp) "Open" else "Play")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(text = "Close") }
        },
    )
}

@XoraPreview
@Composable
private fun GameOptionsDialogPreview() {
    XoraPreviewTheme {
        GameOptionsDialog(
            game = previewGame(favorite = true),
            isDualScreen = true,
            loadPlayers = { emptyList() },
            onDismiss = {},
            onPlay = {},
            onToggleFavorite = {},
            onSelectPlayer = {},
            onSelectDisplay = {},
        )
    }
}
