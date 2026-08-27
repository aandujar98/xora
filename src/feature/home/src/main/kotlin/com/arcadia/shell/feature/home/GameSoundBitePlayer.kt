package com.arcadia.shell.feature.home

import android.media.AudioAttributes
import android.media.MediaPlayer
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** One-shot ROM focus sound bite (customized via Select → ROM options). */
@Singleton
class GameSoundBitePlayer @Inject constructor() {
    private var player: MediaPlayer? = null
    private var lastPath: String? = null

    fun play(path: String?) {
        val file = path?.takeIf { it.isNotBlank() }?.let(::File)
        if (file == null || !file.isFile || file.length() <= 0L) {
            stop()
            return
        }
        if (path == lastPath && player?.isPlaying == true) return
        stop()
        lastPath = path
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

    fun stop() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        lastPath = null
    }
}
