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
import java.util.concurrent.atomic.AtomicLong
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
    /** This device's player number: host is always 1, joiners get 2..4 in join order. */
    val playerSlot: Int = 0,
    /** Players currently in the session (including this device). */
    val playerCount: Int = 0,
    /** XOrA Network usernames by slot (1 = host) for everyone in the session. */
    val playerNames: Map<Int, String> = emptyMap(),
)

/** On-game netplay strip. Status used to live only in the pause menu, so a failed join looked like nothing. */
fun netplayBannerText(
    ui: XoraNetplayUiState,
    @Suppress("UNUSED_PARAMETER") padLive: Boolean = false,
    gameTitle: String = "",
    hasController: Boolean = true,
    @Suppress("UNUSED_PARAMETER") lastKey: String = "",
    sharedConsole: Boolean = true,
): String {
    ui.error?.takeIf { it.isNotBlank() }?.let { return it }
    if (ui.linked && ui.playerSlot >= 1) {
        val lines = mutableListOf("You are Player ${ui.playerSlot}")
        if (ui.playerSlot >= 2) {
            if (sharedConsole) {
                lines += "One game on the host. Your pad is Player ${ui.playerSlot} on that session."
            }
            if (hasController) {
                if (!sharedConsole) {
                    lines += "This device is your Game Boy. The other player has theirs."
                }
            } else {
                lines += "Press the on-screen pad at the bottom of this phone."
            }
        } else if (!hasController) {
            lines += "This phone has no controller. Use the touch pad, or plug one in."
        }
        if (sharedConsole) {
            lines += twoPlayerModeHint(gameTitle)
        } else {
            lines += "Stay on this waiting screen on both devices. XOrA is the Game Link cable."
        }
        return lines.joinToString("\n")
    }
    if (ui.role != XoraNetplayRole.Idle &&
        ui.status.isNotBlank() &&
        !ui.status.equals("Off", ignoreCase = true)
    ) {
        return ui.status
    }
    return ""
}

/**
 * Super Mario Kart (and most 2P games) only read the second pad after 2-player mode is
 * chosen on the title screen. A 1-player Grand Prix has no Player 2 to move.
 */
