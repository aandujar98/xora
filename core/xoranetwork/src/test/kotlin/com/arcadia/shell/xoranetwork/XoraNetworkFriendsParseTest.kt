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
    fun websiteFriendFallsBackToIdWhenUsernameOmitted() {
        val payload = """{"username":"","id":"xoraadmin","displayName":"Admin","state":"friend"}"""
        val friend = json.decodeFromString<WebsiteFriendDto>(payload)
            .toXoraFriend(XoraFriendState.Friend)!!
        assertEquals("xoraadmin", friend.username)
        assertEquals("Admin", friend.displayName)
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

    @Test
    fun websiteEnvelopeWithoutOkStillParsesFriends() {
        val payload = """{"data":{"friends":[{"username":"pal","state":"friend"}],"incoming":[],"outgoing":[]}}"""
        val envelope = json.decodeFromString<WebsiteFriendsResponseDto>(payload)
        assertTrue(envelope.ok)
        assertEquals("pal", envelope.data.friends.single().username)
    }

    @Test
    fun statusUpdateJoinAndLeaveIsStillOnline() {
        val payload = """
            {"status_presence_event":{"joins":[{"user_id":"98d90fe7-5296-4835-a649-a00dfe27fad9","session_id":"abc","username":"xoraadmin","status":"Online"}],"leaves":[{"user_id":"98d90fe7-5296-4835-a649-a00dfe27fad9","session_id":"abc","username":"xoraadmin","status":""}]}}
        """.trimIndent()
        val root = json.parseToJsonElement(payload) as kotlinx.serialization.json.JsonObject
        val events = parseXoraPresenceMessage(root)
        assertEquals(1, events.size)
        val joins = events.single() as XoraPresenceEvent.Joins
        assertEquals(listOf("xoraadmin"), joins.usernames)
    }

    @Test
    fun realLeaveIsOffline() {
        val payload = """
            {"status_presence_event":{"leaves":[{"user_id":"33e69f24-39b1-4e15-b5bc-48c5aacd6bf7","session_id":"abc","username":"xora_agent_pal","status":"Online"}]}}
        """.trimIndent()
        val root = json.parseToJsonElement(payload) as kotlinx.serialization.json.JsonObject
        val events = parseXoraPresenceMessage(root)
        val leaves = events.single() as XoraPresenceEvent.Leaves
        assertEquals(listOf("xora_agent_pal"), leaves.usernames)
    }

    @Test
    fun statusFollowSnapshotMarksFriendsOnline() {
        val payload = """
            {"cid":"2","status":{"presences":[{"user_id":"33e69f24-39b1-4e15-b5bc-48c5aacd6bf7","session_id":"abc","username":"xora_agent_pal","status":"Online"}]}}
        """.trimIndent()
        val root = json.parseToJsonElement(payload) as kotlinx.serialization.json.JsonObject
        val events = parseXoraPresenceMessage(root)
        val joins = events.single() as XoraPresenceEvent.Joins
        assertEquals(listOf("xora_agent_pal"), joins.usernames)
    }

    @Test
    fun emptyStatusJoinOnConnectIsOnline() {
        val payload = """
            {"status_presence_event":{"joins":[{"user_id":"1","session_id":"s","username":"xoraadmin","status":""}]}}
        """.trimIndent()
        val root = json.parseToJsonElement(payload) as kotlinx.serialization.json.JsonObject
        val joins = parseXoraPresenceMessage(root).single() as XoraPresenceEvent.Joins
        assertEquals(listOf("xoraadmin"), joins.usernames)
    }

    @Test
    fun playingStatusIsKeptOnJoin() {
        val payload = """
            {"status_presence_event":{"joins":[{"user_id":"1","session_id":"s","username":"pal","status":"Playing Super Mario 64"}]}}
        """.trimIndent()
        val root = json.parseToJsonElement(payload) as kotlinx.serialization.json.JsonObject
        val joins = parseXoraPresenceMessage(root).single() as XoraPresenceEvent.Joins
        assertEquals("pal", joins.users.single().username)
        assertEquals("Playing Super Mario 64", joins.users.single().status)
    }

    @Test
    fun encodeXoraStatusPublishesPlayingAwayBusyAndHidesInvisible() {
        assertEquals("Online", encodeXoraStatus(XoraPresenceMode.Online, null))
        assertEquals("Online", encodeXoraStatus(XoraPresenceMode.Online, "Browsing XOrA"))
        assertEquals("Playing Super Mario 64", encodeXoraStatus(XoraPresenceMode.Online, "playing Super Mario 64"))
        assertEquals("Away", encodeXoraStatus(XoraPresenceMode.Away, "playing Super Mario 64"))
        assertEquals("Busy", encodeXoraStatus(XoraPresenceMode.Busy, null))
        assertEquals("", encodeXoraStatus(XoraPresenceMode.Invisible, "playing Super Mario 64"))
    }

    @Test
    fun parseXoraPresenceModeAcceptsOfflineAsInvisible() {
        assertEquals(XoraPresenceMode.Online, parseXoraPresenceMode("Online"))
        assertEquals(XoraPresenceMode.Away, parseXoraPresenceMode("away"))
        assertEquals(XoraPresenceMode.Busy, parseXoraPresenceMode("Busy"))
        assertEquals(XoraPresenceMode.Invisible, parseXoraPresenceMode("Invisible"))
        assertEquals(XoraPresenceMode.Invisible, parseXoraPresenceMode("Offline"))
        assertEquals(XoraPresenceMode.Online, parseXoraPresenceMode("nope"))
    }

    @Test
    fun appearanceLabelHidesInvisibleAndOfflineSocket() {
        assertEquals("Online", xoraAppearanceLabel(XoraPresenceMode.Online, selfOnline = true))
        assertEquals("Away", xoraAppearanceLabel(XoraPresenceMode.Away, selfOnline = true))
        assertEquals("Busy", xoraAppearanceLabel(XoraPresenceMode.Busy, selfOnline = true))
        assertEquals("Offline", xoraAppearanceLabel(XoraPresenceMode.Invisible, selfOnline = false))
        assertEquals("Offline", xoraAppearanceLabel(XoraPresenceMode.Online, selfOnline = false))
    }

    @Test
    fun websiteMessageThreadParsesBodies() {
        val payload = """
            {"ok":true,"data":{"username":"pal","displayName":"Pal","messages":[{"id":"1","fromUsername":"xoraadmin","body":"hello","createdAt":"2026-08-14T21:00:00Z"}]}}
        """.trimIndent()
        val envelope = json.decodeFromString<WebsiteMessageThreadResponseDto>(payload)
        assertTrue(envelope.ok)
        assertEquals("pal", envelope.data.username)
        assertEquals("hello", envelope.data.messages.single().body)
        assertEquals("xoraadmin", envelope.data.messages.single().fromUsername)
    }
}
