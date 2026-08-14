package com.arcadia.shell.xoranetwork

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/** Presence events from the Nakama realtime socket. Usernames are the public XOrA ids. */
internal sealed interface XoraPresenceEvent {
    data class Joins(val usernames: List<String>) : XoraPresenceEvent
    data class Leaves(val usernames: List<String>) : XoraPresenceEvent
}

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

    @Volatile
    private var listener: ((XoraPresenceEvent) -> Unit)? = null

    internal fun setListener(onEvent: ((XoraPresenceEvent) -> Unit)?) {
        listener = onEvent
    }

    internal fun connect(accessToken: String) {
        disconnect()
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
                        socket.compareAndSet(webSocket, null)
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        socket.compareAndSet(webSocket, null)
                    }
                },
            ),
        )
    }

    internal fun follow(usernames: Collection<String>) {
        val next = usernames.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        followed.set(next)
        val current = socket.get() ?: return
        if (next.isNotEmpty()) sendFollow(current, next)
    }

    internal fun disconnect() {
        followed.set(emptySet())
        socket.getAndSet(null)?.cancel()
    }

    internal val isConnected: Boolean get() = socket.get() != null

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
        val event = root["status_presence_event"] as? JsonObject
        if (event != null) {
            val joins = usernamesIn(event["joins"])
            val leaves = usernamesIn(event["leaves"])
            if (joins.isNotEmpty()) listener?.invoke(XoraPresenceEvent.Joins(joins))
            if (leaves.isNotEmpty()) listener?.invoke(XoraPresenceEvent.Leaves(leaves))
            return
        }
        val status = root["status"] as? JsonObject
        if (status != null) {
            val online = usernamesIn(status["presences"])
            if (online.isNotEmpty()) listener?.invoke(XoraPresenceEvent.Joins(online))
        }
    }

    private fun usernamesIn(element: kotlinx.serialization.json.JsonElement?): List<String> {
        val array = element as? JsonArray ?: return emptyList()
        return array.mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            (obj["username"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        }
    }

    private companion object {
        const val PING_INTERVAL_SECONDS = 20L
    }
}
