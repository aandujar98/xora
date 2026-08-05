package com.arcadia.shell.scraper.insight

/**
 * Evergreen + optional "latest" blurbs about a selected library game for the XMB detail panel.
 */
data class GameInsight(
    val gameId: String,
    val title: String,
    /** Primary prose: Wikipedia / IGDB summary / local trivia. */
    val summary: String? = null,
    val summarySource: InsightSource? = null,
    val releaseYear: Int? = null,
    val developer: String? = null,
    val genre: String? = null,
    val speedrunBlurb: String? = null,
    val trivia: List<String> = emptyList(),
    val platformLabel: String? = null,
) {
    val hasContent: Boolean
        get() = !summary.isNullOrBlank() ||
            !speedrunBlurb.isNullOrBlank() ||
            releaseYear != null ||
            !developer.isNullOrBlank() ||
            !genre.isNullOrBlank() ||
            trivia.isNotEmpty()
}

enum class InsightSource {
    Wikipedia,
    Igdb,
    Local,
    Speedrun,
}

data class IgdbInsight(
    val summary: String? = null,
    val releaseYear: Int? = null,
    val developer: String? = null,
    val genre: String? = null,
)

data class SpeedrunInsight(
    val blurb: String,
    val releaseYear: Int? = null,
)

data class WikipediaInsight(
    val extract: String,
    val description: String? = null,
)
