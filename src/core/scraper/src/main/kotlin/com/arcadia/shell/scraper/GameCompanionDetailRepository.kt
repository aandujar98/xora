package com.arcadia.shell.scraper

import android.util.Log
import com.arcadia.shell.model.Game
import com.arcadia.shell.datastore.ShellPreferences
import com.arcadia.shell.scraper.util.BoundedLruCache
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Everything the companion bottom screen needs that is not already on [Game]: a manual to page
 * through plus the box-back facts (players, rating, publisher) the library rows never show.
 */
data class GameCompanionDetail(
    /** Absolute local path to a manual — a sidecar file beside the ROM, or a cached download. */
    val manualPath: String? = null,
    /** True once a lookup finished, whether or not a manual was found. */
    val manualResolved: Boolean = false,
    val synopsis: String? = null,
    /** Free-form as ScreenScraper reports it, e.g. "1", "1-2", "4". */
    val players: String? = null,
    val ratingPercent: Int? = null,
    val publisher: String? = null,
    val developer: String? = null,
    val genre: String? = null,
    val releaseYear: Int? = null,
) {
    val hasManual: Boolean get() = !manualPath.isNullOrBlank()
}

/**
 * Resolves [GameCompanionDetail] for a launched game.
 *
 * Manuals are looked for in order of what costs least: one already scraped into [GameManualStore],
 * then a sidecar beside the ROM, which works offline. ScreenScraper is asked only when credentials
 * exist, and its answer is memoised: the panel can be opened and closed repeatedly during one
 * session, and a manual is a large download.
 */
@Singleton
class GameCompanionDetailRepository @Inject constructor(
    private val localLocator: GameManualLocator,
    private val screenScraper: ScreenScraperClient,
    private val manualStore: GameManualStore,
    private val preferences: ShellPreferences,
    private val hasher: RomHasher,
) {
    private val cache = BoundedLruCache<String, GameCompanionDetail>(MAX_CACHED_DETAILS)

    fun cached(gameId: String): GameCompanionDetail? = cache.get(gameId)

    suspend fun detailFor(game: Game): GameCompanionDetail {
        cache.get(game.id)?.let { return it }
        val resolved = resolve(game)
        return cache.putIfAbsent(game.id, resolved) ?: resolved
    }

    fun clearCache() {
        cache.clear()
    }

    private suspend fun resolve(game: Game): GameCompanionDetail {
        val storedManual = runCatching { manualStore.find(game.id) }.getOrNull()
        val localManual = storedManual
            ?: runCatching { localLocator.findLocalManual(game) }.getOrNull()

        val credentials = preferences.credentials.first()
        if (!credentials.hasScreenScraper) {
            return GameCompanionDetail(manualPath = localManual, manualResolved = true)
        }

        val hashes = runCatching { hasher.hash(game) }.getOrNull()
        val detail = runCatching {
            screenScraper.lookupCompanionDetail(game, hashes, credentials)
        }.onFailure { Log.w(TAG, "Companion detail lookup failed for ${game.title}", it) }
            .getOrNull()
            ?: return GameCompanionDetail(manualPath = localManual, manualResolved = true)

        // An on-demand download is kept in the same store the scrape writes to, so opening the
        // manual once is enough for it to be there instantly on every later launch.
        val manualPath = localManual ?: detail.manualUrl?.let { url ->
            runCatching {
                manualStore.findOrDownload(game.id, url, detail.manualFormat)
            }.onFailure { Log.w(TAG, "Manual download failed for ${game.title}", it) }
                .getOrNull()
        }

        return GameCompanionDetail(
            manualPath = manualPath,
            manualResolved = true,
            synopsis = detail.synopsis,
            players = detail.players,
            ratingPercent = detail.ratingPercent,
            publisher = detail.publisher,
            developer = detail.developer,
            genre = detail.genre,
            releaseYear = detail.releaseYear,
        )
    }

    private companion object {
        const val TAG = "GameCompanionDetail"
        const val MAX_CACHED_DETAILS = 24
    }
}
