package com.arcadia.shell.libretro.netplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XoraNetplayBannerTest {

    @Test
    fun idleShowsNothing() {
        assertEquals("", netplayBannerText(XoraNetplayUiState()))
    }

    @Test
    fun joiningStatusIsVisibleWithoutOpeningPause() {
        val text = netplayBannerText(
            XoraNetplayUiState(
                role = XoraNetplayRole.Client,
                status = "Looking for session K7M2QX…",
                sessionCode = "K7M2QX",
                online = true,
            ),
        )
        assertEquals("Looking for session K7M2QX…", text)
    }

    @Test
    fun errorBeatsStatus() {
        val text = netplayBannerText(
            XoraNetplayUiState(
                role = XoraNetplayRole.Client,
                status = "Disconnected",
                error = "Could not load host save state",
            ),
        )
        assertEquals("Could not load host save state", text)
    }

    @Test
    fun linkedJoinerGetsMarioKartTwoPlayerHint() {
        val text = netplayBannerText(
            ui = XoraNetplayUiState(
                role = XoraNetplayRole.Client,
                linked = true,
                playerSlot = 2,
                status = "Joined",
            ),
            padLive = true,
            gameTitle = "Super Mario Kart",
        )
        assertTrue(text.startsWith("You are Player 2 · input"))
        assertTrue(text.contains("2 PLAYER GAME"))
        assertTrue(text.contains("1 PLAYER Grand Prix"))
    }

    @Test
    fun otherGamesGetGenericTwoPlayerHint() {
        val text = netplayBannerText(
            ui = XoraNetplayUiState(linked = true, playerSlot = 2, role = XoraNetplayRole.Client),
            gameTitle = "Street Fighter II",
        )
        assertTrue(text.contains("2-player game"))
    }
}
