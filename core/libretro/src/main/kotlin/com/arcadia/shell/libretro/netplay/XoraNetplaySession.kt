package com.arcadia.shell.libretro.netplay

import com.arcadia.shell.datastore.MAX_NETPLAY_PORT
import com.arcadia.shell.datastore.MIN_NETPLAY_PORT
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.InetAddress
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
    val sessionCode: String = "",
    val online: Boolean = false,
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

data class JoinHostPort(val host: String, val port: Int)

/** Split `host` or `host:port` so join fields can be typed instead of octet-nudged. */
fun parseJoinHostPort(raw: String, fallbackPort: Int): JoinHostPort {
    val fallback = fallbackPort.coerceIn(MIN_NETPLAY_PORT, MAX_NETPLAY_PORT)
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return JoinHostPort("", fallback)
    val colon = trimmed.lastIndexOf(':')
    if (colon > 0 && colon < trimmed.lastIndex) {
        val host = trimmed.substring(0, colon).trim()
        val port = trimmed.substring(colon + 1).trim().toIntOrNull()
        if (host.isNotBlank() && !host.startsWith("[") && port != null) {
            return JoinHostPort(host, port.coerceIn(MIN_NETPLAY_PORT, MAX_NETPLAY_PORT))
        }
    }
    return JoinHostPort(trimmed, fallback)
}

