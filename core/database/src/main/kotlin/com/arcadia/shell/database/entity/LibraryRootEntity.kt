package com.arcadia.shell.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.arcadia.shell.model.LibraryRoot
import com.arcadia.shell.model.RootKind

@Entity(
    tableName = "library_roots",
    indices = [Index(value = ["location"], unique = true)],
)
data class LibraryRootEntity(
    @PrimaryKey val id: String,
    val location: String,
    val kind: RootKind,
    val label: String,
    val forcedPlatformId: String?,
    val recursive: Boolean,
    val addedAt: Long,
)

fun LibraryRootEntity.toDomain(): LibraryRoot = LibraryRoot(
    id = id,
    location = location,
    kind = kind,
    label = label,
    forcedPlatformId = forcedPlatformId,
    recursive = recursive,
)

fun LibraryRoot.toEntity(addedAt: Long = System.currentTimeMillis()): LibraryRootEntity =
    LibraryRootEntity(
        id = id,
        location = location,
        kind = kind,
        label = label,
        forcedPlatformId = forcedPlatformId,
        recursive = recursive,
        addedAt = addedAt,
    )
