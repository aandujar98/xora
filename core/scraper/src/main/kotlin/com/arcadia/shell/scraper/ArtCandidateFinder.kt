package com.arcadia.shell.scraper

import com.arcadia.shell.datastore.ScraperCredentials
import com.arcadia.shell.datastore.ShellPreferences
import com.arcadia.shell.model.Game
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/** Which slot on the game a candidate image can fill. */
enum class ArtSlot {
    BoxArt,
    Hero,
    Logo,
    ;

    val label: String
        get() = when (this) {
            BoxArt -> "Box art"
            Hero -> "Background"
            Logo -> "Logo"
        }
}

/**
 * One choosable image, still remote. [url] is only downloaded once the user commits to it, so
 * opening the picker costs three lookups rather than a library's worth of image traffic.
 */
data class ArtCandidate(
    val slot: ArtSlot,
    val url: String,
    val source: ScrapeSource,
    /** The source's own name for the match, so a wrong title-search hit is visible before applying. */
    val matchedTitle: String?,
) {
    val sourceLabel: String
        get() = when (source) {
            ScrapeSource.ScreenScraper -> "ScreenScraper"
            ScrapeSource.SteamGridDb -> "SteamGridDB"
            ScrapeSource.Igdb -> "IGDB"
        }
}

data class ArtCandidateResult(
    val candidates: List<ArtCandidate> = emptyList(),
    /** No source is configured at all — the picker says so instead of showing an empty grid. */
    val credentialsMissing: Boolean = false,
    /** Sources that were configured but returned nothing, so the user knows it was asked. */
    val emptySources: List<ScrapeSource> = emptyList(),
) {
    fun forSlot(slot: ArtSlot): List<ArtCandidate> = candidates.filter { it.slot == slot }
}

/**
 * Gathers artwork choices for one game from every credentialed source at once.
 *
 * [ScrapeCoordinator] deliberately stops at the first source that answers, because a library-wide
 * scrape wants one good answer per game as cheaply as possible. Choosing art by hand wants the
 * opposite: ask everyone, show everything, let the user decide. Both call the same clients, so
 * neither has to know how the other is shaped.
 */
@Singleton
class ArtCandidateFinder @Inject constructor(
    private val hasher: RomHasher,
    private val screenScraper: ScreenScraperClient,
    private val steamGridDb: SteamGridDbClient,
    private val igdb: IgdbClient,
    private val mediaCache: MediaCache,
    private val preferences: ShellPreferences,
) {
    /**
     * Runs the three lookups concurrently. A source that throws is treated as a source that found
     * nothing: one dead API key must not cost the user the other two sets of artwork.
     */
    suspend fun findCandidates(game: Game, searchTitle: String? = null): ArtCandidateResult {
        val credentials = preferences.credentials.first()
        if (!credentials.hasAny) return ArtCandidateResult(credentialsMissing = true)

        val title = searchTitle?.trim()?.takeIf { it.isNotEmpty() } ?: game.title

        val matches = coroutineScope {
            val ss = async {
                if (credentials.hasScreenScraper) {
                    runCatching { screenScraper.lookup(game, hasher.hash(game), credentials) }
                        .getOrNull()
                } else {
                    null
                }
            }
            val sgdb = async {
                if (credentials.hasSteamGridDb) {
                    runCatching { steamGridDb.lookup(title, credentials.steamGridDbKey) }.getOrNull()
                } else {
                    null
                }
            }
            val ig = async {
                if (credentials.hasIgdb) {
                    runCatching {
                        igdb.lookup(title, credentials.igdbClientId, credentials.igdbClientSecret)
                    }.getOrNull()
                } else {
                    null
                }
            }
            listOfNotNull(ss.await(), sgdb.await(), ig.await())
        }

        val configured = buildList {
            if (credentials.hasScreenScraper) add(ScrapeSource.ScreenScraper)
            if (credentials.hasSteamGridDb) add(ScrapeSource.SteamGridDb)
            if (credentials.hasIgdb) add(ScrapeSource.Igdb)
        }
        val answered = matches.map { it.source }.toSet()

        val candidates = matches.flatMap { match ->
            buildList {
                match.boxArtUrl?.let { add(candidate(ArtSlot.BoxArt, it, match)) }
                match.heroUrl?.let { add(candidate(ArtSlot.Hero, it, match)) }
                match.logoUrl?.let { add(candidate(ArtSlot.Logo, it, match)) }
                // Stills make perfectly good backgrounds and are often the only wide art a
                // title-search source returns.
                match.screenshotUrls.take(SCREENSHOT_LIMIT).forEach {
                    add(candidate(ArtSlot.Hero, it, match))
                }
            }
        }.distinctBy { it.slot to it.url }

        return ArtCandidateResult(
            candidates = candidates,
            emptySources = configured.filterNot { it in answered },
        )
    }

    /** Downloads a chosen candidate into the shared media cache and returns its local path. */
    suspend fun materialize(candidate: ArtCandidate): String? =
        mediaCache.fetchImage(candidate.url)

    private fun candidate(slot: ArtSlot, url: String, match: ScrapeMatch) = ArtCandidate(
        slot = slot,
        url = url,
        source = match.source,
        matchedTitle = match.title,
    )

    private companion object {
        const val SCREENSHOT_LIMIT = 6
    }
}
