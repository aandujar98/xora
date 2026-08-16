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

class XoraNetplaySessionVideoTest {

    private fun session(): XoraNetplaySession = XoraNetplaySession(CoroutineScope(Dispatchers.Unconfined))

    @Test
    fun onlineHostStripsPcmAndRateLimitsVideo() {
        val session = session()
        val link = RecordingNetplayLink()
        session.bindForTest(slot = 1, online = true)
        session.attachLinkForTest(link)
        session.armVideoForTest(muteUntilMs = 0L, lastSentMs = 0L)
        val jpeg = ByteArray(40) { 7 }
        session.sendVideo(jpeg, shortArrayOf(1, 2, 3, 4))
        session.sendVideo(jpeg, shortArrayOf(9))
        assertEquals(1, link.sent.size)
        assertEquals(XoraNetplayProtocol.TYPE_VIDEO, link.sent[0].first)
        val decoded = XoraNetplayProtocol.decodeVideo(link.sent[0].second)
        assertEquals(jpeg.toList(), decoded.jpeg.toList())
        assertEquals(0, decoded.pcm.size)
        session.stop()
    }

    @Test
    fun mutedWarmupDoesNotSendVideo() {
        val session = session()
        val link = RecordingNetplayLink()
        session.bindForTest(slot = 1, online = true)
        session.attachLinkForTest(link)
        session.armVideoForTest(muteUntilMs = System.currentTimeMillis() + 10_000L)
        session.sendVideo(ByteArray(8) { 1 }, ShortArray(0))
        assertEquals(0, link.sent.size)
        session.stop()
    }

    @Test
    fun joinerNeverSendsHostVideo() {
        val session = session()
        val link = RecordingNetplayLink()
        session.bindForTest(slot = 2, online = true)
        session.attachLinkForTest(link)
        session.armVideoForTest(muteUntilMs = 0L)
        session.sendVideo(ByteArray(8) { 1 }, ShortArray(0))
        assertEquals(0, link.sent.size)
        session.stop()
    }
}

class XoraNetplaySessionSerialTest {

    private fun session(): XoraNetplaySession = XoraNetplaySession(CoroutineScope(Dispatchers.Unconfined))

    @Test
    fun handheldSerialFillsLocalSlotAndLeavesOthersEmpty() {
        val session = session()
        val link = RecordingNetplayLink()
        session.bindForTest(slot = 1, mode = NetplaySessionMode.HandheldLink)
        session.attachLinkForTest(link)
        val multi = session.exchangeSerial(0x1234)
        assertEquals(0x1234, multi[0])
        assertEquals(0xFFFF, multi[1])
        assertEquals(XoraNetplayProtocol.TYPE_SERIAL, link.sent[0].first)
        val decoded = XoraNetplayProtocol.decodeSerial(link.sent[0].second)
        assertEquals(1, decoded.slot)
        assertEquals(0x1234, decoded.send)
        session.stop()
    }

    @Test
    fun sharedConsoleDoesNotSendSerial() {
        val session = session()
        val link = RecordingNetplayLink()
        session.bindForTest(slot = 1)
        session.attachLinkForTest(link)
        val multi = session.exchangeSerial(0x22)
        assertEquals(0xFFFF, multi[0])
        assertEquals(0, link.sent.size)
        session.stop()
    }
}

private class RecordingNetplayLink : XoraNetplayLink {
    val sent = mutableListOf<Pair<Int, ByteArray>>()

    override fun send(type: Int, payload: ByteArray) {
        sent += type to payload
    }

    override fun receive(timeoutMs: Int): Pair<Int, ByteArray> {
        throw java.net.SocketTimeoutException("test")
    }

    override fun close() = Unit
}
