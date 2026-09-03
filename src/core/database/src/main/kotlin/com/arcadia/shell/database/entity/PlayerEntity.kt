package com.arcadia.shell.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.arcadia.shell.model.Player

@Entity(tableName = "players")
data class PlayerEntity(
    @PrimaryKey val uniqueId: String,
    val name: String,
    val amStartArguments: String,
    val acceptedFilenameRegex: String,
    val killPackageProcesses: Boolean,
    /** Comma separated platform ids; Room has no set support and this never needs querying. */
    val platformIds: String,
    val builtIn: Boolean,
)

fun PlayerEntity.toDomain(): Player = Player(
    uniqueId = uniqueId,
    name = name,
    amStartArguments = amStartArguments,
    acceptedFilenameRegex = acceptedFilenameRegex,
    killPackageProcesses = killPackageProcesses,
    platformIds = platformIds.split(',').mapNotNull { it.trim().takeIf(String::isNotEmpty) }.toSet(),
    builtIn = builtIn,
)

fun Player.toEntity(): PlayerEntity = PlayerEntity(
    uniqueId = uniqueId,
    name = name,
    amStartArguments = amStartArguments,
    acceptedFilenameRegex = acceptedFilenameRegex,
    killPackageProcesses = killPackageProcesses,
    platformIds = platformIds.joinToString(","),
    builtIn = builtIn,
)
