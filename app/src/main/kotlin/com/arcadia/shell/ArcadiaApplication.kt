package com.arcadia.shell

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.gif.AnimatedImageDecoder
import coil3.memory.MemoryCache
import com.arcadia.shell.audio.BackgroundMusicController
import com.arcadia.shell.audio.OnboardingMusicController
import com.arcadia.shell.companion.CompanionOverlayService
import com.arcadia.shell.datastore.ShellPreferences
import com.arcadia.shell.feature.home.GameCompanionController
import com.arcadia.shell.launcher.PlayerSeeder
import com.arcadia.shell.launcher.discord.DiscordRichPresence
import com.arcadia.shell.launcher.notifications.AppForegroundTracker
import com.arcadia.shell.launcher.notifications.ShellSystemNotifier
import com.arcadia.shell.scanner.LibraryAutoScanner
import com.arcadia.shell.scanner.LibraryScanner
import com.arcadia.shell.scraper.LibraryHashScheduler
import com.arcadia.shell.scraper.ScraperScheduler
import com.arcadia.shell.xoranetwork.XoraNetworkAuthCookies
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import okio.Path.Companion.toOkioPath
import javax.inject.Inject

@HiltAndroidApp
class ArcadiaApplication : Application(), SingletonImageLoader.Factory {

    @Inject lateinit var playerSeeder: PlayerSeeder
    @Inject lateinit var preferences: ShellPreferences
    @Inject lateinit var discordRichPresence: DiscordRichPresence
    @Inject lateinit var backgroundMusic: BackgroundMusicController
    @Inject lateinit var onboardingMusic: OnboardingMusicController
    @Inject lateinit var appForegroundTracker: AppForegroundTracker
    @Inject lateinit var shellSystemNotifier: ShellSystemNotifier
    @Inject lateinit var gameCompanionController: GameCompanionController
    @Inject lateinit var xoraNetworkAuthCookies: XoraNetworkAuthCookies
    @Inject lateinit var libraryAutoScanner: LibraryAutoScanner
    @Inject lateinit var libraryScanner: LibraryScanner
    @Inject lateinit var libraryHashScheduler: LibraryHashScheduler
    @Inject lateinit var scraperScheduler: ScraperScheduler

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Seeding touches the database, so it must not run on the main thread during startup.
        applicationScope.launch { playerSeeder.seedIfNeeded() }
        libraryAutoScanner.start()
        libraryScanner.progress
            .distinctUntilChanged { old, new ->
                old.finishedAt == new.finishedAt && old.isRunning == new.isRunning
            }
            .onEach { progress ->
                if (progress.isRunning || progress.finishedAt == null || progress.error != null) {
                    return@onEach
                }
                libraryHashScheduler.enqueue(rehashAll = false, replace = false)
                if (preferences.settings.first().scrapeAfterScan) {
                    scraperScheduler.enqueue()
                }
            }
            .launchIn(applicationScope)

        // Banner vs Android status-bar routing (ON_RESUME / ON_PAUSE).
        appForegroundTracker.start()
        shellSystemNotifier.ensureChannels()

        preferences.discordSocial
            .onEach { discordRichPresence.setApplicationId(it.applicationId) }
            .launchIn(applicationScope)

        // The companion panel outlives MainActivity, so the window that carries it while a game runs
        // has to be owned by a service rather than by the shell's composition.
        combine(
            gameCompanionController.session,
            gameCompanionController.companionDisplayId,
        ) { session, displayId -> session != null && displayId != null }
            .distinctUntilChanged()
            .onEach { active -> CompanionOverlayService.setActive(this, active) }
            .launchIn(applicationScope)

        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    // Republish after Custom Tab / Discord OAuth / brief backgrounding.
                    discordRichPresence.onAppForeground()
                    libraryAutoScanner.onAppForeground()
                }

                override fun onStop(owner: LifecycleOwner) {
                    discordRichPresence.onAppBackground()
                    // Keep the last published presence. Clearing Browsing here made XOrA vanish
                    // the moment someone switched to Discord to check they were on it.
                }
            },
        )
    }

    /**
     * Bound Coil caches so XMB scrubbing + hub art cannot push handhelds into OOM.
     * Disk is capped separately from [com.arcadia.shell.scraper.MediaCache] scraper files.
     *
     * The animated decoder makes GIF and animated WebP play wherever the shell shows an image —
     * pinned shortcuts, wallpapers, avatars, Discord messages — rather than freezing on frame one.
     */
    override fun newImageLoader(context: Context): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(AnimatedImageDecoder.Factory())
                val cookies = runCatching { xoraNetworkAuthCookies }.getOrNull()
                if (cookies != null) {
                    add(XoraNetworkAvatarInterceptor(cookies))
                }
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, MEMORY_CACHE_PERCENT)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve(COIL_DISK_DIR).toOkioPath())
                    .maxSizeBytes(COIL_DISK_MAX_BYTES)
                    .build()
            }
            .build()

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        when {
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL ||
                level == ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                runCatching { backgroundMusic.releaseForTrim() }
                runCatching { onboardingMusic.releaseForTrim() }
                runCatching { SingletonImageLoader.get(this).memoryCache?.clear() }
            }
            level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW ||
                level == ComponentCallbacks2.TRIM_MEMORY_MODERATE -> {
                runCatching { SingletonImageLoader.get(this).memoryCache?.clear() }
            }
        }
    }

    private companion object {
        const val MEMORY_CACHE_PERCENT = 0.12
        const val COIL_DISK_MAX_BYTES = 48L * 1024L * 1024L
        const val COIL_DISK_DIR = "coil_image_cache"
    }
}
