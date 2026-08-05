package com.arcadia.shell.feature.home

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arcadia.shell.designsystem.ArcadiaGlass
import com.arcadia.shell.designsystem.GlassIntensity
import com.arcadia.shell.designsystem.GlassTone
import com.arcadia.shell.designsystem.liquidGlass
import com.arcadia.shell.designsystem.rememberGlassTokens
import com.arcadia.shell.input.NavAction
import com.arcadia.shell.model.Game
import com.arcadia.shell.scraper.ScraperPreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** One D-pad / stick step of travel through a sheet that is taller than the viewport. */
private val SCROLL_STEP = 96.dp

/**
 * Controller-friendly bottom sheet (Select on game select): scrape sources, favourite,
 * Choose Emulator for the current system, and re-scrape actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScrapeOptionsSheet(
    game: Game,
    gamePreference: ScraperPreference,
    platformPreference: ScraperPreference,
    currentEmulatorLabel: String?,
    navActions: Flow<NavAction>,
    onDismiss: () -> Unit,
    onToggleFavorite: (Boolean) -> Unit,
    onSetGamePreference: (ScraperPreference) -> Unit,
    onSetPlatformPreference: (ScraperPreference) -> Unit,
    onChooseEmulator: () -> Unit,
    onRescrapeGame: () -> Unit,
    onRescrapePlatform: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var localGamePref by remember(game.id) { mutableStateOf(gamePreference) }
    var localPlatformPref by remember(game.platformId) { mutableStateOf(platformPreference) }
    LaunchedEffect(gamePreference) { localGamePref = gamePreference }
    LaunchedEffect(platformPreference) { localPlatformPref = platformPreference }

    fun dismiss() {
        scope.launch {
            sheetState.hide()
            onDismiss()
        }
    }

    val glass = rememberGlassTokens(GlassTone.Surface)
    val scrollState = rememberScrollState()
    val density = LocalDensity.current

    // Controller keys never reach this sheet as focus events, so U/D arrive as NavActions and are
    // turned into scroll steps. Touch drag still works through the same scroll state.
    LaunchedEffect(navActions) {
        val step = with(density) { SCROLL_STEP.toPx() }
        navActions.collect { action ->
            when (action) {
                NavAction.Up -> scrollState.animateScrollBy(-step)
                NavAction.Down -> scrollState.animateScrollBy(step)
                NavAction.Cancel, NavAction.ScrapeMenu -> dismiss()
                else -> Unit
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color.Transparent,
        contentColor = glass.content,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .liquidGlass(
                    shape = ArcadiaGlass.SheetShape,
                    tone = GlassTone.Surface,
                    intensity = GlassIntensity.Strong,
                )
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp)
                .padding(top = 18.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Scrape & library",
                style = MaterialTheme.typography.titleLarge,
                color = glass.content,
            )
            Text(
                text = "${game.title} · ${game.platform.displayName}",
                style = MaterialTheme.typography.bodyMedium,
                color = glass.contentMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            FilterChip(
                selected = game.favorite,
                onClick = { onToggleFavorite(!game.favorite) },
                label = {
                    Text(if (game.favorite) "Favourited ★" else "Add to favourites")
                },
            )

            Text(
                text = "Scraper for this game",
                style = MaterialTheme.typography.titleSmall,
            )
            PreferenceChips(
                selected = localGamePref,
                onSelect = {
                    localGamePref = it
                    onSetGamePreference(it)
                },
            )

            Text(
                text = "Scraper for ${game.platform.shortName} (all games)",
                style = MaterialTheme.typography.titleSmall,
            )
            PreferenceChips(
                selected = localPlatformPref,
                onSelect = {
                    localPlatformPref = it
                    onSetPlatformPreference(it)
                },
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = "Emulator for ${game.platform.shortName}",
                style = MaterialTheme.typography.titleSmall,
            )
            TextButton(
                onClick = {
                    onChooseEmulator()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (currentEmulatorLabel.isNullOrBlank()) {
                        "Choose Emulator"
                    } else {
                        "Choose Emulator · $currentEmulatorLabel"
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            TextButton(
                onClick = {
                    onRescrapeGame()
                    dismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Re-scrape this game")
            }
            TextButton(
                onClick = {
                    onRescrapePlatform()
                    dismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Re-scrape all ${game.platform.shortName} games")
            }
            TextButton(onClick = ::dismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Close")
            }
            Text(
                text = "U/D · Scroll   B · Close",
                style = MaterialTheme.typography.labelMedium,
                color = glass.contentMuted,
            )
        }
    }
}

@Composable
private fun PreferenceChips(
    selected: ScraperPreference,
    onSelect: (ScraperPreference) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ScraperPreference.entries.forEach { preference ->
            FilterChip(
                selected = selected == preference,
                onClick = { onSelect(preference) },
                label = { Text(preference.label) },
            )
        }
    }
}
