package com.arcadia.shell.feature.home

import com.arcadia.shell.retroachievements.RaCompletionGame

/** Sort / filter modes for the RetroAchievements library page. */
enum class RaLibraryTab {
    ByPlatform,
    RecentlyEarned,
    Completion,
}

/**
 * One list row: RA completion progress plus optional recent badge URLs
 * (from [com.arcadia.shell.retroachievements.RaRecentUnlock], when available).
 */
data class RaLibraryGameRow(
    val game: RaCompletionGame,
    val recentBadgeUrls: List<String> = emptyList(),
)

data class RaLibraryUiState(
    val isLoading: Boolean = false,
    val games: List<RaLibraryGameRow> = emptyList(),
    val selectedIndex: Int = 0,
    val tab: RaLibraryTab = RaLibraryTab.ByPlatform,
    /** Null = all platforms; otherwise match [RaCompletionGame.consoleName]. */
    val platformFilter: String? = null,
    val error: String? = null,
) {
    val platforms: List<String>
        get() = games.map { it.game.consoleName }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()

    val visibleGames: List<RaLibraryGameRow>
        get() {
            val filtered = if (platformFilter.isNullOrBlank()) {
                games
            } else {
                games.filter { it.game.consoleName == platformFilter }
            }
            return when (tab) {
                RaLibraryTab.ByPlatform -> filtered.sortedWith(
                    compareBy<RaLibraryGameRow> { it.game.consoleName.lowercase() }
                        .thenBy { it.game.title.lowercase() },
                )
                RaLibraryTab.RecentlyEarned -> filtered.sortedByDescending {
                    it.game.mostRecentAwardedDate.orEmpty()
                }
                RaLibraryTab.Completion -> filtered.sortedByDescending {
                    it.game.completionFraction
                }
            }
        }

    val selectedGame: RaLibraryGameRow?
        get() = visibleGames.getOrNull(selectedIndex.coerceIn(0, (visibleGames.size - 1).coerceAtLeast(0)))
}
