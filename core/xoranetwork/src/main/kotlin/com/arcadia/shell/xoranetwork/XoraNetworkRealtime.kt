package com.arcadia.shell.xoranetwork

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Nakama `/ws` session. REST auth alone never marks a user online — the server only flips
 * `user.online` while this socket is open with `status=true`.
 *
 * The JWT is a query parameter on the handshake and is never logged.
 */
@Singleton
class XoraNetworkRealtime @Inject constructor(
    httpClient: OkHttpClient,
    private val json: Json,
) {
    private val client = httpClient.newBuilder()
        .pingInterval(PING_INTERVAL_SECONDS, TimeUnit.SECONDS)
        .build()

    private val socket = AtomicReference<WebSocket?>(null)
    private val cid = AtomicInteger(1)
    private val followed = AtomicReference<Set<String>>(emptySet())
    private val wantConnected = AtomicBoolean(false)
    private val tokenRef = AtomicReference<String?>(null)
    private val appearOnline = AtomicBoolean(true)
    private val opened = AtomicBoolean(false)
    private val statusText = AtomicReference("Online")
    private val reconnecter: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "xora-presence-reconnect").apply { isDaemon = true }
    }

    @Volatile
    private var listener: ((XoraPresenceEvent) -> Unit)? = null

    private val pendingRpc = ConcurrentHashMap<String, CompletableDeferred<JsonObject>>()
    private val connectedWaiter = AtomicReference<CompletableDeferred<Unit>?>(null)
    private val matchQueues = ConcurrentHashMap<String, LinkedBlockingQueue<MatchIngress>>()
    private val matchMembers = ConcurrentHashMap<String, MutableSet<String>>()
    private val peerWaiters = ConcurrentHashMap<String, PeerWaiter>()

    internal fun setListener(onEvent: ((XoraPresenceEvent) -> Unit)?) {
        listener = onEvent
    }

    internal fun connect(accessToken: String, appearOnline: Boolean = true) {
        wantConnected.set(true)
        tokenRef.set(accessToken)
        this.appearOnline.set(appearOnline)
        closeSocket(notify = false)
        openSocket(accessToken)
    }

    internal fun updateStatus(status: String) {
        statusText.set(status)
        val current = socket.get() ?: return
        if (opened.get() && appearOnline.get() && status.isNotBlank()) {
            sendStatus(current, status)
        }
    }

    internal fun follow(usernames: Collection<String>) {
        val next = usernames.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        followed.set(next)
        val current = socket.get() ?: return
        if (opened.get() && next.isNotEmpty()) sendFollow(current, next)
    }

    internal fun disconnect() {
        wantConnected.set(false)
        tokenRef.set(null)
        followed.set(emptySet())
        closeSocket(notify = true)
    }

    internal val isConnected: Boolean get() = opened.get() && socket.get() != null

    internal suspend fun awaitConnected(timeoutMs: Long): Boolean {
        if (isConnected) return true
        val created = CompletableDeferred<Unit>()
        val waiter = connectedWaiter.updateAndGet { current ->
            if (current == null || current.isCompleted) created else current
        } ?: created
        if (isConnected) return true
        return try {
            withTimeout(timeoutMs) { waiter.await() }
            isConnected
        } catch (_: TimeoutCancellationException) {
            false
        }
    }

    internal suspend fun createNamedMatch(name: String): XoraNetworkMatchSession {
        val response = rpc {
            putJsonObject("match_create") { put("name", name) }
        }
        val match = response["match"] as? JsonObject
            ?: throw XoraNetworkException("Couldn't start that online session.")
        val session = parseMatchSession(match)
            ?: throw XoraNetworkException("Couldn't start that online session.")
        rememberMatchMembers(session.matchId, listOf(session.selfUserId) + session.presenceUserIds)
        matchQueue(session.matchId)
        return session
    }

    internal suspend fun waitForMatchPeer(
        matchId: String,
        selfUserId: String,
        timeoutMs: Long,
    ) {
        if (hasPeer(matchId, selfUserId)) return
        val deferred = CompletableDeferred<Unit>()
        peerWaiters[matchId] = PeerWaiter(selfUserId, deferred)
        if (hasPeer(matchId, selfUserId)) {
            peerWaiters.remove(matchId)
            return
        }
        try {
            withTimeout(timeoutMs) { deferred.await() }
        } catch (_: TimeoutCancellationException) {
            peerWaiters.remove(matchId)
            throw XoraNetworkException("Nobody joined with that code.")
        }
    }

    internal fun sendMatchData(matchId: String, opcode: Int, data: ByteArray, reliable: Boolean) {
        val current = socket.get() ?: throw IOException("XOrA Network disconnected")
        val body = buildJsonObject {
            putJsonObject("match_data_send") {
                put("match_id", matchId)
                put("op_code", opcode)
                put("data", encodeMatchBytes(data))
                put("reliable", reliable)
            }
        }
        if (!current.send(body.toString())) {
            throw IOException("Couldn't send netplay data")
        }
    }

    internal fun receiveMatchData(matchId: String, timeoutMs: Int): Pair<Int, ByteArray> {
        val queue = matchQueue(matchId)
        val item = if (timeoutMs <= 0) {
            queue.take()
        } else {
            queue.poll(timeoutMs.toLong(), TimeUnit.MILLISECONDS)
                ?: throw SocketTimeoutException("Timed out waiting for the other player")
        }
        return when (item) {
            MatchIngress.Closed -> throw IOException("The other player left")
            is MatchIngress.Data -> item.opcode to item.payload
        }
    }

    internal fun leaveMatch(matchId: String) {
        val current = socket.get()
        if (current != null && opened.get()) {
            val body = buildJsonObject {
                put("cid", cid.getAndIncrement().toString())
                putJsonObject("match_leave") { put("match_id", matchId) }
            }
            current.send(body.toString())
        }
        dropMatch(matchId)
    }

    private fun openSocket(accessToken: String) {
        val https = "https://api.xoranetwork.com/ws".toHttpUrl().newBuilder()
            .addQueryParameter("lang", "en")
            .addQueryParameter("format", "json")
            .addQueryParameter("status", if (appearOnline.get()) "true" else "false")
            .addQueryParameter("token", accessToken)
            .build()
        val request = Request.Builder()
            .url(https.toString().replaceFirst("https:", "wss:"))
            .build()
        socket.set(
            client.newWebSocket(
                request,
                object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        opened.set(true)
                        connectedWaiter.getAndSet(null)?.complete(Unit)
                        listener?.invoke(XoraPresenceEvent.Connected)
                        if (appearOnline.get()) {
                            sendStatus(webSocket, statusText.get().ifBlank { "Online" })
                        }
                        val names = followed.get()
                        if (names.isNotEmpty()) sendFollow(webSocket, names)
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        dispatch(text)
                    }

                    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                        webSocket.close(code, reason)
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        handleDrop(webSocket)
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        handleDrop(webSocket)
                    }
                },
            ),
        )
    }

    private fun closeSocket(notify: Boolean) {
        val wasOpen = opened.getAndSet(false)
        val previous = socket.getAndSet(null)
        previous?.cancel()
        failInFlight("XOrA Network disconnected")
        if (notify && wasOpen) listener?.invoke(XoraPresenceEvent.Disconnected)
    }

    private fun handleDrop(webSocket: WebSocket) {
        val dropped = socket.compareAndSet(webSocket, null)
        val wasOpen = opened.getAndSet(false)
        if (dropped) failInFlight("XOrA Network disconnected")
        if (dropped && wasOpen) listener?.invoke(XoraPresenceEvent.Disconnected)
        if (dropped && wantConnected.get()) scheduleReconnect()
    }

    private fun scheduleReconnect() {
        reconnecter.schedule(
            {
                val token = tokenRef.get()
                if (wantConnected.get() && token != null && socket.get() == null) {
                    openSocket(token)
                }
            },
            RECONNECT_DELAY_SECONDS,
            TimeUnit.SECONDS,
        )
    }

    private fun sendStatus(webSocket: WebSocket, status: String) {
        val body = buildJsonObject {
            put("cid", cid.getAndIncrement().toString())
            put("status_update", buildJsonObject { put("status", status) })
        }
        webSocket.send(body.toString())
    }

    private fun sendFollow(webSocket: WebSocket, usernames: Set<String>) {
        val body = buildJsonObject {
            put("cid", cid.getAndIncrement().toString())
            put(
                "status_follow",
                buildJsonObject {
                    put(
                        "usernames",
                        buildJsonArray {
                            usernames.forEach { add(JsonPrimitive(it)) }
                        },
                    )
                },
            )
        }
        webSocket.send(body.toString())
    }

    private fun dispatch(text: String) {
        val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull() ?: return
        parseMatchData(root)?.let { message ->
            matchQueue(message.matchId).offer(MatchIngress.Data(message.opcode, message.payload))
            return
        }
        parseMatchPresenceDelta(root)?.let { delta ->
            val before = matchMembers[delta.matchId]?.size ?: 0
            rememberMatchMembers(delta.matchId, delta.joinedUserIds)
            if (delta.leftUserIds.isNotEmpty()) {
                matchMembers[delta.matchId]?.removeAll(delta.leftUserIds.toSet())
            }
            val after = matchMembers[delta.matchId]?.size ?: 0
            if (before >= 2 && after < 2) {
                matchQueue(delta.matchId).offer(MatchIngress.Closed)
            }
            return
        }
        val cidValue = jsonString(root["cid"])
        if (cidValue != null) {
            val pending = pendingRpc.remove(cidValue)
            if (pending != null) {
                val error = parseRealtimeErrorMessage(root)
                if (error != null) {
                    pending.completeExceptionally(XoraNetworkException(error))
                } else {
                    pending.complete(root)
                }
                return
            }
        }
        parseXoraPresenceMessage(root).forEach { event -> listener?.invoke(event) }
    }

    private suspend fun rpc(builder: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit): JsonObject {
        val current = socket.get() ?: throw XoraNetworkException("XOrA Network isn't online yet.")
        val id = cid.getAndIncrement().toString()
        val deferred = CompletableDeferred<JsonObject>()
        pendingRpc[id] = deferred
        val body = buildJsonObject {
            put("cid", id)
            builder()
        }
        if (!current.send(body.toString())) {
            pendingRpc.remove(id)
            throw XoraNetworkException("Couldn't reach XOrA Network. Check your connection and try again.")
        }
        return try {
            withTimeout(RPC_TIMEOUT_MS) { deferred.await() }
        } catch (_: TimeoutCancellationException) {
            pendingRpc.remove(id)
            throw XoraNetworkException("XOrA Network didn't answer in time. Try again.")
        }
    }

    private fun matchQueue(matchId: String): LinkedBlockingQueue<MatchIngress> =
        matchQueues.getOrPut(matchId) { LinkedBlockingQueue(512) }

    private fun rememberMatchMembers(matchId: String, userIds: Collection<String>) {
        if (matchId.isBlank()) return
        val set = matchMembers.getOrPut(matchId) { ConcurrentHashMap.newKeySet() }
        userIds.filter { it.isNotBlank() }.forEach { set.add(it) }
        val waiter = peerWaiters[matchId] ?: return
        if (hasPeer(matchId, waiter.selfUserId)) {
            peerWaiters.remove(matchId)
            waiter.deferred.complete(Unit)
        }
    }

    private fun hasPeer(matchId: String, selfUserId: String): Boolean {
        val members = matchMembers[matchId] ?: return false
        return members.any { it.isNotBlank() && it != selfUserId }
    }

    private fun dropMatch(matchId: String) {
        peerWaiters.remove(matchId)?.deferred?.completeExceptionally(
            XoraNetworkException("The online session ended."),
        )
        matchMembers.remove(matchId)
        matchQueues.remove(matchId)?.offer(MatchIngress.Closed)
    }

    private fun failInFlight(message: String) {
        pendingRpc.values.forEach { it.completeExceptionally(XoraNetworkException(message)) }
        pendingRpc.clear()
        peerWaiters.values.forEach { it.deferred.completeExceptionally(XoraNetworkException(message)) }
        peerWaiters.clear()
        matchMembers.clear()
        matchQueues.values.forEach { it.offer(MatchIngress.Closed) }
        matchQueues.clear()
        connectedWaiter.getAndSet(null)?.complete(Unit)
    }

    private data class PeerWaiter(
        val selfUserId: String,
        val deferred: CompletableDeferred<Unit>,
    )

    private sealed interface MatchIngress {
        data class Data(val opcode: Int, val payload: ByteArray) : MatchIngress
        data object Closed : MatchIngress
    }

    private companion object {
        const val PING_INTERVAL_SECONDS = 20L
        const val RECONNECT_DELAY_SECONDS = 2L
        const val RPC_TIMEOUT_MS = 15_000L
    }
}
