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
    return XoraMatchDataMessage(matchId = matchId, opcode = opcode, payload = payload)
}

internal fun parseMatchPresenceDelta(root: JsonObject): XoraMatchPresenceDelta? {
    val event = root["match_presence_event"] as? JsonObject ?: return null
    val matchId = jsonString(event["match_id"])?.takeIf { it.isNotBlank() } ?: return null
    return XoraMatchPresenceDelta(
        matchId = matchId,
        joinedUserIds = userIdsInPresence(event["joins"]),
        leftUserIds = userIdsInPresence(event["leaves"]),
    )
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
