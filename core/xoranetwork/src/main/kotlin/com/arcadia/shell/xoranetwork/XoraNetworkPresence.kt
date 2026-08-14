package com.arcadia.shell.xoranetwork

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

internal data class XoraPresenceUser(
    val username: String,
    val status: String = "",
)

/** Presence events from the Nakama realtime socket. Usernames are the public XOrA ids. */
internal sealed interface XoraPresenceEvent {
    data class Joins(val users: List<XoraPresenceUser>) : XoraPresenceEvent {
        val usernames: List<String> get() = users.map { it.username }
    }
    data class Leaves(val users: List<XoraPresenceUser>) : XoraPresenceEvent {
        val usernames: List<String> get() = users.map { it.username }
    }
    data object Connected : XoraPresenceEvent
    data object Disconnected : XoraPresenceEvent
}

/**
 * Nakama emits a leave of the old status plus a join of the new status for the same user when
 * they update their message (including our own `status_update` to "Online"). Those leaves are
 * not actually going offline.
 */
internal fun parseXoraPresenceMessage(root: JsonObject): List<XoraPresenceEvent> {
    val events = ArrayList<XoraPresenceEvent>(2)
    val event = root["status_presence_event"] as? JsonObject
    if (event != null) {
        val joins = usersInPresence(event["joins"])
        val leaves = usersInPresence(event["leaves"])
        val joinKeys = joins.map { it.username.lowercase() }.toSet()
        val realLeaves = leaves.filter { it.username.lowercase() !in joinKeys }
        if (joins.isNotEmpty()) events += XoraPresenceEvent.Joins(joins)
        if (realLeaves.isNotEmpty()) events += XoraPresenceEvent.Leaves(realLeaves)
    }
    val status = root["status"] as? JsonObject
    if (status != null) {
        val online = usersInPresence(status["presences"])
        if (online.isNotEmpty()) events += XoraPresenceEvent.Joins(online)
    }
    return events
}

internal fun usersInPresence(element: JsonElement?): List<XoraPresenceUser> = when (element) {
    is JsonArray -> element.mapNotNull { userFromPresence(it) }
    is JsonObject -> element.values.flatMap { usersInPresence(it) }
    else -> emptyList()
}

private fun userFromPresence(item: JsonElement): XoraPresenceUser? {
    val obj = item as? JsonObject ?: return null
    val username = (obj["username"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
        ?: return null
    val status = (obj["status"] as? JsonPrimitive)?.contentOrNull.orEmpty()
    return XoraPresenceUser(username = username, status = status)
}

/** How this device should appear to other XOrA Network users. */
enum class XoraPresenceMode {
    Online,
    Away,
    Busy,
    /** Signed in, but the socket does not advertise presence. */
    Invisible,
}

fun parseXoraPresenceMode(raw: String?): XoraPresenceMode {
    val value = raw?.trim().orEmpty()
    if (value.equals("Offline", ignoreCase = true)) return XoraPresenceMode.Invisible
    return XoraPresenceMode.entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
        ?: XoraPresenceMode.Online
}

/** Local chip / header copy for how this device currently appears. */
fun xoraAppearanceLabel(mode: XoraPresenceMode, selfOnline: Boolean): String = when {
    mode == XoraPresenceMode.Invisible || !selfOnline -> "Offline"
    mode == XoraPresenceMode.Away -> "Away"
    mode == XoraPresenceMode.Busy -> "Busy"
    else -> "Online"
}

fun encodeXoraStatus(mode: XoraPresenceMode, playingLine: String?): String = when (mode) {
    XoraPresenceMode.Invisible -> ""
    XoraPresenceMode.Away -> "Away"
    XoraPresenceMode.Busy -> "Busy"
    XoraPresenceMode.Online -> {
        val playing = playingLine?.trim().orEmpty()
        when {
            playing.startsWith("playing ", ignoreCase = true) ->
                "Playing ${playing.substringAfter(' ').trim()}"
            playing.startsWith("Playing ") -> playing
            playing.isNotBlank() &&
                !playing.equals("Online", ignoreCase = true) &&
                !playing.equals("Browsing XOrA", ignoreCase = true) &&
                !playing.startsWith("Browsing ", ignoreCase = true) ->
                playing
            else -> "Online"
        }
    }
}
