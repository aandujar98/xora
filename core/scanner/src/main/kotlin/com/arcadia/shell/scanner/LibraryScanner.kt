package com.arcadia.shell.scanner

import com.arcadia.shell.database.dao.GameDao
import com.arcadia.shell.database.entity.GameEntity
import com.arcadia.shell.database.repository.LibraryRootRepository
import com.arcadia.shell.datastore.ShellPreferences
import com.arcadia.shell.model.LibraryRoot
import com.arcadia.shell.model.RootKind
import com.arcadia.shell.model.ScanProgress
import com.arcadia.shell.model.ScrapeState
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
                        // from any existing row with the same stable id.
                        val existingById = gameDao.findByRootId(root.id).associateBy { it.id }
                        val entities = candidates.mapNotNull { file ->
                            toEntity(file, root, startedAt)?.let { fresh ->
                                existingById[fresh.id]?.let { old -> fresh.preservingUserData(old) }
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

                    preferences.setLastScanAt(startedAt)

                    ScanProgress(
                        isRunning = false,
                        filesSeen = filesSeen,
                        gamesFound = gamesFound,
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

        return GameEntity(
            id = stableId(file.location),
            rootId = root.id,
            location = file.location,
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

    private fun GameEntity.preservingUserData(old: GameEntity): GameEntity = copy(
        title = old.title.takeIf { old.scrapeState == ScrapeState.Matched } ?: title,
        sortKey = old.sortKey.takeIf { old.scrapeState == ScrapeState.Matched } ?: sortKey,
        favorite = old.favorite,
        playCount = old.playCount,
        playTimeMs = old.playTimeMs,
        lastPlayedAt = old.lastPlayedAt,
        heroImagePath = old.heroImagePath,
        logoImagePath = old.logoImagePath,
        boxArtPath = old.boxArtPath,
        trailerUrl = old.trailerUrl,
        trailerResolved = old.trailerResolved,
        playerIdOverride = old.playerIdOverride,
        launchDisplayPreference = old.launchDisplayPreference,
        scrapeState = old.scrapeState,
        crc32 = old.crc32,
        md5 = old.md5,
        sha1 = old.sha1,
    )

    private companion object {
        const val BATCH_SIZE = 250
    }
}
