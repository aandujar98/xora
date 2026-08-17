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
    fun gbaUsesInProcessLockstepNotGpsp() {
        assertEquals(true, usesGbaLockstep("gba"))
        assertEquals(true, usesGbaLockstep("GBA"))
        assertEquals(false, usesGbaLockstep("gbc"))
        assertEquals(false, usesGbaGpspLink("gba"))
        assertEquals("mgba", netplayCoreName("gba", "mgba"))
        assertEquals("snes9x", netplayCoreName("snes", "snes9x"))
        assertEquals(0, gbaNetplayClientId(1))
        assertEquals(1, gbaNetplayClientId(2))
    }

    @Test
    fun gbaNetpacketStaysOff() {
        assertEquals(
            false,
            shouldStartGbaNetpacket("gba", handheldLink = true, localSlot = 1, playerCount = 2, alreadyStarted = false),
        )
        assertEquals(
            false,
            shouldArmGbaLinkCable("gba", handheldLink = true, localSlot = 1, hosting = true),
        )
    }

    @Test
    fun gbaLockstepStartsOnceThisGbaHasASeat() {
        assertEquals(
            true,
            shouldStartGbaLockstep("gba", handheldLink = true, localSlot = 1, alreadyActive = false, alreadyAttempted = false),
        )
        assertEquals(
            true,
            shouldStartGbaLockstep("gba", handheldLink = true, localSlot = 2, alreadyActive = false, alreadyAttempted = false),
        )
        assertEquals(
            false,
            shouldStartGbaLockstep("gba", handheldLink = true, localSlot = 0, alreadyActive = false, alreadyAttempted = false),
        )
        assertEquals(
            false,
            shouldStartGbaLockstep("gba", handheldLink = true, localSlot = 1, alreadyActive = true, alreadyAttempted = false),
        )
        assertEquals(
            false,
            shouldStartGbaLockstep("gba", handheldLink = true, localSlot = 1, alreadyActive = false, alreadyAttempted = true),
        )
        assertEquals(
            false,
            shouldStartGbaLockstep("snes", handheldLink = false, localSlot = 1, alreadyActive = false, alreadyAttempted = false),
        )
    }

    @Test
    fun gbaLockstepRestartsWhenTheSecondPlayerLinks() {
        assertEquals("solo:1", gbaLockstepGenerationKey(localSlot = 1, linked = false, playerCount = 1))
        assertEquals("solo:1", gbaLockstepGenerationKey(localSlot = 1, linked = true, playerCount = 1))
        assertEquals("linked:1:2", gbaLockstepGenerationKey(localSlot = 1, linked = true, playerCount = 2))
        assertEquals("linked:2:2", gbaLockstepGenerationKey(localSlot = 2, linked = true, playerCount = 2))
        assertEquals(1, gbaLockstepLocalSlot(playerSlot = 0, hosting = true, joining = false))
        assertEquals(2, gbaLockstepLocalSlot(playerSlot = 0, hosting = false, joining = true))
        assertEquals(2, gbaLockstepLocalSlot(playerSlot = 2, hosting = false, joining = true))
        assertEquals(1, gbaLockstepHiddenPort(1))
        assertEquals(0, gbaLockstepHiddenPort(2))
        assertEquals(true, shouldMirrorGbaLockstepPartnerPad(linked = false, playerCount = 1))
        assertEquals(true, shouldMirrorGbaLockstepPartnerPad(linked = true, playerCount = 1))
        assertEquals(false, shouldMirrorGbaLockstepPartnerPad(linked = true, playerCount = 2))
        assertEquals(3, GBA_LOCKSTEP_INPUT_DELAY_FRAMES)
        assertEquals(2, gbaLockstepPlayerCount(1))
        assertEquals(2, gbaLockstepPlayerCount(2))
    }

    @Test
    fun gbaLockstepRomPathPicksACartInsideAFolder() {
        val dir = java.io.File.createTempFile("rom-directory", "").apply {
            delete()
            mkdir()
            deleteOnExit()
        }
        java.io.File(dir, "readme.txt").writeText("no")
        val cart = java.io.File(dir, "Kirby.gba").apply { writeBytes(ByteArray(0x200) { 0 }) }
        assertEquals(cart.absolutePath, resolveGbaLockstepRomPath(dir.absolutePath))
        assertEquals(cart.absolutePath, resolveGbaLockstepRomPath(cart.absolutePath))
        dir.deleteRecursively()
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
