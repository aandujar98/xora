package com.arcadia.shell.xoranetwork

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** Presence events from the Nakama realtime socket. Usernames are the public XOrA ids. */
internal sealed interface XoraPresenceEvent {
    data class Joins(val usernames: List<String>) : XoraPresenceEvent
    data class Leaves(val usernames: List<String>) : XoraPresenceEvent
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
        val joins = usernamesInPresence(event["joins"])
        val leaves = usernamesInPresence(event["leaves"])
        val joinKeys = joins.map { it.lowercase() }.toSet()
        val realLeaves = leaves.filter { it.lowercase() !in joinKeys }
        if (joins.isNotEmpty()) events += XoraPresenceEvent.Joins(joins)
        if (realLeaves.isNotEmpty()) events += XoraPresenceEvent.Leaves(realLeaves)
    }
    val status = root["status"] as? JsonObject
    if (status != null) {
        val online = usernamesInPresence(status["presences"])
        if (online.isNotEmpty()) events += XoraPresenceEvent.Joins(online)
    }
    return events
}

internal fun usernamesInPresence(element: JsonElement?): List<String> = when (element) {
    is JsonArray -> element.mapNotNull { usernameFromPresence(it) }
    is JsonObject -> element.values.flatMap { usernamesInPresence(it) }
    else -> emptyList()
}

private fun usernameFromPresence(item: JsonElement): String? {
    val obj = item as? JsonObject ?: return null
    return (obj["username"] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
}
