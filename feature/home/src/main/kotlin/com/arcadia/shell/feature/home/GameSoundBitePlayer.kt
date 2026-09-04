package com.arcadia.shell.feature.home

import android.media.AudioAttributes
import android.media.MediaPlayer
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One-shot ROM focus sound bite (customized via Select → ROM options). */
@Singleton
class GameSoundBitePlayer @Inject constructor() {
    private var player: MediaPlayer? = null
    private var lastPath: String? = null
    private val _holdsBackgroundMusic = MutableStateFlow(false)

    /**
     * True while a bite is playing or about to play (focus settle). The shell fades BGM out
     * for the hold, then fades it back in when this returns to false.
     */
    val holdsBackgroundMusic: StateFlow<Boolean> = _holdsBackgroundMusic.asStateFlow()

    fun play(path: String?) {
        val file = path?.takeIf { it.isNotBlank() }?.let(::File)
        if (file == null || !file.isFile || file.length() <= 0L) {
            stop()
            return
        }
        if (path == lastPath && player?.isPlaying == true) return
        stop(releaseBackgroundMusic = false)
        lastPath = path
        _holdsBackgroundMusic.value = true
        runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build(),
                )
                setDataSource(file.absolutePath)
                setOnCompletionListener { stop() }
                setOnErrorListener { _, _, _ ->
                    stop()
                    true
                }
                prepare()
                start()
                player = this
            }
        }.onFailure { stop() }
    }

    /**
     * @param releaseBackgroundMusic when false, the current clip is cut but BGM stays faded
     * so a follow-up bite (focus settle) does not pump the soundtrack back in.
     */
    fun stop(releaseBackgroundMusic: Boolean = true) {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        lastPath = null
        if (releaseBackgroundMusic) {
            _holdsBackgroundMusic.value = false
        }
    }
}
