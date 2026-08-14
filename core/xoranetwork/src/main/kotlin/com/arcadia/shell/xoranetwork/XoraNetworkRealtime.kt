package com.arcadia.shell.xoranetwork

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.Executors
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
    private val opened = AtomicBoolean(false)
    private val reconnecter: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "xora-presence-reconnect").apply { isDaemon = true }
    }

    @Volatile
    private var listener: ((XoraPresenceEvent) -> Unit)? = null

    internal fun setListener(onEvent: ((XoraPresenceEvent) -> Unit)?) {
        listener = onEvent
    }

    internal fun connect(accessToken: String) {
        wantConnected.set(true)
        tokenRef.set(accessToken)
        closeSocket(notify = false)
        openSocket(accessToken)
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

    private fun openSocket(accessToken: String) {
        val https = "https://api.xoranetwork.com/ws".toHttpUrl().newBuilder()
            .addQueryParameter("lang", "en")
            .addQueryParameter("format", "json")
            .addQueryParameter("status", "true")
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
                        listener?.invoke(XoraPresenceEvent.Connected)
                        sendStatus(webSocket, "Online")
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
        if (notify && wasOpen) listener?.invoke(XoraPresenceEvent.Disconnected)
    }

    private fun handleDrop(webSocket: WebSocket) {
        val dropped = socket.compareAndSet(webSocket, null)
        val wasOpen = opened.getAndSet(false)
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
        parseXoraPresenceMessage(root).forEach { event -> listener?.invoke(event) }
    }

    private companion object {
        const val PING_INTERVAL_SECONDS = 20L
        const val RECONNECT_DELAY_SECONDS = 2L
    }
}
