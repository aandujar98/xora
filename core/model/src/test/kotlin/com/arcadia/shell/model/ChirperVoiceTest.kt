package com.arcadia.shell.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChirperVoiceTest {

    @Test
    fun idsAreUniqueAndResourceSafe() {
        val ids = ChirperVoice.entries.map { it.id }
        assertEquals("ids collide", ids.size, ids.toSet().size)
        // Each id becomes chirper_<id>.gif and chirp_<id>_<n>.wav, and an Android resource name
        // may only be lowercase letters, digits and underscores.
        ids.forEach { id ->
            assertTrue("'$id' is not a legal resource name", id.matches(Regex("[a-z][a-z0-9_]*")))
        }
    }

    @Test
    fun everyVoiceHasAtLeastOneTake() {
        ChirperVoice.entries.forEach {
            assertTrue("${it.id} has no takes", it.takes >= 1)
        }
    }

    @Test
    fun accentsAreOpaque() {
        // The picker paints the chosen pill with this colour; a missing alpha channel would make
        // the whole row invisible rather than merely wrong.
        ChirperVoice.entries.forEach {
            assertEquals("${it.id} is not opaque", 0xFFL, (it.accent ushr 24) and 0xFF)
        }
    }

    @Test
    fun anUnknownIdFallsBackRatherThanThrowing() {
        assertEquals(ChirperVoice.Default, ChirperVoice.fromId(null))
        assertEquals(ChirperVoice.Default, ChirperVoice.fromId(""))
        assertEquals(ChirperVoice.Default, ChirperVoice.fromId("a-voice-from-a-newer-build"))
    }

    @Test
    fun aStoredIdRoundTrips() {
        ChirperVoice.entries.forEach {
            assertEquals(it, ChirperVoice.fromId(it.id))
        }
        // Not everything resolves to the default, or the test above would pass on a stub.
        assertNotEquals(ChirperVoice.Default, ChirperVoice.fromId("marlix"))
    }
}
