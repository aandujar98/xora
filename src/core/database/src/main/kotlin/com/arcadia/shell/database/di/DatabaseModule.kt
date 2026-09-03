package com.arcadia.shell.database.di

import android.content.Context
import androidx.room.Room
import com.arcadia.shell.database.ArcadiaDatabase
import com.arcadia.shell.database.dao.GameDao
import com.arcadia.shell.database.dao.LibraryRootDao
import com.arcadia.shell.database.dao.PlatformSettingsDao
import com.arcadia.shell.database.dao.PlayerDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ArcadiaDatabase =
        Room.databaseBuilder(context, ArcadiaDatabase::class.java, ArcadiaDatabase.NAME)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideGameDao(database: ArcadiaDatabase): GameDao = database.gameDao()

    @Provides
    fun provideLibraryRootDao(database: ArcadiaDatabase): LibraryRootDao = database.libraryRootDao()

    @Provides
    fun providePlayerDao(database: ArcadiaDatabase): PlayerDao = database.playerDao()

    @Provides
    fun providePlatformSettingsDao(database: ArcadiaDatabase): PlatformSettingsDao =
        database.platformSettingsDao()
}
