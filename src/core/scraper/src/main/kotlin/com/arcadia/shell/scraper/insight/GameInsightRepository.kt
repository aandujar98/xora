package com.arcadia.shell.scraper.insight

import com.arcadia.shell.datastore.ScraperCredentials
import com.arcadia.shell.model.Game
import com.arcadia.shell.scraper.IgdbClient
import com.arcadia.shell.scraper.util.BoundedLruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves evergreen + optional "latest" blurbs for the selected XMB game.
 *
 * Sources (best-effort, parallel): Wikipedia summary, Speedrun.com, IGDB (when credentials exist),
 * plus local library trivia that always works offline.
 */
@Singleton
class GameInsightRepository @Inject constructor(
    private val wikipedia: WikipediaSummaryClient,
    private val speedrun: SpeedrunClient,
    private val igdb: IgdbClient,
) {
    /** Cap prevents unbounded growth while scrubbing a large library on handhelds. */
    private val cache = BoundedLruCache<String, GameInsight>(MAX_CACHED_INSIGHTS)

    suspend fun insightFor(
        game: Game,
        credentials: ScraperCredentials,
    ): GameInsight = withContext(Dispatchers.IO) {
        cache.get(game.id)?.let { return@withContext it }
        val resolved = resolve(game, credentials)
        cache.putIfAbsent(game.id, resolved) ?: resolved
    }

    fun cached(gameId: String): GameInsight? = cache.get(gameId)

    fun clearCache() {
        cache.clear()
    }

    private companion object {
        const val MAX_CACHED_INSIGHTS = 48
    }

    private suspend fun resolve(
        game: Game,
        credentials: ScraperCredentials,
    ): GameInsight = coroutineScope {
        val localTrivia = localTrivia(game).toMutableList()
        val wikiDeferred = async { runCatching { wikipedia.lookup(game.title) }.getOrNull() }
        val speedDeferred = async { runCatching { speedrun.lookup(game.title) }.getOrNull() }
        val igdbDeferred = async {
            if (!credentials.hasIgdb) return@async null
            runCatching {
                igdb.lookupInsight(
                    title = game.title,
                    clientId = credentials.igdbClientId,
                    clientSecret = credentials.igdbClientSecret,
                )
            }.getOrNull()
        }

        val wiki = wikiDeferred.await()
        val speed = speedDeferred.await()
        val igdbInsight = igdbDeferred.await()

        wiki?.description?.takeIf { it.isNotBlank() }?.let { desc ->
            if (localTrivia.none { it.equals(desc, ignoreCase = true) }) {
                localTrivia.add(0, desc.replaceFirstChar { it.uppercaseChar() })
            }
        }

        val summaryPair = when {
            !igdbInsight?.summary.isNullOrBlank() ->
                igdbInsight!!.summary to InsightSource.Igdb
            wiki != null ->
                wiki.extract to InsightSource.Wikipedia
            localTrivia.isNotEmpty() ->
                localTrivia.first() to InsightSource.Local
            else -> null to null
        }

        GameInsight(
            gameId = game.id,
            title = game.title,
            summary = summaryPair.first,
            summarySource = summaryPair.second,
            releaseYear = igdbInsight?.releaseYear ?: speed?.releaseYear,
            developer = igdbInsight?.developer,
            genre = igdbInsight?.genre,
            speedrunBlurb = speed?.blurb,
            trivia = localTrivia,
            platformLabel = game.platform.displayName,
        )
    }

    private fun localTrivia(game: Game): List<String> = buildList {
        add("Part of your ${game.platform.displayName} library on XOrA.")
        if (game.favorite) add("Marked as a favourite.")
        if (game.playCount > 0) {
            add(
                if (game.playCount == 1) "You've launched this once."
                else "Launched ${game.playCount} times.",
            )
        }
        if (game.playTimeMs >= 60_000L) {
            val minutes = (game.playTimeMs / 60_000L).toInt()
            add("About $minutes min recorded playtime.")
        }
        if (game.lastPlayedAt != null) add("Recently in your play history.")
        if (game.isAndroidApp) add("Installed Android app — open with A.")
    }
}
