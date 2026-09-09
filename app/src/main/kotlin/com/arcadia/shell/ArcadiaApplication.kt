package com.arcadia.shell

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.gif.AnimatedImageDecoder
import coil3.memory.MemoryCache
import com.arcadia.shell.designsystem.readDeviceVisualBudget
import com.arcadia.shell.audio.BackgroundMusicController
import com.arcadia.shell.audio.OnboardingMusicController
import com.arcadia.shell.companion.CompanionOverlayService
import com.arcadia.shell.datastore.ShellPreferences
import com.arcadia.shell.feature.home.GameCompanionController
import com.arcadia.shell.launcher.EmulatorInstallMonitor
import com.arcadia.shell.launcher.PlayerSeeder
import com.arcadia.shell.launcher.music.MusicPlaybackSession
import com.arcadia.shell.launcher.music.NowPlayingController
import com.arcadia.shell.music.MusicPlaybackService
import com.arcadia.shell.launcher.discord.DiscordRichPresence
import com.arcadia.shell.launcher.notifications.AppForegroundTracker
import com.arcadia.shell.launcher.notifications.ShellNotificationCenter
import com.arcadia.shell.launcher.notifications.ShellSystemNotifier
import com.arcadia.shell.launcher.notifications.isFriendPresenceBanner
import com.arcadia.shell.notifications.FriendBannerOverlayService
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import okio.Path.Companion.toOkioPath
import javax.inject.Inject

@HiltAndroidApp
class ArcadiaApplication : Application(), SingletonImageLoader.Factory {

    @Inject lateinit var playerSeeder: PlayerSeeder
    @Inject lateinit var emulatorInstallMonitor: EmulatorInstallMonitor
    @Inject lateinit var preferences: ShellPreferences
    @Inject lateinit var discordRichPresence: DiscordRichPresence
    @Inject lateinit var backgroundMusic: BackgroundMusicController
    @Inject lateinit var onboardingMusic: OnboardingMusicController
    @Inject lateinit var appForegroundTracker: AppForegroundTracker
    @Inject lateinit var shellSystemNotifier: ShellSystemNotifier
    @Inject lateinit var gameCompanionController: GameCompanionController
    @Inject lateinit var shellNotificationCenter: ShellNotificationCenter
    @Inject lateinit var xoraNetworkAuthCookies: XoraNetworkAuthCookies
    @Inject lateinit var libraryAutoScanner: LibraryAutoScanner
    @Inject lateinit var libraryScanner: LibraryScanner
    @Inject lateinit var libraryHashScheduler: LibraryHashScheduler
    @Inject lateinit var scraperScheduler: ScraperScheduler
    @Inject lateinit var nowPlayingController: NowPlayingController

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        installCrashLogger()
        // Seeding touches the database, so it must not run on the main thread during startup.
        applicationScope.launch { playerSeeder.seedIfNeeded() }
        emulatorInstallMonitor.start()
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

        // Same idea for the friend-online / friend-playing banner over other apps: the service
        // only needs to be alive while there is something eligible to show and XOrA is not it.
        combine(
            shellNotificationCenter.active,
            appForegroundTracker.isForeground,
        ) { notification, foreground -> !foreground && notification?.isFriendPresenceBanner() == true }
            .distinctUntilChanged()
            .onEach { active ->
                Log.i("ArcadiaApplication", "FriendBannerOverlayService.setActive($active)")
                // applicationScope runs on Dispatchers.IO — Toast needs the main looper.
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    android.widget.Toast.makeText(
                        this,
                        "FriendBannerOverlayService.setActive($active)",
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                }
                FriendBannerOverlayService.setActive(this, active)
            }
            .launchIn(applicationScope)

        nowPlayingController.state
            .map { MusicPlaybackSession.shouldHoldService(it) }
            .distinctUntilChanged()
            .onEach { active -> MusicPlaybackService.setSessionActive(this, active) }
            .launchIn(applicationScope)

        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStart(owner: LifecycleOwner) {
                    // Republish after Custom Tab / Discord OAuth / brief backgrounding.
                    discordRichPresence.onAppForeground()
                    libraryAutoScanner.onAppForeground()
                    emulatorInstallMonitor.onAppForeground()
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
                val lite = readDeviceVisualBudget(context).suggestsLiteVisuals
                if (!lite) {
                    add(AnimatedImageDecoder.Factory())
                }
                val cookies = runCatching { xoraNetworkAuthCookies }.getOrNull()
                if (cookies != null) {
                    add(XoraNetworkAvatarInterceptor(cookies))
                }
            }
            .memoryCache {
                val lite = readDeviceVisualBudget(context).suggestsLiteVisuals
                MemoryCache.Builder()
                    .maxSizePercent(
                        context,
                        if (lite) LITE_MEMORY_CACHE_PERCENT else MEMORY_CACHE_PERCENT,
                    )
                    .build()
            }
            .diskCache {
                val lite = readDeviceVisualBudget(context).suggestsLiteVisuals
                DiskCache.Builder()
                    .directory(context.cacheDir.resolve(COIL_DISK_DIR).toOkioPath())
                    .maxSizeBytes(if (lite) LITE_COIL_DISK_MAX_BYTES else COIL_DISK_MAX_BYTES)
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

    /**
     * Writes any uncaught crash to a plain-text file in Downloads (or the app's own external
     * files dir if Downloads is not writable), so a crash can be diagnosed by opening a file
     * manager instead of needing adb. Re-delivers to the previous handler afterward so the
     * crash still surfaces normally — this only ever adds a copy of what would happen anyway.
     */
    private fun installCrashLogger() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val downloads = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS,
                )
                val dir = if (downloads.isDirectory || downloads.mkdirs()) {
                    downloads
                } else {
                    getExternalFilesDir(null) ?: filesDir
                }
                val stamp = System.currentTimeMillis()
                val file = java.io.File(dir, "XOrA_crash_$stamp.txt")
                file.writeText(
                    buildString {
                        appendLine("XOrA crash at $stamp")
                        appendLine("Thread: ${thread.name}")
                        appendLine()
                        appendLine(Log.getStackTraceString(throwable))
                    },
                )
                Log.e("CrashLogger", "Wrote crash log to ${file.absolutePath}")
            }.onFailure { Log.e("CrashLogger", "Failed to write crash log", it) }
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    private companion object {
        const val MEMORY_CACHE_PERCENT = 0.12
        const val LITE_MEMORY_CACHE_PERCENT = 0.07
        const val COIL_DISK_MAX_BYTES = 48L * 1024L * 1024L
        const val LITE_COIL_DISK_MAX_BYTES = 24L * 1024L * 1024L
        const val COIL_DISK_DIR = "coil_image_cache"
    }
}
