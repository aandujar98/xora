package com.arcadia.shell.scraper.insight

import com.arcadia.shell.datastore.ScraperCredentials
import com.arcadia.shell.model.Game
import com.arcadia.shell.scraper.IgdbClient
import com.arcadia.shell.scraper.MediaCache
import com.arcadia.shell.scraper.ScreenScraperClient
import com.arcadia.shell.scraper.util.BoundedLruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves up to 6 local screenshot paths for the selected XMB game.
 *
 * Prefer dedicated ScreenScraper / IGDB stills when credentials exist; otherwise fall back to
 * already-scraped hero art so the panel is not empty for an offline library.
 */
@Singleton
class GameScreenshotRepository @Inject constructor(
    private val screenScraper: ScreenScraperClient,
    private val igdb: IgdbClient,
    private val mediaCache: MediaCache,
) {
    private val cache = BoundedLruCache<String, List<String>>(MAX_CACHED_GAMES)
    private val fetchLimiter = Semaphore(MAX_CONCURRENT_FETCHES)

    suspend fun screenshotsFor(
        game: Game,
        credentials: ScraperCredentials,
        limit: Int = MAX_SCREENSHOTS,
    ): List<String> = withContext(Dispatchers.IO) {
        cache.get(game.id)?.let { return@withContext it }
        val resolved = resolve(game, credentials, limit)
        cache.putIfAbsent(game.id, resolved) ?: resolved
    }

    fun cached(gameId: String): List<String>? = cache.get(gameId)

    fun clearCache() {
        cache.clear()
    }

    private suspend fun resolve(
        game: Game,
        credentials: ScraperCredentials,
        limit: Int,
    ): List<String> = coroutineScope {
        if (game.isAndroidApp) return@coroutineScope localFallback(game, limit)

        val ssDeferred = async {
            if (!credentials.hasScreenScraper) return@async emptyList()
            runCatching {
                screenScraper.lookup(game, hashes = null, credentials)
                    ?.screenshotUrls
                    .orEmpty()
            }.getOrDefault(emptyList())
        }
        val igdbDeferred = async {
            if (!credentials.hasIgdb) return@async emptyList()
            runCatching {
                igdb.lookupScreenshots(
                    title = game.title,
                    clientId = credentials.igdbClientId,
                    clientSecret = credentials.igdbClientSecret,
                    limit = limit,
                )
            }.getOrDefault(emptyList())
        }

        val remoteUrls = (ssDeferred.await() + igdbDeferred.await())
            .distinct()
            .take(limit)

        val downloaded = remoteUrls.mapNotNull { url ->
            fetchLimiter.withPermit {
                runCatching { mediaCache.fetch(url) }.getOrNull()
            }
        }

        downloaded.ifEmpty { localFallback(game, limit) }
    }

    private fun localFallback(game: Game, limit: Int): List<String> =
        listOfNotNull(game.heroImagePath, game.boxArtPath)
            .distinct()
            .take(limit)

    private companion object {
        const val MAX_SCREENSHOTS = 6
        /** Keep artwork downloads from stampeding the heap on ~7–8GB handhelds. */
        const val MAX_CONCURRENT_FETCHES = 2
        /** Path lists only — still bound so scrubbing a huge library cannot grow forever. */
        const val MAX_CACHED_GAMES = 40
    }
}
