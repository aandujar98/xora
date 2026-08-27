package com.arcadia.shell.scraper

import android.util.Log
import com.arcadia.shell.database.repository.LibraryRepository
import com.arcadia.shell.model.Game
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

data class LibraryHashBatchResult(
    val processed: Int,
    val hashed: Int,
    val skipped: Int,
    val hasMore: Boolean,
    val remainingMissing: Int,
    val nextOffset: Int,
)

/**
 * Background RetroAchievements / ScreenScraper hash pass for the whole library.
 *
 * Independent of artwork credentials — launcher RA lookups need MD5s stored on every ROM, not only
 * titles that happen to scrape through ScreenScraper.
 */
@Singleton
class LibraryHashCoordinator @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val hasher: RomHasher,
) {
    /**
     * @param rehashAll when true, recompute hashes for every ROM (fixes stale size-based MD5s).
     *   When false, only games with a missing MD5 are processed.
     * @param offset used with [rehashAll] to walk the library in pages.
     */
    suspend fun hashBatch(
        limit: Int = DEFAULT_BATCH,
        rehashAll: Boolean = false,
        offset: Int = 0,
    ): LibraryHashBatchResult {
        val queue = if (rehashAll) {
            libraryRepository.allRomsForHashing(limit, offset)
        } else {
            libraryRepository.pendingHashes(limit)
        }

        var hashed = 0
        var skipped = 0

        for (game in queue) {
            if (game.isAndroidApp) {
                skipped++
                continue
            }
            if (!isHashable(game)) {
                skipped++
                continue
            }

            val hashes = runCatching { hasher.hash(game) }.getOrNull()
            if (hashes == null) {
                skipped++
                continue
            }
            libraryRepository.setHashes(game.id, hashes.crc32, hashes.md5, hashes.sha1)
            hashed++
            Log.i(
                TAG,
                "Stored hash ${game.fileName} platform=${game.platformId} md5=${hashes.md5}",
            )
            delay(YIELD_MS)
        }

        val remaining = libraryRepository.countMissingHashes()
        val nextOffset = offset + queue.size
        val hasMore = if (rehashAll) {
            queue.size >= limit
        } else {
            remaining > 0
        }

        return LibraryHashBatchResult(
            processed = queue.size,
            hashed = hashed,
            skipped = skipped,
            hasMore = hasMore,
            remainingMissing = remaining,
            nextOffset = nextOffset,
        )
    }

    private fun isHashable(game: Game): Boolean {
        val extension = game.fileName.substringAfterLast('.', missingDelimiterValue = "")
            .lowercase()
        if (extension == "7z") return false
        if (game.platformId in RaHashRules.UNSUPPORTED_CUSTOM_HASH_PLATFORMS) return false
        if (game.platformId in RaHashRules.DISC_HASH_PLATFORMS &&
            extension in RaDiscHash.UNSUPPORTED_DISC_EXTENSIONS
        ) {
            return false
        }
        if (game.filePath == null && game.documentUri == null) return false
        return true
    }

    private companion object {
        const val TAG = "LibraryHash"
        const val DEFAULT_BATCH = 25
        const val YIELD_MS = 15L
    }
}
