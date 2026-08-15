package com.arcadia.shell.xoranetwork

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class XoraNetworkMatchParseTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun namedMatchParsesSelfAndPeers() {
        val payload = """
            {"match":{"match_id":"abc.nakama","self":{"user_id":"host-id","session_id":"s1","username":"host"},"presences":[{"user_id":"join-id","session_id":"s2","username":"joiner"}]}}
        """.trimIndent()
        val root = json.parseToJsonElement(payload) as JsonObject
        val match = parseMatchSession(root["match"] as JsonObject)!!
        assertEquals("abc.nakama", match.matchId)
        assertEquals("host-id", match.selfUserId)
        assertEquals(listOf("join-id"), match.presenceUserIds)
        assertTrue(match.hasPeer())
    }

    @Test
    fun emptyMatchHasNoPeer() {
        val payload = """
            {"match_id":"solo.nakama","self":{"user_id":"host-id","username":"host"},"presences":[]}
        """.trimIndent()
        val match = parseMatchSession(json.parseToJsonElement(payload) as JsonObject)!!
        assertFalse(match.hasPeer())
    }

    @Test
    fun matchDataDecodesBase64AndStringOpcode() {
        val bytes = byteArrayOf(1, 2, 3, 4)
        val encoded = Base64.getEncoder().encodeToString(bytes)
        val payload = """
            {"match_data":{"match_id":"m1","op_code":"1","data":"$encoded"}}
        """.trimIndent()
        val message = parseMatchData(json.parseToJsonElement(payload) as JsonObject)!!
        assertEquals("m1", message.matchId)
        assertEquals(1, message.opcode)
        assertArrayEquals(bytes, message.payload)
        assertEquals("", message.senderUserId)
    }

    @Test
    fun matchDataReadsSenderUserIdFromPresence() {
        val bytes = byteArrayOf(9, 8, 7)
        val encoded = Base64.getEncoder().encodeToString(bytes)
        val payload = """
            {"match_data":{"match_id":"m1","op_code":3,"data":"$encoded","presence":{"user_id":"host-id","session_id":"s1"}}}
        """.trimIndent()
        val message = parseMatchData(json.parseToJsonElement(payload) as JsonObject)!!
        assertEquals(3, message.opcode)
        assertEquals("host-id", message.senderUserId)
        assertArrayEquals(bytes, message.payload)
    }

    @Test
    fun matchPresenceEventReadsJoinsAndLeaves() {
        val payload = """
            {"match_presence_event":{"match_id":"m1","joins":[{"user_id":"peer"}],"leaves":[]}}
        """.trimIndent()
        val delta = parseMatchPresenceDelta(json.parseToJsonElement(payload) as JsonObject)!!
        assertEquals("m1", delta.matchId)
        assertEquals(listOf("peer"), delta.joinedUserIds)
        assertTrue(delta.leftUserIds.isEmpty())
    }

    @Test
    fun realtimeErrorsStayFriendly() {
        val payload = """{"cid":"3","error":{"code":3,"message":"match not found"}}"""
        val message = parseRealtimeErrorMessage(json.parseToJsonElement(payload) as JsonObject)
        assertEquals("No session for that code.", message)
    }
}
