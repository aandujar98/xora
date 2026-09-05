package com.arcadia.shell.feature.home

import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arcadia.shell.datastore.GAME_ART_ALIGN_STEP
import com.arcadia.shell.designsystem.ArcadiaGlass
import com.arcadia.shell.designsystem.GlassIntensity
import com.arcadia.shell.designsystem.GlassTone
import com.arcadia.shell.designsystem.liquidGlass
import com.arcadia.shell.designsystem.rememberGlassTokens
import com.arcadia.shell.input.NavAction
import com.arcadia.shell.libretro.GameSaveEntry
import com.arcadia.shell.libretro.GameSaveKind
import com.arcadia.shell.model.Game
import com.arcadia.shell.model.RomSoundBiteLocator
import com.arcadia.shell.scraper.ScraperPreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import java.util.Locale

/** One D-pad / stick step of travel through a sheet that is taller than the viewport. */
private val SCROLL_STEP = 96.dp

/**
 * Select on a focused ROM: customize box art / background / sound bite, inspect save files,
 * plus scrape / emulator library actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RomOptionsSheet(
    game: Game,
    saves: List<GameSaveEntry>,
    gamePreference: ScraperPreference,
    platformPreference: ScraperPreference,
    currentEmulatorLabel: String?,
    navActions: Flow<NavAction>,
    onDismiss: () -> Unit,
    onToggleFavorite: (Boolean) -> Unit,
    hidden: Boolean = false,
    onToggleHidden: (Boolean) -> Unit = {},
    artAlignX: Float = 0f,
    artAlignY: Float = 0f,
    onNudgeCover: (Float, Float) -> Unit = { _, _ -> },
    onResetCover: () -> Unit = {},
    onPickBoxArt: () -> Unit,
    onPickBackground: () -> Unit,
    onPickSoundBite: () -> Unit,
    onPickIdleVideo: () -> Unit,
    onClearBoxArt: () -> Unit,
    onClearBackground: () -> Unit,
    onClearSoundBite: () -> Unit,
    onClearIdleVideo: () -> Unit,
    onPreviewSoundBite: () -> Unit,
    idleVideoPath: String? = null,
    onImportSaves: () -> Unit,
    onDeleteSave: (GameSaveEntry) -> Unit,
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

    val batteryCount = saves.count { it.kind == GameSaveKind.Battery }
    val externalCount = saves.count { it.kind == GameSaveKind.External }
    val stateCount = saves.count {
        it.kind == GameSaveKind.State || it.kind == GameSaveKind.Autosave
    }
    val saveSummary = when {
        batteryCount > 0 && externalCount > 0 ->
            "Existing save detected · $batteryCount in XOrA, $externalCount ready to import"
        batteryCount > 0 ->
            "Existing save detected · $batteryCount battery file${if (batteryCount == 1) "" else "s"}"
        externalCount > 0 ->
            "Save detected outside XOrA · tap Import to copy"
        stateCount > 0 ->
            "Save states found · $stateCount file${if (stateCount == 1) "" else "s"}"
        else -> "No save files found for this ROM yet"
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
                text = "ROM options",
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

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = game.favorite,
                    onClick = { onToggleFavorite(!game.favorite) },
                    label = {
                        Text(if (game.favorite) "Favourited ★" else "Add to favourites")
                    },
                )
                FilterChip(
                    selected = hidden,
                    onClick = { onToggleHidden(!hidden) },
                    label = {
                        Text(if (hidden) "Hidden from library" else "Hide from library")
                    },
                )
            }

            SectionLabel("Customize")
            MediaRow(
                title = "Box art",
                status = pathStatus(game.boxArtPath),
                onChange = onPickBoxArt,
                onClear = onClearBoxArt.takeIf { !game.boxArtPath.isNullOrBlank() },
            )
            MediaRow(
                title = "Background",
                status = pathStatus(game.heroImagePath),
                onChange = onPickBackground,
                onClear = onClearBackground.takeIf { !game.heroImagePath.isNullOrBlank() },
            )
            MediaRow(
                title = "Sound bite",
                status = soundBiteStatus(game),
                onChange = onPickSoundBite,
                onClear = onClearSoundBite.takeIf {
                    RomSoundBiteLocator.resolve(game) != null
                },
                onExtra = onPreviewSoundBite.takeIf {
                    RomSoundBiteLocator.resolve(game) != null
                },
                extraLabel = "Preview",
                clearLabel = "Remove",
            )
            MediaRow(
                title = "Idle video",
                status = pathStatus(idleVideoPath),
                onChange = onPickIdleVideo,
                onClear = onClearIdleVideo.takeIf { !idleVideoPath.isNullOrBlank() },
            )

            SectionLabel("Cover position")
            Text(
                text = "Pan box art inside the Game Icon. Does not move the plate.",
                style = MaterialTheme.typography.bodySmall,
                color = glass.contentMuted,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { onNudgeCover(-GAME_ART_ALIGN_STEP, 0f) }) { Text("Left") }
                TextButton(onClick = { onNudgeCover(0f, -GAME_ART_ALIGN_STEP) }) { Text("Up") }
                TextButton(onClick = { onNudgeCover(0f, GAME_ART_ALIGN_STEP) }) { Text("Down") }
                TextButton(onClick = { onNudgeCover(GAME_ART_ALIGN_STEP, 0f) }) { Text("Right") }
                TextButton(onClick = onResetCover) { Text("Reset") }
            }
            if (artAlignX != 0f || artAlignY != 0f) {
                Text(
                    text = "Offset ${"%.2f".format(Locale.US, artAlignX)}, ${"%.2f".format(Locale.US, artAlignY)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = glass.contentMuted,
                )
            }

            SectionLabel("Save files")
            Text(
                text = saveSummary,
                style = MaterialTheme.typography.bodyMedium,
                color = if (saves.isNotEmpty()) glass.content else glass.contentMuted,
            )
            TextButton(
                onClick = onImportSaves,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (externalCount > 0) {
                        "Import detected saves ($externalCount)"
                    } else {
                        "Scan & import RetroArch saves"
                    },
                )
            }
            if (saves.isEmpty()) {
                Text(
                    text = "Play once to create a battery save, or import from RetroArch / beside the ROM.",
                    style = MaterialTheme.typography.bodySmall,
                    color = glass.contentMuted,
                )
            } else {
                saves.forEach { entry ->
                    SaveRow(
                        entry = entry,
                        muted = glass.contentMuted,
                        onDelete = if (entry.kind != GameSaveKind.External) {
                            { onDeleteSave(entry) }
                        } else {
                            null
                        },
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            SectionLabel("Library")

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

            Text(
                text = "Emulator for ${game.platform.shortName}",
                style = MaterialTheme.typography.titleSmall,
            )
            TextButton(
                onClick = onChooseEmulator,
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
                text = "U/D · Scroll   B / Select · Close",
                style = MaterialTheme.typography.labelMedium,
                color = glass.contentMuted,
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun MediaRow(
    title: String,
    status: String,
    onChange: () -> Unit,
    onClear: (() -> Unit)?,
    onExtra: (() -> Unit)? = null,
    extraLabel: String = "",
    clearLabel: String = "Clear",
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleSmall)
        Text(
            text = status,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onChange) { Text("Change") }
            if (onExtra != null) {
                TextButton(onClick = onExtra) { Text(extraLabel) }
            }
            if (onClear != null) {
                TextButton(onClick = onClear) { Text(clearLabel) }
            }
        }
    }
}

@Composable
private fun SaveRow(
    entry: GameSaveEntry,
    muted: Color,
    onDelete: (() -> Unit)?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatSaveMeta(entry),
                style = MaterialTheme.typography.labelMedium,
                color = muted,
            )
        }
        if (onDelete != null) {
            TextButton(onClick = onDelete) { Text("Delete") }
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

private fun pathStatus(path: String?): String =
    if (path.isNullOrBlank()) "Not set" else path.substringAfterLast('/')

private fun soundBiteStatus(game: Game): String {
    if (!game.soundBitePath.isNullOrBlank()) return pathStatus(game.soundBitePath)
    val sidecar = RomSoundBiteLocator.resolve(game) ?: return "Not set — drop Game name.mp3 in the ROMs folder"
    return "ROMs folder · ${sidecar.substringAfterLast('/')}"
}

private fun formatSaveMeta(entry: GameSaveEntry): String {
    val size = formatBytes(entry.sizeBytes)
    val kind = when (entry.kind) {
        GameSaveKind.Battery -> "Battery"
        GameSaveKind.State -> "State"
        GameSaveKind.Autosave -> "Autosave"
        GameSaveKind.External -> "External"
    }
    return "$kind · $size"
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
}

/**
 * Backward-compatible alias used by older call sites / dual-display hosts.
 */
@Deprecated("Use RomOptionsSheet", ReplaceWith("RomOptionsSheet(...)"))
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
    RomOptionsSheet(
        game = game,
        saves = emptyList(),
        gamePreference = gamePreference,
        platformPreference = platformPreference,
        currentEmulatorLabel = currentEmulatorLabel,
        navActions = navActions,
        onDismiss = onDismiss,
        onToggleFavorite = onToggleFavorite,
        onPickBoxArt = {},
        onPickBackground = {},
        onPickSoundBite = {},
        onPickIdleVideo = {},
        onClearBoxArt = {},
        onClearBackground = {},
        onClearSoundBite = {},
        onClearIdleVideo = {},
        onPreviewSoundBite = {},
        onImportSaves = {},
        onDeleteSave = {},
        onSetGamePreference = onSetGamePreference,
        onSetPlatformPreference = onSetPlatformPreference,
        onChooseEmulator = onChooseEmulator,
        onRescrapeGame = onRescrapeGame,
        onRescrapePlatform = onRescrapePlatform,
    )
}
