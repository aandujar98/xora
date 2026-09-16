package com.arcadia.shell.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arcadia.shell.designsystem.ArcadiaMotion
import com.arcadia.shell.designsystem.GlassTone
import com.arcadia.shell.designsystem.rememberGlassTokens
import com.arcadia.shell.feature.home.component.ArtworkImage
import com.arcadia.shell.feature.home.component.HERO_DECODE_MAX_EDGE_PX
import com.arcadia.shell.input.NavAction
import com.arcadia.shell.model.GamePlatform
import com.arcadia.shell.scraper.ScraperPreference
import kotlinx.coroutines.flow.Flow

enum class PlatformEditorSection(val label: String) {
    Details("Details"),
    Artwork("Artwork"),
    Emulators("Emulators"),
    Library("Library"),
}

data class PlatformEditorActions(
    val onDismiss: () -> Unit,
    val onUploadBanner: () -> Unit,
    val onClearBanner: () -> Unit,
    val onRefreshArt: () -> Unit,
    val onSetPlatformPreference: (ScraperPreference) -> Unit,
    val onChooseEmulator: () -> Unit,
    val onClearEmulator: () -> Unit,
    val onRescrapePlatform: () -> Unit,
    val onToggleShowHidden: () -> Unit = {},
)

private enum class PlatformEditorColumn { Rail, Rows }

private val PLATFORM_RAIL_WIDTH = 216.dp
private val PLATFORM_HEADER_ART_W = 212.dp
private val PLATFORM_HEADER_ART_H = 132.dp

/**
 * Full-screen console editor matching [RomEditorPane]: left rail, focusable rows, A to act, B back.
 */
