package com.arcadia.shell.scraper

import android.content.Context
import android.util.Log
import com.arcadia.shell.datastore.ShellPreferences
import com.arcadia.shell.model.PlatformCatalog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Scrapes and caches ScreenScraper console/system artwork (illustration → photo → controller →
 * wheel) for XMB "All Games" system rows.
 *
 * Paths are content-addressed under [MediaCache]; this repository only keeps a platformId → path
 * index so the home shell can paint systems without re-hitting the API.
 */
@Singleton
class PlatformArtRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val screenScraper: ScreenScraperClient,
    private val mediaCache: MediaCache,
    private val preferences: ShellPreferences,
    private val json: Json,
) {
    private val indexFile = File(context.filesDir, "platform_art/index.json")
    private val bundledDir = File(context.filesDir, "platform_art_bundled")
    private val mutex = Mutex()
    private val art = MutableStateFlow<Map<String, String>>(emptyMap())
    private var failedUntil = mapOf<String, Long>()

    val artByPlatformId: StateFlow<Map<String, String>> = art.asStateFlow()

    init {
        runCatching { seedBundledArt() }
        runCatching { loadIndex() }
        runCatching { applyBundledArt() }
    }

    /**
     * Ensures artwork for the given catalog platform ids. Already-cached paths are reused; missing
     * ones are scraped sequentially with a small pause so ScreenScraper quotas stay healthy.
     */
    suspend fun ensureArt(platformIds: Collection<String>) = withContext(Dispatchers.IO) {
        val unique = platformIds.distinct().filter { it.isNotBlank() && it != "android" }
        if (unique.isEmpty()) return@withContext

        mutex.withLock {
            val credentials = preferences.credentials.first()
            if (!credentials.hasScreenScraper) return@withLock

            val now = System.currentTimeMillis()
            val current = art.value.toMutableMap()
            var indexDirty = false

            for (platformId in unique) {
                val existing = current[platformId]
                val existingFile = existing?.let(::File)
                if (existingFile != null && existingFile.isFile && existingFile.length() > 0L) {
                    continue
                }
                if (existing != null) {
                    current.remove(platformId)
                    indexDirty = true
                }
                val retryAt = failedUntil[platformId] ?: 0L
                if (retryAt > now) continue

                val ssId = PlatformCatalog.byId(platformId)?.screenScraperSystemId
                if (ssId == null) {
                    failedUntil = failedUntil + (platformId to now + FAIL_BACKOFF_MS)
                    indexDirty = true
                    continue
                }

                val path = scrapeOne(ssId, credentials)
                if (path != null) {
                    current[platformId] = path
                    failedUntil = failedUntil - platformId
                    indexDirty = true
                    art.value = current.toMap()
                } else {
                    failedUntil = failedUntil + (platformId to now + FAIL_BACKOFF_MS)
                    indexDirty = true
                }
                delay(REQUEST_SPACING_MS)
            }

            if (indexDirty) {
                art.value = current.toMap()
                persistIndex()
            }
        }
    }

    private suspend fun scrapeOne(
        systemId: Int,
        credentials: com.arcadia.shell.datastore.ScraperCredentials,
    ): String? {
        for (media in MEDIA_PRIORITY) {
            for (region in REGIONS) {
                val url = screenScraper.systemMediaDownloadUrl(
                    systemId = systemId,
                    mediaType = media,
                    region = region,
                    credentials = credentials,
                ) ?: continue
                val path = mediaCache.fetchImage(url) ?: continue
                return path
            }
            // Region-less attempt (some system media is world-shared).
            val bare = screenScraper.systemMediaDownloadUrl(
                systemId = systemId,
                mediaType = media,
                region = null,
                credentials = credentials,
            ) ?: continue
            mediaCache.fetchImage(bare)?.let { return it }
        }
        return null
    }

    /** Copy shipped system banners out of assets so Coil can load a real file path. */
    private fun seedBundledArt() {
        bundledDir.mkdirs()
        val assets = context.assets
        val names = assets.list(BundledPlatformArt.ASSET_DIR).orEmpty()
        for (name in names) {
            if (!name.endsWith(".png", ignoreCase = true)) continue
            val stem = name.substringBeforeLast('.')
            if (stem !in BundledPlatformArt.PLATFORM_IDS) continue
            val target = File(bundledDir, BundledPlatformArt.assetNameFor(stem))
            if (target.isFile && target.length() > 0L) continue
            runCatching {
                assets.open("${BundledPlatformArt.ASSET_DIR}/$name").use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            }.onFailure { Log.w(TAG, "Failed to seed bundled platform art $name", it) }
        }
    }

    private fun bundledArt(): Map<String, String> =
        bundledDir.listFiles()
            ?.filter { file ->
                file.isFile &&
                    file.length() > 0L &&
                    file.extension.equals("png", ignoreCase = true) &&
                    file.nameWithoutExtension in BundledPlatformArt.PLATFORM_IDS
            }
            ?.associate { it.nameWithoutExtension to it.absolutePath }
            .orEmpty()

    /** Official PLATFORMS banners win over ScreenScraper; user imports still overlay later. */
    private fun applyBundledArt() {
        val bundled = bundledArt()
        if (bundled.isEmpty()) return
        art.value = art.value + bundled
    }

    private fun loadIndex() {
        if (!indexFile.isFile) return
        val parsed = runCatching {
            json.decodeFromString<PlatformArtIndex>(indexFile.readText())
        }.getOrNull() ?: return
        failedUntil = parsed.failedUntil
        art.value = parsed.paths.filter { (_, path) ->
            File(path).isFile && File(path).length() > 0L
        }
    }

    private fun persistIndex() {
        runCatching {
            indexFile.parentFile?.mkdirs()
            indexFile.writeText(
                json.encodeToString(
                    PlatformArtIndex.serializer(),
                    PlatformArtIndex(paths = art.value, failedUntil = failedUntil),
                ),
            )
        }.onFailure { Log.w(TAG, "Failed to persist platform art index", it) }
    }

    @Serializable
    private data class PlatformArtIndex(
        val paths: Map<String, String> = emptyMap(),
        val failedUntil: Map<String, Long> = emptyMap(),
    )

    private companion object {
        const val TAG = "PlatformArt"
        const val REQUEST_SPACING_MS = 350L
        const val FAIL_BACKOFF_MS = 24L * 60L * 60L * 1000L
        /** Product-shot first; logo wheel only as last resort. */
        val MEDIA_PRIORITY = listOf("illustration", "photo", "controleur", "wheel")
        val REGIONS = listOf("us", "wor", "eu", "jp", "ss")
    }
}
