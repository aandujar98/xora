package com.arcadia.shell.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import com.arcadia.shell.R
import com.arcadia.shell.datastore.DEFAULT_BGM_VOLUME
import com.arcadia.shell.datastore.HomeThemeMediaStore
import com.arcadia.shell.datastore.ShellPreferences
import com.arcadia.shell.designsystem.ShellThemeCatalog
import com.arcadia.shell.designsystem.THEME_CROSSFADE_MS
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Looping shell soundtrack. Plays while the activity is in the foreground and pauses when the app
 * goes to the background (including when an external emulator takes over).
 *
 * Source priority:
 * 1. User-picked file from [ShellPreferences] / [HomeThemeMediaStore]
 * 2. Active launcher theme asset BGM (e.g. Persona 3 Reload) when the file exists
 * 3. Bundled [R.raw.background]
 *
 * Theme / track changes soft-mix (crossfade) instead of hard-cutting.
 *
 * Theme / default loads use [android.content.res.AssetManager.openFd] / raw FDs so playback does
 * not depend on `android.resource://` URI package matching — important when [applicationId]
 * (`com.sora.shell`) differs from the R-class namespace (`com.arcadia.shell`).
 */
@Singleton
class BackgroundMusicController @Inject constructor(
    @ApplicationContext private val context: Context,
    preferences: ShellPreferences,
    private val themeMediaStore: HomeThemeMediaStore,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var player: MediaPlayer? = null
    private var volume: Float = DEFAULT_BGM_VOLUME
    private var duckFactor: Float = 1f
    private var foreground: Boolean = false
    private var focusGranted: Boolean = false
    private var focusRequest: AudioFocusRequest? = null
    private var customBgmPath: String? = null
    private var shellThemeId: String = ShellThemeCatalog.Default.id.id
    /** Path / key currently loaded into [player]; empty means not loaded. */
    private var loadedKey: String = KEY_UNLOADED
    /** When true, shell BGM stays paused so onboarding music can own the soundtrack. */
    private var onboardingActive: Boolean = false
    private var crossfadeJob: Job? = null
    /** Outgoing player during a soft mix; released when the fade completes or is cancelled. */
    private var fadingOutPlayer: MediaPlayer? = null

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                focusGranted = true
                applyVolume()
                syncPlayback()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                val ducked = (volume * duckFactor * TRAILER_DUCK_FACTOR).coerceIn(0f, 1f)
                runCatching { player?.setVolume(ducked, ducked) }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                runCatching { player?.pause() }
            }
            AudioManager.AUDIOFOCUS_LOSS -> {
                focusGranted = false
                runCatching { player?.pause() }
            }
        }
    }

    init {
        scope.launch {
            preferences.settings
                .map { Triple(it.bgmVolume, it.customBgmPath, it.shellThemeId) }
                .distinctUntilChanged()
                .collect { (nextVolume, bgmPath, themeId) ->
                    volume = nextVolume.coerceIn(0f, 1f)
                    val pathChanged = bgmPath != customBgmPath
                    val themeChanged = themeId != shellThemeId
                    customBgmPath = bgmPath
                    shellThemeId = themeId
                    if (pathChanged || themeChanged) {
                        crossfadeToDesiredSource()
                    } else {
                        applyVolume()
                        syncPlayback()
                    }
                }
        }
    }

    /** Soften (or restore) BGM while an idle trailer is playing. */
    fun setTrailerDucked(ducked: Boolean) {
        duckFactor = if (ducked) TRAILER_DUCK_FACTOR else 1f
        applyVolume()
    }

    /**
     * Pause the home/shell soundtrack while first-run (or Settings-restarted) onboarding is
     * showing. Cleared when onboarding ends so default/custom BGM can resume.
     */
    fun setOnboardingActive(active: Boolean) {
        if (onboardingActive == active) return
        onboardingActive = active
        syncPlayback()
    }

    fun onForeground() {
        foreground = true
        syncPlayback()
    }

    fun onBackground() {
        foreground = false
        runCatching { player?.pause() }
        abandonAudioFocus()
    }

    /** Release the MediaPlayer when the process is under memory pressure. */
    fun releaseForTrim() {
        cancelCrossfade(releaseOutgoing = true)
        resetPlayer()
        abandonAudioFocus()
    }

    /** Force-reload after Themes replaces or clears the custom track. */
    fun reloadSource() {
        crossfadeToDesiredSource()
    }

    private fun syncPlayback() {
        if (!foreground || volume <= 0f || onboardingActive) {
            runCatching { player?.pause() }
            if (!foreground) abandonAudioFocus()
            return
        }
        requestAudioFocus()
        val media = ensurePlayer(hardCut = false) ?: return
        applyVolume()
        val playing = runCatching { media.isPlaying }.getOrDefault(false)
        if (!playing) {
            runCatching { media.start() }
                .onFailure { resetPlayer() }
        }
    }

    private fun crossfadeToDesiredSource() {
        val nextKey = desiredKey()
        if (player != null && loadedKey == nextKey) {
            applyVolume()
            syncPlayback()
            return
        }
        if (!foreground || volume <= 0f || onboardingActive || player == null || loadedKey == KEY_UNLOADED) {
            cancelCrossfade(releaseOutgoing = true)
            resetPlayer()
            syncPlayback()
            return
        }

        cancelCrossfade(releaseOutgoing = true)
        val previousKey = loadedKey
        val outgoing = player
        player = null
        loadedKey = KEY_UNLOADED
        fadingOutPlayer = outgoing

        val incoming = createPlayerForDesired()
        if (incoming == null) {
            // Keep outgoing if the new source failed to load.
            player = outgoing
            fadingOutPlayer = null
            loadedKey = previousKey
            applyVolume()
            syncPlayback()
            return
        }

        player = incoming
        loadedKey = nextKey
        runCatching { incoming.setVolume(0f, 0f) }
        requestAudioFocus()
        runCatching { incoming.start() }
            .onFailure {
                releasePlayer(incoming)
                player = outgoing
                fadingOutPlayer = null
                loadedKey = previousKey
                applyVolume()
                syncPlayback()
                return
            }

        val targetVol = (volume * duckFactor).coerceIn(0f, 1f)
        crossfadeJob = scope.launch {
            val steps = CROSSFADE_STEPS
            val stepDelay = THEME_CROSSFADE_MS.toLong() / steps
            for (i in 1..steps) {
                val t = i.toFloat() / steps
                val outVol = targetVol * (1f - t)
                val inVol = targetVol * t
                runCatching { outgoing?.setVolume(outVol, outVol) }
                runCatching { incoming.setVolume(inVol, inVol) }
                delay(stepDelay)
            }
            releasePlayer(outgoing)
            if (fadingOutPlayer === outgoing) fadingOutPlayer = null
            applyVolume()
            syncPlayback()
        }
    }

    private fun desiredSource(): BgmSource {
        val custom = themeMediaStore.resolveBgm(customBgmPath)
        if (custom != null) return BgmSource.File(custom.absolutePath)
        val themeAsset = ShellThemeCatalog.resolve(shellThemeId).bgm?.assetPath
        if (!themeAsset.isNullOrBlank() && assetExists(themeAsset)) {
            return BgmSource.Asset(themeAsset)
        }
        return BgmSource.DefaultRaw
    }

    private fun desiredKey(): String = when (val source = desiredSource()) {
        is BgmSource.File -> source.path
        is BgmSource.Asset -> "asset:${source.path}"
        BgmSource.DefaultRaw -> KEY_DEFAULT
    }

    private fun ensurePlayer(hardCut: Boolean): MediaPlayer? {
        val key = desiredKey()
        if (player != null && loadedKey == key) return player
        if (hardCut) {
            cancelCrossfade(releaseOutgoing = true)
            if (player != null) resetPlayer()
        } else if (player != null && loadedKey != key) {
            crossfadeToDesiredSource()
            return player
        }

        val created = createPlayerForDesired()
        player = created
        loadedKey = if (created != null) key else KEY_UNLOADED
        return created
    }

    private fun createPlayerForDesired(): MediaPlayer? {
        val source = desiredSource()
        return runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                when (source) {
                    is BgmSource.File -> setDataSource(source.path)
                    is BgmSource.Asset -> {
                        val afd = context.assets.openFd(source.path)
                        try {
                            setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                        } finally {
                            runCatching { afd.close() }
                        }
                    }
                    BgmSource.DefaultRaw -> {
                        context.resources.openRawResourceFd(R.raw.background).use { afd ->
                            setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                        }
                    }
                }
                isLooping = true
                setOnErrorListener { _, _, _ ->
                    resetPlayer()
                    true
                }
                prepare()
                val v = (volume * duckFactor).coerceIn(0f, 1f)
                setVolume(v, v)
            }
        }.getOrNull()
    }

    private fun assetExists(path: String): Boolean =
        runCatching {
            context.assets.openFd(path).use { true }
        }.getOrDefault(false)

    private fun cancelCrossfade(releaseOutgoing: Boolean) {
        crossfadeJob?.cancel()
        crossfadeJob = null
        if (releaseOutgoing) {
            releasePlayer(fadingOutPlayer)
            fadingOutPlayer = null
        }
    }

    private fun resetPlayer() {
        cancelCrossfade(releaseOutgoing = true)
        releasePlayer(player)
        player = null
        loadedKey = KEY_UNLOADED
    }

    private fun releasePlayer(media: MediaPlayer?) {
        if (media == null) return
        runCatching { media.stop() }
        runCatching { media.reset() }
        runCatching { media.release() }
    }

    private fun applyVolume() {
        val v = (volume * duckFactor).coerceIn(0f, 1f)
        runCatching { player?.setVolume(v, v) }
    }

    private fun requestAudioFocus(): Boolean {
        if (focusGranted) return true
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setOnAudioFocusChangeListener(focusChangeListener)
                .setWillPauseWhenDucked(false)
                .build()
                .also { focusRequest = it }
            audioManager.requestAudioFocus(request)
        } else {
            @Suppress("DEPRECATION")
            audioManager.requestAudioFocus(
                focusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN,
            )
        }
        focusGranted = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        return focusGranted
    }

    private fun abandonAudioFocus() {
        if (!focusGranted && focusRequest == null) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            focusRequest?.let { runCatching { audioManager.abandonAudioFocusRequest(it) } }
        } else {
            @Suppress("DEPRECATION")
            runCatching { audioManager.abandonAudioFocus(focusChangeListener) }
        }
        focusGranted = false
    }

    private sealed interface BgmSource {
        data class File(val path: String) : BgmSource
        data class Asset(val path: String) : BgmSource
        data object DefaultRaw : BgmSource
    }

    private companion object {
        const val TRAILER_DUCK_FACTOR = 0.12f
        const val KEY_DEFAULT = "__default__"
        const val KEY_UNLOADED = "__unloaded__"
        const val CROSSFADE_STEPS = 24
    }
}
