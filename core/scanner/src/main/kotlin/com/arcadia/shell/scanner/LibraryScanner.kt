package com.arcadia.shell.scanner

import android.provider.DocumentsContract
import androidx.core.net.toUri
import com.arcadia.shell.database.dao.GameDao
import com.arcadia.shell.database.entity.GameEntity
import com.arcadia.shell.database.repository.LibraryRootRepository
import com.arcadia.shell.datastore.ShellPreferences
import com.arcadia.shell.model.LaunchDisplayPreference
import com.arcadia.shell.model.LibraryRoot
import com.arcadia.shell.model.RootKind
import com.arcadia.shell.model.ScanProgress
import com.arcadia.shell.model.ScrapeState
import com.arcadia.shell.model.StorageDocumentIds
import com.arcadia.shell.model.TitleCleaner
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryScanner @Inject constructor(
    private val rootRepository: LibraryRootRepository,
    private val gameDao: GameDao,
    private val filesystemWalker: FilesystemRomWalker,
    private val safWalker: SafRomWalker,
    private val platformResolver: PlatformResolver,
    private val preferences: ShellPreferences,
) {
    private val _progress = MutableStateFlow(ScanProgress())
    val progress: StateFlow<ScanProgress> = _progress.asStateFlow()

    /** A second scan while one is in flight would double-count and fight over the same rows. */
    private val scanLock = Mutex()

    suspend fun scan(dispatcher: CoroutineDispatcher = Dispatchers.IO): ScanProgress {
        if (scanLock.isLocked) return _progress.value

        return scanLock.withLock {
            withContext(dispatcher) {
                val startedAt = System.currentTimeMillis()
                _progress.value = ScanProgress(isRunning = true)

                try {
                    var filesSeen = 0
                    var gamesFound = 0

                    for (root in rootRepository.getRoots()) {
                        _progress.value = _progress.value.copy(currentRoot = root.label)

                        val discovered = walkerFor(root).walk(root).toList()
                        filesSeen += discovered.size

                        val candidates = DiscTrackFilter.filter(discovered)
                        // Upsert replaces the whole row, so scrape/play metadata has to be copied
                        // from any existing row with the same stable id (including a twin from the
                        // other access method).
                        val existingById = gameDao.getAll().associateBy { it.id }
                        val entities = candidates.mapNotNull { file ->
                            toEntity(file, root, startedAt)?.let { fresh ->
                                existingById[fresh.id]
                                    ?.let { fresh.preservingUserData(it).mergingAccess(it) }
                                    ?: fresh
                            }
                        }

                        entities.chunked(BATCH_SIZE).forEach { gameDao.upsertAll(it) }
                        gamesFound += entities.size

                        // Pruning is keyed on having actually read the root. An unmounted volume
                        // yields nothing, and treating that as "everything was deleted" would
                        // destroy a library the moment an SD card is removed.
                        if (discovered.isNotEmpty()) {
                            gameDao.pruneMissing(root.id, startedAt)
                        }

                        _progress.value = _progress.value.copy(
                            filesSeen = filesSeen,
                            gamesFound = gamesFound,
                        )
                    }

                    // Collapse leftover FS↔SAF twins from older scans (different stable ids).
                    val removed = mergeDuplicateGames(startedAt)
                    gamesFound = (gamesFound - removed).coerceAtLeast(0)

                    preferences.setLastScanAt(startedAt)

                    ScanProgress(
                        isRunning = false,
                        filesSeen = filesSeen,
                        gamesFound = gameDao.count(),
                        finishedAt = System.currentTimeMillis(),
                    ).also { _progress.value = it }
                } catch (throwable: Throwable) {
                    ScanProgress(
                        isRunning = false,
                        error = throwable.message ?: throwable::class.simpleName,
                        finishedAt = System.currentTimeMillis(),
                    ).also { _progress.value = it }
                }
            }
        }
    }

    private fun walkerFor(root: LibraryRoot): RomWalker = when (root.kind) {
        RootKind.Filesystem -> filesystemWalker
        RootKind.SafTree -> safWalker
    }

    private fun toEntity(
        file: ScannedFile,
        root: LibraryRoot,
        scanStartedAt: Long,
    ): GameEntity? {
        val platform = platformResolver.resolve(file, root.forcedPlatformId) ?: return null
        val title = TitleCleaner.clean(file.name)
        // Prefer the filesystem path as the stable identity so SAF and all-files scans of the same
        // ROM land on one row that XOrA Emulator can launch.
        val location = file.filePath ?: file.documentUri.orEmpty()
        if (location.isBlank()) return null

        return GameEntity(
            id = stableId(location),
            rootId = root.id,
            location = location,
            title = title,
            sortKey = TitleCleaner.sortKey(title),
            platformId = platform.id,
            fileName = file.name,
            filePath = file.filePath,
            documentUri = file.documentUri,
            sizeBytes = file.sizeBytes,
            lastModified = file.lastModified,
            lastSeenAt = scanStartedAt,
        )
    }

    /**
     * Ids are derived from the file location rather than generated, so rescanning an unchanged
     * library reuses the same rows and preserves favourites, playtime, and scraped artwork.
     */
    private fun stableId(location: String): String =
        MessageDigest.getInstance("SHA-1")
            .digest(location.toByteArray())
            .joinToString("") { "%02x".format(it) }

    /**
     * Merges games that represent the same physical ROM under different access methods
     * (filesystem path vs SAF content uri). Returns how many duplicate rows were deleted.
     */
    private suspend fun mergeDuplicateGames(scanStartedAt: Long): Int {
        val all = gameDao.getAll().filter { it.platformId != "android" }
        if (all.size < 2) return 0

        val groups = all.groupBy { logicalKey(it) ?: fallbackKey(it) }
        var removed = 0
        val toUpsert = mutableListOf<GameEntity>()
        val toDelete = mutableListOf<String>()

        for ((_, group) in groups) {
            if (group.size < 2) continue
            val keeper = selectCanonical(group)
            var merged = keeper.copy(lastSeenAt = scanStartedAt)
            for (other in group) {
                if (other.id == keeper.id) continue
                merged = merged.preservingUserData(other).mergingAccess(other)
                toDelete += other.id
            }
            if (merged != keeper || merged.lastSeenAt != keeper.lastSeenAt) {
                toUpsert += merged
            }
        }

        if (toUpsert.isNotEmpty()) {
            toUpsert.chunked(BATCH_SIZE).forEach { gameDao.upsertAll(it) }
        }
        if (toDelete.isNotEmpty()) {
            toDelete.chunked(BATCH_SIZE).forEach { gameDao.deleteByIds(it) }
            removed = toDelete.size
        }
        return removed
    }

    private fun logicalKey(game: GameEntity): String? {
        game.filePath?.let { StorageDocumentIds.logicalKeyForPath(it) }?.let { return it }
        val documentId = game.documentUri?.let { uri ->
            runCatching {
                val parsed = uri.toUri()
                DocumentsContract.getDocumentId(parsed)
                    ?: DocumentsContract.getTreeDocumentId(parsed)
            }.getOrNull()
        }
        return documentId?.let(StorageDocumentIds::logicalKeyForDocumentId)
    }

    private fun fallbackKey(game: GameEntity): String =
        "${game.platformId}\u0000${game.fileName.lowercase()}\u0000${game.sizeBytes}"

    /**
     * Prefer the row that already has a filesystem path (XOrA Emulator / Dolphin / DuckStation),
     * then the one with richer user/scrape data.
     */
    private fun selectCanonical(group: List<GameEntity>): GameEntity =
        group.sortedWith(
            compareByDescending<GameEntity> { it.filePath != null }
                .thenByDescending { it.scrapeState == ScrapeState.Matched }
                .thenByDescending { it.playCount }
                .thenByDescending { it.favorite }
                .thenBy { it.id },
        ).first()

    private fun GameEntity.preservingUserData(old: GameEntity): GameEntity = copy(
        title = old.title.takeIf { old.scrapeState == ScrapeState.Matched } ?: title,
        sortKey = old.sortKey.takeIf { old.scrapeState == ScrapeState.Matched } ?: sortKey,
        favorite = favorite || old.favorite,
        playCount = maxOf(playCount, old.playCount),
        playTimeMs = maxOf(playTimeMs, old.playTimeMs),
        lastPlayedAt = listOfNotNull(lastPlayedAt, old.lastPlayedAt).maxOrNull(),
        heroImagePath = heroImagePath ?: old.heroImagePath,
        logoImagePath = logoImagePath ?: old.logoImagePath,
        boxArtPath = boxArtPath ?: old.boxArtPath,
        soundBitePath = soundBitePath ?: old.soundBitePath,
        trailerUrl = trailerUrl ?: old.trailerUrl,
        trailerResolved = trailerResolved || old.trailerResolved,
        playerIdOverride = playerIdOverride ?: old.playerIdOverride,
        launchDisplayPreference = launchDisplayPreference.takeIf {
            it != LaunchDisplayPreference.Inherit
        } ?: old.launchDisplayPreference,
        scrapeState = when {
            scrapeState == ScrapeState.Matched -> scrapeState
            old.scrapeState == ScrapeState.Matched -> old.scrapeState
            else -> scrapeState
        },
        crc32 = crc32 ?: old.crc32,
        md5 = md5 ?: old.md5,
        sha1 = sha1 ?: old.sha1,
    )

    private fun GameEntity.mergingAccess(other: GameEntity): GameEntity = copy(
        filePath = filePath ?: other.filePath,
        documentUri = preferredDocumentUri(documentUri, other.documentUri),
    )

    /**
     * Prefer a real tree-granted SAF uri over a synthesized externalstorage document uri.
     */
    private fun preferredDocumentUri(a: String?, b: String?): String? {
        val candidates = listOfNotNull(a, b)
        if (candidates.isEmpty()) return null
        return candidates.firstOrNull { uri ->
            runCatching {
                val parsed = uri.toUri()
                parsed.authority == "com.android.externalstorage.documents" &&
                    parsed.toString().contains("/tree/", ignoreCase = false)
            }.getOrDefault(false)
        } ?: candidates.first()
    }

    private companion object {
        const val BATCH_SIZE = 250
    }
}
