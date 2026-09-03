package com.arcadia.shell.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.arcadia.shell.designsystem.ArcadiaMotion
import com.arcadia.shell.designsystem.arcadiaTween
import com.arcadia.shell.feature.home.component.xmb.PlatformIcon
import com.arcadia.shell.feature.home.component.xmb.XmbGameTile
import com.arcadia.shell.feature.home.component.xmb.XmbInsightPanel
import com.arcadia.shell.model.Game
import kotlin.math.abs
import kotlinx.coroutines.delay

/**
 * Horizontal PS-style XMB: system dock (auto-hides) + case strip in the upper band,
 * always-visible About + screenshots panel below. Platform-tinted sky when on a system tab.
 */
@Composable
fun XmbPane(
    state: HomeUiState,
    onSelectTab: (Int) -> Unit,
    onSelectGame: (Int) -> Unit,
    onLaunchGame: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dockVisible by remember { mutableStateOf(false) }
    var previousTabIndex by remember { mutableStateOf<Int?>(null) }
    var dockHideToken by remember { mutableIntStateOf(0) }

    // Show the system icon row only while switching systems; auto-hide after idle.
    LaunchedEffect(state.selectedTabIndex) {
        val previous = previousTabIndex
        previousTabIndex = state.selectedTabIndex
        if (previous == null || previous == state.selectedTabIndex) return@LaunchedEffect
        dockVisible = true
        val token = dockHideToken + 1
        dockHideToken = token
        delay(SYSTEM_DOCK_HIDE_MS)
        if (dockHideToken == token) {
            dockVisible = false
        }
    }

    // Same home wallpaper (still / GIF / MP4 / theme) as the hub. Tile art sits above it in the
    // strip — wallpaper is only the pane backdrop, never a layer over ROM media.
    Box(modifier = modifier.fillMaxSize()) {
        HomeWallpaper(
            customPath = state.homeHub.wallpaperPath,
            dim = true,
            dimBlendMode = BlendMode.Multiply,
            alignX = state.homeHub.wallpaperAlignX,
            alignY = state.homeHub.wallpaperAlignY,
            modifier = Modifier.fillMaxSize(),
        )
        Column(modifier = Modifier.fillMaxSize()) {
            AnimatedVisibility(
                visible = dockVisible,
                enter = fadeIn(arcadiaTween(ArcadiaMotion.Medium)) + expandVertically(
                    animationSpec = arcadiaTween<IntSize>(ArcadiaMotion.Medium),
                ),
                exit = fadeOut(arcadiaTween(ArcadiaMotion.Fast)) + shrinkVertically(
                    animationSpec = arcadiaTween<IntSize>(ArcadiaMotion.Fast),
                ),
            ) {
                XmbCategoryRow(
                    tabs = state.tabs,
                    selectedIndex = state.selectedTabIndex,
                    onSelect = onSelectTab,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                )
            }

            if (state.games.isEmpty()) {
                XmbEmptyNotice(
                    isScanning = state.scanProgress.isRunning,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            } else {
                val tabEnter = fadeIn(arcadiaTween(ArcadiaMotion.Medium))
                val tabExit = fadeOut(arcadiaTween(ArcadiaMotion.Fast))
                AnimatedContent(
                    targetState = state.selectedTabIndex,
                    transitionSpec = { tabEnter togetherWith tabExit },
                    label = "xmbGamesCrossfade",
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(GAME_STRIP_WEIGHT),
                ) { tabIndex ->
                    XmbGameStrip(
                        games = state.games,
                        selectedIndex = state.selectedGameIndex,
                        enabled = tabIndex == state.selectedTabIndex,
                        onSelectGame = onSelectGame,
                        onLaunchGame = onLaunchGame,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                XmbInsightPanel(
                    insight = state.insight,
                    gameTitle = state.selectedGame?.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(INSIGHTS_WEIGHT),
                )
            }
        }
    }
}

@Composable
private fun XmbCategoryRow(
    tabs: List<LibraryTab>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    LaunchedEffect(selectedIndex) {
        if (tabs.isEmpty()) return@LaunchedEffect
        listState.animateScrollToItem(selectedIndex.coerceIn(0, tabs.lastIndex))
    }

    val dockDp = arcadiaTween<Dp>(ArcadiaMotion.Medium)
    val dockFloat = arcadiaTween<Float>(ArcadiaMotion.Medium)

    LazyRow(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        itemsIndexed(items = tabs, key = { _, tab -> tab.id }) { index, tab ->
            val selected = index == selectedIndex
            val iconSize by animateDpAsState(
                targetValue = if (selected) 38.dp else 30.dp,
                animationSpec = dockDp,
                label = "xmbIconSize_$index",
            )
            val dockOffset by animateDpAsState(
                targetValue = if (selected) (-2).dp else 0.dp,
                animationSpec = dockDp,
                label = "xmbDockOffset_$index",
            )
            val dimAlpha by animateFloatAsState(
                targetValue = if (selected) 1f else 0.40f,
                animationSpec = dockFloat,
                label = "xmbIconAlpha_$index",
            )

            PlatformIcon(
                tab = tab,
                selected = selected,
                size = iconSize,
                modifier = Modifier
                    .animateItem()
                    .offset(y = dockOffset)
                    .alpha(dimAlpha)
                    .clickable { onSelect(index) },
            )
        }
    }
}

@Composable
private fun XmbGameStrip(
    games: List<Game>,
    selectedIndex: Int,
    enabled: Boolean,
    onSelectGame: (Int) -> Unit,
    onLaunchGame: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val focused = games.getOrNull(selectedIndex)

    LaunchedEffect(selectedIndex, games.size) {
        if (games.isEmpty()) return@LaunchedEffect
        val target = selectedIndex.coerceIn(0, games.lastIndex)
        listState.animateScrollToItem(target)
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
    ) {
        LazyRow(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            userScrollEnabled = enabled,
        ) {
            itemsIndexed(items = games, key = { _, game -> game.id }) { index, game ->
                val distance = abs(index - selectedIndex)
                XmbGameTile(
                    game = game,
                    focused = index == selectedIndex,
                    distanceFromFocus = distance,
                    onClick = {
                        if (index == selectedIndex) {
                            onLaunchGame(index)
                        } else {
                            onSelectGame(index)
                        }
                    },
                    baseWidth = 104.dp,
                    modifier = Modifier.animateItem(),
                )
            }
        }

        val titleEnter = fadeIn(arcadiaTween(ArcadiaMotion.Medium))
        val titleExit = fadeOut(arcadiaTween(ArcadiaMotion.Fast))
        AnimatedContent(
            targetState = focused?.id to focused?.title,
            transitionSpec = { titleEnter togetherWith titleExit },
            label = "xmbFocusedTitle",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 2.dp),
        ) { (_, title) ->
            Text(
                text = title.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun XmbEmptyNotice(isScanning: Boolean, modifier: Modifier = Modifier) {
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

/** How long the system dock stays up after the last Up/Down system change. */
private const val SYSTEM_DOCK_HIDE_MS = 1_350L

/** Strip + insights share the column; weights keep case art and About panel both visible. */
private const val GAME_STRIP_WEIGHT = 0.56f
private const val INSIGHTS_WEIGHT = 0.44f
