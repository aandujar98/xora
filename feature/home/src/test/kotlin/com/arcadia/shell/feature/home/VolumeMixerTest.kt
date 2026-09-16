package com.arcadia.shell.feature.home

import org.junit.Assert.assertEquals
import org.junit.Test

class VolumeMixerTest {

    @Test
    fun nudgeStaysInsideZeroToOne() {
        assertEquals(0f, nudgeMixerVolume(0.02f, -0.05f), 0.0001f)
        assertEquals(1f, nudgeMixerVolume(0.98f, 0.05f), 0.0001f)
        assertEquals(0.55f, nudgeMixerVolume(0.50f, 0.05f), 0.0001f)
    }
}
