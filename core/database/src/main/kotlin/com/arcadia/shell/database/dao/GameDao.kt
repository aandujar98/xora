package com.arcadia.shell.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.arcadia.shell.database.entity.GameEntity
import com.arcadia.shell.model.LaunchDisplayPreference
import com.arcadia.shell.model.ScrapeState
import kotlinx.coroutines.flow.Flow

data class PlatformCount(val platformId: String, val gameCount: Int)

@Dao
interface GameDao {

    @Query("SELECT * FROM games ORDER BY sortKey ASC")
    fun observeAll(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE platformId = :platformId ORDER BY sortKey ASC")
    fun observeByPlatform(platformId: String): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE favorite = 1 ORDER BY sortKey ASC")
    fun observeFavorites(): Flow<List<GameEntity>>

    @Query("SELECT * FROM games WHERE lastPlayedAt IS NOT NULL ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<GameEntity>>

    @Query("SELECT platformId, COUNT(*) AS gameCount FROM games GROUP BY platformId")
    fun observePlatformCounts(): Flow<List<PlatformCount>>

    @Query("SELECT * FROM games WHERE id = :id")
    suspend fun findById(id: String): GameEntity?

    @Query("SELECT * FROM games WHERE rootId = :rootId")
    suspend fun findByRootId(rootId: String): List<GameEntity>

    @Query(
        """
        SELECT * FROM games
        WHERE scrapeState = :state AND platformId != 'android'
        LIMIT :limit
        """,
    )
    suspend fun findByScrapeState(state: ScrapeState, limit: Int): List<GameEntity>

    @Query("SELECT COUNT(*) FROM games")
    suspend fun count(): Int

    @Upsert
    suspend fun upsertAll(games: List<GameEntity>)

    /**
     * Removes rows that a completed scan pass did not touch, which is how deletions on disk
     * propagate into the library. Scoped to a single root so an unmounted SD card cannot wipe
     * games belonging to internal storage.
     */
    @Query("DELETE FROM games WHERE rootId = :rootId AND lastSeenAt < :scanStartedAt")
    suspend fun pruneMissing(rootId: String, scanStartedAt: Long): Int

    @Query("UPDATE games SET favorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: String, favorite: Boolean)

    @Query(
        """
        UPDATE games
        SET playCount = playCount + 1,
            playTimeMs = playTimeMs + :elapsedMs,
            lastPlayedAt = :playedAt
        WHERE id = :id
        """,
    )
    suspend fun recordPlaySession(id: String, elapsedMs: Long, playedAt: Long)

    @Query(
        """
        UPDATE games
        SET heroImagePath = :hero, logoImagePath = :logo, boxArtPath = :boxArt,
            title = :title, sortKey = :sortKey, scrapeState = :state
        WHERE id = :id
        """,
    )
    suspend fun applyScrapeResult(
        id: String,
        title: String,
        sortKey: String,
        hero: String?,
        logo: String?,
        boxArt: String?,
        state: ScrapeState,
    )

    @Query("UPDATE games SET scrapeState = :state WHERE id = :id")
    suspend fun setScrapeState(id: String, state: ScrapeState)

    @Query(
        """
        UPDATE games
        SET scrapeState = :state, trailerUrl = NULL, trailerResolved = 0
        WHERE id = :id
        """,
    )
    suspend fun resetScrape(id: String, state: ScrapeState)

    @Query(
        """
        UPDATE games
        SET scrapeState = :state, trailerUrl = NULL, trailerResolved = 0
        WHERE platformId = :platformId AND platformId != 'android'
        """,
    )
    suspend fun resetScrapeForPlatform(platformId: String, state: ScrapeState)

    @Query(
        """
        UPDATE games
        SET trailerUrl = :trailerUrl, trailerResolved = 1
        WHERE id = :id
        """,
    )
    suspend fun setTrailer(id: String, trailerUrl: String?)

    /** Re-open trailer lookup for games that previously resolved to nothing. */
    @Query(
        """
        UPDATE games
        SET trailerResolved = 0
        WHERE trailerUrl IS NULL AND trailerResolved = 1 AND platformId != 'android'
        """,
    )
    suspend fun clearNullTrailerResolutions(): Int

    @Query("UPDATE games SET crc32 = :crc32, md5 = :md5, sha1 = :sha1 WHERE id = :id")
    suspend fun setHashes(id: String, crc32: String?, md5: String?, sha1: String?)

    @Query("UPDATE games SET playerIdOverride = :playerId WHERE id = :id")
    suspend fun setPlayerOverride(id: String, playerId: String?)

    @Query("UPDATE games SET launchDisplayPreference = :preference WHERE id = :id")
    suspend fun setLaunchDisplayPreference(id: String, preference: LaunchDisplayPreference)

    @Query("DELETE FROM games")
    suspend fun deleteAll()
}
