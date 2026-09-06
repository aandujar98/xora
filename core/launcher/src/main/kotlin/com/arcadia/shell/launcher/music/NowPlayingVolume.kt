package com.arcadia.shell.launcher.music

/**
 * Local Now Playing mix, independent of emulator [AudioTrack] volume.
 *
 * Applied to the device [android.media.MediaPlayer]. Spotify stays on its own app volume.
 */
object NowPlayingVolume {
    const val DEFAULT = 1f
    const val STEP = 0.1f
    const val DUCK = 0.35f

    fun coerce(value: Float): Float = value.coerceIn(0f, 1f)

    fun nudge(current: Float, delta: Float): Float = coerce(current + delta)

    /** Gain sent to MediaPlayer, including transient audio-focus ducking. */
    fun outputGain(volume: Float, ducked: Boolean): Float =
        coerce(volume) * if (ducked) DUCK else 1f

    fun percentLabel(volume: Float): String =
        "${(coerce(volume) * 100f).toInt()}%"
}

/** When the device Now Playing session should keep a foreground media service. */
object MusicPlaybackSession {
    fun shouldHoldService(state: NowPlayingState): Boolean =
        state.track?.source == MusicSource.Device && state.hasTrack
}

/** Compact emulator HUD — shown whenever a track is loaded, including while paused. */
object EmulatorNowPlayingHudVisibility {
    fun isVisible(state: NowPlayingState): Boolean = state.hasTrack
}
