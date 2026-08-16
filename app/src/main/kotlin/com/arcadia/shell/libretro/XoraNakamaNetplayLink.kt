package com.arcadia.shell.libretro

import com.arcadia.shell.libretro.netplay.XoraNetplayLink
import com.arcadia.shell.libretro.netplay.XoraNetplayProtocol
import com.arcadia.shell.xoranetwork.XoraNetworkRepository
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.net.SocketTimeoutException
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
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
 *
 * VIDEO is also queued, then sent as Nakama-sized [TYPE_CHUNK] pieces. A single JPEG used to
 * exceed match_data limits and Nakama would drop the joiner the moment they linked.
 */
internal class XoraNakamaNetplayLink(
    private val network: XoraNetworkRepository,
    private val matchId: String,
) : XoraNetplayLink {
    private val closed = AtomicBoolean(false)
    // Large enough that a brief websocket stall doesn't drop pad frames — a dropped frame
    // means every other player holds/zeroes that slot for its lockstep window.
    private val inputQueue = ArrayBlockingQueue<ByteArray>(256)
    private val videoQueue = ArrayBlockingQueue<ByteArray>(1)
    private val serialQueue = ArrayBlockingQueue<ByteArray>(1)
    private val inputSender = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "xora-np-input").apply { isDaemon = true }
    }

    init {
        inputSender.execute {
            while (!closed.get()) {
                val video = videoQueue.poll()
                if (video != null && !closed.get()) {
                    sendRelayed(
                        XoraNetplayProtocol.TYPE_VIDEO,
                        video,
                        reliable = true,
                        swallowErrors = true,
                        maxChunks = XoraNetplayProtocol.RELAY_MAX_CHUNKS,
                    )
                }
                val serial = serialQueue.poll()
                if (serial != null && !closed.get()) {
                    sendRelayed(
                        XoraNetplayProtocol.TYPE_SERIAL,
                        serial,
                        reliable = false,
                        swallowErrors = true,
                    )
                }
                val payload = try {
                    inputQueue.poll(50, TimeUnit.MILLISECONDS)
                } catch (_: InterruptedException) {
                    break
                } ?: continue
                if (closed.get()) break
                sendRelayed(
                    XoraNetplayProtocol.TYPE_INPUT,
                    payload,
                    reliable = false,
                    swallowErrors = true,
                )
            }
        }
    }

    override fun send(type: Int, payload: ByteArray) {
        if (closed.get()) return
        if (type == XoraNetplayProtocol.TYPE_INPUT) {
            queueLatest(inputQueue, payload)
            return
        }
        if (type == XoraNetplayProtocol.TYPE_SERIAL) {
            queueLatest(serialQueue, payload)
            return
        }
        if (type == XoraNetplayProtocol.TYPE_VIDEO) {
            queueLatest(videoQueue, payload)
            return
        }
        val body = if (type == XoraNetplayProtocol.TYPE_STATE) gzip(payload) else payload
        sendRelayed(type, body, reliable = true, swallowErrors = false)
    }

    private fun sendRelayed(
        type: Int,
        payload: ByteArray,
        reliable: Boolean,
        swallowErrors: Boolean,
        maxChunks: Int = 0,
    ) {
        val frames = XoraNetplayProtocol.relayFrames(type, payload, maxChunks = maxChunks)
        if (frames.isEmpty()) return
        for ((opcode, body) in frames) {
            if (closed.get()) return
            val result = runCatching {
                network.sendMatchData(matchId, opcode, body, reliable)
            }
            if (!swallowErrors) result.getOrThrow()
        }
    }

    // Chunk reassembly must survive across receive() calls: other players keep streaming
    // INPUT while a savestate is chunking through, and each interrupted call used to throw
    // the collected pieces away — the state could then never finish assembling.
    // Video and state assemble independently so a JPEG cannot discard a savestate.
    private val chunkParts = ConcurrentHashMap<Int, LinkedHashMap<Int, ByteArray>>()
    private val chunkExpected = ConcurrentHashMap<Int, Int>()
    private val chunkTotal = ConcurrentHashMap<Int, Int>()

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
            val part = runCatching { XoraNetplayProtocol.decodeChunk(payload) }.getOrNull() ?: continue
            val originalType = part.originalType
            val expected = chunkExpected[originalType]
            if (expected == null || expected != part.count || chunkTotal[originalType] != part.total) {
                chunkParts[originalType] = LinkedHashMap()
                chunkExpected[originalType] = part.count
                chunkTotal[originalType] = part.total
            }
            val parts = chunkParts.getOrPut(originalType) { LinkedHashMap() }
            parts[part.index] = part.slice
            val need = chunkExpected[originalType] ?: continue
            val total = chunkTotal[originalType] ?: continue
            if (need > 0 && parts.size == need) {
                val assembled = runCatching {
                    XoraNetplayProtocol.assembleChunks(parts, total)
                }.getOrNull()
                chunkParts.remove(originalType)
                chunkExpected.remove(originalType)
                chunkTotal.remove(originalType)
                if (assembled == null) continue
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

    private fun queueLatest(queue: ArrayBlockingQueue<ByteArray>, payload: ByteArray) {
        if (closed.get()) return
        if (!queue.offer(payload)) {
            queue.poll()
            queue.offer(payload)
        }
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
