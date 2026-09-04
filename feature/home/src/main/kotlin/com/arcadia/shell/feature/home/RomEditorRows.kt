package com.arcadia.shell.feature.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.arcadia.shell.datastore.GAME_ART_ALIGN_STEP
import com.arcadia.shell.libretro.GameSaveEntry
import com.arcadia.shell.libretro.GameSaveKind
import com.arcadia.shell.model.Game
import com.arcadia.shell.model.RomSoundBiteLocator
import com.arcadia.shell.model.TrailerRef
import com.arcadia.shell.scraper.ArtSlot
import com.arcadia.shell.scraper.ScraperPreference
import java.util.Locale

/**
 * Builds the rows for one section.
 *
 * Split out from the pane so the pane only ever deals in "a list of rows" — every screenful of this
 * editor is the same widget reading a different list, which is what lets one focus model drive all
 * of it.
 */
@Composable
internal fun rememberEditorRows(
    section: RomEditorSection,
    game: Game,
    customTitle: String?,
    saves: List<GameSaveEntry>,
    hidden: Boolean,
    trailer: TrailerRef?,
    trailerResolving: Boolean,
    gamePreference: ScraperPreference,
    platformPreference: ScraperPreference,
    currentEmulatorLabel: String?,
    artAlignX: Float,
    artAlignY: Float,
    onStartRename: () -> Unit,
    onOpenArtPicker: (ArtSlot) -> Unit,
    actions: RomEditorActions,
): List<RomEditorRow> = remember(
    section,
    game,
    customTitle,
    saves,
    hidden,
    trailer,
    trailerResolving,
    gamePreference,
    platformPreference,
    currentEmulatorLabel,
    artAlignX,
    artAlignY,
) {
    when (section) {
        RomEditorSection.Details -> detailRows(game, customTitle, hidden, onStartRename, actions)
        RomEditorSection.Artwork ->
            artworkRows(game, artAlignX, artAlignY, onOpenArtPicker, actions)
        RomEditorSection.Audio -> audioRows(game, actions)
        RomEditorSection.Video -> videoRows(trailer, trailerResolving, actions)
        RomEditorSection.Saves -> saveRows(saves, actions)
        RomEditorSection.Library -> libraryRows(
            game = game,
            gamePreference = gamePreference,
            platformPreference = platformPreference,
            currentEmulatorLabel = currentEmulatorLabel,
            actions = actions,
        )
    }
}

private fun detailRows(
    game: Game,
    customTitle: String?,
    hidden: Boolean,
    onStartRename: () -> Unit,
    actions: RomEditorActions,
): List<RomEditorRow> = buildList {
    add(
        RomEditorRow(
            key = "name",
            label = "Name",
            value = customTitle ?: game.title,
            hint = if (customTitle != null) "Your name · X restores the scanned one" else null,
            onActivate = onStartRename,
            onClear = actions.onResetName.takeIf { customTitle != null },
        ),
    )
    add(
        RomEditorRow(
            key = "favorite",
            label = "Favourite",
            value = if (game.favorite) "Yes" else "No",
            onActivate = { actions.onToggleFavorite(!game.favorite) },
            onAdjust = { actions.onToggleFavorite(!game.favorite) },
        ),
    )
    add(
        RomEditorRow(
            key = "hidden",
            label = "Hide from library",
            value = if (hidden) "Hidden" else "Visible",
            hint = "Hidden titles stay installed and keep their saves.",
            onActivate = { actions.onToggleHidden(!hidden) },
            onAdjust = { actions.onToggleHidden(!hidden) },
        ),
    )
    add(RomEditorRow(key = "platform", label = "System", value = game.platform.displayName))
    add(
        RomEditorRow(
            key = "file",
            label = "File",
            value = game.fileName,
            hint = game.filePath ?: game.documentUri,
        ),
    )
}

