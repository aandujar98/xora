package com.arcadia.shell.libretro.netplay

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class NetplaySessionModeTest {

    @Test
    fun handheldsAreSeparateInstances() {
        listOf("gb", "gbc", "gba", "psp", "nds", "3ds", "gamegear").forEach { id ->
            assertEquals(id, NetplaySessionMode.HandheldLink, netplaySessionMode(id))
        }
    }

    @Test
    fun homeConsolesShareOneInstance() {
        listOf("nes", "snes", "n64", "genesis", "ps1", "gamecube").forEach { id ->
            assertEquals(id, NetplaySessionMode.SharedConsole, netplaySessionMode(id))
        }
    }

    @Test
    fun gbaUsesGpspNetpacketNotLockstep() {
        assertEquals(false, usesGbaLockstep("gba"))
        assertEquals(true, usesGbaGpspLink("gba"))
        assertEquals(true, usesGbaGpspLink("GBA"))
        assertEquals(false, usesGbaGpspLink("gbc"))
        assertEquals(false, usesGbaGpspLink("snes"))
        assertEquals("gpsp", netplayCoreName("gba", "mgba"))
        assertEquals("snes9x", netplayCoreName("snes", "snes9x"))
        assertEquals(0, gbaNetplayClientId(1))
        assertEquals(1, gbaNetplayClientId(2))
    }

    @Test
    fun gbaNetpacketStartsOnceAfterHandshake() {
        assertEquals(
            true,
            shouldStartGbaNetpacket("gba", handheldLink = true, localSlot = 1, playerCount = 2, alreadyStarted = false),
        )
        assertEquals(
            true,
            shouldStartGbaNetpacket("gba", handheldLink = true, localSlot = 2, playerCount = 2, alreadyStarted = false),
        )
        assertEquals(
            false,
            shouldStartGbaNetpacket("gba", handheldLink = true, localSlot = 1, playerCount = 1, alreadyStarted = false),
        )
        assertEquals(
            false,
            shouldStartGbaNetpacket("gba", handheldLink = true, localSlot = 1, playerCount = 2, alreadyStarted = true),
        )
        assertEquals(
            false,
            shouldStartGbaNetpacket("snes", handheldLink = false, localSlot = 1, playerCount = 2, alreadyStarted = false),
        )
    }

    @Test
    fun gbaLockstepIsDisabled() {
        assertEquals(
            false,
            shouldStartGbaLockstep("gba", handheldLink = true, localSlot = 1, alreadyActive = false, alreadyAttempted = false),
        )
    }

    @Test
    fun sharedConsoleJoinersDoNotLoadHostSavestate() {
        assertEquals(false, NetplaySessionMode.SharedConsole.joinerAppliesHostState())
        assertEquals(false, NetplaySessionMode.HandheldLink.joinerAppliesHostState())
        assertEquals(true, NetplaySessionMode.SharedConsole.usesSavestateBarrier())
        assertEquals(false, NetplaySessionMode.HandheldLink.usesSavestateBarrier())
    }
}

class XoraNetplayVideoPacketTest {

    @Test
    fun videoRoundTripKeepsJpegAndPcm() {
        val jpeg = byteArrayOf(1, 2, 3, 4, 5)
        val pcm = shortArrayOf(-1, 0, 32767)
        val decoded = XoraNetplayProtocol.decodeVideo(
            XoraNetplayProtocol.encodeVideo(9, jpeg, pcm),
        )
        assertEquals(9, decoded.seq)
        assertArrayEquals(jpeg, decoded.jpeg)
        assertEquals(pcm.size, decoded.pcm.size)
        assertEquals(pcm[0], decoded.pcm[0])
        assertEquals(pcm[2], decoded.pcm[2])
    }
}
