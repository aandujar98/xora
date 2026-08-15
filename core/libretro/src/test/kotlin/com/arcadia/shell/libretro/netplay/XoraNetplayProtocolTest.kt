package com.arcadia.shell.libretro.netplay

import org.junit.Assert.assertEquals
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
}
