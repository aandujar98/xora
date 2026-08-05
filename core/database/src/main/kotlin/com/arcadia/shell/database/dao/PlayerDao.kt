package com.arcadia.shell.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.arcadia.shell.database.entity.PlatformSettingsEntity
import com.arcadia.shell.database.entity.PlayerEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayerDao {

    @Query("SELECT * FROM players ORDER BY name ASC")
    fun observeAll(): Flow<List<PlayerEntity>>

    @Query("SELECT * FROM players ORDER BY name ASC")
    suspend fun getAll(): List<PlayerEntity>

    @Query("SELECT * FROM players WHERE uniqueId = :id")
    suspend fun findById(id: String): PlayerEntity?

    @Upsert
    suspend fun upsertAll(players: List<PlayerEntity>)

    @Query("DELETE FROM players WHERE uniqueId = :id AND builtIn = 0")
    suspend fun deleteCustom(id: String)

    @Query("SELECT COUNT(*) FROM players")
    suspend fun count(): Int
}

@Dao
interface PlatformSettingsDao {

    @Query("SELECT * FROM platform_settings")
    fun observeAll(): Flow<List<PlatformSettingsEntity>>

    @Query("SELECT * FROM platform_settings WHERE platformId = :platformId")
    suspend fun findById(platformId: String): PlatformSettingsEntity?

    @Upsert
    suspend fun upsert(settings: PlatformSettingsEntity)
}
