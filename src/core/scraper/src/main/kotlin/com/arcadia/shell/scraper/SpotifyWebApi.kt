package com.arcadia.shell.scraper

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

/** A playlist from the linked Spotify account. */
data class SpotifyPlaylist(
    val id: String,
    val name: String,
    val ownerName: String,
    val imageUrl: String?,
    val trackCount: Int,
    /** `spotify:playlist:…` — the context handed to playback. */
    val uri: String,
)

/** One track inside a Spotify playlist. */
data class SpotifyTrack(
    val id: String,
    val title: String,
    val artist: String,
    val albumName: String,
    val albumArtUrl: String?,
    val durationMs: Long,
    val uri: String,
)

/** A Connect / desktop / phone target Spotify can stream to. */
data class SpotifyDevice(
    val id: String,
    val name: String,
    val type: String,
    val isActive: Boolean,
    val isRestricted: Boolean,
)

/** Why a playback request could not be honoured, in words the shell can show. */
sealed interface SpotifyPlaybackResult {
    data object Started : SpotifyPlaybackResult
    data object NoActiveDevice : SpotifyPlaybackResult
    data object NeedsPremium : SpotifyPlaybackResult
    data class Failed(val message: String) : SpotifyPlaybackResult
}

/**
 * Spotify Web API reads for the Music category.
 *
 * Playlists and their tracks are fetched with the linked account's token; playback is asked for
 * through the player endpoints, which stream on whichever device Spotify considers active
 * (Premium only). No client secret is involved — every call rides the PKCE token.
 */
