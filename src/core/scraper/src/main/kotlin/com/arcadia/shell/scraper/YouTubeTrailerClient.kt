package com.arcadia.shell.scraper

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Playnite-style YouTube trailer discovery by game title — no Data API key required.
 *
 * Uses YouTube's public InnerTube search endpoint (same family of clients the website embeds),
 * falling back to scraping `ytInitialData` from the results HTML when InnerTube is unavailable.
 * Results are scored so "official trailer" titles win over Shorts / gameplay uploads. Returns a
 * ranked candidate list so the player can fail over when a video blocks embedding (error 152).
 */
@Singleton
class YouTubeTrailerClient @Inject constructor(
    private val httpClient: OkHttpClient,
    private val json: Json,
) {
    suspend fun findTrailerVideoId(
        title: String,
        platformName: String? = null,
    ): String? = findTrailerVideoIds(title, platformName).firstOrNull()

    /** Ranked embed candidates (best first). Empty when nothing useful was found. */
    suspend fun findTrailerVideoIds(
        title: String,
        platformName: String? = null,
        limit: Int = MAX_CANDIDATES,
    ): List<String> = withContext(Dispatchers.IO) {
        val cleaned = sanitizeTitle(title) ?: return@withContext emptyList()
        val query = buildString {
            append(cleaned)
            platformName?.takeIf { it.isNotBlank() && !it.equals("Unsorted", ignoreCase = true) }
                ?.let { append(' ').append(it) }
            append(" official trailer")
        }
        Log.i(TAG, "YouTube search: $query")

        val fromInner = runCatching { innertubeSearch(query, cleaned, limit) }
            .onFailure { Log.w(TAG, "InnerTube search failed: ${it.message}") }
            .getOrNull()
            .orEmpty()
        if (fromInner.isNotEmpty()) {
            Log.i(TAG, "YouTube InnerTube hits: $fromInner")
            return@withContext fromInner
        }

        val fromHtml = runCatching { htmlSearch(query, cleaned, limit) }
            .onFailure { Log.w(TAG, "HTML search failed: ${it.message}") }
            .getOrNull()
            .orEmpty()
        if (fromHtml.isNotEmpty()) {
            Log.i(TAG, "YouTube HTML hits: $fromHtml")
        } else {
            Log.i(TAG, "YouTube: no trailer for \"$cleaned\"")
        }
        fromHtml
    }

    private fun innertubeSearch(query: String, gameTitle: String, limit: Int): List<String> {
        val escapedQuery = query
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", " ")
            .replace("\r", " ")
        val body = """
            {
              "context": {
                "client": {
                  "hl": "en",
                  "gl": "US",
                  "clientName": "WEB",
                  "clientVersion": "$CLIENT_VERSION"
                }
              },
              "query": "$escapedQuery"
            }
        """.trimIndent()

        val request = Request.Builder()
            .url(INNERTUBE_SEARCH_URL)
            .header("User-Agent", USER_AGENT)
            .header("Content-Type", "application/json")
            .header("X-YouTube-Client-Name", "1")
            .header("X-YouTube-Client-Version", CLIENT_VERSION)
            .post(body.toRequestBody(JSON_MEDIA))
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "InnerTube HTTP ${response.code}")
                return emptyList()
            }
            val root = json.parseToJsonElement(response.body.string()).jsonObject
            val candidates = mutableListOf<ScoredVideo>()
            collectVideoRenderers(root, candidates)
            return pickBest(candidates, gameTitle, limit)
        }
    }

    private fun htmlSearch(query: String, gameTitle: String, limit: Int): List<String> {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8)
        val request = Request.Builder()
            // sp=EgIQAQ%3D%3D filters results to type=video (excludes Shorts shelves / playlists).
            .url("https://www.youtube.com/results?search_query=$encoded&hl=en&gl=US&sp=EgIQAQ%3D%3D")
            .header("User-Agent", USER_AGENT)
            .header("Accept-Language", "en-US,en;q=0.9")
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                Log.w(TAG, "HTML search HTTP ${response.code}")
                return emptyList()
            }
            val html = response.body.string()
            val initial = extractYtInitialData(html) ?: run {
                return VIDEO_ID_REGEX.findAll(html)
                    .map { it.groupValues[1] }
                    .distinct()
                    .take(limit)
                    .toList()
            }
            val root = json.parseToJsonElement(initial).jsonObject
            val candidates = mutableListOf<ScoredVideo>()
            collectVideoRenderers(root, candidates)
            val picked = pickBest(candidates, gameTitle, limit)
            if (picked.isNotEmpty()) return picked
            return VIDEO_ID_REGEX.findAll(initial)
                .map { it.groupValues[1] }
                .distinct()
                .take(limit)
                .toList()
        }
    }

    private fun extractYtInitialData(html: String): String? {
        val markers = listOf("var ytInitialData = ", "ytInitialData = ")
        for (marker in markers) {
            val start = html.indexOf(marker)
            if (start >= 0) {
                return extractJsonObject(html, start + marker.length)
            }
        }
        return null
    }

    private fun extractJsonObject(html: String, from: Int): String? {
        if (from >= html.length || html[from] != '{') return null
        var depth = 0
        var inString = false
        var escape = false
        for (i in from until html.length) {
            val c = html[i]
            if (inString) {
                when {
                    escape -> escape = false
                    c == '\\' -> escape = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{' -> depth++
                '}' -> {
                    depth--
                    if (depth == 0) return html.substring(from, i + 1)
                }
            }
        }
        return null
    }

    private fun collectVideoRenderers(element: JsonElement, out: MutableList<ScoredVideo>) {
        when (element) {
            is JsonObject -> {
                val renderer = element["videoRenderer"]?.jsonObject
                    ?: element["compactVideoRenderer"]?.jsonObject
                if (renderer != null) {
                    val id = renderer["videoId"]?.jsonPrimitive?.content
                        ?.takeIf { it.matches(VIDEO_ID_PATTERN) }
                    val title = renderer["title"]?.let { readTitle(it) }.orEmpty()
                    if (id != null && !isShortsRenderer(renderer, title)) {
                        out += ScoredVideo(id, title)
                    }
                }
                element.values.forEach { collectVideoRenderers(it, out) }
            }
            is JsonArray -> element.forEach { collectVideoRenderers(it, out) }
            else -> Unit
        }
    }

    /** Skip YouTube Shorts — they rarely embed cleanly and are almost never official trailers. */
    private fun isShortsRenderer(renderer: JsonObject, title: String): Boolean {
        val lower = title.lowercase()
        if ("#shorts" in lower || " #short" in lower) return true
        val nav = renderer["navigationEndpoint"]?.jsonObject
        if (nav?.containsKey("reelWatchEndpoint") == true) return true
        val overlays = renderer["thumbnailOverlays"]?.jsonArray ?: return false
        return overlays.any { overlay ->
            val status = overlay.jsonObject["thumbnailOverlayTimeStatusRenderer"]?.jsonObject
            val style = status?.get("style")?.jsonPrimitive?.content
            style.equals("SHORTS", ignoreCase = true)
        }
    }

    private fun readTitle(node: JsonElement): String? {
        val obj = node as? JsonObject ?: return runCatching { node.jsonPrimitive.content }.getOrNull()
        obj["simpleText"]?.jsonPrimitive?.content?.let { return it }
        val runs = obj["runs"]?.jsonArray ?: return null
        return runs.mapNotNull { it.jsonObject["text"]?.jsonPrimitive?.content }
            .joinToString("")
            .takeIf { it.isNotBlank() }
    }

    private fun pickBest(candidates: List<ScoredVideo>, gameTitle: String, limit: Int): List<String> {
        if (candidates.isEmpty()) return emptyList()
        val distinct = candidates.distinctBy { it.videoId }
        val ranked = distinct
            .map { it to score(it.title, gameTitle) }
            .filter { (_, score) -> score > 0 }
            .sortedByDescending { it.second }
            .map { it.first.videoId }
        if (ranked.isNotEmpty()) return ranked.take(limit)
        // Last resort: still surface something rather than nothing.
        return distinct.map { it.videoId }.take(limit)
    }

    private fun score(videoTitle: String, gameTitle: String): Int {
        val title = videoTitle.lowercase()
        val game = gameTitle.lowercase()
        var score = 0
        if (title.contains("official trailer")) score += 20
        if (title.contains("trailer")) score += 12
        if (title.contains("official")) score += 6
        if (title.contains("teaser")) score += 4
        if (title.contains("launch")) score += 2
        if (title.contains("cinematic")) score += 2
        if (game.isNotBlank() && title.contains(game)) score += 10
        game.split(Regex("\\s+")).filter { it.length >= 3 }.forEach { token ->
            if (title.contains(token)) score += 2
        }
        if (title.contains("gameplay") && !title.contains("trailer")) score -= 8
        if (title.contains("walkthrough") || title.contains("let's play") ||
            title.contains("longplay") || title.contains("ost") || title.contains("soundtrack")
        ) {
            score -= 10
        }
        if (title.contains("review") && !title.contains("trailer")) score -= 6
        if (title.contains("#shorts") || title.contains("shorts")) score -= 20
        return score
    }

    private fun sanitizeTitle(raw: String): String? {
        var t = raw.trim()
        if (t.isEmpty()) return null
        // Strip common ROM tags: (USA), [!], disc markers, etc.
        t = t.replace(Regex("""\([^)]*\)"""), " ")
        t = t.replace(Regex("""\[[^\]]*\]"""), " ")
        t = t.replace(Regex("""\{[^}]*\}"""), " ")
        t = t.replace(Regex("""\s+"""), " ").trim()
        return t.takeIf { it.length >= 2 }
    }

    private data class ScoredVideo(val videoId: String, val title: String)

    private companion object {
        const val TAG = "YouTubeTrailer"
        const val MAX_CANDIDATES = 5
        const val CLIENT_VERSION = "2.20240401.00.00"
        // Public WEB client key embedded in youtube.com (not a secret API product key).
        const val INNERTUBE_SEARCH_URL =
            "https://www.youtube.com/youtubei/v1/search?prettyPrint=false&key=AIzaSyAO_FJ2SlqU8Q4STEHLGCilw_Y9_11qcW8"
        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
        val VIDEO_ID_PATTERN = Regex("^[A-Za-z0-9_-]{11}$")
        val VIDEO_ID_REGEX = Regex(""""videoId"\s*:\s*"([A-Za-z0-9_-]{11})"""")
    }
}
