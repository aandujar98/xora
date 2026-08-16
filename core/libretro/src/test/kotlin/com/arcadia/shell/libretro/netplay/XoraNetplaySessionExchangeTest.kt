package com.arcadia.shell.libretro.netplay

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XoraNetplaySessionExchangeTest {

    private fun session(): XoraNetplaySession = XoraNetplaySession(CoroutineScope(Dispatchers.Unconfined))

    @Test
    fun unassignedSlotDoesNotPretendToBeHost() {
        val session = session()
        session.bindForTest(slot = 0, epoch = 1)
        val out = session.exchange(
            XoraNetplayProtocol.PadFrame(frame = 0, buttons = 0x0010),
        )
        assertEquals(0, out.pads[0].buttons)
        assertEquals(0, out.pads[1].buttons)
        session.stop()
    }

    @Test
    fun joinerLocalPadAppliesImmediatelyToPort1() {
        val session = session()
        session.bindForTest(slot = 2, epoch = 3)
        val out = session.exchange(
            XoraNetplayProtocol.PadFrame(frame = 0, buttons = 0x00FF, lx = 12),
        )
        assertEquals(0, out.pads[0].buttons)
        assertEquals(0x00FF, out.pads[1].buttons)
        assertEquals(12.toShort(), out.pads[1].lx)
        assertEquals(2, out.pads[1].slot)
        assertEquals(3, out.pads[1].epoch)
        session.stop()
    }

    @Test
    fun hostAppliesLatestRemoteEvenWhenFramesDoNotMatch() {
        val session = session()
        session.bindForTest(slot = 1, epoch = 2)
        session.ingestRemoteForTest(
            XoraNetplayProtocol.PadFrame(
                frame = 4,
                buttons = 0x0002,
                slot = 2,
                epoch = 2,
            ),
        )
        val out = session.exchange(
            XoraNetplayProtocol.PadFrame(frame = 40, buttons = 0x0001),
        )
        assertEquals(0x0001, out.pads[0].buttons)
        assertEquals(0x0002, out.pads[1].buttons)
        assertEquals(2, out.pads[1].slot)
        session.stop()
    }

    @Test
    fun staleEpochRemoteIsIgnored() {
        val session = session()
        session.bindForTest(slot = 1, epoch = 5)
        session.ingestRemoteForTest(
            XoraNetplayProtocol.PadFrame(
                frame = 9,
                buttons = 0x00AA,
                slot = 2,
                epoch = 4,
            ),
        )
        val out = session.exchange(
            XoraNetplayProtocol.PadFrame(frame = 1, buttons = 1),
        )
        assertEquals(0, out.pads[1].buttons)
        session.stop()
    }

    @Test
    fun newerRemoteWinsOverOlderQueuedFrames() {
        val session = session()
        session.bindForTest(slot = 1, epoch = 1)
        session.ingestRemoteForTest(
            XoraNetplayProtocol.PadFrame(frame = 3, buttons = 0x0004, slot = 2, epoch = 1),
        )
        session.ingestRemoteForTest(
            XoraNetplayProtocol.PadFrame(frame = 9, buttons = 0x0008, slot = 2, epoch = 1),
        )
        val out = session.exchange(
            XoraNetplayProtocol.PadFrame(frame = 20, buttons = 1),
        )
        assertEquals(0x0008, out.pads[1].buttons)
        session.stop()
    }

    @Test
    fun firstFramesAreNotForcedIdle() {
        val session = session()
        session.bindForTest(slot = 2, epoch = 1)
        repeat(12) { frame ->
            val out = session.exchange(
                XoraNetplayProtocol.PadFrame(frame = frame, buttons = 0x0020),
            )
            assertTrue("frame $frame should already drive P2", out.pads[1].buttons == 0x0020)
        }
        session.stop()
    }
}
