package com.arcadia.shell.launcher.discord

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DiscordPresenceModule {
    @Binds
    @Singleton
    abstract fun bindDiscordRichPresence(impl: DiscordPresenceController): DiscordRichPresence
}
