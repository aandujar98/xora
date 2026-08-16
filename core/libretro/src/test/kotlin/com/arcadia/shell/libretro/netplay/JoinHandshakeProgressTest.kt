package com.arcadia.shell.libretro.netplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JoinHandshakeProgressTest {

    @Test
    fun handheldJoinCompletesWhenGoArrivesBeforeAssign() {
        val progress = JoinHandshakeProgress()
        val go = XoraNetplayProtocol.encodeGo(epoch = 1, slotsMask = 0b11)
        progress.onGo(go)
        assertFalse(progress.handheldReady())
        progress.onAssign(2)
        assertTrue(progress.handheldReady())
        assertEquals(2, progress.slot)
        assertNotNull(progress.goPayload)
    }

    @Test
    fun handheldJoinCompletesWhenAssignArrivesBeforeGo() {
        val progress = JoinHandshakeProgress()
        progress.onAssign(2)
        assertFalse(progress.handheldReady())
        progress.onGo(XoraNetplayProtocol.encodeGo(epoch = 1, slotsMask = 0b11))
        assertTrue(progress.handheldReady())
    }

    @Test
    fun sharedConsoleJoinCompletesOnGoWithoutSavestate() {
        val progress = JoinHandshakeProgress()
        progress.onAssign(2)
        assertFalse(progress.ready())
        progress.onGo(XoraNetplayProtocol.encodeGo(epoch = 3, slotsMask = 0b11))
        assertTrue(progress.ready())
        assertFalse(progress.sharedStateReady())
    }
}
