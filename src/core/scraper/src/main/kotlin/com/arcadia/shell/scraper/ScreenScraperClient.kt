package com.arcadia.shell.scraper

import com.arcadia.shell.datastore.ScraperCredentials
import com.arcadia.shell.model.Game
import com.arcadia.shell.model.TrailerRefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Hash-based lookups against ScreenScraper.
 *
 * This is queried first because a CRC or SHA-1 match identifies an exact dump, including the right
 * regional release, where a title search can only guess. The tradeoff is that it requires developer
 * credentials in addition to a user account, so it is often unavailable and the title-based sources
 * have to carry the work.
 */
@Singleton
class ScreenScraperClient @Inject constructor(
    private val httpClient: OkHttpClient,
    private val json: Json,
) {
    suspend fun lookup(
        game: Game,
        hashes: RomHashes?,
        credentials: ScraperCredentials,
    ): ScrapeMatch? = withContext(Dispatchers.IO) {
        val body = requestJeuInfos(game, hashes, credentials) ?: return@withContext null
        runCatching {
            json.decodeFromString<SsEnvelope>(body).response?.jeu?.toMatch()
        }.getOrNull()
    }

    /**
     * Second, narrower read of the same `jeuInfos` payload for the companion panel: the `manuel`
     * media plus the detail fields the shell does not persist on [Game] (players, rating, publisher,
     * synopsis).
     *
     * Deliberately decoded through its own DTOs. Widening [SsEnvelope] would put artwork scraping —
     * the feature the whole library depends on — at the mercy of a field whose shape only this panel
     * needs.
     */
    suspend fun lookupCompanionDetail(
        game: Game,
        hashes: RomHashes?,
        credentials: ScraperCredentials,
    ): ScreenScraperDetail? = withContext(Dispatchers.IO) {
        val body = requestJeuInfos(game, hashes, credentials) ?: return@withContext null
        runCatching {
            json.decodeFromString<SsDetailEnvelope>(body).response?.jeu?.toDetail()
        }.getOrNull()
    }

    /**
     * Download URL for a ScreenScraper **system** media asset (`mediaSysteme.php`).
     *
     * Preferred media types for console product art: `illustration`, `photo`, `controleur`, `wheel`.
     * Region suffix is optional (`illustration(us)` vs bare `illustration`).
     */
    fun systemMediaDownloadUrl(
        systemId: Int,
        mediaType: String,
        region: String?,
        credentials: ScraperCredentials,
    ): String? {
        if (!credentials.hasScreenScraper) return null
        val media = if (region.isNullOrBlank()) mediaType else "$mediaType($region)"
        return SYSTEM_MEDIA_URL.toHttpUrl().newBuilder().apply {
            addQueryParameter("devid", credentials.screenScraperDevId)
            addQueryParameter("devpassword", credentials.screenScraperDevPassword)
            addQueryParameter("softname", SOFT_NAME)
            addQueryParameter("ssid", credentials.screenScraperUser)
            addQueryParameter("sspassword", credentials.screenScraperPassword)
            addQueryParameter("systemeid", systemId.toString())
            addQueryParameter("media", media)
            addQueryParameter("maxwidth", "512")
            addQueryParameter("maxheight", "512")
            addQueryParameter("outputformat", "png")
        }.build().toString()
    }

    private fun requestJeuInfos(
        game: Game,
        hashes: RomHashes?,
        credentials: ScraperCredentials,
    ): String? {
        if (!credentials.hasScreenScraper) return null
        val systemId = game.platform.screenScraperSystemId ?: return null

        val url = BASE_URL.toHttpUrl().newBuilder().apply {
            addQueryParameter("devid", credentials.screenScraperDevId)
            addQueryParameter("devpassword", credentials.screenScraperDevPassword)
            addQueryParameter("softname", SOFT_NAME)
            addQueryParameter("output", "json")
            addQueryParameter("ssid", credentials.screenScraperUser)
            addQueryParameter("sspassword", credentials.screenScraperPassword)
            addQueryParameter("systemeid", systemId.toString())
            addQueryParameter("romnom", game.fileName)
            addQueryParameter("romtaille", game.sizeBytes.toString())

            // Hashes are optional but are the only thing that makes the match exact.
            hashes?.let {
                addQueryParameter("crc", it.crc32)
                addQueryParameter("md5", it.md5)
                addQueryParameter("sha1", it.sha1)
            }
        }.build()

        val request = Request.Builder().url(url).header("User-Agent", SOFT_NAME).build()

        return runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body.string() else null
            }
        }.getOrNull()
    }

    private fun SsGame.toMatch(): ScrapeMatch? {
        val medias = medias.orEmpty()

        val videoUrl = medias.pickUrl("video-normalized", "video")
        val heroUrl = medias.pickUrl("fanart", "mixrbv1", "mixrbv2", "ss")
        val screenshotUrls = medias.pickUrls("ss", limit = 6)
            .filter { it != heroUrl }
            .ifEmpty { medias.pickUrls("ss", limit = 6) }
        val manual = medias.pickMedia("manuel")
        val match = ScrapeMatch(
            title = preferredTitle(),
            // "fanart" is the closest thing to a landscape hero image; a mixed image or plain
            // screenshot is a reasonable second choice.
            heroUrl = heroUrl,
            logoUrl = medias.pickUrl("wheel", "wheel-hd", "screenmarquee"),
            boxArtUrl = medias.pickUrl("box-2D", "box-2D-side", "box-3D"),
            screenshotUrls = screenshotUrls,
            trailerUrl = videoUrl?.let { url ->
                TrailerRefs.extractYouTubeId(url)?.let(TrailerRefs::youtube) ?: url
            },
            manualUrl = manual?.url,
            manualFormat = manual?.format?.trim()?.lowercase()?.takeIf { it.isNotBlank() },
            source = ScrapeSource.ScreenScraper,
        )

        return match.takeIf {
            it.hasArtwork || it.title != null || it.trailerUrl != null || it.screenshotUrls.isNotEmpty()
        }
    }

    private fun SsDetailGame.toDetail(): ScreenScraperDetail {
        val medias = medias.orEmpty()
        val manual = medias.pickLocalized("manuel")
        return ScreenScraperDetail(
            manualUrl = manual?.url,
            manualFormat = manual?.format?.trim()?.lowercase()?.takeIf { it.isNotBlank() },
            synopsis = synopsis.orEmpty().pickLocalizedText(),
            players = joueurs?.text?.trim()?.takeIf { it.isNotBlank() },
            // ScreenScraper grades out of 20; a percentage reads the same on every platform.
            ratingPercent = note?.text?.trim()?.toFloatOrNull()
                ?.let { (it / 20f * 100f).toInt() }
                ?.coerceIn(0, 100),
            publisher = editeur?.text?.trim()?.takeIf { it.isNotBlank() },
            developer = developpeur?.text?.trim()?.takeIf { it.isNotBlank() },
            genre = genres.orEmpty()
                .mapNotNull { it.noms.orEmpty().pickLocalizedText() }
                .distinct()
                .take(MAX_GENRES)
                .joinToString(", ")
                .takeIf { it.isNotBlank() },
            releaseYear = dates.orEmpty()
                .let { entries ->
                    PREFERRED_REGIONS.firstNotNullOfOrNull { region ->
                        entries.firstOrNull { it.region == region }?.text
                    } ?: entries.firstOrNull()?.text
                }
                ?.take(4)
                ?.toIntOrNull(),
        )
    }

    /** Region preference exists because a single game carries a differently spelled name per market. */
    private fun SsGame.preferredTitle(): String? {
        val names = noms.orEmpty()
        return PREFERRED_REGIONS.firstNotNullOfOrNull { region ->
            names.firstOrNull { it.region == region }?.text
        } ?: names.firstOrNull()?.text
    }

    private fun List<SsMedia>.pickUrl(vararg types: String): String? =
        types.firstNotNullOfOrNull { type -> pickMedia(type)?.url }

    /** Like [pickUrl] but keeps the whole entry, for media whose `format` the caller needs too. */
    private fun List<SsMedia>.pickMedia(type: String): SsMedia? {
        val candidates = filter { it.type == type && !it.url.isNullOrBlank() }
        return PREFERRED_REGIONS.firstNotNullOfOrNull { region ->
            candidates.firstOrNull { it.region == region }
        } ?: candidates.firstOrNull()
    }

    /** Collects up to [limit] distinct media urls for a single ScreenScraper type. */
    private fun List<SsMedia>.pickUrls(type: String, limit: Int): List<String> {
        val candidates = filter { it.type == type && !it.url.isNullOrBlank() }
        val ordered = PREFERRED_REGIONS.flatMap { region ->
            candidates.filter { it.region == region }
        } + candidates.filter { media ->
            media.region == null || media.region !in PREFERRED_REGIONS
        }
        return ordered.mapNotNull { it.url }.distinct().take(limit)
    }

    private fun List<SsDetailMedia>.pickLocalized(type: String): SsDetailMedia? {
        val candidates = filter { it.type == type && !it.url.isNullOrBlank() }
        return PREFERRED_REGIONS.firstNotNullOfOrNull { region ->
            candidates.firstOrNull { it.region == region }
        } ?: candidates.firstOrNull()
    }

    private fun List<SsLocalizedText>.pickLocalizedText(): String? {
        val candidates = filter { !it.text.isNullOrBlank() }
        return (
            PREFERRED_LANGUAGES.firstNotNullOfOrNull { language ->
                candidates.firstOrNull { it.langue == language }?.text
            } ?: candidates.firstOrNull()?.text
            )?.trim()?.takeIf { it.isNotBlank() }
    }

    @Serializable
    private data class SsEnvelope(val response: SsResponse? = null)

    @Serializable
    private data class SsResponse(val jeu: SsGame? = null)

    @Serializable
    private data class SsGame(
        val noms: List<SsName>? = null,
        val medias: List<SsMedia>? = null,
    )

    @Serializable
    private data class SsName(val region: String? = null, val text: String? = null)

    @Serializable
    private data class SsMedia(
        val type: String? = null,
        val url: String? = null,
        val region: String? = null,
        @SerialName("format") val format: String? = null,
    )

    @Serializable
    private data class SsDetailEnvelope(val response: SsDetailResponse? = null)

    @Serializable
    private data class SsDetailResponse(val jeu: SsDetailGame? = null)

    @Serializable
    private data class SsDetailGame(
        val medias: List<SsDetailMedia>? = null,
        val synopsis: List<SsLocalizedText>? = null,
        val joueurs: SsPlainText? = null,
        val note: SsPlainText? = null,
        val editeur: SsPlainText? = null,
        val developpeur: SsPlainText? = null,
        val dates: List<SsRegionText>? = null,
        val genres: List<SsGenre>? = null,
    )

    @Serializable
    private data class SsDetailMedia(
        val type: String? = null,
        val url: String? = null,
        val region: String? = null,
        val format: String? = null,
    )

    @Serializable
    private data class SsPlainText(val text: String? = null)

    @Serializable
    private data class SsRegionText(val region: String? = null, val text: String? = null)

    @Serializable
    private data class SsLocalizedText(val langue: String? = null, val text: String? = null)

    @Serializable
    private data class SsGenre(val noms: List<SsLocalizedText>? = null)

    private companion object {
        const val BASE_URL = "https://api.screenscraper.fr/api2/jeuInfos.php"
        const val SYSTEM_MEDIA_URL = "https://api.screenscraper.fr/api2/mediaSysteme.php"
        const val SOFT_NAME = "arcadia"
        const val MAX_GENRES = 3
        val PREFERRED_REGIONS = listOf("us", "wor", "eu", "jp", "ss")
        val PREFERRED_LANGUAGES = listOf("en", "wor", "us", "fr")
    }
}

/**
 * Detail fields the shell reads only for the companion bottom screen. Nothing here is persisted on
 * [Game], so it is resolved per session and cached in memory.
 */
data class ScreenScraperDetail(
    val manualUrl: String? = null,
    /** ScreenScraper's own `format` for the manual media, almost always `pdf`. */
    val manualFormat: String? = null,
    val synopsis: String? = null,
    /** Free-form as ScreenScraper reports it, e.g. "1", "1-2", "4". */
    val players: String? = null,
    val ratingPercent: Int? = null,
    val publisher: String? = null,
    val developer: String? = null,
    val genre: String? = null,
    val releaseYear: Int? = null,
)
