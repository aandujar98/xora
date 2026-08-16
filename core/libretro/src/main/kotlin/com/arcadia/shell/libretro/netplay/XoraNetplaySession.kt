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
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicIntegerArray
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.LockSupport

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
    /** This device's player number: host is always 1, joiners get 2..4 in join order. */
    val playerSlot: Int = 0,
    /** Players currently in the session (including this device). */
    val playerCount: Int = 0,
    /** XOrA Network usernames by slot (1 = host) for everyone in the session. */
    val playerNames: Map<Int, String> = emptyMap(),
)

/** Pads to apply this frame after netplay delay; index = libretro port (slot − 1). */
data class XoraNetplayExchange(
    val pads: List<XoraNetplayProtocol.PadFrame>,
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
 * RetroArch-style host/join: handshake + savestate, then per-frame pad exchange.
 *
 * The host is always Player 1; each joiner is assigned the next free slot (2..4 online,
 * 2 on LAN TCP). Every join re-syncs all devices from a fresh host savestate behind an
 * epoch barrier, so pad frames from before a resync can never be confused with new ones.
 *
 * The emu thread calls [exchange] once per frame; a dedicated IO coroutine owns the link.
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

    private val mySlot = AtomicInteger(0)
    private val epoch = AtomicInteger(0)
    private val activeSlotsMask = AtomicInteger(0)
    private val maxPlayersNow = AtomicInteger(2)
    private val hostName = AtomicReference("")
    private val peerNames = ConcurrentHashMap<Int, String>()
    private val joinerSlots: MutableSet<Int> = ConcurrentHashMap.newKeySet()
    private val parkedHellos = ArrayDeque<XoraNetplayProtocol.Hello>()

    /** Remote pads per slot, keyed by (epoch << 32 | frame) so resyncs can't collide. */
    private val pendingRemote = Array(XoraNetplayProtocol.MAX_PLAYERS) {
        ConcurrentHashMap<Long, XoraNetplayProtocol.PadFrame>()
    }
    private val lastRemote = Array(XoraNetplayProtocol.MAX_PLAYERS) {
        AtomicReference(EMPTY_PAD)
    }
    private val missStreak = AtomicIntegerArray(XoraNetplayProtocol.MAX_PLAYERS)
    private val lastConsumed = AtomicIntegerArray(XoraNetplayProtocol.MAX_PLAYERS)
    private val pendingLocal = ConcurrentHashMap<Int, XoraNetplayProtocol.PadFrame>()
    private val lastLocal = AtomicReference(EMPTY_PAD)
    private val frameCounter = AtomicInteger(0)
    private val inputDelay = AtomicInteger(0)
    private val freezeCore = AtomicBoolean(false)

    private val captureStateRef = AtomicReference<(suspend () -> ByteArray?)?>(null)
    private val applyStateRef = AtomicReference<(suspend (ByteArray) -> Boolean)?>(null)
    /** Host hello reused when admitting late joiners from the read loop. */
    private val lastSelfHello = AtomicReference<XoraNetplayProtocol.Hello?>(null)
    /** Token of this joiner's in-flight seat-change request (0 = none). */
    private val pendingSeatToken = AtomicInteger(0)

    val linkedNow: Boolean get() = linked.get()
    val hosting: Boolean get() = isHost.get() && running.get()
    /** True while a savestate is in flight — the core must not advance. */
    val holdEmulation: Boolean get() = freezeCore.get()

    fun host(
        port: Int,
        hello: XoraNetplayProtocol.Hello,
        captureState: suspend () -> ByteArray?,
    ) {
        stop()
        val gen = generation.get()
        beginHostState(online = false, maxPlayers = 2, hello = hello, captureState = captureState)
        val addresses = localIpv4Addresses()
        _state.value = XoraNetplayUiState(
            role = XoraNetplayRole.Host,
            status = hostStatus(addresses, port),
            localAddresses = addresses,
            playerSlot = 1,
            playerCount = 1,
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
                runHostHandshake(link, hello, gen)
                if (generation.get() != gen || !running.get()) return@launch
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
        beginHostState(
            online = true,
            maxPlayers = XoraNetplayProtocol.MAX_PLAYERS,
            hello = hello,
            captureState = captureState,
        )
        inputDelay.set(ONLINE_INPUT_DELAY)
        linkRef.set(link)
        _state.value = XoraNetplayUiState(
            role = XoraNetplayRole.Host,
            status = "Code $sessionCode — waiting for a player",
            sessionCode = sessionCode,
            online = true,
            playerSlot = 1,
            playerCount = 1,
        )
        networkJob = scope.launch(Dispatchers.IO) {
            try {
                waitForPeer().getOrElse { error ->
                    fail(error.message ?: "Nobody joined with that code.", gen)
                    return@launch
                }
                if (generation.get() != gen || !running.get()) return@launch
                _state.value = _state.value.copy(status = "Connecting…")
                runHostHandshake(link, hello, gen)
                if (generation.get() != gen || !running.get()) return@launch
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
        beginJoinState(applyState)
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
                runJoinHandshake(link, hello, helloTimeoutMs = 20_000, gen = gen)
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
        beginJoinState(applyState)
        inputDelay.set(ONLINE_INPUT_DELAY)
        linkRef.set(link)
        _state.value = XoraNetplayUiState(
            role = XoraNetplayRole.Client,
            status = "Looking for session $sessionCode…",
            sessionCode = sessionCode,
            online = true,
        )
        networkJob = scope.launch(Dispatchers.IO) {
            try {
                runJoinHandshake(link, hello, helloTimeoutMs = 25_000, gen = gen)
                if (generation.get() != gen || !linked.get() || !running.get()) return@launch
                readLoop(link, gen)
            } catch (t: Throwable) {
                if (generation.get() == gen) fail(onlineJoinFailureMessage(t), gen)
            }
        }
    }

    /**
     * Send the current local pad and return the pads every port must apply this frame.
     *
     * Online buffers [ONLINE_INPUT_DELAY] frames so RTT does not hitch, then waits for the
     * matching remote pads of that delayed frame. A slot that misses its window briefly holds
     * its last pad; a slot silent for [ZERO_AFTER_MISSES] frames goes neutral, and one silent
     * for [STALL_SKIP_FRAMES] frames stops being waited on until its input reappears.
     */
    fun exchange(local: XoraNetplayProtocol.PadFrame): XoraNetplayExchange {
        val slot = mySlot.get().coerceIn(1, XoraNetplayProtocol.MAX_PLAYERS)
        val currentEpoch = epoch.get()
        val idle = XoraNetplayProtocol.PadFrame(frame = local.frame, buttons = 0)
        val pads = Array(XoraNetplayProtocol.MAX_PLAYERS) { idle }
        if (!linked.get()) return XoraNetplayExchange(pads.toList())
        val frame = local.frame
        val tagged = local.copy(slot = slot, epoch = currentEpoch)
        pendingLocal[frame] = tagged
        writeInput(tagged)
        trimLocal(frame)
        val delay = inputDelay.get().coerceAtLeast(0)
        val target = frame - delay
        if (target < 0) {
            return XoraNetplayExchange(pads.toList())
        }
        val delayedLocal = pendingLocal[target]
            ?: lastLocal.get().takeIf { it.frame >= 0 }
            ?: idle
        lastLocal.set(delayedLocal)
        pads[slot - 1] = delayedLocal
        collectRemotePads(pads, currentEpoch, target, slot, idle)
        return XoraNetplayExchange(pads.toList())
    }

    fun nextFrameIndex(): Int = frameCounter.getAndIncrement()

    /**
     * Ask the host to move this joiner to [requestedSlot] (2..4). The host answers with an
     * ASSIGN (seat granted or taken) and, when granted, a savestate barrier re-seats everyone.
     */
    fun requestSeat(requestedSlot: Int) {
        if (isHost.get() || !linked.get() || !running.get()) return
        val current = mySlot.get()
        if (requestedSlot == current || requestedSlot !in 2..XoraNetplayProtocol.MAX_PLAYERS) return
        val link = linkRef.get() ?: return
        val token = generateJoinToken()
        pendingSeatToken.set(token)
        _state.value = _state.value.copy(status = "Asking the host for Player $requestedSlot…")
        runCatching {
            link.send(
                XoraNetplayProtocol.TYPE_SEAT,
                XoraNetplayProtocol.encodeSeat(token, current, requestedSlot),
            )
        }
    }

    fun stop() {
        val leavingSlot = mySlot.get()
        generation.incrementAndGet()
        running.set(false)
        linked.set(false)
        isHost.set(false)
        networkJob?.cancel()
        networkJob = null
        val link = linkRef.getAndSet(null)
        if (leavingSlot >= 1) {
            // Unassigned joiners must stay silent: BYE(0) would read as "the host left".
            runCatching {
                link?.send(
                    XoraNetplayProtocol.TYPE_BYE,
                    XoraNetplayProtocol.encodeBye(leavingSlot),
                )
            }
        }
        runCatching { link?.close() }
        runCatching { socket?.close() }
        socket = null
        runCatching { server?.close() }
        server = null
        resetPadBuffers()
        mySlot.set(0)
        epoch.set(0)
        activeSlotsMask.set(0)
        peerNames.clear()
        joinerSlots.clear()
        parkedHellos.clear()
        hostName.set("")
        frameCounter.set(0)
        inputDelay.set(0)
        freezeCore.set(false)
        captureStateRef.set(null)
        applyStateRef.set(null)
        lastSelfHello.set(null)
        pendingSeatToken.set(0)
        _state.value = XoraNetplayUiState(
            status = "Off",
            localAddresses = localIpv4Addresses(),
        )
    }

    private fun beginHostState(
        online: Boolean,
        maxPlayers: Int,
        hello: XoraNetplayProtocol.Hello,
        captureState: suspend () -> ByteArray?,
    ) {
        running.set(true)
        isHost.set(true)
        mySlot.set(1)
        maxPlayersNow.set(maxPlayers.coerceIn(2, XoraNetplayProtocol.MAX_PLAYERS))
        inputDelay.set(if (online) ONLINE_INPUT_DELAY else 0)
        captureStateRef.set(captureState)
        lastSelfHello.set(hello)
    }

    private fun beginJoinState(applyState: suspend (ByteArray) -> Boolean) {
        running.set(true)
        isHost.set(false)
        inputDelay.set(0)
        freezeCore.set(true)
        applyStateRef.set(applyState)
    }

    private fun resetPadBuffers() {
        pendingRemote.forEach { it.clear() }
        lastRemote.forEach { it.set(EMPTY_PAD) }
        for (i in 0 until XoraNetplayProtocol.MAX_PLAYERS) {
            missStreak.set(i, 0)
            lastConsumed.set(i, -1)
        }
        pendingLocal.clear()
        lastLocal.set(EMPTY_PAD)
    }

    // ---------------------------------------------------------------------------------------
    // Host side
    // ---------------------------------------------------------------------------------------

    /** Waits for the first joiner HELLO, then runs the shared admission barrier. */
    private suspend fun runHostHandshake(
        link: XoraNetplayLink,
        selfHello: XoraNetplayProtocol.Hello,
        gen: Int,
    ) {
        val deadline = System.currentTimeMillis() + 30_000L
        while (running.get() && generation.get() == gen) {
            val remaining = (deadline - System.currentTimeMillis()).toInt()
            if (remaining <= 0) {
                fail("Peer did not send hello", gen)
                return
            }
            val (type, payload) = receiveControl(link, remaining)
            if (type != XoraNetplayProtocol.TYPE_HELLO) continue
            val peer = XoraNetplayProtocol.decodeHello(payload)
            if (peer.token == 0) continue // own broadcast echo
            admitJoiner(link, selfHello, peer, gen)
            drainParkedHellos(link, selfHello, gen)
            return
        }
    }

    /**
     * Admission barrier: assign a slot, broadcast a fresh savestate, wait for every joiner's
     * START, then GO with a new epoch so all devices restart lockstep from frame 0 together.
     */
    private suspend fun admitJoiner(
        link: XoraNetplayLink,
        selfHello: XoraNetplayProtocol.Hello,
        peer: XoraNetplayProtocol.Hello,
        gen: Int,
    ) {
        // Token 0 is the host's own hello — Nakama can echo it back with a blank sender.
        if (peer.token == 0) return
        if (peer.version != XoraNetplayProtocol.VERSION) {
            runCatching {
                link.send(
                    XoraNetplayProtocol.TYPE_ASSIGN,
                    XoraNetplayProtocol.encodeAssign(
                        peer.token,
                        0,
                        XoraNetplayProtocol.REJECT_VERSION,
                    ),
                )
            }
            if (joinerSlots.isEmpty()) {
                fail("Netplay version mismatch — both devices need the same XOrA build", gen)
            }
            return
        }
        if (peer.coreName.isNotBlank() &&
            selfHello.coreName.isNotBlank() &&
            !peer.coreName.equals(selfHello.coreName, ignoreCase = true)
        ) {
            runCatching {
                link.send(
                    XoraNetplayProtocol.TYPE_ASSIGN,
                    XoraNetplayProtocol.encodeAssign(peer.token, 0, XoraNetplayProtocol.REJECT_CORE),
                )
            }
            if (joinerSlots.isEmpty()) fail("Core mismatch (${peer.coreName})", gen)
            return
        }
        val slot = nextFreeSlot()
        if (slot == null) {
            runCatching {
                link.send(
                    XoraNetplayProtocol.TYPE_ASSIGN,
                    XoraNetplayProtocol.encodeAssign(peer.token, 0, XoraNetplayProtocol.REJECT_FULL),
                )
            }
            return
        }
        freezeCore.set(true)
        _state.value = _state.value.copy(
            status = "Syncing ${peer.nickname.ifBlank { "Player $slot" }}…",
        )
        link.send(XoraNetplayProtocol.TYPE_HELLO, XoraNetplayProtocol.encodeHello(selfHello))
        link.send(
            XoraNetplayProtocol.TYPE_ASSIGN,
            XoraNetplayProtocol.encodeAssign(peer.token, slot),
        )
        val savestate = captureStateRef.get()?.invoke()
        if (savestate == null) {
            fail("Could not capture save state", gen)
            return
        }
        link.send(XoraNetplayProtocol.TYPE_STATE, savestate)

        val expected = (joinerSlots + slot).toMutableSet()
        val confirmed = collectStartBarrier(link, gen, expected)
        if (generation.get() != gen || !running.get()) return

        // Anyone who never confirmed the barrier is dropped so the game does not hang on them.
        (expected - confirmed).forEach { lost ->
            joinerSlots.remove(lost)
            peerNames.remove(lost)
        }
        if (slot in confirmed) {
            joinerSlots.add(slot)
            peerNames[slot] = peer.nickname.ifBlank { "Player $slot" }
        }
        if (joinerSlots.isEmpty()) {
            softUnlink("Nobody finished joining — still open")
            return
        }
        val newEpoch = (epoch.get() + 1) and 0xFF
        val mask = XoraNetplayProtocol.slotsMaskOf(joinerSlots + 1)
        link.send(
            XoraNetplayProtocol.TYPE_GO,
            XoraNetplayProtocol.encodeGo(newEpoch, mask, sessionRoster()),
        )
        applyGo(newEpoch, mask)
        refreshLinkedState()
    }

    /** Slot → XOrA username roster the host broadcasts with every GO. */
    private fun sessionRoster(): Map<Int, String> = buildMap {
        lastSelfHello.get()?.nickname?.takeIf { it.isNotBlank() }?.let { put(1, it) }
        peerNames.forEach { (slot, name) -> if (name.isNotBlank()) put(slot, name) }
    }

    /** Wait for START from every slot in [expected]; parks HELLOs, honors BYEs. */
    private suspend fun collectStartBarrier(
        link: XoraNetplayLink,
        gen: Int,
        expected: MutableSet<Int>,
    ): MutableSet<Int> {
        val confirmed = mutableSetOf<Int>()
        val deadline = System.currentTimeMillis() + START_BARRIER_TIMEOUT_MS
        while (confirmed != expected &&
            expected.isNotEmpty() &&
            running.get() &&
            generation.get() == gen
        ) {
            val remaining = (deadline - System.currentTimeMillis()).toInt()
            if (remaining <= 0) break
            val (type, payload) = try {
                receiveControl(link, remaining)
            } catch (_: SocketTimeoutException) {
                break
            }
            when (type) {
                XoraNetplayProtocol.TYPE_START -> {
                    val startSlot = XoraNetplayProtocol.decodeStartSlot(payload)
                    // Legacy empty START means the single joiner in a 1v1 handshake.
                    val resolved = if (startSlot == 0 && expected.size == 1) {
                        expected.first()
                    } else {
                        startSlot
                    }
                    if (resolved in expected) confirmed += resolved
                }
                XoraNetplayProtocol.TYPE_HELLO ->
                    parkedHellos.addLast(XoraNetplayProtocol.decodeHello(payload))
                XoraNetplayProtocol.TYPE_BYE -> {
                    val gone = XoraNetplayProtocol.decodeByeSlot(payload)
                    if (gone != mySlot.get()) {
                        expected.remove(gone)
                        confirmed.remove(gone)
                        joinerSlots.remove(gone)
                        peerNames.remove(gone)
                    }
                }
                else -> Unit
            }
        }
        return confirmed
    }

    /**
     * Joiner asked for a different seat: re-map their slot, then run the same savestate
     * barrier as a join so every device flips port mapping at the same frame.
     */
    private suspend fun handleSeatRequest(link: XoraNetplayLink, payload: ByteArray, gen: Int) {
        val seat = runCatching { XoraNetplayProtocol.decodeSeat(payload) }.getOrNull() ?: return
        val from = seat.currentSlot
        val want = seat.requestedSlot
        val movable = from in joinerSlots &&
            want in 2..maxPlayersNow.get() &&
            want != from &&
            want !in joinerSlots
        if (!movable) {
            runCatching {
                link.send(
                    XoraNetplayProtocol.TYPE_ASSIGN,
                    XoraNetplayProtocol.encodeAssign(seat.token, 0, XoraNetplayProtocol.REJECT_FULL),
                )
            }
            return
        }
        val name = peerNames.remove(from) ?: "Player $want"
        joinerSlots.remove(from)
        joinerSlots.add(want)
        peerNames[want] = name
        freezeCore.set(true)
        _state.value = _state.value.copy(status = "Moving $name to Player $want…")
        link.send(
            XoraNetplayProtocol.TYPE_ASSIGN,
            XoraNetplayProtocol.encodeAssign(seat.token, want),
        )
        val savestate = captureStateRef.get()?.invoke()
        if (savestate == null) {
            fail("Could not capture save state", gen)
            return
        }
        link.send(XoraNetplayProtocol.TYPE_STATE, savestate)
        val expected = joinerSlots.toMutableSet()
        val confirmed = collectStartBarrier(link, gen, expected)
        if (generation.get() != gen || !running.get()) return
        (expected - confirmed).forEach { lost ->
            joinerSlots.remove(lost)
            peerNames.remove(lost)
        }
        if (joinerSlots.isEmpty()) {
            softUnlink("Everyone left — code ${_state.value.sessionCode.ifBlank { "open" }} still live")
            return
        }
        val newEpoch = (epoch.get() + 1) and 0xFF
        val mask = XoraNetplayProtocol.slotsMaskOf(joinerSlots + 1)
        link.send(
            XoraNetplayProtocol.TYPE_GO,
            XoraNetplayProtocol.encodeGo(newEpoch, mask, sessionRoster()),
        )
        applyGo(newEpoch, mask)
        refreshLinkedState()
    }

    private suspend fun drainParkedHellos(
        link: XoraNetplayLink,
        selfHello: XoraNetplayProtocol.Hello,
        gen: Int,
    ) {
        while (running.get() && generation.get() == gen) {
            val next = parkedHellos.removeFirstOrNull() ?: return
            admitJoiner(link, selfHello, next, gen)
        }
    }

    private fun nextFreeSlot(): Int? =
        (2..maxPlayersNow.get()).firstOrNull { it !in joinerSlots }

    /** Host keeps the lobby open with no joiners; play continues single-player. */
    private fun softUnlink(status: String) {
        linked.set(false)
        freezeCore.set(false)
        joinerSlots.clear()
        peerNames.clear()
        activeSlotsMask.set(XoraNetplayProtocol.slotsMaskOf(listOf(1)))
        _state.value = _state.value.copy(
            linked = false,
            peerName = "",
            status = status,
            playerCount = 1,
            playerNames = sessionRoster(),
        )
    }

    // ---------------------------------------------------------------------------------------
    // Joiner side
    // ---------------------------------------------------------------------------------------

    private suspend fun runJoinHandshake(
        link: XoraNetplayLink,
        hello: XoraNetplayProtocol.Hello,
        helloTimeoutMs: Int,
        gen: Int,
    ) {
        val token = generateJoinToken()
        link.send(
            XoraNetplayProtocol.TYPE_HELLO,
            XoraNetplayProtocol.encodeHello(hello.copy(token = token)),
        )
        var host: XoraNetplayProtocol.Hello? = null
        var slot = 0
        var statePayload: ByteArray? = null
        var deadline = System.currentTimeMillis() + helloTimeoutMs
        while (statePayload == null) {
            if (generation.get() != gen || !running.get()) return
            val remaining = (deadline - System.currentTimeMillis()).toInt()
            if (remaining <= 0) {
                fail("Timed out waiting for the host", gen)
                return
            }
            val (type, payload) = receiveControl(link, remaining)
            when (type) {
                XoraNetplayProtocol.TYPE_HELLO -> {
                    val decoded = XoraNetplayProtocol.decodeHello(payload)
                    if (decoded.token == token) continue // own broadcast echo
                    if (decoded.version != XoraNetplayProtocol.VERSION) {
                        fail("Netplay version mismatch — both devices need the same XOrA build", gen)
                        return
                    }
                    if (decoded.coreName.isNotBlank() &&
                        hello.coreName.isNotBlank() &&
                        !decoded.coreName.equals(hello.coreName, ignoreCase = true)
                    ) {
                        fail("Core mismatch (${decoded.coreName})", gen)
                        return
                    }
                    host = decoded
                }
                XoraNetplayProtocol.TYPE_ASSIGN -> {
                    val assign = XoraNetplayProtocol.decodeAssign(payload)
                    if (assign.token != token) continue // another joiner's slot
                    if (assign.slot == 0) {
                        fail(
                            when (assign.reason) {
                                XoraNetplayProtocol.REJECT_VERSION ->
                                    "Netplay version mismatch — both devices need the same XOrA build"
                                XoraNetplayProtocol.REJECT_CORE ->
                                    "The host is playing with a different core."
                                else -> "That session is full."
                            },
                            gen,
                        )
                        return
                    }
                    slot = assign.slot
                    // The savestate download can be large; give it its own window.
                    deadline = maxOf(deadline, System.currentTimeMillis() + STATE_DOWNLOAD_TIMEOUT_MS)
                }
                XoraNetplayProtocol.TYPE_STATE -> {
                    // A state broadcast before our ASSIGN belongs to another joiner's barrier.
                    if (slot != 0) statePayload = payload
                }
                else -> Unit
            }
        }
        mySlot.set(slot)
        hostName.set(host?.nickname.orEmpty())
        freezeCore.set(true)
        _state.value = _state.value.copy(status = "Loading host game…")
        val applied = applyStateRef.get()?.invoke(statePayload) ?: false
        if (!applied) {
            fail("Could not load host save state", gen)
            return
        }
        if (generation.get() != gen) return
        link.send(XoraNetplayProtocol.TYPE_START, XoraNetplayProtocol.encodeStart(slot))
        awaitGo(link, gen, timeoutMs = START_BARRIER_TIMEOUT_MS)
    }

    /** Wait for GO; a fresh STATE mid-wait means another joiner triggered a newer barrier. */
    private suspend fun awaitGo(link: XoraNetplayLink, gen: Int, timeoutMs: Long) {
        var deadline = System.currentTimeMillis() + timeoutMs
        while (running.get() && generation.get() == gen) {
            val remaining = (deadline - System.currentTimeMillis()).toInt()
            if (remaining <= 0) {
                fail("Host did not start the session", gen)
                return
            }
            val (type, payload) = receiveControl(link, remaining)
            when (type) {
                XoraNetplayProtocol.TYPE_GO -> {
                    val go = XoraNetplayProtocol.decodeGo(payload)
                    applyGo(go.epoch, go.slotsMask, go.names)
                    refreshLinkedState()
                    return
                }
                XoraNetplayProtocol.TYPE_STATE -> {
                    val applied = applyStateRef.get()?.invoke(payload) ?: false
                    if (!applied) {
                        fail("Could not load host save state", gen)
                        return
                    }
                    link.send(
                        XoraNetplayProtocol.TYPE_START,
                        XoraNetplayProtocol.encodeStart(mySlot.get()),
                    )
                    // A newer barrier restarted the wait.
                    deadline = System.currentTimeMillis() + timeoutMs
                }
                XoraNetplayProtocol.TYPE_BYE -> {
                    val gone = XoraNetplayProtocol.decodeByeSlot(payload)
                    if (gone == 1 || gone == 0) {
                        fail("The host left", gen)
                        return
                    }
                }
                else -> Unit
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Shared read loop + barrier plumbing
    // ---------------------------------------------------------------------------------------

    private suspend fun readLoop(link: XoraNetplayLink, gen: Int) {
        while (running.get() && generation.get() == gen) {
            val (type, payload) = try {
                link.receive()
            } catch (t: Throwable) {
                if (generation.get() != gen) return
                if (isHost.get() && _state.value.online && running.get()) {
                    // Nakama reports "everyone left"; keep the code open for the next joiner.
                    softUnlink(
                        "Everyone left — code ${_state.value.sessionCode.ifBlank { "open" }} still live",
                    )
                    continue
                }
                fail(t.message ?: "Connection lost", gen)
                return
            }
            when (type) {
                XoraNetplayProtocol.TYPE_INPUT -> stashRemoteInput(payload)
                XoraNetplayProtocol.TYPE_HELLO -> {
                    if (isHost.get()) {
                        val peer = XoraNetplayProtocol.decodeHello(payload)
                        val selfHello = lastSelfHello.get()
                        if (selfHello != null) {
                            admitJoiner(link, selfHello, peer, gen)
                            drainParkedHellos(link, selfHello, gen)
                        }
                    }
                }
                XoraNetplayProtocol.TYPE_STATE -> {
                    if (!isHost.get()) {
                        // Host is running a barrier for a new joiner — re-sync and confirm.
                        freezeCore.set(true)
                        _state.value = _state.value.copy(status = "Re-syncing with host…")
                        val applied = applyStateRef.get()?.invoke(payload) ?: false
                        if (!applied) {
                            fail("Could not load host save state", gen)
                            return
                        }
                        link.send(
                            XoraNetplayProtocol.TYPE_START,
                            XoraNetplayProtocol.encodeStart(mySlot.get()),
                        )
                    }
                }
                XoraNetplayProtocol.TYPE_GO -> {
                    if (!isHost.get()) {
                        val go = XoraNetplayProtocol.decodeGo(payload)
                        applyGo(go.epoch, go.slotsMask, go.names)
                        refreshLinkedState()
                    }
                }
                XoraNetplayProtocol.TYPE_ASSIGN -> {
                    if (!isHost.get()) {
                        val assign = runCatching {
                            XoraNetplayProtocol.decodeAssign(payload)
                        }.getOrNull()
                        if (assign != null &&
                            assign.token != 0 &&
                            assign.token == pendingSeatToken.get()
                        ) {
                            pendingSeatToken.set(0)
                            if (assign.slot == 0) {
                                _state.value = _state.value.copy(
                                    error = "That seat is already taken.",
                                )
                            } else {
                                // The barrier STATE/GO that re-seats everyone follows.
                                mySlot.set(assign.slot)
                            }
                        }
                    }
                }
                XoraNetplayProtocol.TYPE_SEAT -> {
                    if (isHost.get()) handleSeatRequest(link, payload, gen)
                }
                XoraNetplayProtocol.TYPE_START -> Unit
                XoraNetplayProtocol.TYPE_BYE -> {
                    val gone = XoraNetplayProtocol.decodeByeSlot(payload)
                    when {
                        gone == mySlot.get() -> Unit // own broadcast echo
                        isHost.get() -> {
                            joinerSlots.remove(gone)
                            peerNames.remove(gone)
                            if (joinerSlots.isEmpty()) {
                                softUnlink(
                                    "Everyone left — code " +
                                        "${_state.value.sessionCode.ifBlank { "open" }} still live",
                                )
                            } else {
                                val mask = XoraNetplayProtocol.slotsMaskOf(joinerSlots + 1)
                                activeSlotsMask.set(mask)
                                runCatching {
                                    link.send(
                                        XoraNetplayProtocol.TYPE_GO,
                                        XoraNetplayProtocol.encodeGo(epoch.get(), mask, sessionRoster()),
                                    )
                                }
                                refreshLinkedState()
                            }
                        }
                        gone == 1 || gone == 0 -> {
                            fail("The host left", gen)
                            return
                        }
                        else -> {
                            // Another joiner left; stop waiting on their slot.
                            val mask = activeSlotsMask.get() and
                                (1 shl (gone - 1)).inv()
                            activeSlotsMask.set(mask)
                            peerNames.remove(gone)
                            refreshLinkedState()
                        }
                    }
                }
                XoraNetplayProtocol.TYPE_ERROR -> {
                    if (!isHost.get()) {
                        fail(String(payload).ifBlank { "Session error" }, gen)
                        return
                    }
                }
            }
        }
    }

    /** GO barrier: same epoch = mask-only update, new epoch = full lockstep restart. */
    private fun applyGo(newEpoch: Int, mask: Int, names: Map<Int, String> = emptyMap()) {
        if (!isHost.get() && names.isNotEmpty()) {
            names[1]?.let { hostName.set(it) }
            peerNames.clear()
            peerNames.putAll(names)
        }
        val changed = newEpoch != epoch.get() || !linked.get()
        epoch.set(newEpoch)
        activeSlotsMask.set(mask)
        if (changed) {
            frameCounter.set(0)
            pendingLocal.clear()
            lastLocal.set(EMPTY_PAD)
            for (i in 0 until XoraNetplayProtocol.MAX_PLAYERS) {
                missStreak.set(i, 0)
                lastConsumed.set(i, -1)
                lastRemote[i].set(EMPTY_PAD)
            }
        }
        freezeCore.set(false)
        linked.set(true)
    }

    private fun refreshLinkedState() {
        val slot = mySlot.get()
        val count = Integer.bitCount(activeSlotsMask.get()).coerceAtLeast(1)
        val roster = if (isHost.get()) {
            sessionRoster()
        } else {
            peerNames.toMap().ifEmpty {
                hostName.get().takeIf { it.isNotBlank() }?.let { mapOf(1 to it) }.orEmpty()
            }
        }
        val host = roster[1] ?: hostName.get()
        val others = roster.entries
            .filter { it.key != slot }
            .sortedBy { it.key }
            .joinToString(", ") { it.value }
        _state.value = _state.value.copy(
            linked = true,
            peerName = others.ifBlank { if (isHost.get()) "" else host },
            playerSlot = slot,
            playerCount = count,
            playerNames = roster,
            status = if (isHost.get()) {
                "Hosting as ${host.ifBlank { "Player 1" }} · " +
                    "Playing with ${others.ifBlank { "players" }} · You are Player 1"
            } else {
                "Joined ${host.ifBlank { "the host" }}'s session · You are Player $slot"
            },
            error = null,
        )
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

    private fun stashRemoteInput(payload: ByteArray) {
        val pad = runCatching { XoraNetplayProtocol.decodePadFrame(payload) }.getOrNull() ?: return
        val slot = pad.slot
        if (slot !in 1..XoraNetplayProtocol.MAX_PLAYERS) return
        if (slot == mySlot.get()) return // Nakama can echo our own INPUT back
        val map = pendingRemote[slot - 1]
        map[padKey(pad.epoch, pad.frame)] = pad
        pruneRemote(map, slot - 1)
    }

    /** Never prune frames the consumer still needs — only consumed or stale-epoch entries. */
    private fun pruneRemote(
        map: ConcurrentHashMap<Long, XoraNetplayProtocol.PadFrame>,
        slotIndex: Int,
    ) {
        if (map.size <= REMOTE_PRUNE_THRESHOLD) return
        val currentEpoch = epoch.get()
        val consumed = lastConsumed.get(slotIndex)
        map.keys.removeAll { key ->
            val keyEpoch = (key ushr 32).toInt()
            val keyFrame = key.toInt()
            keyEpoch != currentEpoch || keyFrame < consumed - 60
        }
        if (map.size > REMOTE_HARD_CAP) {
            map.keys.sorted().take(map.size - REMOTE_PRUNE_THRESHOLD).forEach { map.remove(it) }
        }
    }

    private fun trimLocal(frame: Int) {
        val minKeep = frame - inputDelay.get() - 30
        if (minKeep <= 0) return
        pendingLocal.keys.removeAll { it < minKeep }
    }

    private fun collectRemotePads(
        pads: Array<XoraNetplayProtocol.PadFrame>,
        currentEpoch: Int,
        target: Int,
        selfSlot: Int,
        idle: XoraNetplayProtocol.PadFrame,
    ) {
        val gen = generation.get()
        val remoteSlots = XoraNetplayProtocol.slotsInMask(activeSlotsMask.get())
            .filter { it != selfSlot }
        if (remoteSlots.isEmpty()) return
        val waiting = ArrayList<Int>(remoteSlots.size)
        for (slot in remoteSlots) {
            val pad = takeRemote(slot, currentEpoch, target)
            when {
                pad != null -> pads[slot - 1] = pad
                missStreak.get(slot - 1) >= STALL_SKIP_FRAMES ->
                    pads[slot - 1] = missPad(slot, idle) // silent slot: don't stall the frame
                else -> waiting.add(slot)
            }
        }
        if (waiting.isEmpty()) return
        val deadline = System.nanoTime() + INPUT_STALL_NS
        while (waiting.isNotEmpty() && System.nanoTime() < deadline) {
            if (!linked.get() || generation.get() != gen) break
            val iterator = waiting.iterator()
            while (iterator.hasNext()) {
                val slot = iterator.next()
                val pad = takeRemote(slot, currentEpoch, target)
                if (pad != null) {
                    pads[slot - 1] = pad
                    iterator.remove()
                }
            }
            if (waiting.isNotEmpty()) LockSupport.parkNanos(250_000L)
        }
        waiting.forEach { slot -> pads[slot - 1] = missPad(slot, idle) }
    }

    private fun takeRemote(slot: Int, currentEpoch: Int, target: Int): XoraNetplayProtocol.PadFrame? {
        val pad = pendingRemote[slot - 1].remove(padKey(currentEpoch, target)) ?: return null
        missStreak.set(slot - 1, 0)
        lastConsumed.set(slot - 1, target)
        lastRemote[slot - 1].set(pad)
        return pad
    }

    /**
     * A brief miss holds the last pad (a dropped packet must not blank P2); a slot silent
     * for [ZERO_AFTER_MISSES] frames goes neutral so a held button can't run away.
     */
    private fun missPad(slot: Int, idle: XoraNetplayProtocol.PadFrame): XoraNetplayProtocol.PadFrame {
        val streak = missStreak.incrementAndGet(slot - 1)
        if (streak >= ZERO_AFTER_MISSES) return idle
        return lastRemote[slot - 1].get().takeIf { it.frame >= 0 } ?: idle
    }

    /**
     * Handshake receive that parks INPUT packets instead of treating them as control messages.
     * Nakama can deliver pad frames while a savestate is still applying.
     */
    private fun receiveControl(link: XoraNetplayLink, timeoutMs: Int): Pair<Int, ByteArray> {
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
            val (type, payload) = link.receive(wait)
            if (type == XoraNetplayProtocol.TYPE_INPUT) {
                stashRemoteInput(payload)
                continue
            }
            return type to payload
        }
    }

    private fun fail(message: String, gen: Int) {
        if (generation.get() != gen) return
        linked.set(false)
        running.set(false)
        freezeCore.set(false)
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
        private const val ONLINE_INPUT_DELAY = 12
        private const val INPUT_STALL_NS = 120_000_000L // 120ms per frame before hold-last
        private const val ZERO_AFTER_MISSES = 45 // ~0.75s of hold-last, then neutral pad
        private const val STALL_SKIP_FRAMES = 240 // ~4s silent: stop waiting, quick-check only
        private const val START_BARRIER_TIMEOUT_MS = 60_000L
        private const val STATE_DOWNLOAD_TIMEOUT_MS = 90_000L
        private const val REMOTE_PRUNE_THRESHOLD = 1024
        private const val REMOTE_HARD_CAP = 4096

        /** Token 0 is reserved for the host hello, so joiner tokens must be non-zero. */
        private fun generateJoinToken(): Int {
            val random = SecureRandom()
            var token = random.nextInt()
            while (token == 0) token = random.nextInt()
            return token
        }

        private val EMPTY_PAD = XoraNetplayProtocol.PadFrame(frame = -1, buttons = 0)

        fun padKey(epoch: Int, frame: Int): Long =
            ((epoch.toLong() and 0xFF) shl 32) or (frame.toLong() and 0xFFFFFFFFL)

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