fun formatJoinHostPort(host: String, port: Int): String {
    val h = host.trim()
    if (h.isBlank()) return ""
    return "$h:${port.coerceIn(MIN_NETPLAY_PORT, MAX_NETPLAY_PORT)}"
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
    private val generation = AtomicInteger(0)
    private var networkJob: Job? = null
    private var server: ServerSocket? = null
    private var socket: Socket? = null
    private val linkRef = AtomicReference<XoraNetplayLink?>(null)

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
        val gen = generation.get()
        running.set(true)
        isHost.set(true)
        val addresses = localIpv4Addresses()
        _state.value = XoraNetplayUiState(
            role = XoraNetplayRole.Host,
            status = hostStatus(addresses, port),
            localAddresses = addresses,
        )
        networkJob = scope.launch(Dispatchers.IO) {
            try {
                val listener = ServerSocket().also { server = it }
                listener.reuseAddress = true
                listener.bind(InetSocketAddress(InetAddress.getByName("0.0.0.0"), port), 8)
                listener.soTimeout = 500
                var client: Socket? = null
                while (isActive && running.get() && generation.get() == gen && client == null) {
                    client = try {
                        listener.accept()
                    } catch (_: SocketTimeoutException) {
                        null
                    }
                }
                val sock = client ?: return@launch
                socket = sock
                sock.tcpNoDelay = true
                val link = XoraTcpNetplayLink(sock).also { linkRef.set(it) }
                runHostHandshake(link, hello, captureState, gen)
                if (generation.get() != gen || !linked.get() || !running.get()) return@launch
                readLoop(link, gen)
            } catch (t: Throwable) {
                if (generation.get() == gen) fail(t.message ?: "Host failed", gen)
            }
        }
    }

    fun hostOnLink(
        link: XoraNetplayLink,
        hello: XoraNetplayProtocol.Hello,
        sessionCode: String,
        waitForPeer: suspend () -> Result<Unit>,
        captureState: suspend () -> ByteArray?,
    ) {
        stop()
        val gen = generation.get()
        running.set(true)
        isHost.set(true)
        linkRef.set(link)
        _state.value = XoraNetplayUiState(
            role = XoraNetplayRole.Host,
            status = "Code $sessionCode — waiting for a player",
            sessionCode = sessionCode,
            online = true,
        )
        networkJob = scope.launch(Dispatchers.IO) {
            try {
                waitForPeer().getOrElse { error ->
                    fail(error.message ?: "Nobody joined with that code.", gen)
                    return@launch
                }
                if (generation.get() != gen || !running.get()) return@launch
                _state.value = _state.value.copy(status = "Connecting…")
                runHostHandshake(link, hello, captureState, gen)
                if (generation.get() != gen || !linked.get() || !running.get()) return@launch
                readLoop(link, gen)
            } catch (t: Throwable) {
                if (generation.get() == gen) fail(t.message ?: "Host failed", gen)
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
        val gen = generation.get()
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
                sock.connect(InetSocketAddress(InetAddress.getByName(address.trim()), port), 12_000)
                sock.tcpNoDelay = true
                socket = sock
                val link = XoraTcpNetplayLink(sock).also { linkRef.set(it) }
                runJoinHandshake(link, hello, applyState, helloTimeoutMs = 12_000, gen = gen)
                if (generation.get() != gen || !linked.get() || !running.get()) return@launch
                readLoop(link, gen)
            } catch (t: Throwable) {
                if (generation.get() == gen) fail(joinFailureMessage(address, port, t), gen)
            }
        }
    }

    fun joinOnLink(
        link: XoraNetplayLink,
        hello: XoraNetplayProtocol.Hello,
        sessionCode: String,
        applyState: suspend (ByteArray) -> Boolean,
    ) {
        stop()
        val gen = generation.get()
        running.set(true)
        isHost.set(false)
        linkRef.set(link)
        _state.value = XoraNetplayUiState(
            role = XoraNetplayRole.Client,
            status = "Looking for session $sessionCode…",
            sessionCode = sessionCode,
            online = true,
        )
        networkJob = scope.launch(Dispatchers.IO) {
            try {
                runJoinHandshake(link, hello, applyState, helloTimeoutMs = 15_000, gen = gen)
                if (generation.get() != gen || !linked.get() || !running.get()) return@launch
                readLoop(link, gen)
            } catch (t: Throwable) {
                if (generation.get() == gen) fail(onlineJoinFailureMessage(t), gen)
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
        generation.incrementAndGet()
        running.set(false)
        linked.set(false)
        isHost.set(false)
        networkJob?.cancel()
        networkJob = null
        val link = linkRef.getAndSet(null)
        runCatching { link?.send(XoraNetplayProtocol.TYPE_BYE, ByteArray(0)) }
        runCatching { link?.close() }
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

    private suspend fun runHostHandshake(
        link: XoraNetplayLink,
        hello: XoraNetplayProtocol.Hello,
        captureState: suspend () -> ByteArray?,
        gen: Int,
    ) {
        link.send(XoraNetplayProtocol.TYPE_HELLO, XoraNetplayProtocol.encodeHello(hello))
        val (helloType, helloPayload) = link.receive(15_000)
        if (helloType != XoraNetplayProtocol.TYPE_HELLO) {
            fail("Peer did not send hello", gen)
            return
        }
        val peer = XoraNetplayProtocol.decodeHello(helloPayload)
        if (peer.version != XoraNetplayProtocol.VERSION) {
            fail("Netplay version mismatch", gen)
            return
        }
        val savestate = captureState()
        if (savestate == null) {
            fail("Could not capture save state", gen)
            return
        }
        link.send(XoraNetplayProtocol.TYPE_STATE, savestate)
        val (startType, _) = link.receive(30_000)
        if (startType != XoraNetplayProtocol.TYPE_START) {
            fail("Peer failed to start", gen)
            return
        }
        if (generation.get() != gen) return
        markLinked(peer.nickname, host = true)
    }

    private suspend fun runJoinHandshake(
        link: XoraNetplayLink,
        hello: XoraNetplayProtocol.Hello,
        applyState: suspend (ByteArray) -> Boolean,
        helloTimeoutMs: Int,
        gen: Int,
    ) {
        val (helloType, helloPayload) = link.receive(helloTimeoutMs)
        if (helloType != XoraNetplayProtocol.TYPE_HELLO) {
            fail("Host did not send hello", gen)
            return
        }
        val peer = XoraNetplayProtocol.decodeHello(helloPayload)
        if (peer.version != XoraNetplayProtocol.VERSION) {
            fail("Netplay version mismatch", gen)
            return
        }
        if (peer.coreName.isNotBlank() &&
            hello.coreName.isNotBlank() &&
            !peer.coreName.equals(hello.coreName, ignoreCase = true)
        ) {
            fail("Core mismatch (${peer.coreName})", gen)
            return
        }
        link.send(XoraNetplayProtocol.TYPE_HELLO, XoraNetplayProtocol.encodeHello(hello))
        val (stateType, statePayload) = link.receive(60_000)
        if (stateType != XoraNetplayProtocol.TYPE_STATE) {
            fail("Host did not send a save state", gen)
            return
        }
        val applied = applyState(statePayload)
        if (!applied) {
            fail("Could not load host save state", gen)
            return
        }
        if (generation.get() != gen) return
        link.send(XoraNetplayProtocol.TYPE_START, ByteArray(0))
        markLinked(peer.nickname, host = false)
    }

    private fun markLinked(peerName: String, host: Boolean) {
        frameCounter.set(0)
        pendingRemote.clear()
        linked.set(true)
        _state.value = _state.value.copy(
            linked = true,
            peerName = peerName,
            status = if (host) {
                "Playing with ${peerName.ifBlank { "P2" }}"
            } else {
                "Joined ${peerName.ifBlank { "host" }}"
            },
            error = null,
        )
    }

    private fun readLoop(link: XoraNetplayLink, gen: Int) {
        try {
            while (running.get() && generation.get() == gen) {
                val (type, payload) = link.receive()
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
                        fail(message, gen)
                        return
                    }
                }
            }
        } catch (t: Throwable) {
            if (generation.get() == gen) fail(t.message ?: "Connection lost", gen)
        }
    }

    private fun writeInput(frame: XoraNetplayProtocol.PadFrame) {
        val link = linkRef.get() ?: return
        runCatching {
            link.send(
                XoraNetplayProtocol.TYPE_INPUT,
                XoraNetplayProtocol.encodePadFrame(frame),
            )
        }
    }

    private fun fail(message: String, gen: Int) {
        if (generation.get() != gen) return
        linked.set(false)
        running.set(false)
        _state.value = _state.value.copy(
            linked = false,
            status = "Disconnected",
            error = message,
        )
        runCatching { linkRef.get()?.close() }
        runCatching { socket?.close() }
        runCatching { server?.close() }
    }

    companion object {
        private const val INPUT_WAIT_NS = 40_000_000L // 40ms

        fun localIpv4Addresses(): List<String> = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .sortedBy { iface ->
                    val n = iface.name.lowercase()
                    when {
                        n.startsWith("wlan") || n.startsWith("ap") || n.startsWith("wifi") -> 0
                        n.startsWith("eth") -> 1
                        n.startsWith("rmnet") || n.startsWith("ccmni") || n.startsWith("wwan") -> 3
                        n.contains("vpn") || n.startsWith("tun") || n.startsWith("ppp") -> 4
                        else -> 2
                    }
                }
                .flatMap { iface ->
                    iface.inetAddresses.toList()
                        .filterIsInstance<Inet4Address>()
                        .filter { !it.isLoopbackAddress && !it.isLinkLocalAddress }
                        .mapNotNull { it.hostAddress }
                }
                .distinct()
        }.getOrDefault(emptyList())

        private fun hostStatus(addresses: List<String>, port: Int): String {
            if (addresses.isEmpty()) return "Hosting on port $port"
            return "Hosting · " + addresses.take(2).joinToString(" · ") { "$it:$port" }
        }

        fun joinFailureMessage(address: String, port: Int, error: Throwable): String {
            val raw = error.message.orEmpty()
            val target = "$address:$port"
            return when {
                raw.contains("ECONNREFUSED", ignoreCase = true) ||
                    raw.contains("Connection refused", ignoreCase = true) ->
                    "Nothing listening on $target. Host must tap Host session first, same Wi‑Fi, same port."
                raw.contains("EPERM", ignoreCase = true) ||
                    raw.contains("failed to connect", ignoreCase = true) ||
                    error is SocketTimeoutException ||
                    error is java.net.ConnectException ->
                    "Couldn't reach $target. Allow Nearby devices / local network, stay on the same Wi‑Fi, and match the host port."
                else -> raw.ifBlank { "Join failed" }
            }
        }

        fun onlineJoinFailureMessage(error: Throwable): String {
            val raw = error.message.orEmpty()
            return when {
                error is SocketTimeoutException ||
                    raw.contains("Timed out", ignoreCase = true) ->
                    "No host for that code. Check the 6 characters, and make sure they tapped Host online."
                raw.contains("left", ignoreCase = true) ->
                    "The other player left."
                raw.isBlank() -> "Couldn't join that online session."
                else -> raw
            }
        }
    }
}
