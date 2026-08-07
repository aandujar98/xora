package com.arcadia.shell.launcher

import com.arcadia.shell.database.dao.GameDao
import com.arcadia.shell.database.entity.GameEntity
import com.arcadia.shell.datastore.ShellPreferences
import com.arcadia.shell.model.GamePlatform
import com.arcadia.shell.model.ScrapeState
import com.arcadia.shell.model.TitleCleaner
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mirrors the PackageManager launchable set into the games table under a synthetic root.
 *
 * Favourites and play stats are preserved across refreshes by merging any existing android rows
 * before the upsert. Uninstalled packages are pruned the same way ROM scans prune missing files.
 */
@Singleton
class InstalledAppSync @Inject constructor(
    private val catalog: InstalledAppCatalog,
    private val gameDao: GameDao,
    private val preferences: ShellPreferences,
) {
    suspend fun refresh() {
        val startedAt = System.currentTimeMillis()
        val existing = gameDao.findByRootId(ROOT_ID).associateBy { it.id }
        val apps = if (preferences.settings.first().androidAppSyncEnabled) {
            catalog.listLaunchableApps()
        } else {
            // Syncing off: fall through to the prune below so the Apps tab clears out.
            emptyList()
        }

        val entities = apps.map { app ->
            val id = idFor(app.packageName)
            val previous = existing[id]
            GameEntity(
                id = id,
                rootId = ROOT_ID,
                location = id,
                title = app.label,
                sortKey = TitleCleaner.sortKey(app.label),
                platformId = GamePlatform.Android.id,
                fileName = app.packageName,
                filePath = app.packageName,
                documentUri = null,
                sizeBytes = 0,
                lastModified = startedAt,
                favorite = previous?.favorite ?: false,
                playCount = previous?.playCount ?: 0,
                playTimeMs = previous?.playTimeMs ?: 0,
                lastPlayedAt = previous?.lastPlayedAt,
                heroImagePath = null,
                logoImagePath = null,
                boxArtPath = iconPathFor(app.packageName),
                playerIdOverride = null,
                scrapeState = ScrapeState.NoMatch,
                lastSeenAt = startedAt,
            )
        }

        if (entities.isNotEmpty()) {
            entities.chunked(BATCH_SIZE).forEach { gameDao.upsertAll(it) }
        }
        // Always prune: an empty launchable set (unlikely) still means every prior row is gone.
        gameDao.pruneMissing(ROOT_ID, startedAt)
    }

    companion object {
        const val ROOT_ID = "android-apps"
        const val ID_PREFIX = "android:"
        const val ICON_SCHEME = "appicon:"

        fun idFor(packageName: String): String = "$ID_PREFIX$packageName"

        fun iconPathFor(packageName: String): String = "$ICON_SCHEME$packageName"

        private const val BATCH_SIZE = 250
    }
}
