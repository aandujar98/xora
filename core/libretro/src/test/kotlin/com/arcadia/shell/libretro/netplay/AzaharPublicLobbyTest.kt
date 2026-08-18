package com.arcadia.shell.libretro.netplay

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AzaharPublicLobbyTest {

    @Test
    fun publicLobbyKindIsNdsAnd3dsOnly() {
        assertEquals(PublicLobbyKind.NdsWfc, publicLobbyKind("nds"))
        assertEquals(PublicLobbyKind.NdsWfc, publicLobbyKind("NDS"))
        assertEquals(PublicLobbyKind.AzaharRooms, publicLobbyKind("3ds"))
        assertEquals(PublicLobbyKind.None, publicLobbyKind("gba"))
        assertEquals(PublicLobbyKind.None, publicLobbyKind("psp"))
        assertEquals(PublicLobbyKind.None, publicLobbyKind("snes"))
    }

    @Test
    fun lobbyRequestUrlAppendsLobbyOnce() {
        assertEquals(
            "https://api.example/lobby",
            AzaharPublicLobbies.lobbyRequestUrl("https://api.example/"),
        )
        assertEquals(
            "https://api.example/lobby",
            AzaharPublicLobbies.lobbyRequestUrl("https://api.example/lobby"),
        )
        assertEquals("", AzaharPublicLobbies.lobbyRequestUrl("   "))
    }

    @Test
    fun configuredApiIsTriedBeforeCommunityThenHistoricalCitra() {
        val bases = AzaharPublicLobbies.candidateApiBases("https://lobby.community/")
        assertEquals("https://lobby.community", bases.first())
        assertTrue(bases.contains(AzaharPublicLobbies.COMMUNITY_AZAHAR_API))
        assertTrue(bases.contains(AzaharPublicLobbies.HISTORICAL_CITRA_API))
        assertEquals(
            listOf(
                AzaharPublicLobbies.COMMUNITY_AZAHAR_API,
                AzaharPublicLobbies.HISTORICAL_CITRA_API,
            ),
            AzaharPublicLobbies.candidateApiBases(""),
        )
    }

    @Test
    fun parseLobbyJsonReadsCommunityCamelCaseAddressRooms() {
        val rooms = AzaharPublicLobbies.parseLobbyJson(
            """
            {
              "rooms": [
                {
                  "name": "Kex's Public Monster Hunter Room (EU) #1",
                  "address": "88.198.47.46",
                  "port": 5001,
                  "maxPlayers": 8,
                  "hasPassword": false,
                  "preferredGameName": "Monster Hunter XX",
                  "players": [
                    { "nickname": "Leon", "gameName": "MONSTER HUNTER 4 ULTIMATE" }
                  ]
                }
              ]
            }
            """.trimIndent(),
        )
        assertEquals(1, rooms.size)
        assertEquals("88.198.47.46", rooms[0].ip)
        assertEquals(5001, rooms[0].port)
        assertEquals("88.198.47.46:5001", AzaharPublicLobbies.directConnect(rooms[0]))
        assertEquals(
            "88.198.47.46:5001 · Kex's Public Monster Hunter Room (EU) #1",
            AzaharPublicLobbies.roomTitle(rooms[0]),
        )
        assertEquals("Monster Hunter XX", rooms[0].preferredGame)
        assertEquals("Leon", rooms[0].members[0].nickname)
        assertTrue(
            AzaharPublicLobbies.roomSubtitle(rooms[0]).contains("88.198.47.46:5001"),
        )
    }

    @Test
    fun parseLobbyJsonReadsCitraRooms() {
        val rooms = AzaharPublicLobbies.parseLobbyJson(
            """
            {
              "rooms": [
                {
                  "id": "abc",
                  "name": "MK7 Night",
                  "description": "open",
                  "owner": "host",
                  "ip": "1.2.3.4",
                  "port": 24872,
                  "max_player": 8,
                  "net_version": 4,
                  "has_password": false,
                  "preferred_game": "Mario Kart 7",
                  "preferred_game_id": 123,
                  "members": [
                    {
                      "username": "p1",
                      "nickname": "P1",
                      "avatar_url": "",
                      "game_name": "Mario Kart 7",
                      "game_id": 123
                    },
                    {
                      "username": "p2",
                      "nickname": "P2",
                      "game_name": "Mario Kart 7"
                    }
                  ]
                },
                {
                  "name": "Locked room",
                  "has_password": true,
                  "max_player": 4,
                  "preferred_game": "Smash"
                }
              ]
            }
            """.trimIndent(),
        )
        assertEquals(2, rooms.size)
        assertEquals("MK7 Night", rooms[0].name)
        assertEquals("1.2.3.4:24872 · MK7 Night", AzaharPublicLobbies.roomTitle(rooms[0]))
        assertEquals("Locked room", AzaharPublicLobbies.roomTitle(rooms[1]))
        assertEquals(2, rooms[0].members.size)
        assertEquals("2/8 · Mario Kart 7 · Open · 1.2.3.4:24872", AzaharPublicLobbies.roomSubtitle(rooms[0]))
        assertEquals("0/4 · Smash · Password", AzaharPublicLobbies.roomSubtitle(rooms[1]))
        assertEquals("1.2.3.4:24872", AzaharPublicLobbies.directConnect(rooms[0]))
        assertEquals("", AzaharPublicLobbies.directConnect(rooms[1]))
        assertFalse(rooms[0].hasPassword)
        assertTrue(rooms[1].hasPassword)
    }

    @Test
    fun directConnectRequiresIpAndPort() {
        assertEquals(
            "10.0.0.8:24872",
            AzaharPublicLobbies.directConnect(
                AzaharPublicRoom(ip = " 10.0.0.8 ", port = 24872),
            ),
        )
        assertEquals("", AzaharPublicLobbies.directConnect(AzaharPublicRoom(ip = "10.0.0.8", port = 0)))
        assertEquals("", AzaharPublicLobbies.directConnect(AzaharPublicRoom(port = 24872)))
    }

    @Test
    fun parseLobbyJsonIgnoresUnknownKeysAndBlankRooms() {
        val rooms = AzaharPublicLobbies.parseLobbyJson(
            """
            { "rooms": [ { "extra": true }, { "name": "Alive" } ], "unused": 1 }
            """.trimIndent(),
        )
        assertEquals(1, rooms.size)
        assertEquals("Alive", rooms[0].name)
        assertEquals("0/0 · No game set · Open", AzaharPublicLobbies.roomSubtitle(rooms[0]))
    }

    @Test
    fun marioKart7OpenRoomIsPinnedWhenRegistryHasNoMk7() {
        val live = listOf(
            AzaharPublicRoom(
                name = "MH hall",
                ip = "1.1.1.1",
                port = 24872,
                preferredGame = "Monster Hunter 4 Ultimate",
            ),
        )
        val shown = AzaharPublicLobbies.displayRooms(live)
        assertEquals("Mario Kart 7 · open", shown[0].name)
        assertEquals("Mario Kart 7", shown[0].preferredGame)
        assertFalse(shown[0].hasPassword)
        assertEquals("198.57.46.213:5000", AzaharPublicLobbies.directConnect(shown[0]))
        assertEquals("MH hall", shown[1].name)
    }

    @Test
    fun emptyRegistryStillShowsMarioKart7Open() {
        val shown = AzaharPublicLobbies.displayRooms(emptyList())
        assertEquals(1, shown.size)
        assertEquals(AzaharPublicLobbies.MARIO_KART_7_OPEN, shown[0])
    }

    @Test
    fun passwordedMarioKart7DoesNotReplaceOpenFallback() {
        val live = AzaharPublicRoom(
            name = "Mario Kart 7 private",
            ip = "8.8.8.8",
            port = 24872,
            hasPassword = true,
            preferredGame = "Mario Kart 7",
        )
        val shown = AzaharPublicLobbies.displayRooms(listOf(live))
        assertEquals("198.57.46.213:5000", AzaharPublicLobbies.directConnect(shown[0]))
        assertEquals("Mario Kart 7 private", shown[1].name)
    }

    @Test
    fun titleIdAloneCountsAsMarioKart7() {
        assertTrue(
            AzaharPublicLobbies.isMarioKart7(
                AzaharPublicRoom(name = "Public hall", preferredGameId = 0x0004000000030600L),
            ),
        )
    }

    @Test
    fun liveOpenMarioKart7RoomWinsOverFeaturedFallback() {
        val live = AzaharPublicRoom(
            name = "MK7 Night",
            ip = "9.9.9.9",
            port = 24872,
            hasPassword = false,
            preferredGame = "Mario Kart 7",
            preferredGameId = 0x0004000000030700L,
        )
        val shown = AzaharPublicLobbies.displayRooms(listOf(live))
        assertEquals(1, shown.size)
        assertEquals("9.9.9.9:24872", AzaharPublicLobbies.directConnect(shown[0]))
        assertTrue(AzaharPublicLobbies.isMarioKart7(live))
        assertFalse(
            AzaharPublicLobbies.isMarioKart7(
                AzaharPublicRoom(name = "MH4U", preferredGame = "Monster Hunter"),
            ),
        )
    }
}
