package com.arcadia.shell.feature.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.arcadia.shell.designsystem.ArcadiaMotion
import com.arcadia.shell.designsystem.arcadiaTween

/**
 * Bottom / grid role for the Home hub: Smash shard menu (page 1) and shortcut grid (page 2).
 *
 * Themes / Add-shortcut overlays and media pickers are hosted by the parent so Activity Result
 * never registers under a secondary [android.app.Presentation].
 */
@Composable
fun HomeHubPane(
    hub: HomeHubUiState,
    raAvatarUrl: String?,
    onSelectShard: (HomeShard) -> Unit,
    onActivateShard: (HomeShard) -> Unit,
    onSelectShortcut: (Int) -> Unit,
    onActivateShortcut: (Int) -> Unit,
    onAddShortcut: () -> Unit,
    onCycleSpan: ((Int) -> Unit)? = null,
    onAdjustShortcutColumns: ((Int) -> Unit)? = null,
    onAdjustShortcutRows: ((Int) -> Unit)? = null,
    onFocusShortcutCustomizeChrome: ((ShortcutCustomizeChrome) -> Unit)? = null,
    modifier: Modifier = Modifier,
    showWallpaperBackdrop: Boolean = false,
) {
    Box(modifier = modifier.fillMaxSize()) {
        if (showWallpaperBackdrop) {
            HomeWallpaper(
                customPath = hub.wallpaperPath,
                dim = false,
                modifier = Modifier.fillMaxSize(),
            )
        }

        val fade = arcadiaTween<Float>(ArcadiaMotion.Medium)
        val slide = arcadiaTween<IntOffset>(ArcadiaMotion.Medium)
        AnimatedContent(
            targetState = hub.section,
            transitionSpec = {
                if (targetState == HomeHubSection.Shortcuts) {
                    (slideInVertically(slide) { it / 4 } + fadeIn(fade)) togetherWith
                        (slideOutVertically(slide) { -it / 4 } + fadeOut(fade))
                } else {
                    (slideInVertically(slide) { -it / 4 } + fadeIn(fade)) togetherWith
                        (slideOutVertically(slide) { it / 4 } + fadeOut(fade))
                }
            },
            label = "homeHubSection",
            modifier = Modifier.fillMaxSize(),
        ) { section ->
            when (section) {
                HomeHubSection.ShardMenu -> HomeShardMenu(
                    continueGame = hub.continueGame,
                    focused = hub.shard,
                    raAvatarUrl = raAvatarUrl,
                    onSelect = onSelectShard,
                    onActivate = onActivateShard,
                    modifier = Modifier.fillMaxSize(),
                )

                HomeHubSection.Shortcuts -> HomeShortcutsGrid(
                    shortcuts = hub.shortcuts,
                    selectedIndex = hub.shortcutIndex,
                    editMode = hub.shortcutsEditMode,
                    onSelect = onSelectShortcut,
                    onActivate = onActivateShortcut,
                    onAddSlot = onAddShortcut,
                    onCycleSpan = onCycleSpan,
                    columns = hub.shortcutGridColumns,
                    rows = hub.shortcutGridRows,
                    customizeChrome = hub.customizeChrome,
                    onAdjustColumns = onAdjustShortcutColumns,
                    onAdjustRows = onAdjustShortcutRows,
                    onFocusCustomizeChrome = onFocusShortcutCustomizeChrome,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        if (hub.section == HomeHubSection.ShardMenu) {
            Text(
                text = "↓ Shortcuts",
                color = Color.White.copy(alpha = 0.45f),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 6.dp),
            )
        }
    }
}