private fun artworkRows(
    game: Game,
    artAlignX: Float,
    artAlignY: Float,
    onOpenArtPicker: (ArtSlot) -> Unit,
    actions: RomEditorActions,
): List<RomEditorRow> = buildList {
    add(
        RomEditorRow(
            key = "boxart",
            label = "Box art",
            value = mediaStatus(game.boxArtPath),
            hint = "Browse every scraper, or use your own image.",
            onActivate = { onOpenArtPicker(ArtSlot.BoxArt) },
            onClear = { actions.onClearArt(ArtSlot.BoxArt) }.takeIf {
                !game.boxArtPath.isNullOrBlank()
            },
        ),
    )
    add(
        RomEditorRow(
            key = "hero",
            label = "Background",
            value = mediaStatus(game.heroImagePath),
            hint = "Hero art behind the title on Home.",
            onActivate = { onOpenArtPicker(ArtSlot.Hero) },
            onClear = { actions.onClearArt(ArtSlot.Hero) }.takeIf {
                !game.heroImagePath.isNullOrBlank()
            },
        ),
    )
    add(
        RomEditorRow(
            key = "logo",
            label = "Logo",
            value = mediaStatus(game.logoImagePath),
            onActivate = { onOpenArtPicker(ArtSlot.Logo) },
            onClear = { actions.onClearArt(ArtSlot.Logo) }.takeIf {
                !game.logoImagePath.isNullOrBlank()
            },
        ),
    )
    add(
        RomEditorRow(
            key = "coverx",
            label = "Cover pan · horizontal",
            value = String.format(Locale.US, "%.2f", artAlignX),
            hint = "Moves the box art inside the Game Icon, not the plate.",
            onAdjust = { direction ->
                actions.onNudgeCover(GAME_ART_ALIGN_STEP * direction, 0f)
            },
        ),
    )
    add(
        RomEditorRow(
            key = "covery",
            label = "Cover pan · vertical",
            value = String.format(Locale.US, "%.2f", artAlignY),
            onAdjust = { direction ->
                actions.onNudgeCover(0f, GAME_ART_ALIGN_STEP * direction)
            },
        ),
    )
    if (artAlignX != 0f || artAlignY != 0f) {
        add(
            RomEditorRow(
                key = "coverreset",
                label = "Reset cover position",
                onActivate = actions.onResetCover,
            ),
        )
    }
}

private fun audioRows(game: Game, actions: RomEditorActions): List<RomEditorRow> = buildList {
    val resolved = RomSoundBiteLocator.resolve(game)
    add(
        RomEditorRow(
            key = "bite",
            label = "Sound bite",
            value = when {
                !game.soundBitePath.isNullOrBlank() -> "Your clip"
                resolved != null -> "Found beside ROM"
                else -> "None"
            },
            hint = "Plays under the title while you browse. Drops in as " +
                "\"${game.fileName.substringBeforeLast('.')}.mp3\" beside the ROM too.",
            onActivate = actions.onPickSoundBite,
            onClear = actions.onClearSoundBite.takeIf { !game.soundBitePath.isNullOrBlank() },
        ),
    )
    if (resolved != null) {
        add(
            RomEditorRow(
                key = "preview",
                label = "Preview sound bite",
                onActivate = actions.onPreviewSoundBite,
            ),
        )
    }
}

private fun videoRows(
    trailer: TrailerRef?,
    resolving: Boolean,
    actions: RomEditorActions,
): List<RomEditorRow> = buildList {
    add(
        RomEditorRow(
            key = "trailer",
            label = "Trailer",
            value = when {
                resolving -> "Searching…"
                trailer is TrailerRef.Direct -> "Your video"
                trailer is TrailerRef.YouTube -> "YouTube"
                else -> "None"
            },
            hint = "Plays on the hero while the title sits focused.",
            onActivate = actions.onUploadTrailer,
            onClear = actions.onClearTrailer.takeIf { trailer != null },
        ),
    )
    add(
        RomEditorRow(
            key = "trailerupload",
            label = "Upload my own video",
            hint = "Any local mp4 / webm / mkv. Overrides YouTube for this title.",
            onActivate = actions.onUploadTrailer,
        ),
    )
    add(
        RomEditorRow(
            key = "traileryt",
            label = "Use YouTube instead",
            hint = "Looks the trailer up and plays it from YouTube.",
            onActivate = actions.onUseYouTubeTrailer,
        ),
    )
}

