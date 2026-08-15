package com.arcadia.shell.libretro.netplay

import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.charset.StandardCharsets

/**
 * Length-prefixed TCP messages for XOrA netplay (RetroArch-style host/join + state sync).
 *
 * Wire format: `u8 type` + `u32be payloadLength` + payload.
 *
 * Version 3 adds player slots (host is always Player 1, joiners get 2..4), a join token so
 * ASSIGN can address one joiner on a broadcast relay, and an epoch that increments on every
 * savestate resync so stale pad frames can never poison a new session segment.
 */
object XoraNetplayProtocol {
    const val VERSION: Int = 3
    const val MAX_PLAYERS: Int = 4
    const val MAX_PAYLOAD: Int = 32 * 1024 * 1024
    /** Nakama match data is small; savestates go out as [TYPE_CHUNK] pieces. */
    const val RELAY_CHUNK_BYTES: Int = 900

    const val TYPE_HELLO: Int = 1
    const val TYPE_STATE: Int = 2
    const val TYPE_INPUT: Int = 3
    const val TYPE_START: Int = 4
    const val TYPE_ERROR: Int = 5
    const val TYPE_BYE: Int = 6
    /** Host → joiners: savestate is live, start lockstep together. */
    const val TYPE_GO: Int = 7
    /** Host → one joiner (matched by token): your player slot. Slot 0 = session full. */
    const val TYPE_ASSIGN: Int = 8
    const val TYPE_CHUNK: Int = 100

    const val SESSION_CODE_ALPHABET: String = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    const val SESSION_CODE_LENGTH: Int = 6
    const val MATCH_NAME_PREFIX: String = "xora-np-"

    data class Hello(
        val nickname: String,
        val coreName: String,
        val platformId: String,
        val romName: String,
        val version: Int = VERSION,
        /** Random per-join token echoed back in ASSIGN so a joiner knows which slot is theirs. */
        val token: Int = 0,
    )

    data class PadFrame(
        val frame: Int,
        val buttons: Int,
        val lx: Short = 0,
        val ly: Short = 0,
        val rx: Short = 0,
        val ry: Short = 0,
        /** 0 = unknown/legacy, 1 = host, 2..4 = joiners in join order. */
        val slot: Int = SLOT_UNKNOWN,
        /** Resync epoch this frame belongs to; bumped on every savestate barrier. */
        val epoch: Int = 0,
    ) {
        companion object {
            const val SLOT_UNKNOWN: Int = 0
            const val SLOT_HOST: Int = 1
        }
    }

    fun encodeHello(hello: Hello): ByteArray {
        val body = listOf(
            hello.version.toString(),
            hello.nickname,
            hello.coreName,
            hello.platformId,
            hello.romName,
            hello.token.toString(),
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
            token = parts.getOrNull(5)?.toIntOrNull() ?: 0,
        )
    }

