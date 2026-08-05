package com.arcadia.shell.scraper.insight

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.floor

/**
 * Speedrun.com public API (no key). Resolves a fuzzy game match and a recent verified run blurb.
 */
@Singleton
class SpeedrunClient @Inject constructor(
    private val httpClient: OkHttpClient,
    private val json: Json,
) {
    suspend fun lookup(title: String): SpeedrunInsight? = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext null
        val game = searchGame(title) ?: return@withContext null
        val run = latestVerifiedRun(game.id)
        val blurb = formatBlurb(game.name, run) ?: return@withContext null
        SpeedrunInsight(
            blurb = blurb,
            releaseYear = game.released?.takeIf { it in 1970..2100 },
        )
    }

    private fun searchGame(title: String): SrcGame? {
        val url = "$API_ROOT/games".toHttpUrl().newBuilder()
            .addQueryParameter("name", title)
            .addQueryParameter("max", "1")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .get()
            .build()

        return runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                json.decodeFromString<SrcListResponse<SrcGame>>(response.body.string())
                    .data
                    .firstOrNull()
            }
        }.getOrNull()
    }

    private fun latestVerifiedRun(gameId: String): SrcRun? {
        val url = "$API_ROOT/runs".toHttpUrl().newBuilder()
            .addQueryParameter("game", gameId)
            .addQueryParameter("status", "verified")
            .addQueryParameter("orderby", "verify-date")
            .addQueryParameter("direction", "desc")
            .addQueryParameter("max", "1")
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .get()
            .build()

        return runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                json.decodeFromString<SrcListResponse<SrcRun>>(response.body.string())
                    .data
                    .firstOrNull()
            }
        }.getOrNull()
    }

    private fun formatBlurb(gameName: String, run: SrcRun?): String? {
        if (run == null) {
            return "Tracked on Speedrun.com — check leaderboards for $gameName."
        }
        val player = run.players.firstNotNullOfOrNull { it.displayName() } ?: "A runner"
        val time = run.times?.primaryT?.let(::formatTime)
        return buildString {
            append(player)
            append(" completed a verified speedrun")
            if (time != null) {
                append(" in ")
                append(time)
            }
            append('.')
        }
    }

    private fun formatTime(seconds: Double): String {
        val total = floor(seconds).toInt().coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) {
            String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.US, "%d:%02d", m, s)
        }
    }

    @Serializable
    private data class SrcListResponse<T>(
        val data: List<T> = emptyList(),
    )

    @Serializable
    private data class SrcGame(
        val id: String,
        val names: SrcNames? = null,
        val released: Int? = null,
    ) {
        val name: String get() = names?.international?.takeIf { it.isNotBlank() } ?: id
    }

    @Serializable
    private data class SrcNames(
        val international: String? = null,
    )

    @Serializable
    private data class SrcRun(
        val id: String? = null,
        val times: SrcTimes? = null,
        val players: List<SrcPlayerRef> = emptyList(),
    )

    @Serializable
    private data class SrcTimes(
        @SerialName("primary_t") val primaryT: Double? = null,
    )

    @Serializable
    private data class SrcPlayerRef(
        val rel: String? = null,
        val id: String? = null,
        val name: String? = null,
    ) {
        fun displayName(): String? = name?.takeIf { it.isNotBlank() }
    }

    private companion object {
        const val API_ROOT = "https://www.speedrun.com/api/v1"
        const val USER_AGENT = "SORA/1.0 (Android; Arcadia Shell)"
    }
}
