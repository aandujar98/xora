package com.arcadia.shell.feature.home

import com.arcadia.shell.retroachievements.RaAchievement
import com.arcadia.shell.retroachievements.RaCompletionGame
import com.arcadia.shell.retroachievements.RaGameProgress

/** Columns in the per-game cheevo window — keep pad navigation in lockstep with the grid. */
internal const val RA_CHEEVO_GRID_COLUMNS = 8

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
    val gameDetail: RaGameProgress? = null,
    val gameDetailLoading: Boolean = false,
    val gameDetailError: String? = null,
    val cheevoIndex: Int = 0,
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

    val gameDetailOpen: Boolean
        get() = gameDetail != null || gameDetailLoading || gameDetailError != null

    val selectedCheevo: RaAchievement?
        get() {
            val list = gameDetail?.achievements.orEmpty()
            if (list.isEmpty()) return null
            return list.getOrNull(cheevoIndex.coerceIn(0, list.lastIndex))
        }
}
