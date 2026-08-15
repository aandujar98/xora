package com.arcadia.shell.libretro.netplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream

class XoraNetplayProtocolTest {

    @Test
    fun helloRoundTrip() {
        val hello = XoraNetplayProtocol.Hello(
            nickname = "iprinceangel",
            coreName = "mupen64plus_next_gles3",
            platformId = "n64",
            romName = "Mario Kart 64",
            token = -123456789,
        )
        val decoded = XoraNetplayProtocol.decodeHello(XoraNetplayProtocol.encodeHello(hello))
        assertEquals(hello.nickname, decoded.nickname)
        assertEquals(hello.coreName, decoded.coreName)
        assertEquals(hello.platformId, decoded.platformId)
        assertEquals(hello.romName, decoded.romName)
        assertEquals(hello.token, decoded.token)
        assertEquals(XoraNetplayProtocol.VERSION, decoded.version)
    }

    @Test
    fun helloWithoutTokenDecodesAsZero() {
        val legacyBody = listOf("2", "Player", "core", "nes", "Game")
            .joinToString("\u0000")
            .toByteArray(Charsets.UTF_8)
        val decoded = XoraNetplayProtocol.decodeHello(legacyBody)
        assertEquals(2, decoded.version)
        assertEquals(0, decoded.token)
    }

    @Test
    fun padFrameRoundTrip() {
        val frame = XoraNetplayProtocol.PadFrame(
            frame = 1204,
            buttons = 0x12AB,
            lx = -300,
            ly = 20,
            rx = 32767,
            ry = -32768,
            slot = 3,
            epoch = 7,
        )
        val decoded = XoraNetplayProtocol.decodePadFrame(XoraNetplayProtocol.encodePadFrame(frame))
        assertEquals(frame.frame, decoded.frame)
        assertEquals(frame.buttons, decoded.buttons)
        assertEquals(frame.lx, decoded.lx)
        assertEquals(frame.ly, decoded.ly)
        assertEquals(frame.rx, decoded.rx)
        assertEquals(frame.ry, decoded.ry)
        assertEquals(3, decoded.slot)
        assertEquals(7, decoded.epoch)
    }

    @Test
    fun padFrameDecodesLegacyShortPayloads() {
        val full = XoraNetplayProtocol.encodePadFrame(
            XoraNetplayProtocol.PadFrame(frame = 9, buttons = 4, slot = 2, epoch = 5),
        )
        val without = XoraNetplayProtocol.decodePadFrame(full.copyOf(14))
        assertEquals(9, without.frame)
        assertEquals(4, without.buttons)
        assertEquals(XoraNetplayProtocol.PadFrame.SLOT_UNKNOWN, without.slot)
        assertEquals(0, without.epoch)

        val slotOnly = XoraNetplayProtocol.decodePadFrame(full.copyOf(15))
        assertEquals(2, slotOnly.slot)
        assertEquals(0, slotOnly.epoch)
    }

    @Test
    fun assignRoundTrip() {
        val encoded = XoraNetplayProtocol.encodeAssign(token = -987654321, slot = 4)
        val decoded = XoraNetplayProtocol.decodeAssign(encoded)
        assertEquals(-987654321, decoded.token)
        assertEquals(4, decoded.slot)
        assertEquals(0, XoraNetplayProtocol.decodeAssign(XoraNetplayProtocol.encodeAssign(7, 0)).slot)
    }

    @Test
    fun startGoByeRoundTrip() {
        assertEquals(3, XoraNetplayProtocol.decodeStartSlot(XoraNetplayProtocol.encodeStart(3)))
        assertEquals(0, XoraNetplayProtocol.decodeStartSlot(ByteArray(0)))

        val go = XoraNetplayProtocol.decodeGo(XoraNetplayProtocol.encodeGo(epoch = 9, slotsMask = 0b1011))
        assertEquals(9, go.epoch)
        assertEquals(0b1011, go.slotsMask)
        assertEquals(emptyMap<Int, String>(), go.names)
        // Legacy empty GO means a 1v1 session.
        val legacy = XoraNetplayProtocol.decodeGo(ByteArray(0))
        assertEquals(XoraNetplayProtocol.slotsMaskOf(listOf(1, 2)), legacy.slotsMask)

        // GO can carry the XOrA-username roster so everyone knows who the host is.
        val roster = XoraNetplayProtocol.decodeGo(
            XoraNetplayProtocol.encodeGo(
                epoch = 3,
                slotsMask = 0b0111,
                names = mapOf(1 to "angel", 2 to "pal", 3 to "thirdguy", 9 to "ignored"),
            ),
        )
        assertEquals(3, roster.epoch)
        assertEquals(0b0111, roster.slotsMask)
        assertEquals(mapOf(1 to "angel", 2 to "pal", 3 to "thirdguy"), roster.names)

        assertEquals(2, XoraNetplayProtocol.decodeByeSlot(XoraNetplayProtocol.encodeBye(2)))
        assertEquals(0, XoraNetplayProtocol.decodeByeSlot(ByteArray(0)))
    }

