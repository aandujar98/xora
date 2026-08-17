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
        assertTrue(text.startsWith("You are Player 2"))
        assertTrue(!text.contains("input"))
        assertTrue(!text.contains("no input"))
        assertTrue(text.contains("2 PLAYER GAME"))
        assertTrue(text.contains("1 PLAYER Grand Prix"))
    }

    @Test
    fun linkedJoinerWithoutPadSaysNoInputAndAsksForController() {
        val text = netplayBannerText(
            ui = XoraNetplayUiState(
                role = XoraNetplayRole.Client,
                linked = true,
                playerSlot = 2,
            ),
            padLive = false,
            gameTitle = "Super Mario Kart",
            hasController = false,
        )
        assertTrue(text.startsWith("You are Player 2"))
        assertTrue(!text.contains("no input"))
        assertTrue(text.contains("on-screen pad"))
        assertTrue(text.contains("bottom"))
    }

    @Test
    fun linkedJoinerWithPhysicalPadDoesNotAskForTouchOverlay() {
        val text = netplayBannerText(
            ui = XoraNetplayUiState(
                role = XoraNetplayRole.Client,
                linked = true,
                playerSlot = 2,
            ),
            padLive = false,
            gameTitle = "Super Mario Kart",
            hasController = true,
            lastKey = "",
        )
        assertTrue(text.startsWith("You are Player 2"))
        assertTrue(!text.contains("no input"))
        assertTrue(!text.contains(" · input"))
        assertTrue(text.contains("host"))
        assertTrue(!text.contains("on-screen pad"))
        assertTrue(!text.contains("bottom of this phone"))
    }

    @Test
    fun handheldJoinerMentionsSeparateGames() {
        val text = netplayBannerText(
            ui = XoraNetplayUiState(
                role = XoraNetplayRole.Client,
                linked = true,
                playerSlot = 2,
            ),
            padLive = true,
            gameTitle = "Pokemon Red",
            hasController = true,
            sharedConsole = false,
        )
        assertTrue(text.contains("your Game Boy"))
        assertTrue(text.contains("Game Link cable"))
        assertTrue(!text.contains("2 PLAYER GAME"))
    }

    @Test
    fun gbaGpspLinkExplainsBuiltInCable() {
        val text = netplayBannerText(
            ui = XoraNetplayUiState(
                role = XoraNetplayRole.Client,
                linked = true,
                playerSlot = 2,
            ),
            padLive = true,
            gameTitle = "Mario Kart Super Circuit",
            hasController = true,
            sharedConsole = false,
            gbaGpspLink = true,
            gbaGpspLinkLive = true,
        )
        assertTrue(text.contains("Game Link cable"))
        assertTrue(text.contains("link menu") || text.contains("2-player"))
        assertTrue(!text.contains("your Game Boy"))
        assertTrue(!text.contains("2 PLAYER GAME"))
        assertTrue(!text.contains("second player"))
    }

    @Test
    fun gbaGpspBannerDoesNotWaitToPlugTheCable() {
        val text = netplayBannerText(
            ui = XoraNetplayUiState(
                role = XoraNetplayRole.Host,
                linked = true,
                playerSlot = 1,
            ),
            padLive = true,
            gameTitle = "Kirby",
            hasController = true,
            sharedConsole = false,
            gbaGpspLink = true,
            gbaGpspLinkLive = false,
        )
        assertTrue(text.contains("Game Link cable"))
        assertTrue(text.contains("plugged"))
        assertTrue(!text.contains("second player"))
        assertTrue(!text.contains("two GBAs"))
    }

    @Test
    fun handheldLastKeyDoesNotAskForMarioKartMode() {
        val text = netplayBannerText(
            ui = XoraNetplayUiState(
                role = XoraNetplayRole.Client,
                linked = true,
                playerSlot = 2,
            ),
            padLive = false,
            gameTitle = "Mario Kart Super Circuit",
            hasController = true,
            lastKey = "Button A",
            sharedConsole = false,
        )
        assertTrue(text.contains("Game Link"))
        assertTrue(!text.contains("2 PLAYER GAME"))
        assertTrue(!text.contains("Button A"))
        assertTrue(!text.contains("no input"))
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
