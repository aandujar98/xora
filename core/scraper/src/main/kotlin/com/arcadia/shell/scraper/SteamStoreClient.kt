package com.arcadia.shell.scraper

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Best-effort Steam store trailer lookup.
 *
 * Prefers a known Steam app id (e.g. from IGDB). Also supports title search via the public
 * storesearch API — no API key. Epic is not wired.
 */
@Singleton
class SteamStoreClient @Inject constructor(
    private val httpClient: OkHttpClient,
    private val json: Json,
) {
    suspend fun trailerMp4Url(steamAppId: String): String? = withContext(Dispatchers.IO) {
        val id = steamAppId.trim().takeIf { it.isNotEmpty() } ?: return@withContext null
        val request = Request.Builder()
            .url("$APPDETAILS_URL?appids=$id&filters=movies")
            .header("User-Agent", "arcadia")
            .build()

        runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val root = json.parseToJsonElement(response.body.string()).jsonObject
                val app = root[id]?.jsonObject ?: return@runCatching null
                val success = app["success"]?.toString() == "true"
                if (!success) return@runCatching null
                val data = app["data"]?.jsonObject ?: return@runCatching null
                val movies = data["movies"] ?: return@runCatching null
                val parsed = json.decodeFromJsonElement<List<SteamMovie>>(movies)
                parsed.firstNotNullOfOrNull { movie ->
                    movie.mp4?.max?.takeIf { it.isNotBlank() }
                        ?: movie.mp4?.p480?.takeIf { it.isNotBlank() }
                }
            }
        }.getOrNull()
    }

    /** Search the Steam store by title and return the first matching game's trailer mp4. */
    suspend fun findTrailerByTitle(title: String): String? = withContext(Dispatchers.IO) {
        val cleaned = title.trim().takeIf { it.length >= 2 } ?: return@withContext null
        val encoded = URLEncoder.encode(cleaned, StandardCharsets.UTF_8)
        val request = Request.Builder()
            .url("$STORE_SEARCH_URL?term=$encoded&l=english&cc=US")
            .header("User-Agent", "arcadia")
            .build()

        val appId = runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Steam search HTTP ${response.code}")
                    return@runCatching null
                }
                val parsed = json.decodeFromString<SteamSearchResponse>(response.body.string())
                parsed.items
                    ?.firstOrNull { it.type.equals("app", ignoreCase = true) || it.id != null }
                    ?.id
                    ?.toString()
            }
        }.getOrNull() ?: return@withContext null

        Log.i(TAG, "Steam search \"$cleaned\" → app $appId")
        trailerMp4Url(appId)
    }

    @Serializable
    private data class SteamMovie(val mp4: SteamMp4? = null)

    @Serializable
    private data class SteamMp4(
        val max: String? = null,
        @kotlinx.serialization.SerialName("480") val p480: String? = null,
    )

    @Serializable
    private data class SteamSearchResponse(
        val items: List<SteamSearchItem>? = null,
    )

    @Serializable
    private data class SteamSearchItem(
        val id: Long? = null,
        val type: String? = null,
        val name: String? = null,
    )

    private companion object {
        const val TAG = "SteamStore"
        const val APPDETAILS_URL = "https://store.steampowered.com/api/appdetails"
        const val STORE_SEARCH_URL = "https://store.steampowered.com/api/storesearch/"
    }
}
