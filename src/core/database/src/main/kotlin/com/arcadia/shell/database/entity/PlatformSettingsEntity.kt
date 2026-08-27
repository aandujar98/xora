package com.arcadia.shell.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.arcadia.shell.model.LaunchDisplayPreference

/**
 * Per-platform overrides. A row only exists once the user has customised something, so absence
 * means "use the defaults".
 */
@Entity(tableName = "platform_settings")
data class PlatformSettingsEntity(
    @PrimaryKey val platformId: String,
    val selectedPlayerId: String? = null,
    val launchDisplayPreference: LaunchDisplayPreference = LaunchDisplayPreference.Inherit,
    val hidden: Boolean = false,
)
