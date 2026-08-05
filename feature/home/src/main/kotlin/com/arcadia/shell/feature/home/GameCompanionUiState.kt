package com.arcadia.shell.feature.home

/** Touch targets on the companion bottom screen. */
enum class GameCompanionAction { About, Manual }

/** Which full-screen sheet the companion panel has open, if any. */
enum class GameCompanionOverlay { None, About, Manual }

/**
 * The bottom screen while a single-screen game runs in dual-screen mode.
 *
 * [about] deliberately reuses [GameInsightUiState] so the panel shows the same scraped copy the XMB
 * detail pane does, with the box-back fields ScreenScraper supplies for this session layered on top.
 */
data class GameCompanionUiState(
    val gameId: String,
    val title: String,
    val platformLabel: String,
    /** Scraped fanart / screenshot, falling back to box art. Null renders the themed backdrop. */
    val backdropPath: String? = null,
    val focusedAction: GameCompanionAction = GameCompanionAction.About,
    val overlay: GameCompanionOverlay = GameCompanionOverlay.None,
    val about: GameInsightUiState = GameInsightUiState(),
    /** True while the ScreenScraper detail / manual lookup for this session is in flight. */
    val detailLoading: Boolean = false,
    val manualPath: String? = null,
    /** True once the manual lookup finished, whether or not one was found. */
    val manualResolved: Boolean = false,
    val players: String? = null,
    val ratingPercent: Int? = null,
    val publisher: String? = null,
    /** RetroAchievements title and progress captured at launch, when the ROM was matched. */
    val raTitle: String? = null,
    val raProgressLabel: String? = null,
) {
    val hasManual: Boolean get() = !manualPath.isNullOrBlank()

    val manualLoading: Boolean get() = detailLoading && !manualResolved

    val manualMissing: Boolean get() = manualResolved && !hasManual

    /** One-line facts row under the title in the About sheet. */
    val factLine: String
        get() = buildList {
            add(platformLabel)
            about.releaseYear?.let { add(it.toString()) }
            about.genre?.takeIf { it.isNotBlank() }?.let { add(it) }
            about.developer?.takeIf { it.isNotBlank() }?.let { add(it) }
            publisher?.takeIf { it.isNotBlank() && it != about.developer }?.let { add(it) }
            players?.takeIf { it.isNotBlank() }?.let { add(playersLabel(it)) }
            ratingPercent?.let { add("$it% rated") }
        }.joinToString(" · ")

    private fun playersLabel(raw: String): String =
        if (raw == "1") "1 player" else "$raw players"
}
