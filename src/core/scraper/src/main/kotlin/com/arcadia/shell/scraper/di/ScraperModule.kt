package com.arcadia.shell.scraper.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ScraperModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        // Artwork downloads are large enough that a stalled transfer would otherwise hold the
        // scrape queue open indefinitely.
        .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        // Every one of these APIs returns far more fields than the shell reads, and they add more
        // over time, so unknown keys must never be an error.
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    private const val CONNECT_TIMEOUT_SECONDS = 15L
    private const val READ_TIMEOUT_SECONDS = 30L
    private const val CALL_TIMEOUT_SECONDS = 90L
}
