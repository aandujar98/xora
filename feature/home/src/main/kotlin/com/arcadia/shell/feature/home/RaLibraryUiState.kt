package com.arcadia.shell.feature.home

import com.arcadia.shell.retroachievements.RaAchievement
import com.arcadia.shell.retroachievements.RaCompletionGame
import com.arcadia.shell.retroachievements.RaFollowedUser
import com.arcadia.shell.retroachievements.RaGameProgress

/** Columns in the per-game cheevo window — keep pad navigation in lockstep with the grid. */
internal const val RA_CHEEVO_GRID_COLUMNS = 8

/** Sort / filter modes for the RetroAchievements library page. */
enum class RaLibraryTab {
    ByPlatform,
    RecentlyEarned,
    Completion,
}

/** Pad focus: your (or a friend's) game list vs the Following leaderboard. */
enum class RaLibraryFocusColumn {
    Games,
    Following,
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
    val following: List<RaFollowedUser> = emptyList(),
    val followingIndex: Int = 0,
    val followingLoading: Boolean = false,
    val followingError: String? = null,
    val focusColumn: RaLibraryFocusColumn = RaLibraryFocusColumn.Games,
    /** Null = your library; otherwise that user's completion games. */
    val viewedUser: String? = null,
    val viewedUserGames: List<RaLibraryGameRow> = emptyList(),
    val viewedUserLoading: Boolean = false,
    val compareEnabled: Boolean = false,
    /** The other player's progress for the open game (you vs them). */
    val compareProgress: RaGameProgress? = null,
) {
    val viewingFollower: Boolean
        get() = !viewedUser.isNullOrBlank()

    val libraryGames: List<RaLibraryGameRow>
        get() = if (viewingFollower) viewedUserGames else games

    val platforms: List<String>
        get() = libraryGames.map { it.game.consoleName }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()

    val visibleGames: List<RaLibraryGameRow>
        get() {
            val filtered = if (platformFilter.isNullOrBlank()) {
                libraryGames
            } else {
                libraryGames.filter { it.game.consoleName == platformFilter }
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

    val selectedFollower: RaFollowedUser?
        get() = following.getOrNull(
            followingIndex.coerceIn(0, (following.size - 1).coerceAtLeast(0)),
        )

    val comparePeer: RaFollowedUser?
        get() = when {
            viewedUser.isNullOrBlank() -> selectedFollower
            else -> following.firstOrNull { it.username.equals(viewedUser, ignoreCase = true) }
        }

    val gameDetailOpen: Boolean
        get() = gameDetail != null || gameDetailLoading || gameDetailError != null

    val selectedCheevo: RaAchievement?
        get() {
            val list = gameDetail?.achievements.orEmpty()
            if (list.isEmpty()) return null
            return list.getOrNull(cheevoIndex.coerceIn(0, list.lastIndex))
        }

    fun compareAchievement(id: Int): RaAchievement? =
        compareProgress?.achievements?.firstOrNull { it.id == id }

    fun cheevoStatusLine(cheevo: RaAchievement): String {
        if (!compareEnabled) {
            return when {
                cheevo.earnedHardcore -> "Hardcore · ${cheevo.points} pts"
                cheevo.earned -> "Earned · ${cheevo.points} pts"
                else -> "Locked · ${cheevo.points} pts"
            }
        }
        val mine = if (viewingFollower) compareAchievement(cheevo.id) else cheevo
        val theirs = if (viewingFollower) cheevo else compareAchievement(cheevo.id)
        val themName = viewedUser ?: selectedFollower?.username ?: "Them"
        return "You: ${unlockWord(mine)}  ·  $themName: ${unlockWord(theirs)}  ·  ${cheevo.points} pts"
    }

    private fun unlockWord(cheevo: RaAchievement?): String = when {
        cheevo == null -> "—"
        cheevo.earnedHardcore -> "Hardcore"
        cheevo.earned -> "Earned"
        else -> "Locked"
    }
}
