package com.arcadia.shell.database

import androidx.room.TypeConverter
import com.arcadia.shell.model.LaunchDisplayPreference
import com.arcadia.shell.model.RootKind
import com.arcadia.shell.model.ScrapeState

/**
 * Enums are stored by name rather than ordinal so reordering a declaration cannot silently
 * reinterpret existing rows.
 */
class ArcadiaConverters {

    @TypeConverter
    fun fromLaunchDisplayPreference(value: LaunchDisplayPreference): String = value.name

    @TypeConverter
    fun toLaunchDisplayPreference(value: String): LaunchDisplayPreference =
        runCatching { LaunchDisplayPreference.valueOf(value) }
            .getOrDefault(LaunchDisplayPreference.Inherit)

    @TypeConverter
    fun fromScrapeState(value: ScrapeState): String = value.name

    @TypeConverter
    fun toScrapeState(value: String): ScrapeState =
        runCatching { ScrapeState.valueOf(value) }.getOrDefault(ScrapeState.Pending)

    @TypeConverter
    fun fromRootKind(value: RootKind): String = value.name

    @TypeConverter
    fun toRootKind(value: String): RootKind =
        runCatching { RootKind.valueOf(value) }.getOrDefault(RootKind.SafTree)
}
