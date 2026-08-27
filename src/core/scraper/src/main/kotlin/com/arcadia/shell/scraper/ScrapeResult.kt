package com.arcadia.shell.scraper

enum class ScrapeSource {
    /** Hash-accurate, so the match is certain when it succeeds. */
    ScreenScraper,

    /** Title search; good artwork, but the match is only as good as the cleaned filename. */
    SteamGridDb,
    Igdb,
}

/**
 * User preference for which metadata source to use when scraping.
 * [Auto] keeps the default fallback order (ScreenScraper → SteamGridDB → IGDB).
 */
enum class ScraperPreference {
    Auto,
    ScreenScraper,
    SteamGridDb,
    Igdb,
    ;

    val label: String
        get() = when (this) {
            Auto -> "Automatic"
            ScreenScraper -> "ScreenScraper"
            SteamGridDb -> "SteamGridDB"
            Igdb -> "IGDB"
        }

    fun toSourceOrNull(): ScrapeSource? = when (this) {
        Auto -> null
        ScreenScraper -> ScrapeSource.ScreenScraper
        SteamGridDb -> ScrapeSource.SteamGridDb
        Igdb -> ScrapeSource.Igdb
    }
}

data class ScrapeMatch(
    val title: String?,
    val heroUrl: String? = null,
    val logoUrl: String? = null,
    val boxArtUrl: String? = null,
    /** Up to a few gameplay / capture stills for the XMB insight panel. */
    val screenshotUrls: List<String> = emptyList(),
    /** Encoded trailer ([com.arcadia.shell.model.TrailerRefs]) when the source provided one. */
    val trailerUrl: String? = null,
    /**
     * Scanned manual, when the source carries one. Read from the same response as the artwork
     * rather than through a second lookup, because ScreenScraper quotas are counted per request and
     * a library-wide scrape would double its cost for a file most users never open.
     */
    val manualUrl: String? = null,
    /** The source's own format for [manualUrl], almost always `pdf`. */
    val manualFormat: String? = null,
    val source: ScrapeSource,
) {
    val hasArtwork: Boolean get() = heroUrl != null || logoUrl != null || boxArtUrl != null
}
