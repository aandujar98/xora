package com.arcadia.shell.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GameShortcutIconTest {

    @Test
    fun dedicatedShortcutIconWinsOverGridArt() {
        val game = sample(
            shortcutIconPath = "/icon.png",
            boxArtPath = "/box.png",
            heroImagePath = "/hero.png",
        )
        assertEquals("/icon.png", game.shortcutIcon)
        assertEquals("/box.png", game.gridArt)
    }

    @Test
    fun pinFallsBackToBoxThenHeroThenLogo() {
        assertEquals("/box.png", sample(boxArtPath = "/box.png", heroImagePath = "/hero.png").shortcutIcon)
        assertEquals("/hero.png", sample(heroImagePath = "/hero.png", logoImagePath = "/logo.png").shortcutIcon)
        assertEquals("/logo.png", sample(logoImagePath = "/logo.png").shortcutIcon)
        assertNull(sample().shortcutIcon)
    }

    private fun sample(
        shortcutIconPath: String? = null,
        boxArtPath: String? = null,
        heroImagePath: String? = null,
        logoImagePath: String? = null,
    ) = Game(
        id = "nds:zelda",
        title = "Zelda",
        sortKey = "Zelda",
        platformId = "nds",
        fileName = "Zelda.nds",
        filePath = null,
        documentUri = null,
        sizeBytes = 1L,
        shortcutIconPath = shortcutIconPath,
        boxArtPath = boxArtPath,
        heroImagePath = heroImagePath,
        logoImagePath = logoImagePath,
    )
}
