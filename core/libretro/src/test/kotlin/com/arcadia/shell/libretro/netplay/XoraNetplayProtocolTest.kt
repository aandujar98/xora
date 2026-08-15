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
        )
        val decoded = XoraNetplayProtocol.decodeHello(XoraNetplayProtocol.encodeHello(hello))
        assertEquals(hello.nickname, decoded.nickname)
        assertEquals(hello.coreName, decoded.coreName)
        assertEquals(hello.platformId, decoded.platformId)
        assertEquals(hello.romName, decoded.romName)
        assertEquals(XoraNetplayProtocol.VERSION, decoded.version)
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
        )
        val decoded = XoraNetplayProtocol.decodePadFrame(XoraNetplayProtocol.encodePadFrame(frame))
        assertEquals(frame.frame, decoded.frame)
        assertEquals(frame.buttons, decoded.buttons)
        assertEquals(frame.lx, decoded.lx)
        assertEquals(frame.ly, decoded.ly)
        assertEquals(frame.rx, decoded.rx)
        assertEquals(frame.ry, decoded.ry)
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
        assertEquals(2, XoraNetplayProtocol.VERSION)
        assertEquals(7, XoraNetplayProtocol.TYPE_GO)
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
