package com.arcadia.shell.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.arcadia.shell.R
import com.arcadia.shell.datastore.DEFAULT_BGM_VOLUME
import com.arcadia.shell.datastore.ShellPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Looping soundtrack for the first-run / Settings-restarted onboarding flow.
 *
 * Plays [R.raw.onboarding] while active and the activity is foregrounded. Volume follows the same
 * BGM preference as [BackgroundMusicController]. Does not request audio focus — the shell BGM
 * controller is paused for the duration via [BackgroundMusicController.setOnboardingActive].
 */
@Singleton
class OnboardingMusicController @Inject constructor(
    @ApplicationContext private val context: Context,
    preferences: ShellPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var player: MediaPlayer? = null
    private var volume: Float = DEFAULT_BGM_VOLUME
    private var foreground: Boolean = false
    private var active: Boolean = false

    init {
        scope.launch {
            preferences.settings
                .map { it.bgmVolume }
                .distinctUntilChanged()
                .collect { next ->
                    volume = next.coerceIn(0f, 1f)
                    applyVolume()
                    syncPlayback()
                }
        }
    }

    fun setActive(active: Boolean) {
        if (this.active == active) return
        this.active = active
        if (!active) {
            runCatching { player?.pause() }
            resetPlayer()
        }
        syncPlayback()
    }

    fun onForeground() {
        foreground = true
        syncPlayback()
    }

    fun onBackground() {
        foreground = false
        runCatching { player?.pause() }
    }

    fun releaseForTrim() {
        resetPlayer()
    }

    private fun syncPlayback() {
        if (!active || !foreground || volume <= 0f) {
            runCatching { player?.pause() }
            return
        }
        val media = ensurePlayer() ?: return
        applyVolume()
        val playing = runCatching { media.isPlaying }.getOrDefault(false)
        if (!playing) {
            runCatching { media.start() }
                .onFailure { resetPlayer() }
        }
    }

    private fun ensurePlayer(): MediaPlayer? {
        player?.let { return it }
        val created = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                context.resources.openRawResourceFd(R.raw.onboarding).use { afd ->
                    setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                }
                isLooping = true
                setOnErrorListener { _, _, _ ->
                    resetPlayer()
                    true
                }
                prepare()
                val v = volume.coerceIn(0f, 1f)
                setVolume(v, v)
            }
        }.getOrNull()
        player = created
        return created
    }

    private fun resetPlayer() {
        runCatching { player?.reset() }
        runCatching { player?.release() }
        player = null
    }

    private fun applyVolume() {
        val v = volume.coerceIn(0f, 1f)
        runCatching { player?.setVolume(v, v) }
    }
}
