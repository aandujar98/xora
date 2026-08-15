package com.arcadia.shell.libretro.netplay

import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets

/**
 * Length-prefixed TCP messages for XOrA netplay (RetroArch-style host/join + state sync).
 *
 * Wire format: `u8 type` + `u32be payloadLength` + payload.
 */
object XoraNetplayProtocol {
    const val VERSION: Int = 1
    const val MAX_PAYLOAD: Int = 32 * 1024 * 1024

    const val TYPE_HELLO: Int = 1
    const val TYPE_STATE: Int = 2
    const val TYPE_INPUT: Int = 3
    const val TYPE_START: Int = 4
    const val TYPE_ERROR: Int = 5
    const val TYPE_BYE: Int = 6

    data class Hello(
        val nickname: String,
        val coreName: String,
        val platformId: String,
        val romName: String,
        val version: Int = VERSION,
    )

    data class PadFrame(
        val frame: Int,
        val buttons: Int,
        val lx: Short = 0,
        val ly: Short = 0,
        val rx: Short = 0,
        val ry: Short = 0,
    )

    fun encodeHello(hello: Hello): ByteArray {
        val body = listOf(
            hello.version.toString(),
            hello.nickname,
            hello.coreName,
            hello.platformId,
            hello.romName,
        ).joinToString("\u0000")
        return body.toByteArray(StandardCharsets.UTF_8)
    }

    fun decodeHello(payload: ByteArray): Hello {
        val parts = String(payload, StandardCharsets.UTF_8).split('\u0000')
        return Hello(
            version = parts.getOrNull(0)?.toIntOrNull() ?: 0,
            nickname = parts.getOrNull(1).orEmpty(),
            coreName = parts.getOrNull(2).orEmpty(),
            platformId = parts.getOrNull(3).orEmpty(),
            romName = parts.getOrNull(4).orEmpty(),
        )
    }

    fun encodePadFrame(frame: PadFrame): ByteArray {
        val out = ByteArray(14)
        writeInt(out, 0, frame.frame)
        writeShort(out, 4, frame.buttons and 0xFFFF)
        writeShort(out, 6, frame.lx.toInt() and 0xFFFF)
        writeShort(out, 8, frame.ly.toInt() and 0xFFFF)
        writeShort(out, 10, frame.rx.toInt() and 0xFFFF)
        writeShort(out, 12, frame.ry.toInt() and 0xFFFF)
        return out
    }

    fun decodePadFrame(payload: ByteArray): PadFrame {
        require(payload.size >= 14) { "input payload too short" }
        return PadFrame(
            frame = readInt(payload, 0),
            buttons = readShort(payload, 4),
            lx = readShort(payload, 6).toShort(),
            ly = readShort(payload, 8).toShort(),
            rx = readShort(payload, 10).toShort(),
            ry = readShort(payload, 12).toShort(),
        )
    }

    fun writeMessage(out: DataOutputStream, type: Int, payload: ByteArray) {
        require(payload.size <= MAX_PAYLOAD) { "payload too large" }
        out.writeByte(type)
        out.writeInt(payload.size)
        if (payload.isNotEmpty()) out.write(payload)
        out.flush()
    }

    fun readMessage(input: DataInputStream): Pair<Int, ByteArray> {
        val type = input.readUnsignedByte()
        val length = input.readInt()
        require(length in 0..MAX_PAYLOAD) { "invalid payload length $length" }
        val payload = ByteArray(length)
        if (length > 0) input.readFully(payload)
        return type to payload
    }

    private fun writeInt(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = (value ushr 24).toByte()
        buf[offset + 1] = (value ushr 16).toByte()
        buf[offset + 2] = (value ushr 8).toByte()
        buf[offset + 3] = value.toByte()
    }

    private fun writeShort(buf: ByteArray, offset: Int, value: Int) {
        buf[offset] = (value ushr 8).toByte()
        buf[offset + 1] = value.toByte()
    }

    private fun readInt(buf: ByteArray, offset: Int): Int =
        ((buf[offset].toInt() and 0xFF) shl 24) or
            ((buf[offset + 1].toInt() and 0xFF) shl 16) or
            ((buf[offset + 2].toInt() and 0xFF) shl 8) or
            (buf[offset + 3].toInt() and 0xFF)

    private fun readShort(buf: ByteArray, offset: Int): Int =
        ((buf[offset].toInt() and 0xFF) shl 8) or (buf[offset + 1].toInt() and 0xFF)
}
