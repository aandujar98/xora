package com.arcadia.shell.scraper

import android.util.Log
import com.arcadia.shell.database.repository.LibraryRepository
import com.arcadia.shell.datastore.ScraperCredentials
import com.arcadia.shell.datastore.ShellPreferences
import com.arcadia.shell.model.Game
import com.arcadia.shell.model.ScrapeState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

data class ScrapeBatchResult(
    val processed: Int,
    val matched: Int,
    val skipped: Int,
    /** Whether any game is still waiting, without counting the whole queue every batch. */
    val hasMore: Boolean,
    val credentialsMissing: Boolean = false,
)

/**
 * Runs the actual scrape for a batch of games, in a deliberate order of decreasing confidence:
 * an exact hash match first, then title search against the two artwork-focused databases.
 *
 * Every source is optional and independently credentialed, so the pipeline is written to degrade
 * rather than fail: a user with only a SteamGridDB key still gets artwork for most of a library.
 */
@Singleton
class ScrapeCoordinator @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val hasher: RomHasher,
    private val screenScraper: ScreenScraperClient,
    private val steamGridDb: SteamGridDbClient,
    private val igdb: IgdbClient,
    private val mediaCache: MediaCache,
    private val manualStore: GameManualStore,
    private val trailerResolver: TrailerResolver,
    private val preferences: ShellPreferences,
) {
    suspend fun scrapeBatch(limit: Int = DEFAULT_BATCH): ScrapeBatchResult {
        val credentials = preferences.credentials.first()

        if (!credentials.hasAny) {
            return ScrapeBatchResult(
                processed = 0,
                matched = 0,
                skipped = 0,
                hasMore = false,
                credentialsMissing = true,
            )
        }

        val pending = libraryRepository.pendingScrapes(limit)
        var matched = 0
        var skipped = 0

        for (game in pending) {
            // Android app rows are never scrapable; the DAO already excludes them, but a belt-and-
            // braces guard keeps a future query change from hammering ScreenScraper with packages.
            if (game.isAndroidApp) {
                libraryRepository.markScrapeState(game.id, ScrapeState.NoMatch)
                skipped++
                continue
            }

            val result = runCatching { scrapeOne(game, credentials) }.getOrElse { false }
            if (result) matched++ else skipped++

            // A fixed pause between games keeps well inside the per-source rate limits, which are
            // strict enough that a large library would otherwise be throttled or blocked outright.
            delay(REQUEST_SPACING_MS)
        }

        return ScrapeBatchResult(
            processed = pending.size,
            matched = matched,
            skipped = skipped,
            hasMore = libraryRepository.pendingScrapes(1).isNotEmpty(),
        )
    }

    /** Returns true when artwork or a better title was found and stored. */
    private suspend fun scrapeOne(game: Game, credentials: ScraperCredentials): Boolean {
        // Always hash when possible — RetroAchievements launcher lookup needs MD5s even when the
        // user has no ScreenScraper account.
        val hashes = hasher.hash(game)

        hashes?.let {
            libraryRepository.setHashes(game.id, it.crc32, it.md5, it.sha1)
        }

        val match = findMatch(game, hashes, credentials)

        if (match == null) {
            // NoMatch rather than Pending, so a fruitless lookup is not retried on every scan.
            libraryRepository.markScrapeState(game.id, ScrapeState.NoMatch)
            return false
        }

        val heroPath = match.heroUrl?.let { mediaCache.fetch(it) }
        val logoPath = match.logoUrl?.let { mediaCache.fetch(it) }
        val boxArtPath = match.boxArtUrl?.let { mediaCache.fetch(it) }
        // Prefetch screenshots into the content cache so the XMB panel can resolve them quickly.
        match.screenshotUrls.take(6).forEach { url ->
            runCatching { mediaCache.fetch(url) }
        }

        if (heroPath == null && logoPath == null && boxArtPath == null) {
            libraryRepository.markScrapeState(game.id, ScrapeState.NoMatch)
            return false
        }

        libraryRepository.applyScrapeResult(
            gameId = game.id,
            // A scraped title is only trusted when it came from a hash-accurate match; a title
            // search returning the wrong game would otherwise rename the entry too.
            title = match.title?.takeIf { match.source == ScrapeSource.ScreenScraper } ?: game.title,
            heroPath = heroPath,
            logoPath = logoPath,
            boxArtPath = boxArtPath,
        )

        // Trailer and manual are best-effort extras and must not undo a successful artwork match.
        val settings = preferences.settings.first()

        if (settings.manualScrapeEnabled && match.manualUrl != null) {
            runCatching {
                manualStore.findOrDownload(
                    gameId = game.id,
                    url = match.manualUrl,
                    format = match.manualFormat,
                )
            }.onFailure { Log.w(TAG, "Manual download failed for ${game.fileName}", it) }
        }

        if (settings.trailerScrapeEnabled && !game.trailerResolved) {
            val trailer = runCatching {
                trailerResolver.materializeOrResolve(
                    game = game.copy(title = match.title ?: game.title),
                    credentials = credentials,
                    knownEncoded = match.trailerUrl,
                    scrapeEnabled = true,
                    source = settings.trailerSourcePreference,
                )
            }.onFailure {
                Log.w(TAG, "Trailer resolve failed for ${game.fileName}", it)
            }.getOrNull()
            // Persist successes and definitive misses so idle does not immediately re-hit the net;
            // pipeline migration clears prior null-resolved rows when YouTube fallback is added.
            libraryRepository.setTrailer(game.id, trailer)
        }
        return true
    }

    private suspend fun findMatch(
        game: Game,
        hashes: RomHashes?,
        credentials: ScraperCredentials,
    ): ScrapeMatch? {
        val preference = runCatching {
            ScraperPreference.valueOf(
                preferences.resolveScraperPreference(game.id, game.platformId),
            )
        }.getOrDefault(ScraperPreference.Auto)

        val forced = preference.toSourceOrNull()
        if (forced != null) {
            return lookupSource(forced, game, hashes, credentials)
        }

        screenScraper.lookup(game, hashes, credentials)?.let { return it }

        if (credentials.hasSteamGridDb) {
            steamGridDb.lookup(game.title, credentials.steamGridDbKey)?.let { return it }
        }

        if (credentials.hasIgdb) {
            igdb.lookup(game.title, credentials.igdbClientId, credentials.igdbClientSecret)
                ?.let { return it }
        }

        return null
    }

    private suspend fun lookupSource(
        source: ScrapeSource,
        game: Game,
        hashes: RomHashes?,
        credentials: ScraperCredentials,
    ): ScrapeMatch? = when (source) {
        ScrapeSource.ScreenScraper -> screenScraper.lookup(game, hashes, credentials)
        ScrapeSource.SteamGridDb ->
            if (credentials.hasSteamGridDb) {
                steamGridDb.lookup(game.title, credentials.steamGridDbKey)
            } else {
                null
            }
        ScrapeSource.Igdb ->
            if (credentials.hasIgdb) {
                igdb.lookup(game.title, credentials.igdbClientId, credentials.igdbClientSecret)
            } else {
                null
            }
    }

    private companion object {
        const val TAG = "ScrapeCoordinator"
        const val DEFAULT_BATCH = 25
        const val REQUEST_SPACING_MS = 350L
    }
}
