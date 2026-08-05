package com.arcadia.shell.scraper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Title-based artwork lookups against SteamGridDB.
 *
 * This is the strongest source for the shell's specific needs, because its library is organised
 * around exactly the three asset shapes the interface wants: a wide hero, a transparent logo, and a
 * portrait grid tile. Its weakness is that matching is by name, so it depends on the filename having
 * been cleaned well.
 */
@Singleton
class SteamGridDbClient @Inject constructor(
    private val httpClient: OkHttpClient,
    private val json: Json,
) {
    suspend fun lookup(title: String, apiKey: String): ScrapeMatch? = withContext(Dispatchers.IO) {
        if (apiKey.isBlank() || title.isBlank()) return@withContext null

        val gameId = searchGameId(title, apiKey) ?: return@withContext null

        val match = ScrapeMatch(
            title = null,
            heroUrl = firstAssetUrl("heroes", gameId, apiKey),
            logoUrl = firstAssetUrl("logos", gameId, apiKey),
            boxArtUrl = firstAssetUrl("grids", gameId, apiKey),
            source = ScrapeSource.SteamGridDb,
        )

        match.takeIf { it.hasArtwork }
    }

    private fun searchGameId(title: String, apiKey: String): Int? {
        val encoded = title.replace(" ", "%20")
        val response = get("$BASE_URL/search/autocomplete/$encoded", apiKey) ?: return null

        return runCatching {
            json.decodeFromString<SearchEnvelope>(response).data?.firstOrNull()?.id
        }.getOrNull()
    }

    private fun firstAssetUrl(kind: String, gameId: Int, apiKey: String): String? {
        val response = get("$BASE_URL/$kind/game/$gameId", apiKey) ?: return null

        return runCatching {
            json.decodeFromString<AssetEnvelope>(response).data?.firstOrNull()?.url
        }.getOrNull()
    }

    private fun get(url: String, apiKey: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $apiKey")
            .build()

        return runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body.string() else null
            }
        }.getOrNull()
    }

    @Serializable
    private data class SearchEnvelope(val data: List<SearchItem>? = null)

    @Serializable
    private data class SearchItem(val id: Int, val name: String? = null)

    @Serializable
    private data class AssetEnvelope(val data: List<Asset>? = null)

    @Serializable
    private data class Asset(val url: String? = null)

    private companion object {
        const val BASE_URL = "https://www.steamgriddb.com/api/v2"
    }
}
