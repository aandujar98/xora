package com.arcadia.shell.scraper.insight

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Wikipedia REST page summary — no API key. Used as an evergreen fun-fact / description fallback.
 */
@Singleton
class WikipediaSummaryClient @Inject constructor(
    private val httpClient: OkHttpClient,
    private val json: Json,
) {
    suspend fun lookup(title: String): WikipediaInsight? = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext null
        val cleaned = cleanTitle(title)
        lookupExact(cleaned)
            ?: lookupExact("$cleaned (video game)")
            ?: cleaned.substringBefore(':').trim()
                .takeIf { it.length >= 3 && it != cleaned }
                ?.let { lookupExact(it) }
    }

    private fun lookupExact(pageTitle: String): WikipediaInsight? {
        val encoded = URLEncoder.encode(pageTitle.replace(' ', '_'), StandardCharsets.UTF_8)
            .replace("+", "_")
        val request = Request.Builder()
            .url("$REST_ROOT/page/summary/$encoded")
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .get()
            .build()

        return runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val page = json.decodeFromString<WikiSummary>(response.body.string())
                if (page.type == "disambiguation") return@runCatching null
                val extract = page.extract?.trim().orEmpty()
                if (extract.isBlank()) return@runCatching null
                WikipediaInsight(
                    extract = extract,
                    description = page.description?.trim()?.takeIf { it.isNotEmpty() },
                )
            }
        }.getOrNull()
    }

    private fun cleanTitle(raw: String): String =
        raw
            .replace(Regex("""\s*\([^)]*\)\s*$"""), "")
            .replace(Regex("""\s*\[[^\]]*]\s*"""), "")
            .replace('_', ' ')
            .trim()

    @Serializable
    private data class WikiSummary(
        val type: String? = null,
        val extract: String? = null,
        val description: String? = null,
        @SerialName("displaytitle") val displayTitle: String? = null,
    )

    private companion object {
        const val REST_ROOT = "https://en.wikipedia.org/api/rest_v1"
        const val USER_AGENT = "SORA/1.0 (Android; Arcadia Shell; game library insights)"
    }
}
