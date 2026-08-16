package com.arcadia.shell.libretro

import com.arcadia.shell.libretro.netplay.XoraNetplayLink
import com.arcadia.shell.libretro.netplay.XoraNetplayProtocol
import com.arcadia.shell.xoranetwork.XoraNetworkRepository
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.SocketTimeoutException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Relays the existing netplay protocol over a Nakama match so two signed-in devices can play
 * without being on the same LAN or forwarding a port.
 *
 * INPUT is queued off the emu thread so JSON + WebSocket send cannot stall the frame loop.
 * Pad frames are unreliable: only the latest pad matters, and a reliable queue of stale
 * frames used to delay (or starve) the seat the joiner is actually playing.
 */
internal class XoraNakamaNetplayLink(
    private val network: XoraNetworkRepository,
    private val matchId: String,
) : XoraNetplayLink {
    private val closed = AtomicBoolean(false)
    // Large enough that a brief websocket stall doesn't drop pad frames — a dropped frame
    // means every other player holds/zeroes that slot for its lockstep window.
    private val inputQueue = ArrayBlockingQueue<ByteArray>(256)
    private val inputSender = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "xora-np-input").apply { isDaemon = true }
    }

    init {
        inputSender.execute {
            while (!closed.get()) {
                val payload = try {
                    inputQueue.poll(50, TimeUnit.MILLISECONDS)
                } catch (_: InterruptedException) {
                    break
                } ?: continue
                if (closed.get()) break
                runCatching {
                    network.sendMatchData(
                        matchId,
                        XoraNetplayProtocol.TYPE_INPUT,
                        payload,
                        reliable = false,
                    )
                }
            }
        }
    }

    override fun send(type: Int, payload: ByteArray) {
        if (closed.get()) return
        if (type == XoraNetplayProtocol.TYPE_INPUT) {
            if (closed.get()) return
            // Never block the emu thread; drop the oldest pad if the socket is behind.
            if (!inputQueue.offer(payload)) {
                inputQueue.poll()
                inputQueue.offer(payload)
            }
            return
        }
        val body = if (type == XoraNetplayProtocol.TYPE_STATE) gzip(payload) else payload
        if (body.size <= XoraNetplayProtocol.RELAY_CHUNK_BYTES) {
            network.sendMatchData(matchId, type, body, reliable = true)
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

    // Chunk reassembly must survive across receive() calls: other players keep streaming
    // INPUT while a savestate is chunking through, and each interrupted call used to throw
    // the collected pieces away — the state could then never finish assembling.
    private val chunkParts = LinkedHashMap<Int, ByteArray>()
    private var chunkType = -1
    private var chunkExpected = -1
    private var chunkTotal = 0

    override fun receive(timeoutMs: Int): Pair<Int, ByteArray> {
        val deadline = if (timeoutMs <= 0) Long.MAX_VALUE else System.currentTimeMillis() + timeoutMs
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
            if (part.originalType != chunkType || part.count != chunkExpected) {
                // A new transfer started; drop any stale partial one.
                chunkParts.clear()
                chunkType = part.originalType
                chunkExpected = part.count
                chunkTotal = part.total
            }
            chunkParts[part.index] = part.slice
            if (chunkExpected > 0 && chunkParts.size == chunkExpected) {
                val assembled = XoraNetplayProtocol.assembleChunks(chunkParts, chunkTotal)
                val originalType = chunkType
                chunkParts.clear()
                chunkType = -1
                chunkExpected = -1
                chunkTotal = 0
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
        if (!closed.compareAndSet(false, true)) return
        inputSender.shutdownNow()
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
