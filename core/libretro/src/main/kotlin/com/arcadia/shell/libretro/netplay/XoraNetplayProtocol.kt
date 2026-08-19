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
    const val VERSION: Int = 4
    const val MAX_PLAYERS: Int = 4
    const val MAX_PAYLOAD: Int = 32 * 1024 * 1024
    /** Nakama match_data is JSON+Base64; ~2 KB stays under the 4 KB envelope. */
    const val RELAY_CHUNK_BYTES: Int = 2048
    /** Drop a relay payload rather than flooding the match with dozens of chunks. */
    const val RELAY_MAX_CHUNKS: Int = 16

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
    /** Joiner → host: request a different player seat (answered with ASSIGN + a barrier GO). */
    const val TYPE_SEAT: Int = 9
    /**
     * Host → joiners: JPEG of the host framebuffer (+ optional PCM) so home-console
     * sessions stay one instance. Handheld link-cable sessions do not send this.
     */
    const val TYPE_VIDEO: Int = 10
    /**
     * Handheld Game Link: 16-bit SIO send word for this device's slot. Both GBAs keep
     * running; the frontend feeds mGBA's live SIO (not just `memory.io`) so the cores
     * see a connected Game Link cable.
     */
    const val TYPE_SERIAL: Int = 11
    /**
     * gpSP (and other netpacket cores): dest/src client ids plus the core's serial payload.
     * Bridged over the existing LAN/Nakama session so joiners do not open a second socket.
     */
    const val TYPE_NETPACKET: Int = 12
    const val TYPE_CHUNK: Int = 100
    const val NETPACKET_BROADCAST: Int = 0xFFFF

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
        /** Host LAN IPv4s so a joiner can auto-fill the join-IP field. Empty on joiners. */
        val hostAddresses: List<String> = emptyList(),
        val hostPort: Int = 0,
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
            hello.hostAddresses.joinToString(","),
            hello.hostPort.takeIf { it > 0 }?.toString().orEmpty(),
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
            hostAddresses = parts.getOrNull(6)
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                .orEmpty(),
            hostPort = parts.getOrNull(7)?.toIntOrNull() ?: 0,
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

    /**
     * GO payload: new epoch + bitmask of active player slots (bit 0 = Player 1), followed by
     * an optional UTF-8 roster (`slot=name` pairs, NUL-separated) so every device can show
     * who the host is and who just joined by XOrA Network username.
     */
    fun encodeGo(epoch: Int, slotsMask: Int, names: Map<Int, String> = emptyMap()): ByteArray {
        val head = byteArrayOf((epoch and 0xFF).toByte(), (slotsMask and 0xFF).toByte())
        val roster = names.entries
            .filter { (slot, name) -> slot in 1..MAX_PLAYERS && name.isNotBlank() }
            .sortedBy { it.key }
            .joinToString("\u0000") { (slot, name) ->
                "$slot=" + name.replace("\u0000", "").replace("=", "").trim()
            }
        if (roster.isEmpty()) return head
        return head + roster.toByteArray(StandardCharsets.UTF_8)
    }

    data class Go(
        val epoch: Int,
        val slotsMask: Int,
        val names: Map<Int, String> = emptyMap(),
    )

    fun decodeGo(payload: ByteArray): Go {
        val names = if (payload.size > 2) {
            String(payload, 2, payload.size - 2, StandardCharsets.UTF_8)
                .split('\u0000')
                .mapNotNull { pair ->
                    val slot = pair.substringBefore('=').toIntOrNull() ?: return@mapNotNull null
                    val name = pair.substringAfter('=', "").trim()
                    if (slot in 1..MAX_PLAYERS && name.isNotEmpty()) slot to name else null
                }
                .toMap()
        } else {
            emptyMap()
        }
        return Go(
            epoch = if (payload.isNotEmpty()) payload[0].toInt() and 0xFF else 0,
            slotsMask = if (payload.size >= 2) {
                payload[1].toInt() and 0xFF
            } else {
                slotsMaskOf(listOf(1, 2))
            },
            names = names,
        )
    }

    /** SEAT payload: request token + the joiner's current slot + the seat they want. */
    fun encodeSeat(token: Int, currentSlot: Int, requestedSlot: Int): ByteArray {
        val out = ByteArray(6)
        writeInt(out, 0, token)
        out[4] = (currentSlot and 0xFF).toByte()
        out[5] = (requestedSlot and 0xFF).toByte()
        return out
    }

    data class SeatRequest(val token: Int, val currentSlot: Int, val requestedSlot: Int)

    fun decodeSeat(payload: ByteArray): SeatRequest {
        require(payload.size >= 6) { "seat payload too short" }
        return SeatRequest(
            token = readInt(payload, 0),
            currentSlot = payload[4].toInt() and 0xFF,
            requestedSlot = payload[5].toInt() and 0xFF,
        )
    }

    /** BYE payload: the slot that is leaving (0 = unknown → treat as session over). */
    fun encodeBye(slot: Int): ByteArray = byteArrayOf((slot and 0xFF).toByte())

    fun decodeByeSlot(payload: ByteArray): Int =
        if (payload.isNotEmpty()) payload[0].toInt() and 0xFF else 0

    data class VideoPacket(
        val seq: Int,
        val jpeg: ByteArray,
        val pcm: ShortArray,
    )

    fun encodeVideo(seq: Int, jpeg: ByteArray, pcm: ShortArray = ShortArray(0)): ByteArray {
        val out = ByteArray(12 + jpeg.size + pcm.size * 2)
        writeInt(out, 0, seq)
        writeInt(out, 4, jpeg.size)
        writeInt(out, 8, pcm.size)
        if (jpeg.isNotEmpty()) System.arraycopy(jpeg, 0, out, 12, jpeg.size)
        var i = 12 + jpeg.size
        for (sample in pcm) {
            out[i] = (sample.toInt() and 0xFF).toByte()
            out[i + 1] = ((sample.toInt() ushr 8) and 0xFF).toByte()
            i += 2
        }
        return out
    }

    fun decodeVideo(payload: ByteArray): VideoPacket {
        require(payload.size >= 12) { "video payload too short" }
        val jpegLen = readInt(payload, 4).coerceAtLeast(0)
        val pcmCount = readInt(payload, 8).coerceAtLeast(0)
        require(payload.size >= 12 + jpegLen + pcmCount * 2) { "video payload truncated" }
        val jpeg = if (jpegLen == 0) {
            ByteArray(0)
        } else {
            payload.copyOfRange(12, 12 + jpegLen)
        }
        val pcm = ShortArray(pcmCount)
        var i = 12 + jpegLen
        for (n in 0 until pcmCount) {
            val lo = payload[i].toInt() and 0xFF
            val hi = payload[i + 1].toInt() and 0xFF
            pcm[n] = ((hi shl 8) or lo).toShort()
            i += 2
        }
        return VideoPacket(seq = readInt(payload, 0), jpeg = jpeg, pcm = pcm)
    }

    data class SerialPacket(
        val slot: Int,
        val send: Int,
        val siocnt: Int = 0,
    )

    fun encodeSerial(slot: Int, send: Int, siocnt: Int = 0): ByteArray {
        val out = ByteArray(5)
        out[0] = (slot and 0xFF).toByte()
        writeShort(out, 1, send and 0xFFFF)
        writeShort(out, 3, siocnt and 0xFFFF)
        return out
    }

    fun decodeSerial(payload: ByteArray): SerialPacket {
        require(payload.size >= 3) { "serial payload too short" }
        return SerialPacket(
            slot = payload[0].toInt() and 0xFF,
            send = readShort(payload, 1),
            siocnt = if (payload.size >= 5) readShort(payload, 3) else 0,
        )
    }

    data class Netpacket(
        val dest: Int,
        val src: Int,
        val flags: Int,
        val payload: ByteArray,
    )

    fun encodeNetpacket(dest: Int, src: Int, flags: Int, payload: ByteArray): ByteArray {
        val out = ByteArray(6 + payload.size)
        writeShort(out, 0, dest and 0xFFFF)
        writeShort(out, 2, src and 0xFFFF)
        writeShort(out, 4, flags and 0xFFFF)
        if (payload.isNotEmpty()) System.arraycopy(payload, 0, out, 6, payload.size)
        return out
    }

    fun decodeNetpacket(payload: ByteArray): Netpacket {
        require(payload.size >= 6) { "netpacket payload too short" }
        return Netpacket(
            dest = readShort(payload, 0),
            src = readShort(payload, 2),
            flags = readShort(payload, 4),
            payload = if (payload.size == 6) ByteArray(0) else payload.copyOfRange(6, payload.size),
        )
    }

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

    /** Finds a 6-character session code inside free text (inbox bodies, pasted lines). */
    fun extractSessionCode(raw: String): String? {
        normalizeSessionCode(raw)?.let { return it }
        val upper = raw.uppercase()
        val match = Regex("[$SESSION_CODE_ALPHABET]{$SESSION_CODE_LENGTH}").find(upper) ?: return null
        return match.value
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

    /**
     * Split [body] into Nakama-safe frames. A payload that already fits is one frame of
     * [originalType]; larger bodies become [TYPE_CHUNK] pieces. When [maxChunks] is
     * positive, payloads that would need more pieces are dropped (empty list) so a JPEG
     * cannot flood the match. Savestates pass [maxChunks] = 0 (unlimited).
     */
    fun relayFrames(
        originalType: Int,
        body: ByteArray,
        chunkBytes: Int = RELAY_CHUNK_BYTES,
        maxChunks: Int = 0,
    ): List<Pair<Int, ByteArray>> {
        if (body.size <= chunkBytes) return listOf(originalType to body)
        val count = (body.size + chunkBytes - 1) / chunkBytes
        if (maxChunks > 0 && count > maxChunks) return emptyList()
        return List(count) { i ->
            val start = i * chunkBytes
            val end = minOf(start + chunkBytes, body.size)
            TYPE_CHUNK to encodeChunk(
                originalType = originalType,
                index = i,
                count = count,
                total = body.size,
                slice = body.copyOfRange(start, end),
            )
        }
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
