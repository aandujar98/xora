package com.arcadia.shell.feature.home

import com.arcadia.shell.datastore.RetroAchievementsCredentials
import com.arcadia.shell.retroachievements.RaGameLookup
import com.arcadia.shell.retroachievements.RaGameProgress
import com.arcadia.shell.retroachievements.RaProfile
import com.arcadia.shell.retroachievements.RaRecentUnlock

enum class AchievementsPaneTab { ThisGame, Recent }

data class AchievementsUiState(
    val credentials: RetroAchievementsCredentials = RetroAchievementsCredentials(),
    val profile: RaProfile? = null,
    val gameLookup: RaGameLookup? = null,
    val recent: List<RaRecentUnlock> = emptyList(),
    val tab: AchievementsPaneTab = AchievementsPaneTab.ThisGame,
    val isLoading: Boolean = false,
    val isLoggingIn: Boolean = false,
    val needsLogin: Boolean = false,
    /**
     * Set after password login succeeds but Web API still needs the control-panel key.
     * Null when not waiting for that one-time paste.
     */
    val pendingWebApiUsername: String? = null,
    val error: String? = null,
) {
    /** Progress for whichever game the lookup last resolved, or null when unmatched. */
    val focusedGameProgress: RaGameProgress?
        get() = (gameLookup as? RaGameLookup.Matched)?.progress
}