@Composable
fun PlatformEditorPane(
    platform: GamePlatform,
    gameCount: Int,
    bannerPath: String?,
    hasCustomBanner: Boolean,
    platformPreference: ScraperPreference,
    currentEmulatorLabel: String?,
    showHiddenGames: Boolean,
    navActions: Flow<NavAction>,
    actions: PlatformEditorActions,
    modifier: Modifier = Modifier,
) {
    val glass = rememberGlassTokens(GlassTone.Surface)
    val transition = remember { MutableTransitionState(false).apply { targetState = true } }
    LaunchedEffect(transition.currentState, transition.targetState) {
        if (!transition.targetState && !transition.currentState) actions.onDismiss()
    }
    val requestDismiss = { transition.targetState = false }

    var column by remember { mutableStateOf(PlatformEditorColumn.Rail) }
    var sectionIndex by remember { mutableIntStateOf(0) }
    var rowIndex by remember { mutableIntStateOf(0) }

    val sections = PlatformEditorSection.entries
    val section = sections[sectionIndex.coerceIn(0, sections.lastIndex)]
    val rows = rememberPlatformEditorRows(
        section = section,
        platform = platform,
        gameCount = gameCount,
        bannerPath = bannerPath,
        hasCustomBanner = hasCustomBanner,
        platformPreference = platformPreference,
        currentEmulatorLabel = currentEmulatorLabel,
        showHiddenGames = showHiddenGames,
        actions = actions,
    )

    LaunchedEffect(section, rows.size) {
        if (rowIndex > rows.lastIndex) rowIndex = rows.lastIndex.coerceAtLeast(0)
    }

    val railState = rememberLazyListState()
    val rowState = rememberLazyListState()
    LaunchedEffect(sectionIndex) { railState.animateScrollToItem(sectionIndex) }
    LaunchedEffect(rowIndex, section) {
        if (rows.isNotEmpty()) rowState.animateScrollToItem(rowIndex.coerceIn(0, rows.lastIndex))
    }

    LaunchedEffect(navActions, section, rows) {
        navActions.collect { action ->
            when (action) {
                NavAction.Up -> if (column == PlatformEditorColumn.Rail) {
                    sectionIndex = (sectionIndex - 1 + sections.size) % sections.size
                    rowIndex = 0
                } else if (rows.isNotEmpty()) {
                    rowIndex = (rowIndex - 1 + rows.size) % rows.size
                }

                NavAction.Down -> if (column == PlatformEditorColumn.Rail) {
                    sectionIndex = (sectionIndex + 1) % sections.size
                    rowIndex = 0
                } else if (rows.isNotEmpty()) {
                    rowIndex = (rowIndex + 1) % rows.size
                }

                NavAction.Right -> if (column == PlatformEditorColumn.Rail) {
                    if (rows.isNotEmpty()) column = PlatformEditorColumn.Rows
                } else {
                    rows.getOrNull(rowIndex)?.onAdjust?.invoke(1)
                }

                NavAction.Left -> if (column == PlatformEditorColumn.Rows) {
                    val adjust = rows.getOrNull(rowIndex)?.onAdjust
                    if (adjust != null) adjust(-1) else column = PlatformEditorColumn.Rail
                }

                NavAction.Confirm -> if (column == PlatformEditorColumn.Rail) {
                    if (rows.isNotEmpty()) column = PlatformEditorColumn.Rows
                } else {
                    rows.getOrNull(rowIndex)?.onActivate?.invoke()
                }

                NavAction.Cancel -> if (column == PlatformEditorColumn.Rows) {
                    column = PlatformEditorColumn.Rail
                } else {
                    requestDismiss()
                }

                NavAction.ScrapeMenu -> requestDismiss()

                NavAction.Options -> if (column == PlatformEditorColumn.Rows) {
                    rows.getOrNull(rowIndex)?.onClear?.invoke()
                }

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

    AnimatedVisibility(
        visibleState = transition,
        enter = fadeIn(tween(ArcadiaMotion.Slow)) +
            scaleIn(tween(ArcadiaMotion.Slow), initialScale = 0.94f),
        exit = fadeOut(tween(ArcadiaMotion.Medium)) +
            scaleOut(tween(ArcadiaMotion.Medium), targetScale = 0.96f),
    ) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color(0xFA05070C)),
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(28.dp)) {
                PlatformEditorHeader(
                    platform = platform,
                    gameCount = gameCount,
                    bannerPath = bannerPath,
                )
                Spacer(modifier = Modifier.height(18.dp))

                Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    LazyColumn(
                        state = railState,
                        modifier = Modifier
                            .width(PLATFORM_RAIL_WIDTH)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.07f))
                            .padding(vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        itemsIndexed(sections, key = { _, it -> it.name }) { index, entry ->
                            EditorRailRow(
                                label = entry.label,
                                selected = index == sectionIndex,
                                active = column == PlatformEditorColumn.Rail && index == sectionIndex,
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
                                    active = column == PlatformEditorColumn.Rows && index == rowIndex,
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
            }
        }
    }
}

@Composable
private fun rememberPlatformEditorRows(
    section: PlatformEditorSection,
    platform: GamePlatform,
    gameCount: Int,
    bannerPath: String?,
    hasCustomBanner: Boolean,
    platformPreference: ScraperPreference,
    currentEmulatorLabel: String?,
    showHiddenGames: Boolean,
    actions: PlatformEditorActions,
): List<RomEditorRow> = remember(
    section,
    platform,
    gameCount,
    bannerPath,
    hasCustomBanner,
    platformPreference,
    currentEmulatorLabel,
    showHiddenGames,
) {
    platformEditorRows(
        section = section,
        platform = platform,
        gameCount = gameCount,
        bannerPath = bannerPath,
        hasCustomBanner = hasCustomBanner,
        platformPreference = platformPreference,
        currentEmulatorLabel = currentEmulatorLabel,
        showHiddenGames = showHiddenGames,
        actions = actions,
    )
}

internal fun platformEditorRows(
    section: PlatformEditorSection,
    platform: GamePlatform,
    gameCount: Int,
    bannerPath: String?,
    hasCustomBanner: Boolean,
    platformPreference: ScraperPreference,
    currentEmulatorLabel: String?,
    showHiddenGames: Boolean,
    actions: PlatformEditorActions,
): List<RomEditorRow> = when (section) {
    PlatformEditorSection.Details -> listOf(
        RomEditorRow(
            key = "name",
            label = "Name",
            value = platform.displayName,
        ),
        RomEditorRow(
            key = "short",
            label = "Short name",
            value = platform.shortName,
        ),
        RomEditorRow(
            key = "count",
            label = "Games",
            value = gameCount.toString(),
            hint = if (gameCount == 1) "1 title on this system" else "$gameCount titles on this system",
        ),
    )
    PlatformEditorSection.Artwork -> buildList {
        add(
            RomEditorRow(
                key = "banner",
                label = "Console banner",
                value = when {
                    hasCustomBanner -> "Your image"
                    !bannerPath.isNullOrBlank() -> "Scraped"
                    else -> "None"
                },
                hint = "Shown on the system card. Opens the Files app — not Photos.",
                onActivate = actions.onUploadBanner,
                onClear = actions.onClearBanner.takeIf { hasCustomBanner },
            ),
        )
        add(
            RomEditorRow(
                key = "upload",
                label = "Upload my own banner",
                hint = "Opens the Files app for any local jpg / png / webp / gif.",
                onActivate = actions.onUploadBanner,
            ),
        )
        add(
            RomEditorRow(
                key = "refresh",
                label = "Refresh scraped art",
                hint = "Looks up ScreenScraper system media again.",
                onActivate = actions.onRefreshArt,
            ),
        )
    }
    PlatformEditorSection.Emulators -> emulatorRows(platform, currentEmulatorLabel, actions)
    PlatformEditorSection.Library -> {
        val options = ScraperPreference.entries
        listOf(
            RomEditorRow(
                key = "showhidden",
                label = "Show hidden games",
                hint = "Hidden ROMs and apps stay listed everywhere, marked Hidden.",
                value = if (showHiddenGames) "On" else "Off",
                onActivate = actions.onToggleShowHidden,
            ),
            RomEditorRow(
                key = "scraperplatform",
                label = "Scraper for ${platform.shortName}",
                hint = "Applies to every game on this system.",
                value = platformPreference.label,
                onAdjust = { direction ->
                    val next = options[(options.indexOf(platformPreference) + direction +
                        options.size) % options.size]
                    actions.onSetPlatformPreference(next)
                },
            ),
            RomEditorRow(
                key = "rescrapeplatform",
                label = "Re-scrape all ${platform.shortName} games",
                onActivate = actions.onRescrapePlatform,
                destructive = true,
            ),
        )
    }
}

internal fun emulatorRows(
    platform: GamePlatform,
    currentEmulatorLabel: String?,
    actions: PlatformEditorActions,
): List<RomEditorRow> = buildList {
    val label = currentEmulatorLabel?.takeIf { it.isNotBlank() }
    add(
        RomEditorRow(
            key = "emulator",
            label = "Default emulator",
            value = label ?: "Automatic",
            hint = "Used when you start a ${platform.shortName} game. A opens the installed list.",
            onActivate = actions.onChooseEmulator,
            onClear = actions.onClearEmulator.takeIf { label != null },
        ),
    )
    add(
        RomEditorRow(
            key = "emulator_choose",
            label = "Change emulator",
            hint = "Pick which emulator launches ${platform.shortName} titles.",
            onActivate = actions.onChooseEmulator,
        ),
    )
    if (platform.id == "psvita") {
        add(
            RomEditorRow(
                key = "vita_titleid",
                label = "Vita3K Title ID",
                hint = "Vita3K boots installed titles by ID (PCSE#####). Put the ID in the dump " +
                    "name or folder, then pick Vita3K above.",
            ),
        )
    }
    if (label != null) {
        add(
            RomEditorRow(
                key = "emulator_auto",
                label = "Use automatic",
                hint = "First installed emulator for this system.",
                onActivate = actions.onClearEmulator,
            ),
        )
    }
}

@Composable
private fun PlatformEditorHeader(
    platform: GamePlatform,
    gameCount: Int,
    bannerPath: String?,
) {
    val glass = rememberGlassTokens(GlassTone.Surface)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = platform.displayName,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = buildString {
                    append(platform.shortName)
                    append("  ·  ")
                    append(if (gameCount == 1) "1 game" else "$gameCount games")
                },
                style = MaterialTheme.typography.titleSmall,
                color = glass.contentMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(20.dp))
        ArtworkImage(
            path = bannerPath,
            contentDescription = null,
            fallbackText = platform.shortName.take(3).uppercase(),
            contentScale = ContentScale.Crop,
            decodeMaxEdgePx = HERO_DECODE_MAX_EDGE_PX,
            modifier = Modifier
                .size(width = PLATFORM_HEADER_ART_W, height = PLATFORM_HEADER_ART_H)
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(10.dp)),
        )
    }
}