fun twoPlayerModeHint(gameTitle: String): String {
    val title = gameTitle.lowercase()
    return if (title.contains("mario kart") || title.contains("mariokart")) {
        "Pick 2 PLAYER GAME on the title screen. 1 PLAYER Grand Prix has no Player 2."
    } else {
        "Start a 2-player game from the menu. A 1-player game ignores Player 2."
    }
}

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
 *
 * Online pads are **latest-received**, not exact-frame lockstep. Nakama's JSON WebSocket
 * RTT is often larger than a 12-frame buffer, and waiting on a missing frame stalled the
 * emu thread so the two devices' frame counters never lined up — joiners stayed idle.
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
    private val frameCounter = AtomicInteger(0)
    private val freezeCore = AtomicBoolean(false)

    private val captureStateRef = AtomicReference<(suspend () -> ByteArray?)?>(null)
    private val applyStateRef = AtomicReference<(suspend (ByteArray) -> Boolean)?>(null)
    /** Host hello reused when admitting late joiners from the read loop. */
    private val lastSelfHello = AtomicReference<XoraNetplayProtocol.Hello?>(null)
    /** Token of this joiner's in-flight seat-change request (0 = none). */
    private val pendingSeatToken = AtomicInteger(0)
    private val sessionMode = AtomicReference(NetplaySessionMode.SharedConsole)
    private val videoSeq = AtomicInteger(0)
    private val lastVideo = AtomicReference<XoraNetplayProtocol.VideoPacket?>(null)
    private val lastVideoSeq = AtomicInteger(-1)
    private val lastVideoSentMs = AtomicLong(0)
    private val videoMuteUntilMs = AtomicLong(0)
    private val lastSerial = Array(XoraNetplayProtocol.MAX_PLAYERS) { AtomicInteger(0xFFFF) }
    private val lastSerialSentMs = AtomicLong(0)
    private val lastSerialSentWord = AtomicInteger(-1)

    val linkedNow: Boolean get() = linked.get()
    val hosting: Boolean get() = isHost.get() && running.get()
    val onlineNow: Boolean get() = _state.value.online
    /** This device's seat (1 = host, 2..4 = joiners). 0 = not assigned yet. */
    val playerSlotNow: Int get() = mySlot.get()
    /** True while a savestate is in flight — the core must not advance. */
    val holdEmulation: Boolean get() = freezeCore.get()
    val sessionModeNow: NetplaySessionMode get() = sessionMode.get()
    /** Home consoles: only the host advances the core. Handhelds: every device runs its own. */
    val runsLocalCore: Boolean
        get() = !linked.get() ||
            sessionMode.get() == NetplaySessionMode.HandheldLink ||
            isHost.get()

    fun setSessionMode(mode: NetplaySessionMode) {
        sessionMode.set(mode)
    }

    fun setSessionModeFromPlatform(platformId: String) {
        sessionMode.set(netplaySessionMode(platformId))
    }

    fun host(
        port: Int,
        hello: XoraNetplayProtocol.Hello,
        captureState: suspend () -> ByteArray?,
    ) {
        stop()
        val gen = generation.get()
        beginHostState(maxPlayers = 2, hello = hello, captureState = captureState)
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
            maxPlayers = XoraNetplayProtocol.MAX_PLAYERS,
            hello = hello,
            captureState = captureState,
        )
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
                while (generation.get() == gen && running.get()) {
                    try {
                        if (!linked.get()) {
                            _state.value = _state.value.copy(
                                status = "Code ${_state.value.sessionCode.ifBlank { sessionCode }} — waiting for a player",
                            )
                            val waited = waitForPeer()
                            if (waited.isFailure) continue
                            if (generation.get() != gen || !running.get()) return@launch
                            _state.value = _state.value.copy(status = "Connecting…")
                            runHostHandshake(link, hello, gen)
                        }
                        if (generation.get() != gen || !running.get()) return@launch
                        readLoop(link, gen)
                        if (generation.get() == gen && running.get() && _state.value.online) {
                            softUnlink(
                                "Everyone left — code ${_state.value.sessionCode.ifBlank { "open" }} still live",
                            )
                            continue
                        }
                        return@launch
                    } catch (t: Throwable) {
                        if (generation.get() != gen) return@launch
                        if (running.get() && _state.value.online) {
                            // A failed join must not destroy the lobby — keep the code live.
                            softUnlink(
                                (t.message?.takeIf { it.isNotBlank() } ?: "Join failed") +
                                    " — code ${_state.value.sessionCode.ifBlank { "open" }} still live",
                            )
                            continue
                        }
                        fail(t.message ?: "Host failed", gen)
                        return@launch
                    }
                }
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
        beginJoinState(hello, applyState)
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
        beginJoinState(hello, applyState)
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
     * This device's assigned seat gets the **current** local pad (no delay). Every other
     * seat gets the newest remote pad we have for this epoch. A slot that goes silent
     * holds its last pad briefly, then goes neutral after [ZERO_AFTER_MISSES] frames.
     *
     * Slot 0 is not coerced to Player 1 — an unassigned joiner must not tag INPUT as the
     * host, or the real host drops it as an echo and P2 never moves.
     */
    fun exchange(local: XoraNetplayProtocol.PadFrame): XoraNetplayExchange {
        val slot = mySlot.get()
        val currentEpoch = epoch.get()
        val idle = XoraNetplayProtocol.PadFrame(frame = local.frame, buttons = 0)
        val pads = Array(XoraNetplayProtocol.MAX_PLAYERS) { idle }
        if (!linked.get()) return XoraNetplayExchange(pads.toList())
        if (slot in 1..XoraNetplayProtocol.MAX_PLAYERS) {
            val tagged = local.copy(slot = slot, epoch = currentEpoch)
            writeInput(tagged)
            pads[slot - 1] = tagged
        }
        collectLatestRemotePads(pads, currentEpoch, slot, idle)
        return XoraNetplayExchange(pads.toList())
    }

    fun sendVideo(jpeg: ByteArray, pcm: ShortArray) {
        if (!isHost.get() || !linked.get()) return
        if (sessionMode.get() != NetplaySessionMode.SharedConsole) return
        if (jpeg.isEmpty()) return
        val now = System.currentTimeMillis()
        if (now < videoMuteUntilMs.get()) return
        val online = _state.value.online
        val minInterval = if (online) ONLINE_VIDEO_MIN_INTERVAL_MS else LAN_VIDEO_MIN_INTERVAL_MS
        if (now - lastVideoSentMs.get() < minInterval) return
        if (online && jpeg.size > XoraNetplayVideo.ONLINE_MAX_BYTES) return
        lastVideoSentMs.set(now)
        val seq = videoSeq.getAndIncrement()
        val link = linkRef.get() ?: return
        // PCM bloats Nakama match_data past the size cap and kicks the joiner.
        val audio = if (online) ShortArray(0) else pcm
        runCatching {
            link.send(
                XoraNetplayProtocol.TYPE_VIDEO,
                XoraNetplayProtocol.encodeVideo(seq, jpeg, audio),
            )
        }
    }

    fun takeVideo(): XoraNetplayProtocol.VideoPacket? = lastVideo.getAndSet(null)

    /**
     * Handheld Game Link: publish this device's SIO send word and return the 4-slot
     * SIOMULTI table. GBA games treat 0xFFFF as "no unit in this socket", so a live
     * handheld session always exposes at least two connected GBAs (idle partners
     * read as 0) — otherwise Pokemon sits on "Please connect the Game Link cable".
     */
    fun exchangeSerial(localSend: Int, siocnt: Int = 0): IntArray {
        val multi = IntArray(XoraNetplayProtocol.MAX_PLAYERS) { 0xFFFF }
        if (sessionMode.get() != NetplaySessionMode.HandheldLink) return multi
        val slot = mySlot.get()
        if (slot in 1..XoraNetplayProtocol.MAX_PLAYERS) {
            lastSerial[slot - 1].set(localSend and 0xFFFF)
            if (linked.get()) {
                val link = linkRef.get()
                if (link != null) {
                    val word = localSend and 0xFFFF
                    val now = System.currentTimeMillis()
                    val changed = word != lastSerialSentWord.get()
                    if (changed || now - lastSerialSentMs.get() >= 50L) {
                        lastSerialSentWord.set(word)
                        lastSerialSentMs.set(now)
                        runCatching {
                            link.send(
                                XoraNetplayProtocol.TYPE_SERIAL,
                                XoraNetplayProtocol.encodeSerial(slot, localSend, siocnt),
                            )
                        }
                    }
                }
            }
        }
        val occupied = BooleanArray(XoraNetplayProtocol.MAX_PLAYERS)
        if (slot in 1..XoraNetplayProtocol.MAX_PLAYERS) occupied[slot - 1] = true
        for (i in 0 until XoraNetplayProtocol.MAX_PLAYERS) {
            if (lastSerial[i].get() != 0xFFFF) occupied[i] = true
        }
        if (occupied.count { it } < 2) {
            if (!occupied[0]) occupied[0] = true else occupied[1] = true
        }
        for (i in 0 until XoraNetplayProtocol.MAX_PLAYERS) {
            val raw = lastSerial[i].get() and 0xFFFF
            multi[i] = when {
                i == slot - 1 -> localSend and 0xFFFF
                occupied[i] && raw == 0xFFFF -> 0
                else -> raw
            }
        }
        return multi
    }

    private fun stashSerial(payload: ByteArray) {
        val packet = runCatching { XoraNetplayProtocol.decodeSerial(payload) }.getOrNull() ?: return
        if (packet.slot == mySlot.get()) return
        if (packet.slot in 1..XoraNetplayProtocol.MAX_PLAYERS) {
            lastSerial[packet.slot - 1].set(packet.send and 0xFFFF)
        }
    }

    private fun stashVideo(payload: ByteArray) {
        if (isHost.get()) return
        val packet = runCatching { XoraNetplayProtocol.decodeVideo(payload) }.getOrNull() ?: return
        val prev = lastVideoSeq.get()
        if (packet.seq < prev) return
        lastVideoSeq.set(packet.seq)
        lastVideo.set(packet)
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
        freezeCore.set(false)
        captureStateRef.set(null)
        applyStateRef.set(null)
        lastSelfHello.set(null)
        pendingSeatToken.set(0)
        lastVideo.set(null)
        lastVideoSeq.set(-1)
        videoSeq.set(0)
        lastVideoSentMs.set(0)
        videoMuteUntilMs.set(0)
        lastSerial.forEach { it.set(0xFFFF) }
        lastSerialSentMs.set(0)
        lastSerialSentWord.set(-1)
        _state.value = XoraNetplayUiState(
            status = "Off",
            localAddresses = localIpv4Addresses(),
        )
    }

    private fun beginHostState(
        maxPlayers: Int,
        hello: XoraNetplayProtocol.Hello,
        captureState: suspend () -> ByteArray?,
    ) {
        running.set(true)
        isHost.set(true)
        mySlot.set(1)
        sessionMode.set(netplaySessionMode(hello.platformId))
        maxPlayersNow.set(maxPlayers.coerceIn(2, XoraNetplayProtocol.MAX_PLAYERS))
        captureStateRef.set(captureState)
        lastSelfHello.set(hello)
    }

    private fun beginJoinState(hello: XoraNetplayProtocol.Hello, applyState: suspend (ByteArray) -> Boolean) {
        running.set(true)
        isHost.set(false)
        sessionMode.set(netplaySessionMode(hello.platformId))
        // Handhelds keep running their own game during join. Freezing was for savestate load.
        freezeCore.set(sessionMode.get().usesSavestateBarrier())
        applyStateRef.set(applyState)
    }

    private fun resetPadBuffers() {
        pendingRemote.forEach { it.clear() }
        lastRemote.forEach { it.set(EMPTY_PAD) }
        for (i in 0 until XoraNetplayProtocol.MAX_PLAYERS) {
            missStreak.set(i, 0)
            lastConsumed.set(i, -1)
        }
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
        while (running.get() && generation.get() == gen) {
            val (type, payload) = try {
                receiveControl(link, 30_000)
            } catch (_: SocketTimeoutException) {
                continue
            }
            if (type != XoraNetplayProtocol.TYPE_HELLO) continue
            val peer = XoraNetplayProtocol.decodeHello(payload)
            if (peer.token == 0) continue // own broadcast echo
            admitJoiner(link, selfHello, peer, gen)
            drainParkedHellos(link, selfHello, gen)
            return
        }
    }

    /**
     * Admission: assign a slot and GO. Savestates are not sent — joiners never load them
     * and the Nakama chunk flood used to kick Player 2.
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
            rejectJoiner(link, peer.token, XoraNetplayProtocol.REJECT_VERSION)
            return
        }
        if (peer.coreName.isNotBlank() &&
            selfHello.coreName.isNotBlank() &&
            !peer.coreName.equals(selfHello.coreName, ignoreCase = true)
        ) {
            rejectJoiner(link, peer.token, XoraNetplayProtocol.REJECT_CORE)
            return
        }
        val slot = nextFreeSlot()
        if (slot == null) {
            rejectJoiner(link, peer.token, XoraNetplayProtocol.REJECT_FULL)
            return
        }
        val handheld = !sessionMode.get().usesSavestateBarrier()
        if (!handheld) freezeCore.set(true)
        _state.value = _state.value.copy(
            status = "Syncing ${peer.nickname.ifBlank { "Player $slot" }}…",
        )
        link.send(XoraNetplayProtocol.TYPE_HELLO, XoraNetplayProtocol.encodeHello(selfHello))
        link.send(
            XoraNetplayProtocol.TYPE_ASSIGN,
            XoraNetplayProtocol.encodeAssign(peer.token, slot),
        )
        // Never send a savestate over Nakama. Joiners do not load it, and the chunk
        // flood used to kick Player 2 the moment they sat down.
        joinerSlots.add(slot)
        peerNames[slot] = peer.nickname.ifBlank { "Player $slot" }
        val newEpoch = (epoch.get() + 1) and 0xFF
        val mask = XoraNetplayProtocol.slotsMaskOf(joinerSlots + 1)
        link.send(
            XoraNetplayProtocol.TYPE_GO,
            XoraNetplayProtocol.encodeGo(newEpoch, mask, sessionRoster()),
        )
        applyGo(newEpoch, mask)
        refreshLinkedState()
        if (generation.get() != gen) return
    }

    /** Slot → XOrA username roster the host broadcasts with every GO. */
    private fun sessionRoster(): Map<Int, String> = buildMap {
        lastSelfHello.get()?.nickname?.takeIf { it.isNotBlank() }?.let { put(1, it) }
        peerNames.forEach { (slot, name) -> if (name.isNotBlank()) put(slot, name) }
    }

    /**
     * Joiner asked for a different seat: re-map their slot, then GO so every device
     * flips port mapping together. Savestates are not sent — they kicked Nakama.
     */
    private suspend fun handleSeatRequest(link: XoraNetplayLink, payload: ByteArray, gen: Int) {
        if (generation.get() != gen) return
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
        link.send(
            XoraNetplayProtocol.TYPE_ASSIGN,
            XoraNetplayProtocol.encodeAssign(seat.token, want),
        )
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

    /** Tell one joiner they cannot sit down; never tear down the host lobby. */
    private fun rejectJoiner(link: XoraNetplayLink, token: Int, reason: Int) {
        runCatching {
            link.send(
                XoraNetplayProtocol.TYPE_ASSIGN,
                XoraNetplayProtocol.encodeAssign(token, 0, reason),
            )
        }
    }

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
        val progress = JoinHandshakeProgress()
        var deadline = System.currentTimeMillis() + helloTimeoutMs
        while (true) {
            if (generation.get() != gen || !running.get()) return
            val remaining = (deadline - System.currentTimeMillis()).toInt()
            if (remaining <= 0) {
                if (progress.slot != 0) mySlot.set(progress.slot)
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
                    progress.onHostHello(decoded)
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
                    progress.onAssign(assign.slot)
                    deadline = maxOf(deadline, System.currentTimeMillis() + STATE_DOWNLOAD_TIMEOUT_MS)
                }
                XoraNetplayProtocol.TYPE_STATE -> progress.onState(payload)
                XoraNetplayProtocol.TYPE_GO -> progress.onGo(payload)
                else -> Unit
            }
            if (progress.ready()) break
        }
        mySlot.set(progress.slot)
        hostName.set(progress.host?.nickname.orEmpty())
        val go = XoraNetplayProtocol.decodeGo(progress.goPayload!!)
        applyGo(go.epoch, go.slotsMask, go.names)
        refreshLinkedState()
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
                    if (!applyIncomingState(payload)) {
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
                XoraNetplayProtocol.TYPE_VIDEO -> stashVideo(payload)
                XoraNetplayProtocol.TYPE_SERIAL -> stashSerial(payload)
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
                        if (!applyIncomingState(payload)) {
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

    /** GO barrier: same epoch = mask-only update, new epoch = pad-buffer restart. */
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
            for (i in 0 until XoraNetplayProtocol.MAX_PLAYERS) {
                pendingRemote[i].clear()
                missStreak.set(i, 0)
                lastConsumed.set(i, -1)
                lastRemote[i].set(EMPTY_PAD)
            }
        }
        freezeCore.set(false)
        linked.set(true)
        if (isHost.get()) {
            val warmup = if (_state.value.online) ONLINE_VIDEO_WARMUP_MS else LAN_VIDEO_WARMUP_MS
            videoMuteUntilMs.set(System.currentTimeMillis() + warmup)
        }
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

    /**
     * Handheld joiners load the host snapshot onto their own core. Home-console joiners only
     * display host video — unserialize used to fail (or crash) and kick them the instant they joined.
     */
    private suspend fun applyIncomingState(payload: ByteArray): Boolean {
        if (!sessionMode.get().joinerAppliesHostState()) return true
        return applyStateRef.get()?.invoke(payload) ?: false
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

    /**
     * Fill every seat except [selfSlot] from the newest remote pad we have. Do not require
     * an exact (epoch, frame) match — Nakama frames rarely share a counter — and never
     * stall the emu thread waiting for one.
     *
     * Incoming pads are applied even when [activeSlotsMask] omitted that seat, so a late
     * or lost GO mask cannot leave P2 idle while INPUT is arriving.
     */
    private fun collectLatestRemotePads(
        pads: Array<XoraNetplayProtocol.PadFrame>,
        currentEpoch: Int,
        selfSlot: Int,
        idle: XoraNetplayProtocol.PadFrame,
    ) {
        for (slot in 1..XoraNetplayProtocol.MAX_PLAYERS) {
            if (slot == selfSlot) continue
            val latest = takeLatestRemote(slot, currentEpoch)
            pads[slot - 1] = when {
                latest != null -> latest
                else -> missPad(slot, idle)
            }
        }
    }

    private fun takeLatestRemote(slot: Int, currentEpoch: Int): XoraNetplayProtocol.PadFrame? {
        val map = pendingRemote[slot - 1]
        if (map.isEmpty()) return null
        var best: XoraNetplayProtocol.PadFrame? = null
        val stale = ArrayList<Long>()
        for ((key, pad) in map) {
            val keyEpoch = (key ushr 32).toInt()
            if (keyEpoch != currentEpoch) {
                stale.add(key)
                continue
            }
            if (best == null || pad.frame > best.frame) best = pad
        }
        stale.forEach { map.remove(it) }
        val chosen = best ?: return null
        map.keys.removeAll { key ->
            (key ushr 32).toInt() == currentEpoch && key.toInt() <= chosen.frame
        }
        missStreak.set(slot - 1, 0)
        lastConsumed.set(slot - 1, chosen.frame)
        lastRemote[slot - 1].set(chosen)
        return chosen
    }

    /**
     * A brief miss holds the last pad (a dropped packet must not blank P2); a slot silent
     * for [ZERO_AFTER_MISSES] frames goes neutral so a held button can't run away.
     */
    private fun missPad(slot: Int, idle: XoraNetplayProtocol.PadFrame): XoraNetplayProtocol.PadFrame {
        val streak = missStreak.incrementAndGet(slot - 1)
        if (streak >= ZERO_AFTER_MISSES) return idle
        return lastRemote[slot - 1].get().takeIf { it.frame >= 0 && it.epoch == epoch.get() }
            ?: idle
    }

    /** Test-only: mark this session linked as [slot] without a network handshake. */
    internal fun bindForTest(
        slot: Int,
        epoch: Int = 1,
        slotsMask: Int = 0b1111,
        host: Boolean = slot == 1,
        online: Boolean = false,
        mode: NetplaySessionMode = NetplaySessionMode.SharedConsole,
    ) {
        mySlot.set(slot)
        this.epoch.set(epoch)
        activeSlotsMask.set(slotsMask)
        linked.set(true)
        running.set(true)
        isHost.set(host)
        sessionMode.set(mode)
        _state.value = _state.value.copy(
            linked = true,
            online = online,
            role = if (host) XoraNetplayRole.Host else XoraNetplayRole.Client,
            playerSlot = slot,
        )
    }

    internal fun attachLinkForTest(link: XoraNetplayLink) {
        linkRef.set(link)
    }

    /** Test-only: handheld host waiting for a joiner — cable must already look plugged in. */
    internal fun waitHandheldForTest(slot: Int = 1) {
        running.set(true)
        linked.set(false)
        isHost.set(slot == 1)
        mySlot.set(slot)
        sessionMode.set(NetplaySessionMode.HandheldLink)
    }

    internal fun armVideoForTest(muteUntilMs: Long = 0L, lastSentMs: Long = 0L) {
        videoMuteUntilMs.set(muteUntilMs)
        lastVideoSentMs.set(lastSentMs)
    }

    internal fun ingestRemoteForTest(pad: XoraNetplayProtocol.PadFrame) {
        stashRemoteInput(XoraNetplayProtocol.encodePadFrame(pad))
    }

    internal fun ingestSerialForTest(slot: Int, send: Int, siocnt: Int = 0) {
        stashSerial(XoraNetplayProtocol.encodeSerial(slot, send, siocnt))
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
            if (type == XoraNetplayProtocol.TYPE_VIDEO) {
                stashVideo(payload)
                continue
            }
            if (type == XoraNetplayProtocol.TYPE_SERIAL) {
                stashSerial(payload)
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
        val leavingSlot = mySlot.get()
        val link = linkRef.get()
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
        runCatching { server?.close() }
    }

    companion object {
        private const val ZERO_AFTER_MISSES = 45 // ~0.75s of hold-last, then neutral pad
        private const val START_BARRIER_TIMEOUT_MS = 60_000L
        private const val STATE_DOWNLOAD_TIMEOUT_MS = 90_000L
        private const val REMOTE_PRUNE_THRESHOLD = 1024
        private const val REMOTE_HARD_CAP = 4096
        internal const val ONLINE_VIDEO_MIN_INTERVAL_MS = 125L
        internal const val LAN_VIDEO_MIN_INTERVAL_MS = 50L
        internal const val ONLINE_VIDEO_WARMUP_MS = 700L
        internal const val LAN_VIDEO_WARMUP_MS = 200L

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
