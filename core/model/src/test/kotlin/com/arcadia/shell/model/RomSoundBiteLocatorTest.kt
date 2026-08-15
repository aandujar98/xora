package com.arcadia.shell.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RomSoundBiteLocatorTest {

    @Test
    fun matchesExactRomStem() {
        assertTrue(
            RomSoundBiteLocator.matches(
                audioFileName = "Super Mario 64 (USA) [!].mp3",
                title = "Super Mario 64",
                romFileName = "Super Mario 64 (USA) [!].z64",
            ),
        )
    }

    @Test
    fun matchesCleanedGameName() {
        assertTrue(
            RomSoundBiteLocator.matches(
                audioFileName = "Super Mario 64.wav",
                title = "Super Mario 64",
                romFileName = "Super Mario 64 (USA) [!].z64",
            ),
        )
    }

    @Test
    fun matchesDisplayedTitleWhenFilenameDiffers() {
        assertTrue(
            RomSoundBiteLocator.matches(
                audioFileName = "The Legend of Zelda.mp3",
                title = "The Legend of Zelda",
                romFileName = "Legend of Zelda, The (USA).nes",
            ),
        )
    }

    @Test
    fun matchesUnderscoreAudioAgainstCleanTitle() {
        assertTrue(
            RomSoundBiteLocator.matches(
                audioFileName = "Sonic_The_Hedgehog.mp3",
                title = "Sonic The Hedgehog",
                romFileName = "Sonic_The_Hedgehog.md",
            ),
        )
    }

    @Test
    fun rejectsOtherGamesAndNonAudio() {
        assertFalse(
            RomSoundBiteLocator.matches(
                audioFileName = "Chrono Trigger.mp3",
                title = "Super Mario 64",
                romFileName = "Super Mario 64 (USA).z64",
            ),
        )
        assertFalse(
            RomSoundBiteLocator.matches(
                audioFileName = "Super Mario 64.txt",
                title = "Super Mario 64",
                romFileName = "Super Mario 64.z64",
            ),
        )
    }

    @Test
    fun audioExtensionsAreMp3AndWav() {
        assertEquals(setOf("mp3", "wav"), RomSoundBiteLocator.AUDIO_EXTENSIONS)
        assertTrue(RomSoundBiteLocator.isAudioFile("Theme.MP3"))
        assertTrue(RomSoundBiteLocator.isAudioFile("Theme.Wav"))
        assertFalse(RomSoundBiteLocator.isAudioFile("Theme.ogg"))
    }
}
