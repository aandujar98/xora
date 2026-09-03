package com.arcadia.shell.database.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.arcadia.shell.database.entity.LibraryRootEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryRootDao {

    @Query("SELECT * FROM library_roots ORDER BY addedAt ASC")
    fun observeAll(): Flow<List<LibraryRootEntity>>

    @Query("SELECT * FROM library_roots ORDER BY addedAt ASC")
    suspend fun getAll(): List<LibraryRootEntity>

    @Upsert
    suspend fun upsert(root: LibraryRootEntity)

    @Query("DELETE FROM library_roots WHERE id = :id")
    suspend fun delete(id: String)
}
