package com.arcadia.shell.xoranetwork

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * Emulator-owned Nakama storage for netplay session invites. This is not a website collection —
 * the site inbox is read-only, so the app writes a public-read outbox friends can poll.
 */
object XoraNetplayInvites {
    const val COLLECTION = "xora_netplay_invites"
    const val OUTBOX_KEY = "outbox"
    const val TTL_MS = 15L * 60L * 1_000L
    const val MAX_OUTBOX = 8
    const val LOGIN_REQUIRED =
        "Online netplay is exclusive to XOrA Network accounts. Sign in to XOrA Network to use that feature."

    /**
     * `to2:` — not `to:`. The old keys were created with Nakama write permission 0, which blocks
     * every client write forever (even the owner's), so re-inviting the same friend failed with
     * "storage write rejected - permission denied". Those objects can't be overwritten or fixed
     * from the client; a fresh key namespace sidesteps them.
     */
    fun recipientKey(username: String): String = "to2:" + username.trim().lowercase()

    fun isFresh(invite: XoraNetplayInviteRecord, nowMs: Long): Boolean {
        if (invite.createdAtMs <= 0L) return true
        return nowMs - invite.createdAtMs in 0 until TTL_MS
    }

    fun hasJoinableCode(invite: XoraNetplayInviteRecord): Boolean {
        val code = invite.code.trim()
        return code.length == 6 && code.none { it.isWhitespace() }
    }

    fun addressedTo(
        invites: List<XoraNetplayInviteRecord>,
        selfUsername: String,
        nowMs: Long,
    ): List<XoraNetplayInviteRecord> {
        val self = selfUsername.trim().lowercase()
        if (self.isBlank()) return emptyList()
        return invites.filter { invite ->
            isFresh(invite, nowMs) &&
                hasJoinableCode(invite) &&
                invite.toUsername.trim().equals(self, ignoreCase = true)
        }
    }

    fun mergeOutbox(
        existing: List<XoraNetplayInviteRecord>,
        incoming: XoraNetplayInviteRecord,
        nowMs: Long,
    ): List<XoraNetplayInviteRecord> {
        val kept = existing.filter { invite ->
            isFresh(invite, nowMs) &&
                !invite.toUsername.trim().equals(incoming.toUsername.trim(), ignoreCase = true)
        }
        return (listOf(incoming) + kept).take(MAX_OUTBOX)
    }

    fun parseValue(raw: String, json: Json): List<XoraNetplayInviteRecord> {
        val trimmed = raw.trim()
        if (trimmed.isEmpty() || trimmed == "{}" || trimmed == "[]") return emptyList()
        val element = runCatching { json.parseToJsonElement(trimmed) }.getOrNull() ?: return emptyList()
        return parseElement(element, json)
    }

    fun encodeValue(invite: XoraNetplayInviteRecord, json: Json): String =
        json.encodeToString(XoraNetplayInviteRecord.serializer(), invite)

    fun encodeOutbox(invites: List<XoraNetplayInviteRecord>, json: Json): String =
        json.encodeToString(XoraNetplayInviteOutboxDto.serializer(), XoraNetplayInviteOutboxDto(invites))

    private fun parseElement(element: JsonElement, json: Json): List<XoraNetplayInviteRecord> {
        val obj = element as? JsonObject ?: return emptyList()
        val nested = obj["invites"]
        if (nested is JsonArray) {
            return nested.flatMap { parseElement(it, json) }
        }
        if (obj.containsKey("code") || obj.containsKey("toUsername")) {
            val invite = runCatching {
                json.decodeFromJsonElement(XoraNetplayInviteRecord.serializer(), obj)
            }.getOrNull() ?: return emptyList()
            return listOf(invite)
        }
        return emptyList()
    }
}
