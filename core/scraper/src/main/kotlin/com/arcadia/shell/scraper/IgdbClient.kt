package com.arcadia.shell.scraper

import com.arcadia.shell.model.TrailerRefs
import com.arcadia.shell.scraper.insight.IgdbInsight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Last-resort artwork source, and the broadest one for anything obscure.
 *
 * IGDB authenticates through Twitch with a client-credentials token that has to be requested and
 * then reused; fetching a fresh token per game would exhaust the rate limit almost immediately.
 */
@Singleton
class IgdbClient @Inject constructor(
    private val httpClient: OkHttpClient,
    private val json: Json,
    private val steamStore: SteamStoreClient,
) {
    private val tokenLock = Mutex()
    private var cachedToken: String? = null
    private var tokenExpiresAt: Long = 0

    suspend fun lookup(
        title: String,
        clientId: String,
        clientSecret: String,
    ): ScrapeMatch? = withContext(Dispatchers.IO) {
        if (title.isBlank() || clientId.isBlank() || clientSecret.isBlank()) {
            return@withContext null
        }

        val token = accessToken(clientId, clientSecret) ?: return@withContext null

        // IGDB's query language goes in the request body rather than the url.
        val query = """search "${title.replace("\"", "")}"; """ +
            "fields name,cover.image_id,artworks.image_id,screenshots.image_id,videos.video_id," +
            "external_games.uid,external_games.category; limit 1;"

        val request = Request.Builder()
            .url("$API_URL/games")
            .header("Client-ID", clientId)
            .header("Authorization", "Bearer $token")
            .post(query.toRequestBody())
            .build()

        runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val body = response.body.string()

                val game = json.decodeFromString<List<IgdbGame>>(body).firstOrNull()
                    ?: return@runCatching null

                ScrapeMatch(
                    title = game.name,
                    heroUrl = game.artworks?.firstOrNull()?.imageId?.let { imageUrl(it, "1080p") },
                    logoUrl = null,
                    boxArtUrl = game.cover?.imageId?.let { imageUrl(it, "cover_big") },
                    screenshotUrls = game.screenshotUrls(limit = 6),
                    trailerUrl = game.encodedTrailer(),
                    source = ScrapeSource.Igdb,
                ).takeIf { it.hasArtwork || it.trailerUrl != null || it.screenshotUrls.isNotEmpty() }
            }
        }.getOrNull()
    }

    /**
     * Gameplay stills for the XMB insight panel. Requires Twitch/IGDB credentials.
     */
    suspend fun lookupScreenshots(
        title: String,
        clientId: String,
        clientSecret: String,
        limit: Int = 6,
    ): List<String> = withContext(Dispatchers.IO) {
        if (title.isBlank() || clientId.isBlank() || clientSecret.isBlank() || limit <= 0) {
            return@withContext emptyList()
        }
        val token = accessToken(clientId, clientSecret) ?: return@withContext emptyList()
        val query = """search "${title.replace("\"", "")}"; """ +
            "fields screenshots.image_id; limit 1;"

        val request = Request.Builder()
            .url("$API_URL/games")
            .header("Client-ID", clientId)
            .header("Authorization", "Bearer $token")
            .post(query.toRequestBody())
            .build()

        runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching emptyList()
                val game = json.decodeFromString<List<IgdbGame>>(response.body.string())
                    .firstOrNull()
                    ?: return@runCatching emptyList()
                game.screenshotUrls(limit)
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Metadata / summary for the XMB insight panel. Requires Twitch/IGDB credentials.
     */
    suspend fun lookupInsight(
        title: String,
        clientId: String,
        clientSecret: String,
    ): IgdbInsight? = withContext(Dispatchers.IO) {
        if (title.isBlank() || clientId.isBlank() || clientSecret.isBlank()) {
            return@withContext null
        }
        val token = accessToken(clientId, clientSecret) ?: return@withContext null
        val query = """search "${title.replace("\"", "")}"; """ +
            "fields name,summary,first_release_date,genres.name," +
            "involved_companies.company.name,involved_companies.developer; limit 1;"

        val request = Request.Builder()
            .url("$API_URL/games")
            .header("Client-ID", clientId)
            .header("Authorization", "Bearer $token")
            .post(query.toRequestBody())
            .build()

        runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val game = json.decodeFromString<List<IgdbInsightGame>>(response.body.string())
                    .firstOrNull()
                    ?: return@runCatching null
                val insight = IgdbInsight(
                    summary = game.summary?.trim()?.takeIf { it.isNotEmpty() },
                    releaseYear = game.firstReleaseDate?.let(::unixToYear),
                    developer = game.involvedCompanies
                        ?.firstOrNull { it.developer == true }
                        ?.company
                        ?.name
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?: game.involvedCompanies?.firstOrNull()?.company?.name?.trim()
                            ?.takeIf { it.isNotEmpty() },
                    genre = game.genres?.firstOrNull()?.name?.trim()?.takeIf { it.isNotEmpty() },
                )
                insight.takeIf {
                    !it.summary.isNullOrBlank() ||
                        it.releaseYear != null ||
                        !it.developer.isNullOrBlank() ||
                        !it.genre.isNullOrBlank()
                }
            }
        }.getOrNull()
    }

    /**
     * Trailer-only lookup for games that already have artwork from another source.
     * Prefers YouTube (IGDB videos), then a Steam store mp4 when a Steam app id is present.
     */
    suspend fun lookupTrailer(
        title: String,
        clientId: String,
        clientSecret: String,
    ): String? = withContext(Dispatchers.IO) {
        if (title.isBlank() || clientId.isBlank() || clientSecret.isBlank()) {
            return@withContext null
        }

        val token = accessToken(clientId, clientSecret) ?: return@withContext null
        val query = """search "${title.replace("\"", "")}"; """ +
            "fields videos.video_id,external_games.uid,external_games.category; limit 1;"

        val request = Request.Builder()
            .url("$API_URL/games")
            .header("Client-ID", clientId)
            .header("Authorization", "Bearer $token")
            .post(query.toRequestBody())
            .build()

        runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val game = json.decodeFromString<List<IgdbGame>>(response.body.string())
                    .firstOrNull()
                    ?: return@runCatching null
                game.encodedTrailer()
                    ?: game.steamAppId()?.let { steamStore.trailerMp4Url(it) }
            }
        }.getOrNull()
    }

    private suspend fun accessToken(clientId: String, clientSecret: String): String? =
        tokenLock.withLock {
            val existing = cachedToken
            if (existing != null && System.currentTimeMillis() < tokenExpiresAt) return existing

            val url = "$TOKEN_URL?client_id=$clientId&client_secret=$clientSecret" +
                "&grant_type=client_credentials"

            val request = Request.Builder().url(url).post(ByteArray(0).toRequestBody()).build()

            runCatching {
                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@runCatching null
                    val token = json.decodeFromString<TokenResponse>(response.body.string())

                    cachedToken = token.accessToken
                    // Renewed a minute early so a request cannot fail on a token that expires
                    // while it is in flight.
                    tokenExpiresAt = System.currentTimeMillis() +
                        (token.expiresIn * 1000L) - TOKEN_SAFETY_MARGIN_MS
                    token.accessToken
                }
            }.getOrNull()
        }

    private fun imageUrl(imageId: String, size: String): String =
        companionImageUrl(imageId, size)

    private fun unixToYear(epochSeconds: Long): Int? {
        if (epochSeconds <= 0L) return null
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US)
        cal.timeInMillis = epochSeconds * 1000L
        return cal.get(Calendar.YEAR).takeIf { it in 1970..2100 }
    }

    @Serializable
    private data class IgdbInsightGame(
        val name: String? = null,
        val summary: String? = null,
        @SerialName("first_release_date") val firstReleaseDate: Long? = null,
        val genres: List<IgdbNamed>? = null,
        @SerialName("involved_companies") val involvedCompanies: List<IgdbCompanyLink>? = null,
    )

    @Serializable
    private data class IgdbNamed(val name: String? = null)

    @Serializable
    private data class IgdbCompanyLink(
        val developer: Boolean? = null,
        val company: IgdbNamed? = null,
    )

    @Serializable
    private data class TokenResponse(
        @SerialName("access_token") val accessToken: String,
        @SerialName("expires_in") val expiresIn: Long = 0,
    )

    @Serializable
    private data class IgdbGame(
        val name: String? = null,
        val cover: IgdbImage? = null,
        val artworks: List<IgdbImage>? = null,
        val screenshots: List<IgdbImage>? = null,
        val videos: List<IgdbVideo>? = null,
        @SerialName("external_games") val externalGames: List<IgdbExternalGame>? = null,
    ) {
        fun encodedTrailer(): String? =
            videos?.firstNotNullOfOrNull { video ->
                video.videoId?.trim()?.takeIf { it.isNotEmpty() }?.let(TrailerRefs::youtube)
            }

        fun steamAppId(): String? =
            externalGames
                ?.firstOrNull { it.category == STEAM_EXTERNAL_CATEGORY }
                ?.uid
                ?.trim()
                ?.takeIf { it.isNotEmpty() }

        fun screenshotUrls(limit: Int): List<String> =
            screenshots
                .orEmpty()
                .mapNotNull { image ->
                    image.imageId?.trim()?.takeIf { it.isNotEmpty() }
                        ?.let { companionImageUrl(it, "screenshot_med") }
                }
                .distinct()
                .take(limit)
    }

    @Serializable
    private data class IgdbImage(@SerialName("image_id") val imageId: String? = null)

    @Serializable
    private data class IgdbVideo(@SerialName("video_id") val videoId: String? = null)

    @Serializable
    private data class IgdbExternalGame(
        val uid: String? = null,
        val category: Int? = null,
    )

    private companion object {
        const val TOKEN_URL = "https://id.twitch.tv/oauth2/token"
        const val API_URL = "https://api.igdb.com/v4"
        const val TOKEN_SAFETY_MARGIN_MS = 60_000L
        /** IGDB external_games.category for Steam. */
        const val STEAM_EXTERNAL_CATEGORY = 1

        fun companionImageUrl(imageId: String, size: String): String =
            "https://images.igdb.com/igdb/image/upload/t_$size/$imageId.jpg"
    }
}
