package com.arcadia.shell.scraper

import android.util.Log
import com.arcadia.shell.datastore.ScraperCredentials
import com.arcadia.shell.datastore.TrailerSourcePreference
import com.arcadia.shell.model.Game
import com.arcadia.shell.model.TrailerRef
import com.arcadia.shell.model.TrailerRefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves a playable trailer reference for a game (Playnite-style).
 *
 * Default [TrailerSourcePreference.Auto] order:
 * 1. ScreenScraper video (when credentialed)
 * 2. IGDB YouTube / Steam app mp4 (when credentialed)
 * 3. YouTube title search (no API key)
 * 4. Steam store title search (no API key)
 *
 * Direct remote files are cached locally so ScreenScraper CDN auth does not block ExoPlayer later.
 */
@Singleton
class TrailerResolver @Inject constructor(
    private val screenScraper: ScreenScraperClient,
    private val igdb: IgdbClient,
    private val steamStore: SteamStoreClient,
    private val youTube: YouTubeTrailerClient,
    private val hasher: RomHasher,
    private val mediaCache: MediaCache,
) {
    /**
     * Persists a known encoded trailer (caching remote files) or looks one up when [knownEncoded]
     * is null. Returns null immediately when [scrapeEnabled] is false.
     */
    suspend fun materializeOrResolve(
        game: Game,
        credentials: ScraperCredentials,
        knownEncoded: String? = null,
        scrapeEnabled: Boolean = true,
        source: TrailerSourcePreference = TrailerSourcePreference.Auto,
    ): String? = withContext(Dispatchers.IO) {
        if (!scrapeEnabled) {
            Log.i(TAG, "Trailer scrape disabled; skip ${game.fileName}")
            return@withContext null
        }
        knownEncoded?.let { encoded ->
            val persisted = persistDirect(encoded)
            if (persisted != null) return@withContext persisted
            Log.w(TAG, "Failed to materialize known trailer for ${game.fileName}")
        }
        resolve(game, credentials, scrapeEnabled = true, source = source)
    }

    suspend fun resolve(
        game: Game,
        credentials: ScraperCredentials,
        scrapeEnabled: Boolean = true,
        source: TrailerSourcePreference = TrailerSourcePreference.Auto,
    ): String? = withContext(Dispatchers.IO) {
        if (game.isAndroidApp) return@withContext null
        if (!scrapeEnabled) {
            Log.i(TAG, "Trailer scrape disabled; skip ${game.fileName}")
            return@withContext null
        }

        when (source) {
            TrailerSourcePreference.ScreenScraper ->
                return@withContext screenScraperTrailer(game, credentials)?.let { persistDirect(it) }
            TrailerSourcePreference.Igdb ->
                return@withContext igdbTrailer(game, credentials)?.let { persistDirect(it) }
            TrailerSourcePreference.YouTube ->
                return@withContext youTubeTrailer(game)?.let { persistDirect(it) }
            TrailerSourcePreference.Steam ->
                return@withContext steamTitleTrailer(game)?.let { persistDirect(it) }
            TrailerSourcePreference.Auto -> Unit
        }

        screenScraperTrailer(game, credentials)?.let { encoded ->
            Log.i(TAG, "ScreenScraper trailer for ${game.fileName}: $encoded")
            return@withContext persistDirect(encoded)
        }
        igdbTrailer(game, credentials)?.let { encoded ->
            Log.i(TAG, "IGDB/Steam trailer for ${game.fileName}: $encoded")
            return@withContext persistDirect(encoded)
        }
        youTubeTrailer(game)?.let { encoded ->
            Log.i(TAG, "YouTube trailer for ${game.fileName}: $encoded")
            return@withContext persistDirect(encoded)
        }
        steamTitleTrailer(game)?.let { encoded ->
            Log.i(TAG, "Steam title-search trailer for ${game.fileName}: $encoded")
            return@withContext persistDirect(encoded)
        }

        Log.i(TAG, "No trailer found for ${game.fileName}")
        null
    }

    private suspend fun screenScraperTrailer(
        game: Game,
        credentials: ScraperCredentials,
    ): String? {
        if (!credentials.hasScreenScraper) return null
        val hashes = runCatching { hasher.hash(game) }.getOrNull()
        return screenScraper.lookup(game, hashes, credentials)?.trailerUrl
    }

    private suspend fun igdbTrailer(
        game: Game,
        credentials: ScraperCredentials,
    ): String? {
        if (!credentials.hasIgdb) return null
        return igdb.lookupTrailer(
            title = game.title,
            clientId = credentials.igdbClientId,
            clientSecret = credentials.igdbClientSecret,
        )
    }

    private suspend fun youTubeTrailer(game: Game): String? {
        val videoIds = youTube.findTrailerVideoIds(
            title = game.title,
            platformName = game.platform.displayName,
        )
        if (videoIds.isEmpty()) return null
        return TrailerRefs.youtube(videoIds)
    }

    private suspend fun steamTitleTrailer(game: Game): String? =
        steamStore.findTrailerByTitle(game.title)

    private suspend fun persistDirect(encoded: String): String? {
        val ref = TrailerRefs.parse(encoded) ?: run {
            Log.w(TAG, "Unparseable trailer ref: $encoded")
            return null
        }
        return when (ref) {
            is TrailerRef.YouTube -> TrailerRefs.encode(ref)
            is TrailerRef.Direct -> {
                val local = mediaCache.fetch(
                    url = ref.uri,
                    headers = screenScraperHeadersFor(ref.uri),
                )
                when {
                    local != null -> {
                        Log.i(TAG, "Cached trailer → $local")
                        local
                    }
                    ref.uri.startsWith("http", ignoreCase = true) -> {
                        Log.i(TAG, "Using remote trailer URL (cache miss): ${ref.uri}")
                        ref.uri
                    }
                    else -> null
                }
            }
        }
    }

    private fun screenScraperHeadersFor(url: String): Map<String, String> =
        if (url.contains("screenscraper", ignoreCase = true)) {
            mapOf("Referer" to "https://www.screenscraper.fr/")
        } else {
            emptyMap()
        }

    private companion object {
        const val TAG = "TrailerResolver"
    }
}
