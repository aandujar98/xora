package com.arcadia.shell.database.repository

import com.arcadia.shell.database.dao.LibraryRootDao
import com.arcadia.shell.database.entity.LibraryRootEntity
import com.arcadia.shell.database.entity.toDomain
import com.arcadia.shell.database.entity.toEntity
import com.arcadia.shell.model.LibraryRoot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryRootRepository @Inject constructor(
    private val libraryRootDao: LibraryRootDao,
) {
    fun observeRoots(): Flow<List<LibraryRoot>> =
        libraryRootDao.observeAll().map { rows -> rows.map(LibraryRootEntity::toDomain) }

    suspend fun getRoots(): List<LibraryRoot> =
        libraryRootDao.getAll().map(LibraryRootEntity::toDomain)

    suspend fun add(root: LibraryRoot) = libraryRootDao.upsert(root.toEntity())

    suspend fun remove(id: String) = libraryRootDao.delete(id)
}
