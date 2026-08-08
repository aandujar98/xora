package com.arcadia.shell.launcher.music

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** What the Now Playing pill and page show. */
data class NowPlayingState(
    val track: MusicTrack? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val shuffle: Boolean = false,
    val repeat: Boolean = false,
) {
    val hasTrack: Boolean get() = track != null

    /** 0..1 through the track; 0 when the duration is unknown. */
    val progress: Float
        get() {
            val duration = track?.durationMs ?: 0L
            if (duration <= 0L) return 0f
            return (positionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
        }
}

/**
 * Now Playing state plus on-device audio.
 *
 * Device tracks stream from [MusicTrack.contentUri] through [MediaPlayer]. Spotify tracks only
 * update metadata here — Home asks Spotify's Web API to play on the linked account's active
 * device.
 */
@Singleton
class NowPlayingController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val stateFlow = MutableStateFlow(NowPlayingState())
    val state: StateFlow<NowPlayingState> = stateFlow.asStateFlow()

    private var player: MediaPlayer? = null
    private var loadedUri: String? = null
    private var positionJob: Job? = null
    private var focusGranted: Boolean = false
    private var focusRequest: AudioFocusRequest? = null

    private val focusChangeListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                focusGranted = true
                val track = stateFlow.value.track
                if (track?.source == MusicSource.Device && stateFlow.value.isPlaying) {
                    runCatching { player?.start() }
                    startPositionTicker()
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS,
            -> {
                if (change == AudioManager.AUDIOFOCUS_LOSS) focusGranted = false
                runCatching { player?.pause() }
                stopPositionTicker()
                if (stateFlow.value.track?.source == MusicSource.Device) {
                    stateFlow.update { it.copy(isPlaying = false) }
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                runCatching { player?.setVolume(0.35f, 0.35f) }
            }
        }
    }

    /**
     * Publishes [track] as now playing. Device sources start (or stop) local audio; Spotify only
     * mirrors metadata — the Web API owns the stream.
     */
    fun setTrack(track: MusicTrack, playing: Boolean = true) {
        when (track.source) {
            MusicSource.Device -> {
                if (playing) {
                    playDevice(track)
                } else {
                    stopLocalPlayback(keepTrack = track)
                }
            }
            MusicSource.Spotify -> {
                stopLocalPlayback(keepTrack = null)
                stateFlow.value = NowPlayingState(
                    track = track,
                    isPlaying = playing,
                    positionMs = 0,
                    shuffle = stateFlow.value.shuffle,
                    repeat = stateFlow.value.repeat,
                )
            }
        }
    }

    /** Marks Spotify playback playing/paused without touching the local MediaPlayer. */
    fun setRemotePlaying(playing: Boolean) {
        stateFlow.update { current ->
            if (current.track?.source != MusicSource.Spotify) current
            else current.copy(isPlaying = playing)
        }
    }

    fun togglePlayPause() {
        val current = stateFlow.value
        val track = current.track ?: return
        when (track.source) {
            MusicSource.Device -> {
                if (current.isPlaying) pauseDevice() else resumeDevice()
            }
            MusicSource.Spotify -> {
                stateFlow.update { it.copy(isPlaying = !it.isPlaying) }
            }
        }
    }

    fun toggleShuffle() = stateFlow.update { it.copy(shuffle = !it.shuffle) }

    fun toggleRepeat() = stateFlow.update { it.copy(repeat = !it.repeat) }

    fun clear() {
        stopLocalPlayback(keepTrack = null)
        stateFlow.value = NowPlayingState()
    }

    private fun playDevice(track: MusicTrack) {
        val uri = track.contentUri
        if (uri.isBlank()) {
            stateFlow.value = NowPlayingState(
                track = track,
                isPlaying = false,
                positionMs = 0,
                shuffle = stateFlow.value.shuffle,
                repeat = stateFlow.value.repeat,
            )
            return
        }
        if (loadedUri == uri && player != null) {
            if (!requestAudioFocus()) {
                stateFlow.update {
                    it.copy(track = track, isPlaying = false)
                }
                return
            }
            runCatching { player?.start() }
            stateFlow.update {
                it.copy(track = track, isPlaying = true)
            }
            startPositionTicker()
            return
        }
        releasePlayer()
        stateFlow.value = NowPlayingState(
            track = track,
            isPlaying = false,
            positionMs = 0,
            shuffle = stateFlow.value.shuffle,
            repeat = stateFlow.value.repeat,
        )
        if (!requestAudioFocus()) return
        val created = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                setDataSource(context, Uri.parse(uri))
                setOnPreparedListener { media ->
                    loadedUri = uri
                    runCatching { media.start() }
                    stateFlow.update { it.copy(isPlaying = true, positionMs = 0) }
                    startPositionTicker()
                }
                setOnCompletionListener {
                    if (stateFlow.value.repeat) {
                        runCatching {
                            seekTo(0)
                            start()
                        }
                        stateFlow.update { it.copy(isPlaying = true, positionMs = 0) }
                        startPositionTicker()
                    } else {
                        stopPositionTicker()
                        stateFlow.update { it.copy(isPlaying = false, positionMs = track.durationMs) }
                    }
                }
                setOnErrorListener { _, _, _ ->
                    releasePlayer()
                    stateFlow.update { it.copy(isPlaying = false) }
                    true
                }
                prepareAsync()
            }
        }.getOrElse {
            abandonAudioFocus()
            null
        }
        player = created
    }

    private fun pauseDevice() {
        runCatching { player?.pause() }
        stopPositionTicker()
        stateFlow.update { it.copy(isPlaying = false, positionMs = currentPositionMs()) }
    }

    private fun resumeDevice() {
        val track = stateFlow.value.track ?: return
        if (track.source != MusicSource.Device) return
        if (player == null || loadedUri != track.contentUri) {
            playDevice(track)
            return
        }
        if (!requestAudioFocus()) return
        runCatching { player?.setVolume(1f, 1f) }
        runCatching { player?.start() }
        stateFlow.update { it.copy(isPlaying = true) }
        startPositionTicker()
    }

    private fun stopLocalPlayback(keepTrack: MusicTrack?) {
        stopPositionTicker()
        releasePlayer()
        abandonAudioFocus()
        if (keepTrack != null) {
            stateFlow.value = NowPlayingState(
                track = keepTrack,
                isPlaying = false,
                positionMs = 0,
                shuffle = stateFlow.value.shuffle,
                repeat = stateFlow.value.repeat,
            )
        }
    }

    private fun currentPositionMs(): Long =
        runCatching { player?.currentPosition?.toLong() }.getOrNull()
            ?: stateFlow.value.positionMs

    private fun startPositionTicker() {
        positionJob?.cancel()
        positionJob = scope.launch {
            while (isActive) {
                val playing = runCatching { player?.isPlaying == true }.getOrDefault(false)
                if (!playing) break
                val pos = runCatching { player?.currentPosition?.toLong() ?: 0L }.getOrNull()
                if (pos != null) {
                    stateFlow.update { it.copy(positionMs = pos, isPlaying = true) }
                }
                delay(POSITION_TICK_MS)
            }
        }
    }

    private fun stopPositionTicker() {
        positionJob?.cancel()
        positionJob = null
    }

    private fun releasePlayer() {
        stopPositionTicker()
        runCatching { player?.reset() }
        runCatching { player?.release() }
        player = null
        loadedUri = null
    }

    private fun requestAudioFocus(): Boolean {
        if (focusGranted) return true
        val result = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build(),
                )
                .setOnAudioFocusChangeListener(focusChangeListener)
                .build()
            focusRequest = request
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
        focusRequest = null
        focusGranted = false
    }

    companion object {
        private const val POSITION_TICK_MS = 250L
    }
}
