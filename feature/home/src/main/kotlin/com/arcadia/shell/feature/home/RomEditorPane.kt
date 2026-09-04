package com.arcadia.shell.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arcadia.shell.designsystem.ArcadiaGlass
import com.arcadia.shell.designsystem.GlassIntensity
import com.arcadia.shell.designsystem.GlassTone
import com.arcadia.shell.designsystem.liquidGlass
import com.arcadia.shell.designsystem.rememberGlassTokens
import com.arcadia.shell.feature.home.component.ArtworkImage
import com.arcadia.shell.feature.home.component.ButtonHintBar
import com.arcadia.shell.input.NavAction
import com.arcadia.shell.libretro.GameSaveEntry
import com.arcadia.shell.model.Game
import com.arcadia.shell.model.TrailerRef
import com.arcadia.shell.scraper.ArtCandidate
import com.arcadia.shell.scraper.ArtCandidateResult
import com.arcadia.shell.scraper.ArtSlot
import com.arcadia.shell.scraper.ScraperPreference
import kotlinx.coroutines.flow.Flow

/** Sections down the left rail. Order is the order they are read in. */
enum class RomEditorSection(val label: String) {
    Details("Details"),
    Artwork("Artwork"),
    Audio("Audio"),
    Video("Video"),
    Saves("Saves"),
    Library("Library"),
}

/**
 * One focusable line in the right-hand column.
 *
 * A row is a value, not a widget: the pane renders every row the same way and the section builders
 * below only decide what a row *is*. That is what keeps a screen with this much on it navigable —
 * every line answers to the same four buttons.
 */
data class RomEditorRow(
    val key: String,
    val label: String,
    val value: String? = null,
    val hint: String? = null,
    /** A: the primary action. A row without one is a read-only status line. */
    val onActivate: (() -> Unit)? = null,
    /** Left / Right: cycle a value in place, so a choice never costs a submenu. */
    val onAdjust: ((Int) -> Unit)? = null,
    /** X: remove whatever this row is currently holding. */
    val onClear: (() -> Unit)? = null,
    val destructive: Boolean = false,
)

/** Everything the pane can ask the shell to do. Grouped so the call site stays readable. */
data class RomEditorActions(
    val onDismiss: () -> Unit,
    val onRename: (String) -> Unit,
    val onResetName: () -> Unit,
    val onToggleFavorite: (Boolean) -> Unit,
    val onToggleHidden: (Boolean) -> Unit,
    /** Opens the system file picker so the user can supply their own image. */
    val onUploadArt: (ArtSlot) -> Unit,
    val onApplyCandidate: (ArtSlot, ArtCandidate) -> Unit,
    val onClearArt: (ArtSlot) -> Unit,
    val onNudgeCover: (Float, Float) -> Unit,
    val onResetCover: () -> Unit,
    val onPickSoundBite: () -> Unit,
    val onClearSoundBite: () -> Unit,
    val onPreviewSoundBite: () -> Unit,
    val onUploadTrailer: () -> Unit,
    val onUseYouTubeTrailer: () -> Unit,
    val onClearTrailer: () -> Unit,
    val onImportSaves: () -> Unit,
    val onDeleteSave: (GameSaveEntry) -> Unit,
    val onSetGamePreference: (ScraperPreference) -> Unit,
    val onSetPlatformPreference: (ScraperPreference) -> Unit,
    val onChooseEmulator: () -> Unit,
    val onRescrapeGame: () -> Unit,
    val onRescrapePlatform: () -> Unit,
)

/** Which column currently owns the D-pad. */
private enum class EditorColumn { Rail, Rows }

private sealed interface EditorMode {
    data object Browse : EditorMode
    data object Rename : EditorMode
    data class ArtPicker(val slot: ArtSlot) : EditorMode
}

private val RAIL_WIDTH = 208.dp
private val ART_COLUMNS = 4