    @Test
    fun slotMaskHelpers() {
        assertEquals(0b0001, XoraNetplayProtocol.slotsMaskOf(listOf(1)))
        assertEquals(0b1111, XoraNetplayProtocol.slotsMaskOf(listOf(1, 2, 3, 4)))
        assertEquals(0b0101, XoraNetplayProtocol.slotsMaskOf(listOf(1, 3, 9, -2)))
        assertEquals(listOf(1, 3), XoraNetplayProtocol.slotsInMask(0b0101))
        assertEquals(emptyList<Int>(), XoraNetplayProtocol.slotsInMask(0))
    }

    @Test
    fun padKeySeparatesEpochs() {
        val sameFrameOldEpoch = XoraNetplaySession.padKey(epoch = 1, frame = 42)
        val sameFrameNewEpoch = XoraNetplaySession.padKey(epoch = 2, frame = 42)
        assertTrue(sameFrameOldEpoch != sameFrameNewEpoch)
        assertEquals(XoraNetplaySession.padKey(2, 42), XoraNetplaySession.padKey(2, 42))
    }

    @Test
    fun framedMessageRoundTrip() {
        val buffer = ByteArrayOutputStream()
        val payload = byteArrayOf(1, 2, 3, 4)
        XoraNetplayProtocol.writeMessage(
            DataOutputStream(buffer),
            XoraNetplayProtocol.TYPE_STATE,
            payload,
        )
        val (type, body) = XoraNetplayProtocol.readMessage(
            DataInputStream(ByteArrayInputStream(buffer.toByteArray())),
        )
        assertEquals(XoraNetplayProtocol.TYPE_STATE, type)
        assertEquals(payload.toList(), body.toList())
    }

    @Test
    fun sessionCodeNormalizesAndRejectsTypos() {
        assertEquals("K7M2QX", XoraNetplayProtocol.normalizeSessionCode(" k7m-2qx "))
        assertEquals("K7M2QX", XoraNetplayProtocol.normalizeSessionCode("k7m2qx"))
        assertEquals(null, XoraNetplayProtocol.normalizeSessionCode("K7M2Q"))
        assertEquals(null, XoraNetplayProtocol.normalizeSessionCode("hello!"))
        assertEquals("K7M2QX", XoraNetplayProtocol.filterSessionCodeDraft("k7m2qx extra"))
        assertEquals("K7M2Q", XoraNetplayProtocol.filterSessionCodeDraft("K7M2Q"))
        assertEquals("", XoraNetplayProtocol.filterSessionCodeDraft(""))
        assertEquals(
            "xora-np-K7M2QX",
            XoraNetplayProtocol.matchNameForSessionCode("K7M2QX"),
        )
        assertEquals(3, XoraNetplayProtocol.VERSION)
        assertEquals(4, XoraNetplayProtocol.MAX_PLAYERS)
        assertEquals(7, XoraNetplayProtocol.TYPE_GO)
        assertEquals(8, XoraNetplayProtocol.TYPE_ASSIGN)
        val generated = XoraNetplayProtocol.generateSessionCode()
        assertEquals(6, generated.length)
        assertTrue(generated.all { it in XoraNetplayProtocol.SESSION_CODE_ALPHABET })
    }

    @Test
    fun relayChunksRoundTrip() {
        val original = ByteArray(2500) { i -> (i * 13).toByte() }
        val size = XoraNetplayProtocol.RELAY_CHUNK_BYTES
        val count = (original.size + size - 1) / size
        val parts = HashMap<Int, ByteArray>()
        for (i in 0 until count) {
            val start = i * size
            val end = minOf(start + size, original.size)
            val encoded = XoraNetplayProtocol.encodeChunk(
                originalType = XoraNetplayProtocol.TYPE_STATE,
                index = i,
                count = count,
                total = original.size,
                slice = original.copyOfRange(start, end),
            )
            val decoded = XoraNetplayProtocol.decodeChunk(encoded)
            assertEquals(XoraNetplayProtocol.TYPE_STATE, decoded.originalType)
            assertEquals(i, decoded.index)
            assertEquals(count, decoded.count)
            assertEquals(original.size, decoded.total)
            parts[decoded.index] = decoded.slice
        }
        val assembled = XoraNetplayProtocol.assembleChunks(parts, original.size)
        assertEquals(original.toList(), assembled.toList())
    }
}
