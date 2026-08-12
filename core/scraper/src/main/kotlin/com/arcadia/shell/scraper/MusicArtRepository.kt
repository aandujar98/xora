package com.arcadia.shell.scraper

import android.content.Context
import android.util.Log
import com.arcadia.shell.datastore.ShellPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Looks up album artwork for on-device music (mp3, wav, flac, …) and caches it locally.
 *
 * No extra credentials are required: iTunes, Deezer, and Cover Art Archive are public. SteamGridDB
 * and IGDB are used last when the user already has game-scraper keys — useful for game OSTs whose
 * folder is named after the game rather than a published album.
 */
@Singleton
class MusicArtRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val httpClient: OkHttpClient,
    private val json: Json,
    private val mediaCache: MediaCache,
    private val preferences: ShellPreferences,
    private val steamGridDb: SteamGridDbClient,
    private val igdb: IgdbClient,
) {
    private val indexFile = File(context.filesDir, "music_art/index.json")
    private val mutex = Mutex()
    private var paths = mapOf<String, String>()
    private var failedUntil = mapOf<String, Long>()

    init {
        runCatching { loadIndex() }
    }

    /**
     * Returns a local file path for [title] / [artist] artwork, downloading it only when needed.
     */
    suspend fun ensureArt(title: String, artist: String): String? = withContext(Dispatchers.IO) {
        if (!MusicArtMatcher.isScrapableTitle(title)) return@withContext null
        val key = MusicArtMatcher.cacheKey(title, artist)
        mutex.withLock {
            val existing = paths[key]
            if (existing != null && File(existing).isFile && File(existing).length() > 64L) {
                return@withLock existing
            }
            val now = System.currentTimeMillis()
            if ((failedUntil[key] ?: 0L) > now) return@withLock null

            val path = downloadArt(title, artist)
            if (path != null) {
                paths = paths + (key to path)
                failedUntil = failedUntil - key
                persistIndex()
                path
            } else {
                failedUntil = failedUntil + (key to now + FAIL_BACKOFF_MS)
                persistIndex()
                null
            }
        }
    }

    private suspend fun downloadArt(title: String, artist: String): String? {
        itunesArtUrl(title, artist)?.let { mediaCache.fetchImage(it) }?.let { return it }
        deezerArtUrl(title, artist)?.let { mediaCache.fetchImage(it) }?.let { return it }
        musicBrainzArtUrl(title, artist)?.let { mediaCache.fetchImage(it) }?.let { return it }

        val credentials = preferences.credentials.first()
        if (credentials.hasSteamGridDb) {
            runCatching {
                steamGridDb.lookup(title, credentials.steamGridDbKey)?.boxArtUrl
            }.getOrNull()?.let { mediaCache.fetchImage(it) }?.let { return it }
        }
        if (credentials.hasIgdb) {
            runCatching {
                igdb.lookup(title, credentials.igdbClientId, credentials.igdbClientSecret)?.boxArtUrl
            }.getOrNull()?.let { mediaCache.fetchImage(it) }?.let { return it }
        }
        return null
    }

    private fun itunesArtUrl(title: String, artist: String): String? {
        val query = MusicArtMatcher.searchQuery(title, artist)
        val encoded = encode(query)
        val body = get(
            url = "$ITUNES_SEARCH?term=$encoded&entity=album&media=music&limit=8",
            userAgent = USER_AGENT,
        ) ?: return null
        val results = runCatching {
            json.decodeFromString<ItunesSearchResponse>(body).results
        }.getOrDefault(emptyList())
        val candidates = results.mapNotNull { album ->
            val art = album.artworkUrl100?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            MusicArtMatcher.Candidate(
                title = album.collectionName.orEmpty(),
                artist = album.artistName.orEmpty(),
                artUrl = MusicArtMatcher.itunesHiResArtUrl(art),
            )
        }
        return MusicArtMatcher.pickBest(title, artist, candidates)?.artUrl
    }

    private fun deezerArtUrl(title: String, artist: String): String? {
        val query = MusicArtMatcher.searchQuery(title, artist)
        val body = get(
            url = "$DEEZER_SEARCH?q=${encode(query)}",
            userAgent = USER_AGENT,
        ) ?: return null
        val results = runCatching {
            json.decodeFromString<DeezerSearchResponse>(body).data
        }.getOrDefault(emptyList())
        val candidates = results.mapNotNull { album ->
            val art = album.coverXl?.takeIf { it.isNotBlank() }
                ?: album.coverBig?.takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            MusicArtMatcher.Candidate(
                title = album.title.orEmpty(),
                artist = album.artist?.name.orEmpty(),
                artUrl = art,
            )
        }
        return MusicArtMatcher.pickBest(title, artist, candidates)?.artUrl
    }

    private fun musicBrainzArtUrl(title: String, artist: String): String? {
        val lucene = buildString {
            append("release:\"${title.trim().replace("\"", "")}\"")
            val who = artist.trim()
            if (!MusicArtMatcher.isGenericArtist(who)) {
                append(" AND artist:\"${who.replace("\"", "")}\"")
            }
        }
        val body = get(
            url = "$MUSICBRAINZ_SEARCH?query=${encode(lucene)}&fmt=json&limit=5",
            userAgent = MUSICBRAINZ_USER_AGENT,
        ) ?: return null
        val releases = runCatching {
            json.decodeFromString<MusicBrainzSearchResponse>(body).releases
        }.getOrDefault(emptyList())
        val candidates = releases.mapNotNull { release ->
            val id = release.id?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val artistName = release.artistCredit.firstOrNull()?.name
                ?: release.artistCredit.firstOrNull()?.artist?.name
                ?: ""
            MusicArtMatcher.Candidate(
                title = release.title.orEmpty(),
                artist = artistName,
                artUrl = "$COVER_ART_ARCHIVE/release/$id/front-500",
            )
        }
        return MusicArtMatcher.pickBest(title, artist, candidates)?.artUrl
    }

    private fun get(url: String, userAgent: String): String? {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent)
            .header("Accept", "application/json")
            .get()
            .build()
        return runCatching {
            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body.string() else null
            }
        }.onFailure { Log.w(TAG, "Music art lookup failed for $url", it) }.getOrNull()
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8)

    private fun loadIndex() {
        if (!indexFile.isFile) return
        val parsed = runCatching {
            json.decodeFromString<MusicArtIndex>(indexFile.readText())
        }.getOrNull() ?: return
        failedUntil = parsed.failedUntil
        paths = parsed.paths.filter { (_, path) ->
            File(path).isFile && File(path).length() > 64L
        }
    }

    private fun persistIndex() {
        runCatching {
            indexFile.parentFile?.mkdirs()
            indexFile.writeText(
                json.encodeToString(
                    MusicArtIndex.serializer(),
                    MusicArtIndex(paths = paths, failedUntil = failedUntil),
                ),
            )
        }.onFailure { Log.w(TAG, "Failed to persist music art index", it) }
    }

    @Serializable
    private data class MusicArtIndex(
        val paths: Map<String, String> = emptyMap(),
        val failedUntil: Map<String, Long> = emptyMap(),
    )

    @Serializable
    private data class ItunesSearchResponse(
        val results: List<ItunesAlbum> = emptyList(),
    )

    @Serializable
    private data class ItunesAlbum(
        val collectionName: String? = null,
        val artistName: String? = null,
        val artworkUrl100: String? = null,
    )

    @Serializable
    private data class DeezerSearchResponse(
        val data: List<DeezerAlbum> = emptyList(),
    )

    @Serializable
    private data class DeezerAlbum(
        val title: String? = null,
        val artist: DeezerArtist? = null,
        @SerialName("cover_xl") val coverXl: String? = null,
        @SerialName("cover_big") val coverBig: String? = null,
    )

    @Serializable
    private data class DeezerArtist(
        val name: String? = null,
    )

    @Serializable
    private data class MusicBrainzSearchResponse(
        val releases: List<MusicBrainzRelease> = emptyList(),
    )

    @Serializable
    private data class MusicBrainzRelease(
        val id: String? = null,
        val title: String? = null,
        @SerialName("artist-credit") val artistCredit: List<MusicBrainzArtistCredit> = emptyList(),
    )

    @Serializable
    private data class MusicBrainzArtistCredit(
        val name: String? = null,
        val artist: MusicBrainzNamed? = null,
    )

    @Serializable
    private data class MusicBrainzNamed(
        val name: String? = null,
    )

    private companion object {
        const val TAG = "MusicArt"
        const val ITUNES_SEARCH = "https://itunes.apple.com/search"
        const val DEEZER_SEARCH = "https://api.deezer.com/search/album"
        const val MUSICBRAINZ_SEARCH = "https://musicbrainz.org/ws/2/release/"
        const val COVER_ART_ARCHIVE = "https://coverartarchive.org"
        const val USER_AGENT = "XOrA/1.0 (Android; https://github.com/aandujar98/xora)"
        const val MUSICBRAINZ_USER_AGENT =
            "XOrA/1.0 (https://github.com/aandujar98/xora)"
        const val FAIL_BACKOFF_MS = 24L * 60L * 60L * 1000L
    }
}
