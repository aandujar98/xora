package com.arcadia.shell.designsystem

/**
 * The shape every piece of box art is shown in.
 *
 * Scrapers return whatever the source had: ScreenScraper leans to tall game-case scans, SteamGridDB
 * and IGDB to wide capsules and key art. Letting each tile take its source's shape made rows of
 * mixed art fail to line up, so one landscape frame is imposed everywhere and the art is cropped
 * into it. Tall sources lose the most, which is what the per-game pan
 * ([com.arcadia.shell.datastore.GameArtAlignment]) is for.
 */
object ArcadiaArt {
    /** Width over height. Matches the media frame Game Select already used. */
    const val BoxArtAspect = 16f / 10f
}
