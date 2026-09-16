package com.arcadia.shell.feature.home

import com.arcadia.shell.retroachievements.RaAchievement
import com.arcadia.shell.retroachievements.RaCompletionGame
import com.arcadia.shell.retroachievements.RaFollowedUser
import com.arcadia.shell.retroachievements.RaGameProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RaLibraryUiStateTest {

    @Test
    fun followingLeaderboardDoesNotReplaceYourLibraryUntilOpened() {
        val you = game(1, "Your Game", "NES")
        val them = game(2, "Their Game", "SNES")
        val state = RaLibraryUiState(
            games = listOf(RaLibraryGameRow(you)),
            following = listOf(RaFollowedUser("Alice", 9000)),
            viewedUserGames = listOf(RaLibraryGameRow(them)),
        )
        assertEquals(listOf("Your Game"), state.visibleGames.map { it.game.title })
        val opened = state.copy(viewedUser = "Alice")
        assertEquals(listOf("Their Game"), opened.visibleGames.map { it.game.title })
        assertEquals(listOf("SNES"), opened.platforms)
        assertTrue(opened.viewingFollower)
    }

    @Test
    fun compareStatusLineShowsYouAndTheFollowedUser() {
        val cheevo = RaAchievement(
            id = 7,
            title = "First star",
            description = "Collect a star",
            points = 10,
            badgeName = "00001",
            displayOrder = 1,
            earned = true,
            earnedHardcore = false,
        )
        val mine = cheevo.copy(earned = false, earnedHardcore = false)
        val state = RaLibraryUiState(
            viewedUser = "Alice",
            compareEnabled = true,
            compareProgress = RaGameProgress(
                gameId = 1,
                title = "Mario",
                consoleName = "NES",
                numAchievements = 1,
                numAwardedToUser = 0,
                numAwardedToUserHardcore = 0,
                achievements = listOf(mine),
            ),
        )
        assertEquals(
            "You: Locked  ·  Alice: Earned  ·  10 pts",
            state.cheevoStatusLine(cheevo),
        )
    }

    private fun game(id: Int, title: String, console: String) = RaCompletionGame(
        gameId = id,
        title = title,
        imageIconPath = "",
        consoleId = 1,
        consoleName = console,
        maxPossible = 10,
        numAwarded = 2,
        numAwardedHardcore = 1,
        mostRecentAwardedDate = null,
        highestAwardKind = null,
    )
}