private fun saveRows(
    saves: List<GameSaveEntry>,
    actions: RomEditorActions,
): List<RomEditorRow> = buildList {
    val external = saves.count { it.kind == GameSaveKind.External }
    val battery = saves.count { it.kind == GameSaveKind.Battery }
    val states = saves.count { it.kind == GameSaveKind.State || it.kind == GameSaveKind.Autosave }
    add(
        RomEditorRow(
            key = "savesummary",
            label = "Save files",
            value = "${saves.size}",
            hint = when {
                battery > 0 && external > 0 ->
                    "$battery in XOrA, $external ready to import"
                battery > 0 -> "$battery battery file${if (battery == 1) "" else "s"}"
                external > 0 -> "Found outside XOrA — import to use them"
                states > 0 -> "$states save state${if (states == 1) "" else "s"}"
                else -> "Play once to create a battery save, or import from RetroArch."
            },
        ),
    )
    add(
        RomEditorRow(
            key = "import",
            label = if (external > 0) "Import detected saves ($external)" else "Scan for saves",
            onActivate = actions.onImportSaves,
        ),
    )
    saves.forEach { entry ->
        add(
            RomEditorRow(
                key = "save_${entry.path}",
                label = entry.fileName,
                value = entry.label,
                hint = formatSize(entry.sizeBytes),
                onClear = { actions.onDeleteSave(entry) }.takeIf {
                    entry.kind != GameSaveKind.External
                },
                destructive = false,
            ),
        )
    }
}

private fun libraryRows(
    game: Game,
    gamePreference: ScraperPreference,
    platformPreference: ScraperPreference,
    currentEmulatorLabel: String?,
    actions: RomEditorActions,
): List<RomEditorRow> = buildList {
    val options = ScraperPreference.entries
    add(
        RomEditorRow(
            key = "scrapergame",
            label = "Scraper for this game",
            value = gamePreference.label,
            onAdjust = { direction ->
                val next = options[(options.indexOf(gamePreference) + direction + options.size) %
                    options.size]
                actions.onSetGamePreference(next)
            },
        ),
    )
    add(
        RomEditorRow(
            key = "scraperplatform",
            label = "Scraper for ${game.platform.shortName}",
            hint = "Applies to every game on this system.",
            value = platformPreference.label,
            onAdjust = { direction ->
                val next = options[(options.indexOf(platformPreference) + direction +
                    options.size) % options.size]
                actions.onSetPlatformPreference(next)
            },
        ),
    )
    add(
        RomEditorRow(
            key = "emulator",
            label = "Emulator for ${game.platform.shortName}",
            value = currentEmulatorLabel?.takeIf { it.isNotBlank() } ?: "Default",
            onActivate = actions.onChooseEmulator,
        ),
    )
    add(
        RomEditorRow(
            key = "rescrapegame",
            label = "Re-scrape this game",
            hint = "Keeps a name you typed yourself.",
            onActivate = actions.onRescrapeGame,
        ),
    )
    add(
        RomEditorRow(
            key = "rescrapeplatform",
            label = "Re-scrape all ${game.platform.shortName} games",
            onActivate = actions.onRescrapePlatform,
            destructive = true,
        ),
    )
}

private fun mediaStatus(path: String?): String = when {
    path.isNullOrBlank() -> "None"
    else -> "Set"
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes <= 0L -> "empty"
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> "${bytes / 1024L} KB"
        else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
    }
}
