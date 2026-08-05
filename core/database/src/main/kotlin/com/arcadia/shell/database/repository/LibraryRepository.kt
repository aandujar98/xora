package com.arcadia.shell.database.repository

import com.arcadia.shell.database.dao.GameDao
import com.arcadia.shell.database.entity.GameEntity
import com.arcadia.shell.database.entity.toDomain
import com.arcadia.shell.model.Game
import com.arcadia.shell.model.LaunchDisplayPreference
import com.arcadia.shell.model.PlatformCatalog
import com.arcadia.shell.model.PlatformSummary
import com.arcadia.shell.model.ScrapeState
import com.arcadia.shell.model.TitleCleaner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryRepository @Inject constructor(
    private val gameDao: GameDao,
) {
    fun observeGames(): Flow<List<Game>> =
        gameDao.observeAll().map { rows -> rows.map(GameEntity::toDomain) }

    fun observeGamesForPlatform(platformId: String): Flow<List<Game>> =
        gameDao.observeByPlatform(platformId).map { rows -> rows.map(GameEntity::toDomain) }

    fun observeFavorites(): Flow<List<Game>> =
        gameDao.observeFavorites().map { rows -> rows.map(GameEntity::toDomain) }

    fun observeRecent(limit: Int = 12): Flow<List<Game>> =
        gameDao.observeRecent(limit).map { rows -> rows.map(GameEntity::toDomain) }

    /**
     * Platform rows the library actually contains, ordered by the catalog rather than
     * alphabetically so consoles appear in a familiar, stable sequence.
     */
    fun observePlatformSummaries(): Flow<List<PlatformSummary>> =
        gameDao.observePlatformCounts().map { counts ->
            val countById = counts.associate { it.platformId to it.gameCount }
            PlatformCatalog.platforms
                .filter { countById.containsKey(it.id) }
                .map { PlatformSummary(platform = it, gameCount = countById.getValue(it.id)) }
        }

    suspend fun findById(id: String): Game? = gameDao.findById(id)?.toDomain()

    suspend fun count(): Int = gameDao.count()

    suspend fun setFavorite(gameId: String, favorite: Boolean) =
        gameDao.setFavorite(gameId, favorite)

    suspend fun recordPlaySession(gameId: String, elapsedMs: Long) =
        gameDao.recordPlaySession(gameId, elapsedMs, System.currentTimeMillis())

    suspend fun setPlayerOverride(gameId: String, playerId: String?) =
        gameDao.setPlayerOverride(gameId, playerId)

    suspend fun setLaunchDisplayPreference(gameId: String, preference: LaunchDisplayPreference) =
        gameDao.setLaunchDisplayPreference(gameId, preference)

    suspend fun pendingScrapes(limit: Int): List<Game> =
        gameDao.findByScrapeState(ScrapeState.Pending, limit).map(GameEntity::toDomain)

    suspend fun applyScrapeResult(
        gameId: String,
        title: String,
        heroPath: String?,
        logoPath: String?,
        boxArtPath: String?,
    ) = gameDao.applyScrapeResult(
        id = gameId,
        title = title,
        sortKey = TitleCleaner.sortKey(title),
        hero = heroPath,
        logo = logoPath,
        boxArt = boxArtPath,
        state = ScrapeState.Matched,
    )

    suspend fun markScrapeState(gameId: String, state: ScrapeState) =
        gameDao.setScrapeState(gameId, state)

    /** Clears artwork-match state and trailer cache so a scrape pass will run again. */
    suspend fun resetScrape(gameId: String) =
        gameDao.resetScrape(gameId, ScrapeState.Pending)

    suspend fun resetScrapeForPlatform(platformId: String) =
        gameDao.resetScrapeForPlatform(platformId, ScrapeState.Pending)

    suspend fun setTrailer(gameId: String, trailerUrl: String?) =
        gameDao.setTrailer(gameId, trailerUrl)

    /** Clears failed trailer lookups so YouTube / Steam fallbacks can run again. */
    suspend fun clearNullTrailerResolutions(): Int =
        gameDao.clearNullTrailerResolutions()

    suspend fun setHashes(gameId: String, crc32: String?, md5: String?, sha1: String?) =
        gameDao.setHashes(gameId, crc32, md5, sha1)

    suspend fun clear() = gameDao.deleteAll()
}