@Singleton
class SpotifyWebApi @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val auth: SpotifyAuth,
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun playlists(limit: Int = PLAYLIST_LIMIT): List<SpotifyPlaylist> =
        withContext(Dispatchers.IO) {
            val token = auth.accessToken() ?: return@withContext emptyList()
            runCatching {
                val body = get("$BASE/me/playlists?limit=$limit", token) ?: return@runCatching emptyList()
                json.parseToJsonElement(body).jsonObject["items"]?.jsonArray.orEmpty().mapNotNull { item ->
                    val obj = item.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    SpotifyPlaylist(
                        id = id,
                        name = obj["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                            .ifBlank { "Untitled playlist" },
                        ownerName = obj["owner"]?.jsonObject
                            ?.get("display_name")?.jsonPrimitive?.contentOrNull.orEmpty()
                            .ifBlank { "Spotify" },
                        imageUrl = obj["images"]?.jsonArray?.firstOrNull()
                            ?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull,
                        trackCount = obj["tracks"]?.jsonObject
                            ?.get("total")?.jsonPrimitive?.intOrNull ?: 0,
                        uri = obj["uri"]?.jsonPrimitive?.contentOrNull ?: "spotify:playlist:$id",
                    )
                }
            }.getOrDefault(emptyList())
        }

    suspend fun playlistTracks(
        playlistId: String,
        limit: Int = TRACK_LIMIT,
    ): List<SpotifyTrack> = withContext(Dispatchers.IO) {
        val token = auth.accessToken() ?: return@withContext emptyList()
        runCatching {
            val url = "$BASE/playlists/$playlistId/tracks?limit=$limit" +
                "&fields=items(track(id,name,uri,duration_ms,artists(name),album(name,images)))"
            val body = get(url, token) ?: return@runCatching emptyList()
            json.parseToJsonElement(body).jsonObject["items"]?.jsonArray.orEmpty().mapNotNull { item ->
                val track = item.jsonObject["track"]?.jsonObject ?: return@mapNotNull null
                val id = track["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val album = track["album"]?.jsonObject
                SpotifyTrack(
                    id = id,
                    title = track["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        .ifBlank { "Untitled" },
                    artist = track["artists"]?.jsonArray.orEmpty()
                        .mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }
                        .joinToString(", ")
                        .ifBlank { "Unknown artist" },
                    albumName = album?.get("name")?.jsonPrimitive?.contentOrNull.orEmpty(),
                    albumArtUrl = album?.get("images")?.jsonArray?.firstOrNull()
                        ?.jsonObject?.get("url")?.jsonPrimitive?.contentOrNull,
                    durationMs = track["duration_ms"]?.jsonPrimitive?.longOrNull ?: 0L,
                    uri = track["uri"]?.jsonPrimitive?.contentOrNull ?: "spotify:track:$id",
                )
            }
        }.getOrDefault(emptyList())
    }

    suspend fun devices(): List<SpotifyDevice> = withContext(Dispatchers.IO) {
        val token = auth.accessToken() ?: return@withContext emptyList()
        runCatching {
            val body = get("$BASE/me/player/devices", token) ?: return@runCatching emptyList()
            json.parseToJsonElement(body).jsonObject["devices"]?.jsonArray.orEmpty().mapNotNull { item ->
                val obj = item.jsonObject
                val id = obj["id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                SpotifyDevice(
                    id = id,
                    name = obj["name"]?.jsonPrimitive?.contentOrNull.orEmpty().ifBlank { "Spotify" },
                    type = obj["type"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                    isActive = obj["is_active"]?.jsonPrimitive?.booleanOrNull == true,
                    isRestricted = obj["is_restricted"]?.jsonPrimitive?.booleanOrNull == true,
                )
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Starts a track (optionally inside its playlist context) on a Spotify Connect device.
     *
     * When nothing is active, transfers to the first unrestricted available device and retries.
     * Audio still streams from Spotify's player — not from this process.
     */
    suspend fun play(trackUri: String, contextUri: String? = null): SpotifyPlaybackResult =
        withContext(Dispatchers.IO) {
            val token = auth.accessToken() ?: return@withContext SpotifyPlaybackResult.Failed(
                "Spotify is not linked",
            )
            val first = putPlay(token, trackUri, contextUri)
            if (first != SpotifyPlaybackResult.NoActiveDevice) return@withContext first

            val deviceId = devices()
                .firstOrNull { !it.isRestricted }
                ?.id
                ?: return@withContext SpotifyPlaybackResult.NoActiveDevice
            transferPlayback(token, deviceId)
            // Connect needs a beat before /me/player/play accepts the new device.
            kotlinx.coroutines.delay(TRANSFER_SETTLE_MS)
            putPlay(token, trackUri, contextUri, deviceId)
        }

    private fun putPlay(
        token: String,
        trackUri: String,
        contextUri: String?,
        deviceId: String? = null,
    ): SpotifyPlaybackResult {
        val payload = if (contextUri != null) {
            """{"context_uri":"$contextUri","offset":{"uri":"$trackUri"}}"""
        } else {
            """{"uris":["$trackUri"]}"""
        }
        val url = buildString {
            append("$BASE/me/player/play")
            if (!deviceId.isNullOrBlank()) append("?device_id=").append(deviceId)
        }
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .put(payload.toRequestBody(JSON_MEDIA_TYPE))
                .header("Authorization", "Bearer $token")
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                when (response.code) {
                    // 202/204 both mean the command was accepted.
                    200, 202, 204 -> SpotifyPlaybackResult.Started
                    403 -> SpotifyPlaybackResult.NeedsPremium
                    404 -> SpotifyPlaybackResult.NoActiveDevice
                    else -> SpotifyPlaybackResult.Failed("Spotify playback failed (${response.code})")
                }
            }
        }.getOrElse { error ->
            SpotifyPlaybackResult.Failed(error.message ?: "Spotify playback failed")
        }
    }

    private fun transferPlayback(token: String, deviceId: String): Boolean {
        val payload = """{"device_ids":["$deviceId"],"play":true}"""
        return runCatching {
            val request = Request.Builder()
                .url("$BASE/me/player")
                .put(payload.toRequestBody(JSON_MEDIA_TYPE))
                .header("Authorization", "Bearer $token")
                .build()
            okHttpClient.newCall(request).execute().use { it.isSuccessful || it.code == 204 }
        }.getOrDefault(false)
    }

    suspend fun pause(): Boolean = withContext(Dispatchers.IO) {
        val token = auth.accessToken() ?: return@withContext false
        runCatching {
            val request = Request.Builder()
                .url("$BASE/me/player/pause")
                .put(EMPTY_JSON.toRequestBody(JSON_MEDIA_TYPE))
                .header("Authorization", "Bearer $token")
                .build()
            okHttpClient.newCall(request).execute().use { it.isSuccessful || it.code == 404 }
        }.getOrDefault(false)
    }

    suspend fun skipToNext(): Boolean = playerCommand("next")

    suspend fun skipToPrevious(): Boolean = playerCommand("previous")

    private suspend fun playerCommand(command: String): Boolean = withContext(Dispatchers.IO) {
        val token = auth.accessToken() ?: return@withContext false
        runCatching {
            val request = Request.Builder()
                .url("$BASE/me/player/$command")
                .post(EMPTY_JSON.toRequestBody(JSON_MEDIA_TYPE))
                .header("Authorization", "Bearer $token")
                .build()
            okHttpClient.newCall(request).execute().use { it.isSuccessful || it.code == 204 }
        }.getOrDefault(false)
    }

    private fun get(url: String, token: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .build()
        return okHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) null else response.body.string()
        }
    }

    companion object {
        private const val BASE = "https://api.spotify.com/v1"
        private const val PLAYLIST_LIMIT = 50
        private const val TRACK_LIMIT = 100
        private const val TRANSFER_SETTLE_MS = 400L
        private const val EMPTY_JSON = "{}"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
    }
}
