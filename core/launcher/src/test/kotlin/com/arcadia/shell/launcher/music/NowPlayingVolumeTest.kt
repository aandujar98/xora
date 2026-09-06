package com.arcadia.shell.launcher.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NowPlayingVolumeTest {

    @Test
    fun coerceClampsToUnitInterval() {
        assertEquals(0f, NowPlayingVolume.coerce(-0.4f), 0.0001f)
        assertEquals(1f, NowPlayingVolume.coerce(1.7f), 0.0001f)
        assertEquals(0.4f, NowPlayingVolume.coerce(0.4f), 0.0001f)
    }

    @Test
    fun nudgeStepsAndStopsAtTheEdges() {
        assertEquals(0.5f, NowPlayingVolume.nudge(0.4f, NowPlayingVolume.STEP), 0.0001f)
        assertEquals(0.3f, NowPlayingVolume.nudge(0.4f, -NowPlayingVolume.STEP), 0.0001f)
        assertEquals(0f, NowPlayingVolume.nudge(0.05f, -NowPlayingVolume.STEP), 0.0001f)
        assertEquals(1f, NowPlayingVolume.nudge(0.95f, NowPlayingVolume.STEP), 0.0001f)
    }

    @Test
    fun duckingScalesThePlayerGain() {
        assertEquals(1f, NowPlayingVolume.outputGain(1f, ducked = false), 0.0001f)
        assertEquals(0.35f, NowPlayingVolume.outputGain(1f, ducked = true), 0.0001f)
        assertEquals(0.175f, NowPlayingVolume.outputGain(0.5f, ducked = true), 0.0001f)
    }

    @Test
    fun percentLabelIsWholePercents() {
        assertEquals("0%", NowPlayingVolume.percentLabel(0f))
        assertEquals("50%", NowPlayingVolume.percentLabel(0.5f))
        assertEquals("100%", NowPlayingVolume.percentLabel(1f))
        assertEquals("100%", NowPlayingVolume.percentLabel(4f))
    }
}

class MusicPlaybackSessionTest {

    private val device = MusicTrack(
        id = "1",
        title = "Battle Hymn",
        artist = "Lotus Juice",
        albumTitle = "P3R",
        albumArtUri = null,
        durationMs = 180_000,
        contentUri = "content://media/1",
        source = MusicSource.Device,
    )

    private val spotify = device.copy(
        id = "sp",
        contentUri = "spotify:track:1",
        source = MusicSource.Spotify,
    )

    @Test
    fun deviceTrackHoldsTheForegroundService() {
        assertTrue(
            MusicPlaybackSession.shouldHoldService(
                NowPlayingState(track = device, isPlaying = true),
            ),
        )
        assertTrue(
            MusicPlaybackSession.shouldHoldService(
                NowPlayingState(track = device, isPlaying = false),
            ),
        )
    }

    @Test
    fun spotifyAndEmptyDoNotHoldTheService() {
        assertFalse(
            MusicPlaybackSession.shouldHoldService(
                NowPlayingState(track = spotify, isPlaying = true),
            ),
        )
        assertFalse(MusicPlaybackSession.shouldHoldService(NowPlayingState()))
    }

    @Test
    fun emulatorHudShowsWheneverATrackIsLoaded() {
        assertTrue(
            EmulatorNowPlayingHudVisibility.isVisible(
                NowPlayingState(track = device, isPlaying = false),
            ),
        )
        assertTrue(
            EmulatorNowPlayingHudVisibility.isVisible(
                NowPlayingState(track = spotify, isPlaying = true),
            ),
        )
        assertFalse(EmulatorNowPlayingHudVisibility.isVisible(NowPlayingState()))
    }

    @Test
    fun volumeSurvivesTrackMetadataCopy() {
        val state = NowPlayingState(track = device, isPlaying = true, volume = 0.4f)
        assertEquals(0.4f, state.copy(isPlaying = false, positionMs = 12_000).volume, 0.0001f)
    }
}
