package com.arcadia.shell.libretro.netplay

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

enum class XoraNetplayRole { Idle, Host, Client }

data class XoraNetplayUiState(
    val role: XoraNetplayRole = XoraNetplayRole.Idle,
    val linked: Boolean = false,
    val peerName: String = "",
    val status: String = "Off",
    val localAddresses: List<String> = emptyList(),
    val error: String? = null,
)

fun parseIpv4(address: String): IntArray {
    val parts = address.trim().split('.')
    val out = IntArray(4)
    repeat(4) { i ->
        out[i] = parts.getOrNull(i)?.toIntOrNull()?.coerceIn(0, 255) ?: 0
    }
    return out
}

fun formatIpv4(octets: IntArray): String =
    octets.take(4).joinToString(".") { it.coerceIn(0, 255).toString() }

fun nudgeIpv4(address: String, octetIndex: Int, delta: Int): String {
    val parts = parseIpv4(address)
    val i = octetIndex.coerceIn(0, 3)
    parts[i] = (parts[i] + delta).mod(256)
    return formatIpv4(parts)
}

/**
 * RetroArch-style host/join: TCP handshake + savestate, then per-frame pad exchange.
 *
 * The emu thread calls [exchange] once per frame; a dedicated IO coroutine owns the socket.
 */
