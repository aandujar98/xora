package com.arcadia.shell.launcher.music

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
 * Single source of truth for what the shell considers "now playing".
 *
 * The audio engine lands later; until then selecting a song publishes its metadata here so the
 * pill and the Now Playing page render real content instead of a placeholder.
 */
@Singleton
class NowPlayingController @Inject constructor() {

    private val stateFlow = MutableStateFlow(NowPlayingState())
    val state: StateFlow<NowPlayingState> = stateFlow.asStateFlow()

    fun setTrack(track: MusicTrack, playing: Boolean = true) {
        stateFlow.value = NowPlayingState(
            track = track,
            isPlaying = playing,
            positionMs = 0,
            shuffle = stateFlow.value.shuffle,
            repeat = stateFlow.value.repeat,
        )
    }

    fun togglePlayPause() {
        stateFlow.update { current ->
            if (current.track == null) current else current.copy(isPlaying = !current.isPlaying)
        }
    }

    fun toggleShuffle() = stateFlow.update { it.copy(shuffle = !it.shuffle) }

    fun toggleRepeat() = stateFlow.update { it.copy(repeat = !it.repeat) }

    fun clear() {
        stateFlow.value = NowPlayingState()
    }
}
