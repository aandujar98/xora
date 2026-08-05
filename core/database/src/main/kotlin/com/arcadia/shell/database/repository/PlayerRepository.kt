package com.arcadia.shell.database.repository

import com.arcadia.shell.database.dao.PlatformSettingsDao
import com.arcadia.shell.database.dao.PlayerDao
import com.arcadia.shell.database.entity.PlatformSettingsEntity
import com.arcadia.shell.database.entity.PlayerEntity
import com.arcadia.shell.database.entity.toDomain
import com.arcadia.shell.database.entity.toEntity
import com.arcadia.shell.model.LaunchDisplayPreference
import com.arcadia.shell.model.Player
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerRepository @Inject constructor(
    private val playerDao: PlayerDao,
    private val platformSettingsDao: PlatformSettingsDao,
) {
    fun observePlayers(): Flow<List<Player>> =
        playerDao.observeAll().map { rows -> rows.map(PlayerEntity::toDomain) }

    fun observePlatformSettings(): Flow<List<PlatformSettingsEntity>> =
        platformSettingsDao.observeAll()

    suspend fun getPlayers(): List<Player> = playerDao.getAll().map(PlayerEntity::toDomain)

    suspend fun findById(id: String): Player? = playerDao.findById(id)?.toDomain()

    suspend fun upsert(players: List<Player>) = playerDao.upsertAll(players.map(Player::toEntity))

    suspend fun deleteCustom(id: String) = playerDao.deleteCustom(id)

    /**
     * Upserts every bundled launch recipe. Built-in rows are keyed by [Player.uniqueId], so new
     * emulators (Azahar package splits, etc.) appear on upgrade and broken templates get patched.
     * Custom (`builtIn = false`) players are never touched. Users who edit a built-in profile in
     * place will see those edits refreshed on the next launch — duplicate into a custom player to
     * keep a permanent override.
     */
    suspend fun syncBuiltIns(builtIn: List<Player>) {
        playerDao.upsertAll(builtIn.map(Player::toEntity))
    }

    suspend fun settingsFor(platformId: String): PlatformSettingsEntity? =
        platformSettingsDao.findById(platformId)

    suspend fun selectPlayerForPlatform(platformId: String, playerId: String?) {
        val existing = platformSettingsDao.findById(platformId)
        platformSettingsDao.upsert(
            existing?.copy(selectedPlayerId = playerId)
                ?: PlatformSettingsEntity(platformId = platformId, selectedPlayerId = playerId),
        )
    }

    suspend fun setPlatformLaunchDisplay(
        platformId: String,
        preference: LaunchDisplayPreference,
    ) {
        val existing = platformSettingsDao.findById(platformId)
        platformSettingsDao.upsert(
            existing?.copy(launchDisplayPreference = preference)
                ?: PlatformSettingsEntity(
                    platformId = platformId,
                    launchDisplayPreference = preference,
                ),
        )
    }
}
