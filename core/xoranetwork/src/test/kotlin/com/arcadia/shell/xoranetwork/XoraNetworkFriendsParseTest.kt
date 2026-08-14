package com.arcadia.shell.xoranetwork

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XoraNetworkFriendsParseTest {

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    @Test
    fun nakamaFriendListParsesIntegerStateAndExtraFields() {
        val payload = """
            {"friends":[{"user":{"id":"33e69f24-39b1-4e15-b5bc-48c5aacd6bf7","username":"xora_agent_pal","lang_tag":"en","metadata":"{}","create_time":"2026-08-14T21:32:35Z","update_time":"2026-08-14T21:32:35Z"},"state":0,"update_time":"2026-08-14T21:32:36Z","metadata":"{}"}]}
        """.trimIndent()
        val list = json.decodeFromString<ApiFriendListDto>(payload)
        val friends = list.friends.mapNotNull { it.toXoraFriend() }
        assertEquals(1, friends.size)
        assertEquals("xora_agent_pal", friends[0].username)
        assertEquals(XoraFriendState.Friend, friends[0].state)
    }

    @Test
    fun nakamaFriendListParsesStringState() {
        val payload = """{"friends":[{"user":{"username":"pal"},"state":"incoming"}]}"""
        val list = json.decodeFromString<ApiFriendListDto>(payload)
        assertEquals(XoraFriendState.IncomingInvite, list.friends.single().toXoraFriend()?.state)
    }

    @Test
    fun emptyObjectIsEmptyFriendsNotAParseFailure() {
        val list = json.decodeFromString<ApiFriendListDto>("{}")
        assertTrue(list.friends.isEmpty())
    }

    @Test
    fun websiteFriendsEnvelopeParsesCamelCase() {
        val payload = """
            {"ok":true,"data":{"friends":[{"id":"xora_agent_pal","username":"xora_agent_pal","displayName":"Pal","avatarUrl":"/api/avatars/xora_agent_pal","location":"","createdAt":"2026-08-14T21:32:35.000Z","online":false,"state":"friend"}],"incoming":[],"outgoing":[]}}
        """.trimIndent()
        val envelope = json.decodeFromString<WebsiteFriendsResponseDto>(payload)
        assertTrue(envelope.ok)
        val friend = envelope.data.friends.single().toXoraFriend(XoraFriendState.Friend)!!
        assertEquals("xora_agent_pal", friend.username)
        assertEquals("Pal", friend.displayName)
        assertEquals(XoraFriendState.Friend, friend.state)
        assertEquals("/api/avatars/xora_agent_pal", friend.avatarUrl)
    }

    @Test
    fun mergePrefersFilledAvatarAndIncomingInvites() {
        val nakama = listOf(
            XoraFriend("pal", "pal", "", online = false, state = XoraFriendState.Friend),
        )
        val website = listOf(
            XoraFriend("pal", "Pal", "/api/avatars/pal", online = true, state = XoraFriendState.Friend),
        )
        val merged = mergeXoraFriends(nakama, website)
        assertEquals(1, merged.size)
        assertEquals("pal", merged[0].displayName)
        assertEquals("/api/avatars/pal", merged[0].avatarUrl)
        assertTrue(merged[0].online)
    }
}
