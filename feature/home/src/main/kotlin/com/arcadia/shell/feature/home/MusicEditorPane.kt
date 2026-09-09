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
import kotlinx.coroutines.flow.Flow

enum class MusicEditorKind { Album, Track }

enum class MusicEditorSection(val label: String) {
    Details("Details"),
    Artwork("Artwork"),
}

data class MusicEditorActions(
    val onDismiss: () -> Unit,
    val onPickAlbumCover: () -> Unit,
    val onClearAlbumCover: () -> Unit,
    val onPickTrackCover: () -> Unit,
    val onClearTrackCover: () -> Unit,
    val onPickBackground: () -> Unit,
    val onClearBackground: () -> Unit,
)

private enum class MusicEditorColumn { Rail, Rows }

private val MUSIC_RAIL_WIDTH = 216.dp
private val MUSIC_HEADER_ART = 132.dp

/**
 * Full-screen music editor matching [RomEditorPane] / [PlatformEditorPane]: left rail, focusable
 * rows, A to act, B back.
 */
@Composable
fun MusicEditorPane(
    title: String,
    subtitle: String?,
    kind: MusicEditorKind,
    albumCoverPath: String?,
    trackCoverPath: String?,
    backgroundPath: String?,
    navActions: Flow<NavAction>,
    actions: MusicEditorActions,
    modifier: Modifier = Modifier,
) {
    val glass = rememberGlassTokens(GlassTone.Surface)
    val transition = remember { MutableTransitionState(false).apply { targetState = true } }
    LaunchedEffect(transition.currentState, transition.targetState) {
        if (!transition.targetState && !transition.currentState) actions.onDismiss()
    }
    val requestDismiss = { transition.targetState = false }

    var column by remember { mutableStateOf(MusicEditorColumn.Rail) }
    var sectionIndex by remember { mutableIntStateOf(0) }
    var rowIndex by remember { mutableIntStateOf(0) }

    val sections = MusicEditorSection.entries
    val section = sections[sectionIndex.coerceIn(0, sections.lastIndex)]
    val rows = remember(
        section,
        kind,
        albumCoverPath,
        trackCoverPath,
        backgroundPath,
    ) {
        musicEditorRows(
            section = section,
            kind = kind,
            albumCoverPath = albumCoverPath,
            trackCoverPath = trackCoverPath,
            backgroundPath = backgroundPath,
            actions = actions,
        )
    }

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
                NavAction.Up -> if (column == MusicEditorColumn.Rail) {
                    sectionIndex = (sectionIndex - 1 + sections.size) % sections.size
                    rowIndex = 0
                } else if (rows.isNotEmpty()) {
                    rowIndex = (rowIndex - 1 + rows.size) % rows.size
                }

                NavAction.Down -> if (column == MusicEditorColumn.Rail) {
                    sectionIndex = (sectionIndex + 1) % sections.size
                    rowIndex = 0
                } else if (rows.isNotEmpty()) {
                    rowIndex = (rowIndex + 1) % rows.size
                }

                NavAction.Right -> if (column == MusicEditorColumn.Rail && rows.isNotEmpty()) {
                    column = MusicEditorColumn.Rows
                }

                NavAction.Left -> if (column == MusicEditorColumn.Rows) {
                    column = MusicEditorColumn.Rail
                }

                NavAction.Confirm -> if (column == MusicEditorColumn.Rail) {
                    if (rows.isNotEmpty()) column = MusicEditorColumn.Rows
                } else {
                    rows.getOrNull(rowIndex)?.onActivate?.invoke()
                }

                NavAction.Cancel -> if (column == MusicEditorColumn.Rows) {
                    column = MusicEditorColumn.Rail
                } else {
                    requestDismiss()
                }

                NavAction.ScrapeMenu -> requestDismiss()

                NavAction.Options -> if (column == MusicEditorColumn.Rows) {
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
                MusicEditorHeader(
                    title = title,
                    subtitle = subtitle,
                    coverPath = trackCoverPath ?: albumCoverPath,
                )
                Spacer(modifier = Modifier.height(18.dp))

                Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    LazyColumn(
                        state = railState,
                        modifier = Modifier
                            .width(MUSIC_RAIL_WIDTH)
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
                                active = column == MusicEditorColumn.Rail && index == sectionIndex,
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
                                    active = column == MusicEditorColumn.Rows && index == rowIndex,
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

internal fun musicEditorRows(
    section: MusicEditorSection,
    kind: MusicEditorKind,
    albumCoverPath: String?,
    trackCoverPath: String?,
    backgroundPath: String?,
    actions: MusicEditorActions,
): List<RomEditorRow> = when (section) {
    MusicEditorSection.Details -> listOf(
        RomEditorRow(
            key = "kind",
            label = "Type",
            value = if (kind == MusicEditorKind.Album) "Album" else "Track",
        ),
        RomEditorRow(
            key = "hint",
            label = "Select",
            hint = "A changes the focused row. X clears custom art. B closes.",
        ),
    )
    MusicEditorSection.Artwork -> buildList {
        add(
            RomEditorRow(
                key = "album_cover",
                label = "Album cover art",
                value = mediaLabel(albumCoverPath),
                hint = "Opens the Files app. Shown on the album card and as a fallback for tracks.",
                onActivate = actions.onPickAlbumCover,
                onClear = actions.onClearAlbumCover.takeIf { !albumCoverPath.isNullOrBlank() },
            ),
        )
        if (kind == MusicEditorKind.Track) {
            add(
                RomEditorRow(
                    key = "track_cover",
                    label = "Track cover art",
                    value = mediaLabel(trackCoverPath),
                    hint = "Opens the Files app. Overrides the album cover on this song.",
                    onActivate = actions.onPickTrackCover,
                    onClear = actions.onClearTrackCover.takeIf { !trackCoverPath.isNullOrBlank() },
                ),
            )
        }
        add(
            RomEditorRow(
                key = "background",
                label = if (kind == MusicEditorKind.Track) {
                    "Track background media"
                } else {
                    "Album background media"
                },
                value = mediaLabel(backgroundPath),
                hint = "Still, GIF, or looping video. Plays behind the XMB while this music plays. " +
                    "Opens the Files app.",
                onActivate = actions.onPickBackground,
                onClear = actions.onClearBackground.takeIf { !backgroundPath.isNullOrBlank() },
            ),
        )
    }
}

private fun mediaLabel(path: String?): String =
    if (path.isNullOrBlank()) "Not set" else "Your file"

@Composable
private fun MusicEditorHeader(
    title: String,
    subtitle: String?,
    coverPath: String?,
) {
    val glass = rememberGlassTokens(GlassTone.Surface)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle?.takeIf { it.isNotBlank() } ?: "Music",
                style = MaterialTheme.typography.titleSmall,
                color = glass.contentMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(modifier = Modifier.width(20.dp))
        ArtworkImage(
            path = coverPath,
            contentDescription = null,
            fallbackText = title.take(2).uppercase(),
            contentScale = ContentScale.Crop,
            decodeMaxEdgePx = HERO_DECODE_MAX_EDGE_PX,
            modifier = Modifier
                .size(MUSIC_HEADER_ART)
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(10.dp)),
        )
    }
}