    fun encodePadFrame(frame: PadFrame): ByteArray {
        val out = ByteArray(16)
        writeInt(out, 0, frame.frame)
        writeShort(out, 4, frame.buttons and 0xFFFF)
        writeShort(out, 6, frame.lx.toInt() and 0xFFFF)
        writeShort(out, 8, frame.ly.toInt() and 0xFFFF)
        writeShort(out, 10, frame.rx.toInt() and 0xFFFF)
        writeShort(out, 12, frame.ry.toInt() and 0xFFFF)
        out[14] = (frame.slot and 0xFF).toByte()
        out[15] = (frame.epoch and 0xFF).toByte()
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
            slot = if (payload.size >= 15) payload[14].toInt() and 0xFF else PadFrame.SLOT_UNKNOWN,
            epoch = if (payload.size >= 16) payload[15].toInt() and 0xFF else 0,
        )
    }

    const val REJECT_FULL: Int = 0
    const val REJECT_VERSION: Int = 1
    const val REJECT_CORE: Int = 2

    /** ASSIGN payload: joiner's hello token + assigned slot (0 = rejected, see reason). */
    fun encodeAssign(token: Int, slot: Int, reason: Int = REJECT_FULL): ByteArray {
        val out = ByteArray(6)
        writeInt(out, 0, token)
        out[4] = (slot and 0xFF).toByte()
        out[5] = (reason and 0xFF).toByte()
        return out
    }

    data class Assign(val token: Int, val slot: Int, val reason: Int = REJECT_FULL)

    fun decodeAssign(payload: ByteArray): Assign {
        require(payload.size >= 5) { "assign payload too short" }
        return Assign(
            token = readInt(payload, 0),
            slot = payload[4].toInt() and 0xFF,
            reason = if (payload.size >= 6) payload[5].toInt() and 0xFF else REJECT_FULL,
        )
    }

    /** START payload: the sender's slot so the host can tick off each joiner's barrier ack. */
    fun encodeStart(slot: Int): ByteArray = byteArrayOf((slot and 0xFF).toByte())

    fun decodeStartSlot(payload: ByteArray): Int =
        if (payload.isNotEmpty()) payload[0].toInt() and 0xFF else 0

    /** GO payload: new epoch + bitmask of active player slots (bit 0 = Player 1). */
    fun encodeGo(epoch: Int, slotsMask: Int): ByteArray =
        byteArrayOf((epoch and 0xFF).toByte(), (slotsMask and 0xFF).toByte())

    data class Go(val epoch: Int, val slotsMask: Int)

    fun decodeGo(payload: ByteArray): Go = Go(
        epoch = if (payload.isNotEmpty()) payload[0].toInt() and 0xFF else 0,
        slotsMask = if (payload.size >= 2) {
            payload[1].toInt() and 0xFF
        } else {
            slotsMaskOf(listOf(1, 2))
        },
    )

    /** BYE payload: the slot that is leaving (0 = unknown → treat as session over). */
    fun encodeBye(slot: Int): ByteArray = byteArrayOf((slot and 0xFF).toByte())

    fun decodeByeSlot(payload: ByteArray): Int =
        if (payload.isNotEmpty()) payload[0].toInt() and 0xFF else 0

    fun slotsMaskOf(slots: Iterable<Int>): Int {
        var mask = 0
        slots.forEach { slot ->
            if (slot in 1..MAX_PLAYERS) mask = mask or (1 shl (slot - 1))
        }
        return mask
    }

    fun slotsInMask(mask: Int): List<Int> =
        (1..MAX_PLAYERS).filter { slot -> mask and (1 shl (slot - 1)) != 0 }

    fun generateSessionCode(random: java.security.SecureRandom = java.security.SecureRandom()): String {
        val alphabet = SESSION_CODE_ALPHABET
        return CharArray(SESSION_CODE_LENGTH) {
            alphabet[random.nextInt(alphabet.length)]
        }.concatToString()
    }

    /** Uppercase, strip spaces/dashes; null unless it is exactly 6 valid characters. */
    fun normalizeSessionCode(raw: String): String? {
        val cleaned = raw.trim().uppercase().filter { it in SESSION_CODE_ALPHABET }
        return cleaned.takeIf { it.length == SESSION_CODE_LENGTH }
    }

    fun filterSessionCodeDraft(raw: String): String =
        raw.uppercase().filter { it in SESSION_CODE_ALPHABET }.take(SESSION_CODE_LENGTH)

    fun matchNameForSessionCode(code: String): String = "$MATCH_NAME_PREFIX$code"

    data class ChunkPart(
        val originalType: Int,
        val index: Int,
        val count: Int,
        val total: Int,
        val slice: ByteArray,
    )

    fun encodeChunk(originalType: Int, index: Int, count: Int, total: Int, slice: ByteArray): ByteArray {
        val out = ByteArray(9 + slice.size)
        out[0] = originalType.toByte()
        writeShort(out, 1, index)
        writeShort(out, 3, count)
        writeInt(out, 5, total)
        if (slice.isNotEmpty()) System.arraycopy(slice, 0, out, 9, slice.size)
        return out
    }

    fun decodeChunk(payload: ByteArray): ChunkPart {
        require(payload.size >= 9) { "chunk too short" }
        return ChunkPart(
            originalType = payload[0].toInt() and 0xFF,
            index = readShort(payload, 1),
            count = readShort(payload, 3),
            total = readInt(payload, 5),
            slice = payload.copyOfRange(9, payload.size),
        )
    }

    fun assembleChunks(parts: Map<Int, ByteArray>, total: Int): ByteArray {
        val out = ByteArray(total)
        var offset = 0
        for (i in 0 until parts.size) {
            val slice = parts[i] ?: error("missing chunk $i")
            System.arraycopy(slice, 0, out, offset, slice.size)
            offset += slice.size
        }
        return out
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
