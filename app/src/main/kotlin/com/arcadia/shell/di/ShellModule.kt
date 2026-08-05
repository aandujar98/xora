package com.arcadia.shell.di

import android.content.Context
import com.arcadia.shell.display.DisplayTopologyMonitor
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ShellModule {

    @Provides
    @Singleton
    fun provideDisplayTopologyMonitor(
        @ApplicationContext context: Context,
    ): DisplayTopologyMonitor = DisplayTopologyMonitor(context)
}
