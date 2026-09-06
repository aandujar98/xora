package com.arcadia.shell.feature.home

import com.arcadia.shell.model.Game
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XoraXmbRecentsArtTest {

    @Test
    fun recentsPlatePrefersCoverOverWallpaper() {
        val game = sampleGame(boxArtPath = "/box.png", heroImagePath = "/hero.png")
        val items = buildXoraCategoryItems(
            category = XoraXmbCategory.Games,
            profileName = "Prince",
            gamesSecondarySlot = GamesSecondarySlot.Continue,
            continueGame = game,
            favoriteGame = null,
        )
        val recents = items.single { it.id == "continue" }
        assertEquals("/box.png", recents.artPath)
        assertEquals("/box.png", xmbRecentsCoverPath(game))
    }

    @Test
    fun recentsPlateFallsBackToWallpaperWhenCoverIsMissing() {
        val game = sampleGame(boxArtPath = null, heroImagePath = "/hero.png")
        assertEquals("/hero.png", xmbRecentsCoverPath(game))
        assertNull(xmbRecentsCoverPath(null))
    }

    @Test
    fun gameSelectWallpaperPrefersHeroOverCover() {
        val game = sampleGame(boxArtPath = "/box.png", heroImagePath = "/hero.png")
        assertEquals("/hero.png", xmbGameSelectWallpaperPath(game))
    }

    @Test
    fun gameSelectWallpaperFallsBackToCoverWhenHeroIsMissing() {
        val game = sampleGame(boxArtPath = "/box.png", heroImagePath = null)
        assertEquals("/box.png", xmbGameSelectWallpaperPath(game))
        assertNull(xmbGameSelectWallpaperPath(null))
    }

    @Test
    fun gameSelectWallpaperPrefersACustomBackground() {
        val game = sampleGame(boxArtPath = "/box.png", heroImagePath = "/hero.png")
        assertEquals("/custom.png", xmbGameSelectWallpaperPath(game, "/custom.png"))
    }

    private fun sampleGame(boxArtPath: String?, heroImagePath: String?) = Game(
        id = "nds:zelda",
        title = "Zelda",
        sortKey = "Zelda",
        platformId = "nds",
        fileName = "Zelda.nds",
        filePath = null,
        documentUri = null,
        sizeBytes = 1L,
        boxArtPath = boxArtPath,
        heroImagePath = heroImagePath,
    )
}
