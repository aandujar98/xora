package com.arcadia.shell.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.arcadia.shell.model.Game
import com.arcadia.shell.model.LaunchDisplayPreference
import com.arcadia.shell.model.ScrapeState
import com.arcadia.shell.model.TitleCleaner

@Entity(
    tableName = "games",
    indices = [
        Index(value = ["location"], unique = true),
        Index(value = ["platformId"]),
        Index(value = ["sortKey"]),
        Index(value = ["rootId"]),
    ],
)
data class GameEntity(
    @PrimaryKey val id: String,
    /**
     * The library root this row was indexed from. Pruning is scoped by this rather than by a
     * location prefix, because SAF document uris are percent-encoded and `%` is a SQL LIKE
     * wildcard, which would silently match rows belonging to other roots.
     */
    val rootId: String,
    /**
     * The path or document uri this game was indexed from. Unique, so a rescan updates rows in
     * place instead of duplicating a library every time it runs.
     */
    val location: String,
    val title: String,
    val sortKey: String,
    val platformId: String,
    val fileName: String,
    val filePath: String?,
    val documentUri: String?,
    val sizeBytes: Long,
    val lastModified: Long,
    val favorite: Boolean = false,
    val playCount: Int = 0,
    val playTimeMs: Long = 0,
    val lastPlayedAt: Long? = null,
    val heroImagePath: String? = null,
    val logoImagePath: String? = null,
    val boxArtPath: String? = null,
    val trailerUrl: String? = null,
    val trailerResolved: Boolean = false,
    val playerIdOverride: String? = null,
    val launchDisplayPreference: LaunchDisplayPreference = LaunchDisplayPreference.Inherit,
    val scrapeState: ScrapeState = ScrapeState.Pending,
    val crc32: String? = null,
    val md5: String? = null,
    val sha1: String? = null,
    /** Marks rows still present on disk after a scan pass, so removals can be pruned. */
    val lastSeenAt: Long = 0,
)

fun GameEntity.toDomain(): Game = Game(
    id = id,
    title = title,
    sortKey = sortKey,
    platformId = platformId,
    fileName = fileName,
    filePath = filePath,
    documentUri = documentUri,
    sizeBytes = sizeBytes,
    favorite = favorite,
    playCount = playCount,
    playTimeMs = playTimeMs,
    lastPlayedAt = lastPlayedAt,
    heroImagePath = heroImagePath,
    logoImagePath = logoImagePath,
    boxArtPath = boxArtPath,
    trailerUrl = trailerUrl,
    trailerResolved = trailerResolved,
    playerIdOverride = playerIdOverride,
    launchDisplayPreference = launchDisplayPreference,
    scrapeState = scrapeState,
)

fun Game.toEntity(
    rootId: String,
    lastModified: Long = 0,
    lastSeenAt: Long = 0,
): GameEntity = GameEntity(
    id = id,
    rootId = rootId,
    location = filePath ?: documentUri.orEmpty(),
    title = title,
    sortKey = sortKey.ifBlank { TitleCleaner.sortKey(title) },
    platformId = platformId,
    fileName = fileName,
    filePath = filePath,
    documentUri = documentUri,
    sizeBytes = sizeBytes,
    lastModified = lastModified,
    favorite = favorite,
    playCount = playCount,
    playTimeMs = playTimeMs,
    lastPlayedAt = lastPlayedAt,
    heroImagePath = heroImagePath,
    logoImagePath = logoImagePath,
    boxArtPath = boxArtPath,
    trailerUrl = trailerUrl,
    trailerResolved = trailerResolved,
    playerIdOverride = playerIdOverride,
    launchDisplayPreference = launchDisplayPreference,
    scrapeState = scrapeState,
    lastSeenAt = lastSeenAt,
)