/**
 * Full-screen ROM editor: rename, artwork, sound bite, trailer, saves and library options.
 *
 * Replaces the scrolling bottom sheet this used to be. That sheet could only be *scrolled* with a
 * controller — nothing on it was focusable, so reaching a button meant scrolling until it was under
 * a thumb and hoping. Here the D-pad moves a real selection: Up/Down within a column, Left/Right
 * between the rail and the rows (and to cycle a value in place), A to act, B to back out.
 */
@Composable
fun RomEditorPane(
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
    artPicker: ArtPickerUiState,
    navActions: Flow<NavAction>,
    actions: RomEditorActions,
    /** Tells the host which slot the picker is showing, so it can run the lookup. */
    onArtPickerSlotChange: (ArtSlot?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val glass = rememberGlassTokens(GlassTone.Surface)

    var mode by remember { mutableStateOf<EditorMode>(EditorMode.Browse) }
    var column by remember { mutableStateOf(EditorColumn.Rail) }
    var sectionIndex by remember { mutableIntStateOf(0) }
    var rowIndex by remember { mutableIntStateOf(0) }
    var artIndex by remember { mutableIntStateOf(0) }
    var renameDraft by remember(game.id) { mutableStateOf(customTitle ?: game.title) }

    val sections = RomEditorSection.entries
    val section = sections[sectionIndex.coerceIn(0, sections.lastIndex)]
    val rows = rememberEditorRows(
        section = section,
        game = game,
        customTitle = customTitle,
        saves = saves,
        hidden = hidden,
        trailer = trailer,
        trailerResolving = trailerResolving,
        gamePreference = gamePreference,
        platformPreference = platformPreference,
        currentEmulatorLabel = currentEmulatorLabel,
        artAlignX = artAlignX,
        artAlignY = artAlignY,
        onStartRename = {
            renameDraft = customTitle ?: game.title
            mode = EditorMode.Rename
        },
        onOpenArtPicker = { slot ->
            artIndex = 0
            mode = EditorMode.ArtPicker(slot)
            onArtPickerSlotChange(slot)
        },
        actions = actions,
    )

    // A section with fewer rows than the last must not leave the selection past the end.
    LaunchedEffect(section, rows.size) {
        if (rowIndex > rows.lastIndex) rowIndex = rows.lastIndex.coerceAtLeast(0)
    }

    val railState = rememberLazyListState()
    val rowState = rememberLazyListState()
    LaunchedEffect(sectionIndex) { railState.animateScrollToItem(sectionIndex) }
    LaunchedEffect(rowIndex, section) {
        if (rows.isNotEmpty()) rowState.animateScrollToItem(rowIndex.coerceIn(0, rows.lastIndex))
    }

    val pickerCandidates = (mode as? EditorMode.ArtPicker)
        ?.let { artPicker.result?.forSlot(it.slot).orEmpty() }
        .orEmpty()

    LaunchedEffect(navActions, mode, section, rows, pickerCandidates) {
        navActions.collect { action ->
            when (val current = mode) {
                EditorMode.Rename -> when (action) {
                    // Everything else belongs to the keyboard while a name is being typed.
                    NavAction.Cancel -> mode = EditorMode.Browse
                    NavAction.Confirm -> {
                        actions.onRename(renameDraft)
                        mode = EditorMode.Browse
                    }
                    else -> Unit
                }

                is EditorMode.ArtPicker -> when (action) {
                    NavAction.Cancel, NavAction.ScrapeMenu -> {
                        mode = EditorMode.Browse
                        onArtPickerSlotChange(null)
                    }
                    NavAction.Left -> if (artIndex > 0) artIndex--
                    NavAction.Right -> if (artIndex < pickerCandidates.lastIndex) artIndex++
                    NavAction.Up -> artIndex = (artIndex - ART_COLUMNS).coerceAtLeast(0)
                    NavAction.Down -> artIndex =
                        (artIndex + ART_COLUMNS).coerceAtMost(pickerCandidates.lastIndex.coerceAtLeast(0))
                    NavAction.Confirm -> {
                        pickerCandidates.getOrNull(artIndex)?.let { candidate ->
                            actions.onApplyCandidate(current.slot, candidate)
                            mode = EditorMode.Browse
                            onArtPickerSlotChange(null)
                        }
                    }
                    // Y on the picker is the escape hatch to your own file.
                    NavAction.ToggleFavorite -> {
                        actions.onUploadArt(current.slot)
                        mode = EditorMode.Browse
                        onArtPickerSlotChange(null)
                    }
                    else -> Unit
                }

                EditorMode.Browse -> when (action) {
                    NavAction.Up -> if (column == EditorColumn.Rail) {
                        sectionIndex = (sectionIndex - 1 + sections.size) % sections.size
                        rowIndex = 0
                    } else if (rows.isNotEmpty()) {
                        rowIndex = (rowIndex - 1 + rows.size) % rows.size
                    }

                    NavAction.Down -> if (column == EditorColumn.Rail) {
                        sectionIndex = (sectionIndex + 1) % sections.size
                        rowIndex = 0
                    } else if (rows.isNotEmpty()) {
                        rowIndex = (rowIndex + 1) % rows.size
                    }

                    NavAction.Right -> if (column == EditorColumn.Rail) {
                        if (rows.isNotEmpty()) column = EditorColumn.Rows
                    } else {
                        rows.getOrNull(rowIndex)?.onAdjust?.invoke(1)
                    }

                    NavAction.Left -> if (column == EditorColumn.Rows) {
                        val adjust = rows.getOrNull(rowIndex)?.onAdjust
                        if (adjust != null) adjust(-1) else column = EditorColumn.Rail
                    }

                    NavAction.Confirm -> if (column == EditorColumn.Rail) {
                        if (rows.isNotEmpty()) column = EditorColumn.Rows
                    } else {
                        rows.getOrNull(rowIndex)?.onActivate?.invoke()
                    }

                    NavAction.Cancel -> if (column == EditorColumn.Rows) {
                        column = EditorColumn.Rail
                    } else {
                        actions.onDismiss()
                    }

                    // Select closes the editor the same way it opened it.
                    NavAction.ScrapeMenu -> actions.onDismiss()

                    NavAction.Options -> if (column == EditorColumn.Rows) {
                        rows.getOrNull(rowIndex)?.onClear?.invoke()
                    }

                    // Shoulders jump whole sections without walking the rail.
                    NavAction.PreviousPlatform -> {
                        sectionIndex = (sectionIndex - 1 + sections.size) % sections.size
                        rowIndex = 0
                    }
                    NavAction.NextPlatform -> {
                        sectionIndex = (sectionIndex + 1) % sections.size
                        rowIndex = 0
                    }

                    else -> Unit
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xF20A0C12)),
    ) {
        Column(modifier = Modifier.fillMaxSize().padding(28.dp)) {
            RomEditorHeader(game = game, customTitle = customTitle)
            Spacer(modifier = Modifier.height(18.dp))

            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                LazyColumn(
                    state = railState,
                    modifier = Modifier
                        .width(RAIL_WIDTH)
                        .fillMaxHeight()
                        .liquidGlass(
                            shape = ArcadiaGlass.SheetShape,
                            tone = GlassTone.Surface,
                            intensity = GlassIntensity.Subtle,
                        )
                        .padding(vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    itemsIndexed(sections, key = { _, it -> it.name }) { index, entry ->
                        RailRow(
                            label = entry.label,
                            selected = index == sectionIndex,
                            active = column == EditorColumn.Rail && index == sectionIndex,
                        )
                    }
                }

                Spacer(modifier = Modifier.width(18.dp))

                Box(modifier = Modifier.fillMaxHeight().weight(1f)) {
                    LazyColumn(
                        state = rowState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        itemsIndexed(rows, key = { _, it -> it.key }) { index, row ->
                            EditorRowItem(
                                row = row,
                                active = column == EditorColumn.Rows && index == rowIndex,
                            )
                        }
                    }
                    if (rows.isEmpty()) {
                        Text(
                            text = "Nothing to edit here yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = glass.contentMuted,
                            modifier = Modifier.align(Alignment.Center),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            ButtonHintBar(
                hints = editorHints(mode, column, rows.getOrNull(rowIndex)),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }

        AnimatedVisibility(visible = mode is EditorMode.ArtPicker, enter = fadeIn(), exit = fadeOut()) {
            (mode as? EditorMode.ArtPicker)?.let { picker ->
                ArtPickerOverlay(
                    slot = picker.slot,
                    state = artPicker,
                    candidates = pickerCandidates,
                    focusedIndex = artIndex,
                )
            }
        }

        AnimatedVisibility(visible = mode is EditorMode.Rename, enter = fadeIn(), exit = fadeOut()) {
            RenameOverlay(
                draft = renameDraft,
                original = game.title,
                onDraftChange = { renameDraft = it },
            )
        }
    }
}

/** Remote candidates for one slot, plus whether the lookup is still running. */
data class ArtPickerUiState(
    val loading: Boolean = false,
    val result: ArtCandidateResult? = null,
)

@Composable
private fun RomEditorHeader(game: Game, customTitle: String?) {
    val glass = rememberGlassTokens(GlassTone.Surface)
    Row(verticalAlignment = Alignment.CenterVertically) {
        ArtworkImage(
            path = game.gridArt,
            contentDescription = null,
            fallbackText = game.title.take(2).uppercase(),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 68.dp, height = 92.dp)
                .clip(RoundedCornerShape(8.dp)),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = customTitle ?: game.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = glass.content,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    append(game.platform.displayName)
                    append(" · ")
                    append(formatXmbPlaytime(game.playTimeMs))
                    if (game.playCount > 0) append(" · ${game.playCount} plays")
                },
                style = MaterialTheme.typography.bodyMedium,
                color = glass.contentMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (customTitle != null) {
                Text(
                    text = "Renamed from ${game.title}",
                    style = MaterialTheme.typography.labelMedium,
                    color = glass.contentMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun RailRow(label: String, selected: Boolean, active: Boolean) {
    val glass = rememberGlassTokens(GlassTone.Surface)
    val background = when {
        active -> MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
        selected -> Color.White.copy(alpha = 0.08f)
        else -> Color.Transparent
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) glass.content else glass.contentMuted,
        )
    }
}

@Composable
private fun EditorRowItem(row: RomEditorRow, active: Boolean) {
    val glass = rememberGlassTokens(GlassTone.Surface)
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (active) accent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.04f))
            .border(
                width = if (active) 2.dp else 0.dp,
                color = if (active) accent.copy(alpha = 0.9f) else Color.Transparent,
                shape = RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.label,
                style = MaterialTheme.typography.titleSmall,
                color = if (row.destructive) {
                    MaterialTheme.colorScheme.error
                } else {
                    glass.content
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            row.hint?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = glass.contentMuted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        row.value?.let {
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = if (row.onAdjust != null) "‹ $it ›" else it,
                style = MaterialTheme.typography.bodyMedium,
                color = if (active) glass.content else glass.contentMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ArtPickerOverlay(
    slot: ArtSlot,
    state: ArtPickerUiState,
    candidates: List<ArtCandidate>,
    focusedIndex: Int,
) {
    val glass = rememberGlassTokens(GlassTone.Surface)
    val gridState = rememberLazyListState()
    val rowOfFocus = if (candidates.isEmpty()) 0 else focusedIndex / ART_COLUMNS
    LaunchedEffect(rowOfFocus) { gridState.animateScrollToItem(rowOfFocus) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF5070910))
            .padding(32.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "Choose ${slot.label.lowercase()}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = glass.content,
            )
            Text(
                text = when {
                    state.loading -> "Searching every configured scraper…"
                    state.result?.credentialsMissing == true ->
                        "No scraper is set up yet. Add a key in Settings, or press Y to use your own image."
                    candidates.isEmpty() -> "No artwork came back. Press Y to use your own image."
                    else -> "${candidates.size} results · Y for your own file"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = glass.contentMuted,
            )
            state.result?.emptySources?.takeIf { it.isNotEmpty() }?.let { empty ->
                Text(
                    text = "Nothing from ${empty.joinToString { it.name }}",
                    style = MaterialTheme.typography.labelMedium,
                    color = glass.contentMuted,
                )
            }
            Spacer(modifier = Modifier.height(16.dp))

            // Chunked into explicit rows rather than a LazyVerticalGrid: the focus model already
            // works in fixed columns, so the two cannot disagree about where an index lives.
            LazyColumn(
                state = gridState,
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(candidates.chunked(ART_COLUMNS)) { rowItems ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        rowItems.forEach { candidate ->
                            val index = candidates.indexOf(candidate)
                            ArtCandidateTile(
                                candidate = candidate,
                                slot = slot,
                                active = index == focusedIndex,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        // Keeps a short final row the same tile width as a full one.
                        repeat(ART_COLUMNS - rowItems.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            ButtonHintBar(
                hints = listOf(
                    "D-pad" to "Browse",
                    "A" to "Use this",
                    "Y" to "My own file",
                    "B" to "Back",
                ),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

@Composable
private fun ArtCandidateTile(
    candidate: ArtCandidate,
    slot: ArtSlot,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val glass = rememberGlassTokens(GlassTone.Surface)
    val accent = MaterialTheme.colorScheme.primary
    Column(modifier = modifier) {
        ArtworkImage(
            path = candidate.url,
            contentDescription = candidate.matchedTitle,
            fallbackText = candidate.sourceLabel,
            contentScale = ContentScale.Crop,
            cacheInMemory = true,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(if (slot == ArtSlot.BoxArt) 0.72f else 1.6f)
                .clip(RoundedCornerShape(10.dp))
                .border(
                    width = if (active) 3.dp else 1.dp,
                    color = if (active) accent else Color.White.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(10.dp),
                ),
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = candidate.sourceLabel,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) glass.content else glass.contentMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        candidate.matchedTitle?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = glass.contentMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun RenameOverlay(
    draft: String,
    original: String,
    onDraftChange: (String) -> Unit,
) {
    val glass = rememberGlassTokens(GlassTone.Surface)
    val focusRequester = remember { FocusRequester() }
    // The on-screen keyboard is the only way to type here, so open it without a second button press.
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xF5070910)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .liquidGlass(
                    shape = ArcadiaGlass.SheetShape,
                    tone = GlassTone.Surface,
                    intensity = GlassIntensity.Strong,
                )
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "Rename",
                style = MaterialTheme.typography.titleLarge,
                color = glass.content,
            )
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            )
            Text(
                text = if (draft.isBlank()) {
                    "An empty name falls back to the scanned one."
                } else {
                    "Scanned name: $original"
                },
                style = MaterialTheme.typography.bodySmall,
                color = glass.contentMuted,
            )
            ButtonHintBar(
                hints = listOf("A" to "Save", "B" to "Cancel"),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        }
    }
}

private fun editorHints(
    mode: EditorMode,
    column: EditorColumn,
    row: RomEditorRow?,
): List<Pair<String, String>> = when (mode) {
    EditorMode.Rename -> listOf("A" to "Save", "B" to "Cancel")
    is EditorMode.ArtPicker -> listOf("A" to "Use this", "Y" to "My own file", "B" to "Back")
    EditorMode.Browse -> buildList {
        add("D-pad" to "Move")
        if (column == EditorColumn.Rail) {
            add("A" to "Open section")
            add("B" to "Close")
        } else {
            if (row?.onActivate != null) add("A" to "Select")
            if (row?.onAdjust != null) add("◀▶" to "Change")
            if (row?.onClear != null) add("X" to "Clear")
            add("B" to "Back")
        }
        add("LB/RB" to "Section")
    }
}
