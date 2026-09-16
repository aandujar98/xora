package com.arcadia.shell.libretro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun secondControllerIsPlayerTwoNotMergedIntoPlayerOne() {
        val mixer = LibretroPadMixer()
        mixer.keyDown(deviceId = 10, bit = LibretroPad.A)
        mixer.keyDown(deviceId = 20, bit = LibretroPad.START)
        val players = mixer.snapshotPlayers(
            connected = listOf(10 to "Pad A", 20 to "Pad B"),
            descriptorOf = { "id:$it" },
            numberOf = { 0 },
        )
        assertEquals(1 shl LibretroPad.A, players.p1.buttons)
        assertEquals(1 shl LibretroPad.START, players.p2.buttons)
        assertFalse(players.p1.buttons and (1 shl LibretroPad.START) != 0)
    }

    @Test
    fun preferredNameBecomesPlayerOne() {
        val mixer = LibretroPadMixer()
        mixer.keyDown(deviceId = 10, bit = LibretroPad.UP)
        mixer.keyDown(deviceId = 20, bit = LibretroPad.DOWN)
        val players = mixer.snapshotPlayers(
            preferredName = "Pad B",
            connected = listOf(10 to "Pad A", 20 to "Pad B"),
            descriptorOf = { "id:$it" },
            numberOf = { 0 },
        )
        assertEquals(1 shl LibretroPad.DOWN, players.p1.buttons)
        assertEquals(1 shl LibretroPad.UP, players.p2.buttons)
    }

    @Test
    fun androidControllerNumberTwoIsPlayerTwo() {
        val mixer = LibretroPadMixer()
        mixer.keyDown(deviceId = 3, bit = LibretroPad.B)
        mixer.keyDown(deviceId = 4, bit = LibretroPad.X)
        val players = mixer.snapshotPlayers(
            connected = listOf(3 to "First", 4 to "Second"),
            descriptorOf = { "id:$it" },
            numberOf = { id -> if (id == 4) 2 else 1 },
        )
        assertEquals(1 shl LibretroPad.B, players.p1.buttons)
        assertEquals(1 shl LibretroPad.X, players.p2.buttons)
    }

    @Test
    fun splitButtonAndAxisDevicesStayOnePlayer() {
        val mixer = LibretroPadMixer()
        mixer.keyDown(deviceId = 11, bit = LibretroPad.A)
        mixer.motion(deviceId = 12, lx = 200, ly = 0, rx = 0, ry = 0, axisButtons = 0)
        mixer.keyDown(deviceId = 21, bit = LibretroPad.START)
        val players = mixer.snapshotPlayers(
            connected = listOf(11 to "Xbox", 12 to "Xbox", 21 to "DualSense"),
            descriptorOf = { id -> if (id == 11 || id == 12) "xbox" else "ds" },
            numberOf = { 0 },
        )
        assertEquals(1 shl LibretroPad.A, players.p1.buttons)
        assertEquals(200.toShort(), players.p1.lx)
        assertEquals(1 shl LibretroPad.START, players.p2.buttons)
    }

    @Test
    fun keyboardFoldsIntoPlayerOne() {
        val mixer = LibretroPadMixer()
        mixer.keyDown(deviceId = 1, bit = LibretroPad.A)
        mixer.keyDown(deviceId = 99, bit = LibretroPad.LEFT)
        val players = mixer.snapshotPlayers(
            connected = listOf(1 to "Pad A"),
            descriptorOf = { "id:$it" },
            numberOf = { 0 },
        )
        assertEquals((1 shl LibretroPad.A) or (1 shl LibretroPad.LEFT), players.p1.buttons)
        assertEquals(0, players.p2.buttons)
        assertEquals(0, players.p3.buttons)
        assertEquals(0, players.p4.buttons)
    }

    @Test
    fun thirdAndFourthControllersAreTheirOwnPlayers() {
        val mixer = LibretroPadMixer()
        mixer.keyDown(deviceId = 1, bit = LibretroPad.A)
        mixer.keyDown(deviceId = 2, bit = LibretroPad.B)
        mixer.keyDown(deviceId = 3, bit = LibretroPad.X)
        mixer.keyDown(deviceId = 4, bit = LibretroPad.Y)
        val players = mixer.snapshotPlayers(
            connected = listOf(
                1 to "Pad 1",
                2 to "Pad 2",
                3 to "Pad 3",
                4 to "Pad 4",
            ),
            descriptorOf = { "id:$it" },
            numberOf = { id -> id },
        )
        assertEquals(1 shl LibretroPad.A, players.p1.buttons)
        assertEquals(1 shl LibretroPad.B, players.p2.buttons)
        assertEquals(1 shl LibretroPad.X, players.p3.buttons)
        assertEquals(1 shl LibretroPad.Y, players.p4.buttons)
    }

    @Test
    fun gpioKeysPadIsPlayerTwoNotFoldedIntoPlayerOne() {
        val mixer = LibretroPadMixer()
        mixer.keyDown(deviceId = 1, bit = LibretroPad.A)
        mixer.keyDown(deviceId = 99, bit = LibretroPad.LEFT)
        val players = mixer.snapshotPlayers(
            connected = listOf(1 to "Pad A"),
            descriptorOf = { "id:$it" },
            numberOf = { 0 },
            nameOf = { id -> if (id == 99) "gpio-keys" else "Pad A" },
        )
        assertEquals(1 shl LibretroPad.A, players.p1.buttons)
        assertEquals(1 shl LibretroPad.LEFT, players.p2.buttons)
    }

    @Test
    fun backIsNotAFaceButton() {
        assertEquals(null, LibretroPad.defaultKeyCodeToButton(android.view.KeyEvent.KEYCODE_BACK))
    }

    @Test
    fun rgRotateNameIsAHandheldPad() {
        assertTrue(LibretroPad.looksLikeHandheldPad("gpio-keys"))
        assertTrue(LibretroPad.looksLikeHandheldPad("rg-rotate-joypad"))
        assertTrue(LibretroPad.looksLikeHandheldPad("Anbernic RG Rotate"))
        assertTrue(LibretroPad.looksLikeHandheldPad("adc-joystick"))
        assertFalse(LibretroPad.looksLikeHandheldPad("qwerty"))
        assertFalse(LibretroPad.looksLikeHandheldPad(""))
    }
}
