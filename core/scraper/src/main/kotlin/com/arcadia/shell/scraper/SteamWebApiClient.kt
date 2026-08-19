package com.arcadia.shell.scraper

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

data class SteamPlayerSummary(
    val steamId: String,
    val displayName: String,
    val avatarUrl: String?,
    /** Steam personastate: 0 offline … 1 online, 2 busy, 3 away, 4 snooze, … */
    val personaState: Int,
    val currentGame: String?,
    val profileUrl: String?,
)

/**
 * Steam Web API helpers for the social menu.
 *
 * Supports [GetFriendList] + [GetPlayerSummaries]. Steam Chat / DMs are not available through
 * the public Web API.
 */
@Singleton
class SteamWebApiClient @Inject constructor(
    private val httpClient: OkHttpClient,
    private val json: Json,
) {
    suspend fun fetchFriends(
        apiKey: String,
        steamId64: String,
    ): Result<List<SteamPlayerSummary>> = withContext(Dispatchers.IO) {
        try {
            val key = apiKey.trim()
            val selfId = steamId64.trim()
            require(key.isNotEmpty() && selfId.isNotEmpty()) {
                "Steam Web API key and SteamID64 are required."
            }

            val friendIds = fetchFriendIds(key, selfId)
            if (friendIds.isEmpty()) return@withContext Result.success(emptyList())

            val (summaries, animatedById) = coroutineScope {
                val summariesJob = async {
                    friendIds
                        .chunked(MAX_SUMMARY_IDS)
                        .flatMap { chunk -> fetchPlayerSummaries(key, chunk) }
                }
                val animatedJob = async { fetchAnimatedAvatarUrls(key, friendIds) }
                summariesJob.await() to animatedJob.await()
            }
            val friends = summaries
                .map { summary ->
                    val animated = animatedById[summary.steamId]
                    if (animated.isNullOrBlank()) summary else summary.copy(avatarUrl = animated)
                }
                .sortedWith(
                    compareByDescending<SteamPlayerSummary> { it.personaState > 0 }
                        .thenByDescending { !it.currentGame.isNullOrBlank() }
                        .thenBy { it.displayName.lowercase() },
                )
            Result.success(friends)
        } catch (error: Throwable) {
            Result.failure(error)
        }
    }

    private fun fetchFriendIds(apiKey: String, steamId64: String): List<String> {
        val key = encode(apiKey)
        val id = encode(steamId64)
        val url =
            "$BASE/ISteamUser/GetFriendList/v1/?key=$key&steamid=$id&relationship=friend"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                error(friendListError(response.code, body))
            }
            val root = json.parseToJsonElement(body).jsonObject
            val friends = root["friendslist"]?.jsonObject
                ?.get("friends")
                ?.jsonArray
                ?: return emptyList()
            return friends.mapNotNull { element ->
                element.jsonObject["steamid"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            }
        }
    }

    private fun fetchPlayerSummaries(
        apiKey: String,
        steamIds: List<String>,
    ): List<SteamPlayerSummary> {
        if (steamIds.isEmpty()) return emptyList()
        val key = encode(apiKey)
        val ids = encode(steamIds.joinToString(","))
        val url = "$BASE/ISteamUser/GetPlayerSummaries/v2/?key=$key&steamids=$ids"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()

        httpClient.newCall(request).execute().use { response ->
            val body = response.body.string()
            if (!response.isSuccessful) {
                Log.w(TAG, "GetPlayerSummaries HTTP ${response.code}")
                error("Steam player lookup failed (HTTP ${response.code}).")
            }
            val root = json.parseToJsonElement(body).jsonObject
            val players = root["response"]?.jsonObject
                ?.get("players")
                ?.jsonArray
                ?: return emptyList()
            return players.mapNotNull { element ->
                val obj = element.jsonObject
                val steamId = obj["steamid"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                SteamPlayerSummary(
                    steamId = steamId,
                    displayName = obj["personaname"]?.jsonPrimitive?.contentOrNull
                        ?.takeIf { it.isNotBlank() }
                        ?: "Steam $steamId",
                    avatarUrl = SteamAvatarUrls.fromPlayerSummary(obj),
                    personaState = obj["personastate"]?.jsonPrimitive?.intOrNull ?: 0,
                    currentGame = obj["gameextrainfo"]?.jsonPrimitive?.contentOrNull
                        ?.takeIf { it.isNotBlank() },
                    profileUrl = obj["profileurl"]?.jsonPrimitive?.contentOrNull
                        ?.takeIf { it.isNotBlank() },
                )
            }
        }
    }

    private fun friendListError(code: Int, body: String): String = when (code) {
        401, 403 ->
            "Steam rejected the API key, or this account’s friend list is private."
        400 ->
            "Invalid SteamID64. Use the 17-digit SteamID64 from steamid.io."
        else -> {
            val snippet = body.take(120).ifBlank { "no body" }
            "Steam friend list failed (HTTP $code): $snippet"
        }
    }

    private suspend fun fetchAnimatedAvatarUrls(
        apiKey: String,
        steamIds: List<String>,
    ): Map<String, String> = coroutineScope {
        val semaphore = Semaphore(EQUIPPED_CONCURRENCY)
        steamIds.map { steamId ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    steamId to fetchEquippedAnimatedAvatar(apiKey, steamId)
                }
            }
        }.awaitAll().mapNotNull { (id, url) -> url?.let { id to it } }.toMap()
    }

    private fun fetchEquippedAnimatedAvatar(apiKey: String, steamId: String): String? {
        val key = encode(apiKey)
        val id = encode(steamId)
        val url = "$BASE/IPlayerService/GetProfileItemsEquipped/v1/?key=$key&steamid=$id"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()
        return runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val root = json.parseToJsonElement(response.body.string()).jsonObject
                SteamAvatarUrls.fromEquippedProfileItems(root)
            }
        }.onFailure { error ->
            Log.d(TAG, "GetProfileItemsEquipped $steamId: ${error.message}")
        }.getOrNull()
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8)

    companion object {
        private const val TAG = "SteamWebApi"
        private const val BASE = "https://api.steampowered.com"
        private const val USER_AGENT = "SORA/1.0 (Android; Arcadia Shell)"
        private const val MAX_SUMMARY_IDS = 100
        private const val EQUIPPED_CONCURRENCY = 8
    }
}
