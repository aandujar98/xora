package com.arcadia.shell.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.arcadia.shell.designsystem.ArcadiaMotion
import com.arcadia.shell.designsystem.arcadiaTween
import com.arcadia.shell.feature.home.component.GameCard
import com.arcadia.shell.feature.home.component.PlatformRail
import com.arcadia.shell.feature.home.preview.XoraPreview
import com.arcadia.shell.feature.home.preview.XoraPreviewTheme
import com.arcadia.shell.feature.home.preview.previewHomeUi

/**
 * The scrolling library grid plus the platform rail above it. This pane always owns input focus,
 * whichever physical display it happens to be on.
 */
@Composable
fun GridPane(
    state: HomeUiState,
    onSelectTab: (Int) -> Unit,
    onSelectGame: (Int) -> Unit,
    onLaunchGame: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridState = rememberLazyGridState()

    // The selection is driven by an index rather than by focus, so scrolling has to be requested
    // explicitly whenever that index moves outside the visible window.
    LaunchedEffect(state.selectedGameIndex, state.selectedTabIndex, state.games.size) {
        if (state.games.isEmpty()) return@LaunchedEffect

        val visible = gridState.layoutInfo.visibleItemsInfo
        val isVisible = visible.any { it.index == state.selectedGameIndex }

        if (!isVisible) {
            // Landing a row above the selection keeps the hero-relevant row off the very edge.
            val target = (state.selectedGameIndex - state.gridColumns).coerceAtLeast(0)
            gridState.animateScrollToItem(target)
        }
    }

    Column(
        modifier = modifier.background(MaterialTheme.colorScheme.surface),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        PlatformRail(
            tabs = state.tabs,
            selectedIndex = state.selectedTabIndex,
            onSelect = onSelectTab,
            modifier = Modifier.fillMaxWidth(),
        )

        val emptyEnter = fadeIn(arcadiaTween(ArcadiaMotion.Medium))
        val emptyExit = fadeOut(arcadiaTween(ArcadiaMotion.Fast))
        AnimatedContent(
            targetState = state.games.isEmpty(),
            transitionSpec = { emptyEnter togetherWith emptyExit },
            label = "gridEmpty",
            modifier = Modifier.fillMaxSize(),
        ) { empty ->
            if (empty) {
                EmptyLibraryNotice(
                    isScanning = state.scanProgress.isRunning,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(state.gridColumns),
                    state = gridState,
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(
                        items = state.games,
                        key = { _, game -> game.id },
                    ) { index, game ->
                        GameCard(
                            game = game,
                            isSelected = index == state.selectedGameIndex,
                            onClick = {
                                // Touch mirrors the controller model: the first tap selects, and a tap
                                // on the already-selected tile launches.
                                if (index == state.selectedGameIndex) {
                                    onLaunchGame(index)
                                } else {
                                    onSelectGame(index)
                                }
                            },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }
}

@XoraPreview
@Composable
private fun GridPanePreview() {
    XoraPreviewTheme {
        GridPane(
            state = previewHomeUi(homePage = HomePage.GameSelector),
            onSelectTab = {},
            onSelectGame = {},
            onLaunchGame = {},
        )
    }
}

@Composable
private fun EmptyLibraryNotice(isScanning: Boolean, modifier: Modifier = Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = if (isScanning) {
                "Scanning your library…"
            } else {
                "No games here yet. Run a scan from Settings, or press Start."
            },
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(24.dp),
        )
    }
}
