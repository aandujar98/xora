package com.arcadia.shell.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.arcadia.shell.database.dao.GameDao
import com.arcadia.shell.database.dao.LibraryRootDao
import com.arcadia.shell.database.dao.PlatformSettingsDao
import com.arcadia.shell.database.dao.PlayerDao
import com.arcadia.shell.database.entity.GameEntity
import com.arcadia.shell.database.entity.LibraryRootEntity
import com.arcadia.shell.database.entity.PlatformSettingsEntity
import com.arcadia.shell.database.entity.PlayerEntity

@Database(
    entities = [
        GameEntity::class,
        LibraryRootEntity::class,
        PlayerEntity::class,
        PlatformSettingsEntity::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(ArcadiaConverters::class)
abstract class ArcadiaDatabase : RoomDatabase() {
    abstract fun gameDao(): GameDao
    abstract fun libraryRootDao(): LibraryRootDao
    abstract fun playerDao(): PlayerDao
    abstract fun platformSettingsDao(): PlatformSettingsDao

    companion object {
        const val NAME = "arcadia.db"

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE games ADD COLUMN shortcutIconPath TEXT")
            }
        }
    }
}