class XoraNetplaySession(
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(XoraNetplayUiState())
    val state: StateFlow<XoraNetplayUiState> = _state.asStateFlow()

    private val running = AtomicBoolean(false)
    private val linked = AtomicBoolean(false)
    private val isHost = AtomicBoolean(false)
    private var networkJob: Job? = null
    private var server: ServerSocket? = null
    private var socket: Socket? = null
    private val output = AtomicReference<DataOutputStream?>(null)

    private val pendingRemote = ConcurrentHashMap<Int, XoraNetplayProtocol.PadFrame>()
    private val lastRemote = AtomicReference(XoraNetplayProtocol.PadFrame(frame = -1, buttons = 0))
    private val frameCounter = AtomicInteger(0)

    val linkedNow: Boolean get() = linked.get()
    val hosting: Boolean get() = isHost.get() && running.get()

    fun host(
        port: Int,
        hello: XoraNetplayProtocol.Hello,
        captureState: suspend () -> ByteArray?,
    ) {
        stop()
        running.set(true)
        isHost.set(true)
        val addresses = localIpv4Addresses()
        _state.value = XoraNetplayUiState(
            role = XoraNetplayRole.Host,
            status = "Hosting on ${addresses.firstOrNull() ?: "0.0.0.0"}:$port",
            localAddresses = addresses,
        )
        networkJob = scope.launch(Dispatchers.IO) {
            try {
                val listener = ServerSocket(port).also { server = it }
                listener.soTimeout = 500
                var client: Socket? = null
                while (isActive && running.get() && client == null) {
                    client = try {
                        listener.accept()
                    } catch (_: SocketTimeoutException) {
                        null
                    }
                }
                val sock = client ?: return@launch
                socket = sock
                sock.tcpNoDelay = true
                val input = DataInputStream(BufferedInputStream(sock.getInputStream()))
                val out = DataOutputStream(BufferedOutputStream(sock.getOutputStream())).also {
                    output.set(it)
                }
                XoraNetplayProtocol.writeMessage(
                    out,
                    XoraNetplayProtocol.TYPE_HELLO,
                    XoraNetplayProtocol.encodeHello(hello),
                )
                val (helloType, helloPayload) = XoraNetplayProtocol.readMessage(input)
                if (helloType != XoraNetplayProtocol.TYPE_HELLO) {
                    fail("Peer did not send hello")
                    return@launch
                }
                val peer = XoraNetplayProtocol.decodeHello(helloPayload)
                if (peer.version != XoraNetplayProtocol.VERSION) {
                    fail("Netplay version mismatch")
                    return@launch
                }
                val savestate = captureState()
                if (savestate == null) {
                    fail("Could not capture save state")
                    return@launch
                }
                XoraNetplayProtocol.writeMessage(out, XoraNetplayProtocol.TYPE_STATE, savestate)
                val (startType, _) = XoraNetplayProtocol.readMessage(input)
                if (startType != XoraNetplayProtocol.TYPE_START) {
                    fail("Peer failed to start")
                    return@launch
                }
                frameCounter.set(0)
                pendingRemote.clear()
                linked.set(true)
                _state.value = _state.value.copy(
                    linked = true,
                    peerName = peer.nickname,
                    status = "Playing with ${peer.nickname.ifBlank { "P2" }}",
                    error = null,
                )
                readLoop(input)
            } catch (t: Throwable) {
                if (running.get()) fail(t.message ?: "Host failed")
            } finally {
                if (running.get() && isHost.get() && !linked.get()) {
                    // Keep listening status if we dropped before a peer arrived.
                }
            }
        }
    }

    fun join(
        address: String,
        port: Int,
        hello: XoraNetplayProtocol.Hello,
        applyState: suspend (ByteArray) -> Boolean,
    ) {
        stop()
        running.set(true)
        isHost.set(false)
        _state.value = XoraNetplayUiState(
            role = XoraNetplayRole.Client,
            status = "Connecting to $address:$port…",
            localAddresses = localIpv4Addresses(),
        )
        networkJob = scope.launch(Dispatchers.IO) {
            try {
                val sock = Socket()
                sock.connect(InetSocketAddress(address.trim(), port), 8_000)
                sock.tcpNoDelay = true
                socket = sock
                val input = DataInputStream(BufferedInputStream(sock.getInputStream()))
                val out = DataOutputStream(BufferedOutputStream(sock.getOutputStream())).also {
                    output.set(it)
                }
                val (helloType, helloPayload) = XoraNetplayProtocol.readMessage(input)
                if (helloType != XoraNetplayProtocol.TYPE_HELLO) {
                    fail("Host did not send hello")
                    return@launch
                }
                val peer = XoraNetplayProtocol.decodeHello(helloPayload)
                if (peer.version != XoraNetplayProtocol.VERSION) {
                    fail("Netplay version mismatch")
                    return@launch
                }
                if (peer.coreName.isNotBlank() &&
                    hello.coreName.isNotBlank() &&
                    !peer.coreName.equals(hello.coreName, ignoreCase = true)
                ) {
                    fail("Core mismatch (${peer.coreName})")
                    return@launch
                }
                XoraNetplayProtocol.writeMessage(
                    out,
                    XoraNetplayProtocol.TYPE_HELLO,
                    XoraNetplayProtocol.encodeHello(hello),
                )
                val (stateType, statePayload) = XoraNetplayProtocol.readMessage(input)
                if (stateType != XoraNetplayProtocol.TYPE_STATE) {
                    fail("Host did not send a save state")
                    return@launch
                }
                val applied = applyState(statePayload)
                if (!applied) {
                    fail("Could not load host save state")
                    return@launch
                }
                XoraNetplayProtocol.writeMessage(out, XoraNetplayProtocol.TYPE_START, ByteArray(0))
                frameCounter.set(0)
                pendingRemote.clear()
                linked.set(true)
                _state.value = _state.value.copy(
                    linked = true,
                    peerName = peer.nickname,
                    status = "Joined ${peer.nickname.ifBlank { "host" }}",
                    error = null,
                )
                readLoop(input)
            } catch (t: Throwable) {
                if (running.get()) fail(t.message ?: "Join failed")
            }
        }
    }

    /**
     * Send local pad for this frame and return the remote pad. Safe to call from the emu thread.
     * Times out quickly and repeats the last remote input so a lag spike cannot freeze the core.
     */
    fun exchange(local: XoraNetplayProtocol.PadFrame): XoraNetplayProtocol.PadFrame {
        if (!linked.get()) return XoraNetplayProtocol.PadFrame(frame = local.frame, buttons = 0)
        val frame = local.frame
        writeInput(local)
        val deadline = System.nanoTime() + INPUT_WAIT_NS
        while (System.nanoTime() < deadline) {
            pendingRemote.remove(frame)?.let {
                lastRemote.set(it)
                return it
            }
            Thread.yield()
        }
        return lastRemote.get()
    }

    fun nextFrameIndex(): Int = frameCounter.getAndIncrement()

    fun stop() {
        running.set(false)
        linked.set(false)
        isHost.set(false)
        networkJob?.cancel()
        networkJob = null
        runCatching {
            output.get()?.let {
                XoraNetplayProtocol.writeMessage(it, XoraNetplayProtocol.TYPE_BYE, ByteArray(0))
            }
        }
        output.set(null)
        runCatching { socket?.close() }
        socket = null
        runCatching { server?.close() }
        server = null
        pendingRemote.clear()
        frameCounter.set(0)
        _state.value = XoraNetplayUiState(
            status = "Off",
            localAddresses = localIpv4Addresses(),
        )
    }

    private suspend fun readLoop(input: DataInputStream) {
        try {
            while (running.get()) {
                val (type, payload) = XoraNetplayProtocol.readMessage(input)
                when (type) {
                    XoraNetplayProtocol.TYPE_INPUT -> {
                        val pad = XoraNetplayProtocol.decodePadFrame(payload)
                        pendingRemote[pad.frame] = pad
                        if (pendingRemote.size > 64) {
                            val minKeep = pad.frame - 32
                            pendingRemote.keys.filter { it < minKeep }.forEach { pendingRemote.remove(it) }
                        }
                    }
                    XoraNetplayProtocol.TYPE_BYE, XoraNetplayProtocol.TYPE_ERROR -> {
                        val message = if (type == XoraNetplayProtocol.TYPE_ERROR) {
                            String(payload)
                        } else {
                            "Peer disconnected"
                        }
                        fail(message)
                        return
                    }
                }
            }
        } catch (t: Throwable) {
            if (running.get()) fail(t.message ?: "Connection lost")
        }
    }

    private fun writeInput(frame: XoraNetplayProtocol.PadFrame) {
        val out = output.get() ?: return
        runCatching {
            synchronized(out) {
                XoraNetplayProtocol.writeMessage(
                    out,
                    XoraNetplayProtocol.TYPE_INPUT,
                    XoraNetplayProtocol.encodePadFrame(frame),
                )
            }
        }
    }

    private fun fail(message: String) {
        linked.set(false)
        running.set(false)
        _state.value = _state.value.copy(
            linked = false,
            status = "Disconnected",
            error = message,
        )
        runCatching { socket?.close() }
        runCatching { server?.close() }
    }

    companion object {
        private const val INPUT_WAIT_NS = 40_000_000L // 40ms

        fun localIpv4Addresses(): List<String> = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .filter { !it.isLoopbackAddress }
                .mapNotNull { it.hostAddress }
                .distinct()
        }.getOrDefault(emptyList())
    }
}
