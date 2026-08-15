package com.arcadia.shell.libretro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibretroPadMixerTest {

    @Test
    fun mergesButtonsFromEveryDevice() {
        val mixer = LibretroPadMixer()
        mixer.keyDown(deviceId = 1, bit = LibretroPad.A)
        mixer.keyDown(deviceId = 2, bit = LibretroPad.START)
        mixer.motion(
            deviceId = 2,
            lx = 100,
            ly = 0,
            rx = 0,
            ry = 0,
            axisButtons = 1 shl LibretroPad.RIGHT,
        )
        val snap = mixer.snapshot()
        assertEquals(
            (1 shl LibretroPad.A) or (1 shl LibretroPad.START) or (1 shl LibretroPad.RIGHT),
            snap.buttons,
        )
        assertEquals(100.toShort(), snap.lx)
    }

    @Test
    fun keyUpClearsOnlyThatDeviceBit() {
        val mixer = LibretroPadMixer()
        mixer.keyDown(1, LibretroPad.A)
        mixer.keyDown(1, LibretroPad.B)
        mixer.keyUp(1, LibretroPad.A)
        assertEquals(1 shl LibretroPad.B, mixer.snapshot().buttons)
    }

    @Test
    fun missingPreferredDoesNotBlockOtherPads() {
        assertTrue(LibretroPad.matchesPreferredController(null, ""))
        // No InputDevices in unit tests, so a saved name cannot be connected.
        assertTrue(LibretroPad.matchesPreferredController(null, "Xbox Wireless Controller"))
        assertTrue(LibretroPad.acceptsController(null, "Xbox", acceptAny = true))
    }
}
