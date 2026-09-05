package com.arcadia.shell.feature.home

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameSoundBitePlayerTest {

    @Test
    fun suppressStopsAndBlocksPlay() {
        val player = GameSoundBitePlayer()
        player.setPlaybackSuppressed(true)
        assertTrue(player.playbackSuppressed.value)
        assertFalse(player.holdsBackgroundMusic.value)
        player.play("/tmp/does-not-exist.mp3")
        assertFalse(player.holdsBackgroundMusic.value)
        player.setPlaybackSuppressed(false)
        assertFalse(player.playbackSuppressed.value)
        assertTrue(!player.holdsBackgroundMusic.value)
    }
}
