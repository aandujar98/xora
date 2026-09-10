package com.arcadia.shell.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The sway springs park the shell's frame loop only when the tilt reads exactly zero, so a
 * device at rest has to produce a true zero rather than the rest baseline's leftover residue.
 */
class TiltDeadzoneTest {

    @Test
    fun restingNoiseFlattensToTrueZero() {
        assertEquals(0f, tiltDeadzoned(0f), 0f)
        assertEquals(0f, tiltDeadzoned(0.0001f), 0f)
        assertEquals(0f, tiltDeadzoned(-0.019f), 0f)
    }

    @Test
    fun realTiltPassesThroughUntouched() {
        assertEquals(0.5f, tiltDeadzoned(0.5f), 0.0001f)
        assertEquals(-0.5f, tiltDeadzoned(-0.5f), 0.0001f)
        assertEquals(0.03f, tiltDeadzoned(0.03f), 0.0001f)
    }

    @Test
    fun swayStaysInsideTheUnitRange() {
        assertEquals(1f, tiltDeadzoned(4.2f), 0f)
        assertEquals(-1f, tiltDeadzoned(-4.2f), 0f)
    }
}
