package com.arcadia.shell.libretro

import com.arcadia.shell.libretro.netplay.XoraNetplayLink
import com.arcadia.shell.libretro.netplay.XoraNetplayProtocol
import com.arcadia.shell.xoranetwork.XoraNetworkRepository
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.SocketTimeoutException
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Relays the existing netplay protocol over a Nakama match so two signed-in devices can play
 * without being on the same LAN or forwarding a port.
 */
internal class XoraNakamaNetplayLink(
    private val network: XoraNetworkRepository,
    private val matchId: String,
) : XoraNetplayLink {
    override fun send(type: Int, payload: ByteArray) {
        val body = if (type == XoraNetplayProtocol.TYPE_STATE) gzip(payload) else payload
        val reliable = type != XoraNetplayProtocol.TYPE_INPUT
        if (body.size <= XoraNetplayProtocol.RELAY_CHUNK_BYTES) {
            network.sendMatchData(matchId, type, body, reliable)
            return
        }
        val chunk = XoraNetplayProtocol.RELAY_CHUNK_BYTES
        val count = (body.size + chunk - 1) / chunk
        for (i in 0 until count) {
            val start = i * chunk
            val end = minOf(start + chunk, body.size)
            network.sendMatchData(
                matchId,
                XoraNetplayProtocol.TYPE_CHUNK,
                XoraNetplayProtocol.encodeChunk(
                    originalType = type,
                    index = i,
                    count = count,
                    total = body.size,
                    slice = body.copyOfRange(start, end),
                ),
                reliable = true,
            )
        }
    }

    override fun receive(timeoutMs: Int): Pair<Int, ByteArray> {
        val deadline = if (timeoutMs <= 0) Long.MAX_VALUE else System.currentTimeMillis() + timeoutMs
        val chunks = LinkedHashMap<Int, ByteArray>()
        var expected = -1
        var originalType = -1
        var total = 0
        while (true) {
            if (timeoutMs > 0 && System.currentTimeMillis() >= deadline) {
                throw SocketTimeoutException("Timed out waiting for the other player")
            }
            val wait = if (timeoutMs <= 0) {
                0
            } else {
                (deadline - System.currentTimeMillis()).toInt().coerceAtLeast(1)
            }
            val (type, payload) = network.receiveMatchData(matchId, wait)
            if (type != XoraNetplayProtocol.TYPE_CHUNK) {
                val body = if (type == XoraNetplayProtocol.TYPE_STATE) maybeGunzip(payload) else payload
                return type to body
            }
            val part = XoraNetplayProtocol.decodeChunk(payload)
            originalType = part.originalType
            expected = part.count
            total = part.total
            chunks[part.index] = part.slice
            if (expected > 0 && chunks.size == expected) {
                val assembled = XoraNetplayProtocol.assembleChunks(chunks, total)
                val body = if (originalType == XoraNetplayProtocol.TYPE_STATE) {
                    maybeGunzip(assembled)
                } else {
                    assembled
                }
                return originalType to body
            }
        }
    }

    override fun close() {
        network.leaveMatch(matchId)
    }

    private fun gzip(data: ByteArray): ByteArray {
        val buffer = ByteArrayOutputStream()
        GZIPOutputStream(buffer).use { it.write(data) }
        return buffer.toByteArray()
    }

    private fun maybeGunzip(data: ByteArray): ByteArray {
        if (data.size < 2 || data[0] != 0x1f.toByte() || data[1] != 0x8b.toByte()) return data
        return runCatching {
            GZIPInputStream(ByteArrayInputStream(data)).use { it.readBytes() }
        }.getOrDefault(data)
    }
}
