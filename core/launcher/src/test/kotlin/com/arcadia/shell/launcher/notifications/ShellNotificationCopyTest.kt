package com.arcadia.shell.launcher.notifications

import org.junit.Assert.assertEquals
import org.junit.Test

class ShellNotificationCopyTest {

    @Test
    fun netplayInviteHeadlineNamesHostAndGame() {
        assertEquals(
            "pal invited you to play Pokémon FireRed",
            netplayInviteHeadline("pal", "Pokémon FireRed"),
        )
        assertEquals(
            "A friend invited you to play a game",
            netplayInviteHeadline("  ", ""),
        )
    }

    @Test
    fun netplayInviteBannerUsesHeadline() {
        val copy = ShellNotification.XoraNetplayInvite(
            id = "xora-netplay:test",
            displayName = "angel",
            gameTitle = "Mario Kart Super Circuit",
            sessionCode = "K7M2QX",
            fromUsername = "angel",
        ).toCopy()
        assertEquals("Netplay", copy.category)
        assertEquals("angel invited you to play Mario Kart Super Circuit", copy.body)
        assertEquals("XOrA Network", copy.subtitle)
    }

    @Test
    fun netplayInviteDismissalKeysSurviveAppUpdates() {
        val keys = ShellNotification.XoraNetplayInvite(
            id = "xora-netplay:pal|ABC123|9",
            displayName = "pal",
            gameTitle = "Kirby",
            sessionCode = "ABC123",
            fromUsername = "pal",
        ).dismissalKeys()
        assertEquals(
            setOf("xora-netplay:pal|ABC123|9", "xora-netplay-session:pal|ABC123"),
            keys,
        )
    }
}
