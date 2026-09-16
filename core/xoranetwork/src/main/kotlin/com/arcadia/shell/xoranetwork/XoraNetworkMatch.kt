package com.arcadia.shell.xoranetwork

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import java.util.Base64

/** One Nakama relayed match used to carry netplay between two signed-in devices. */
data class XoraNetworkMatchSession(
    val matchId: String,
    val selfUserId: String,
    val presenceUserIds: List<String> = emptyList(),
) {
    fun hasPeer(): Boolean = presenceUserIds.any { it.isNotBlank() && it != selfUserId }
}

internal data class XoraMatchDataMessage(
    val matchId: String,
    val opcode: Int,
    val payload: ByteArray,
    val senderUserId: String = "",
)

internal data class XoraMatchPresenceDelta(
    val matchId: String,
    val joinedUserIds: List<String>,
    val leftUserIds: List<String>,
)

internal fun parseMatchSession(match: JsonObject): XoraNetworkMatchSession? {
    val matchId = jsonString(match["match_id"])?.takeIf { it.isNotBlank() } ?: return null
    val selfUserId = (match["self"] as? JsonObject)?.let { jsonString(it["user_id"]) }.orEmpty()
    val others = userIdsInPresence(match["presences"]).filter { it.isNotBlank() && it != selfUserId }
    return XoraNetworkMatchSession(
        matchId = matchId,
        selfUserId = selfUserId,
        presenceUserIds = others,
    )
}

internal fun parseMatchData(root: JsonObject): XoraMatchDataMessage? {
    val data = root["match_data"] as? JsonObject ?: return null
    val matchId = jsonString(data["match_id"])?.takeIf { it.isNotBlank() } ?: return null
    val opcode = jsonLong(data["op_code"]).toInt()
    val payload = decodeMatchBytes(jsonString(data["data"]).orEmpty())
    val presence = data["presence"] as? JsonObject
    val senderUserId = jsonString(presence?.get("user_id")).orEmpty()
        .ifBlank { jsonString(data["user_id"]).orEmpty() }
    return XoraMatchDataMessage(
        matchId = matchId,
        opcode = opcode,
        payload = payload,
        senderUserId = senderUserId,
    )
}

internal fun parseMatchPresenceDelta(root: JsonObject): XoraMatchPresenceDelta? {
    val event = root["match_presence_event"] as? JsonObject ?: return null
    val matchId = jsonString(event["match_id"])?.takeIf { it.isNotBlank() } ?: return null
    val joined = userIdsInPresence(event["joins"])
    val left = userIdsInPresence(event["leaves"])
    return XoraMatchPresenceDelta(
        matchId = matchId,
        joinedUserIds = joined,
        leftUserIds = netPresenceLeaves(joined, left),
    )
}

/**
 * Nakama often reports the same user in both joins and leaves when their session id
 * refreshes. Counting that as a leave used to drop the joiner and close the lobby.
 */
internal fun netPresenceLeaves(joined: List<String>, left: List<String>): List<String> {
    val joinedSet = joined.filter { it.isNotBlank() }.toSet()
    return left.filter { it.isNotBlank() && it !in joinedSet }
}

/**
 * Presence count dropping 2→1 must not close the match socket. Nakama often sends the
 * joiner's old session_id as a *later* leave than the join; treating that as Closed
 * threw "The other player left" and reset the host lobby. Real disconnects are BYE
 * or the websocket dropping.
 */
internal fun presenceShouldCloseMatch(beforeCount: Int, afterCount: Int): Boolean {
    // A 2→1 drop is usually a session_id refresh, not a real disconnect.
    return beforeCount < 0 && afterCount < 0
}

internal fun parseRealtimeErrorMessage(root: JsonObject): String? {
    val error = root["error"] as? JsonObject ?: return null
    val raw = jsonString(error["message"]).orEmpty()
    return when {
        raw.contains("match not found", ignoreCase = true) ->
            "No session for that code."
        raw.isBlank() -> "Couldn't start that online session."
        else -> "Couldn't start that online session."
    }
}

internal fun userIdsInPresence(element: JsonElement?): List<String> = when (element) {
    is JsonArray -> element.mapNotNull { userIdFromPresence(it) }
    is JsonObject -> element.values.flatMap { userIdsInPresence(it) }
    else -> emptyList()
}

private fun userIdFromPresence(item: JsonElement): String? {
    val obj = item as? JsonObject ?: return null
    return jsonString(obj["user_id"])?.takeIf { it.isNotBlank() }
}

internal fun jsonString(element: JsonElement?): String? =
    (element as? JsonPrimitive)?.contentOrNull?.trim()

internal fun jsonLong(element: JsonElement?): Long {
    val primitive = element as? JsonPrimitive ?: return 0L
    return primitive.longOrNull ?: primitive.contentOrNull?.toLongOrNull() ?: 0L
}

internal fun decodeMatchBytes(raw: String): ByteArray {
    if (raw.isBlank()) return ByteArray(0)
    val cleaned = raw.filterNot { it.isWhitespace() }
    return runCatching { Base64.getDecoder().decode(cleaned) }
        .recoverCatching { Base64.getUrlDecoder().decode(cleaned) }
        .getOrDefault(ByteArray(0))
}

internal fun encodeMatchBytes(data: ByteArray): String =
    Base64.getEncoder().encodeToString(data)
