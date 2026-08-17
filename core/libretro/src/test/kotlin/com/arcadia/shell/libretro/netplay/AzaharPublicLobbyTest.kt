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
    fun configuredApiIsTriedBeforeHistoricalCitra() {
        val bases = AzaharPublicLobbies.candidateApiBases("https://lobby.community/")
        assertEquals("https://lobby.community", bases.first())
        assertTrue(bases.contains(AzaharPublicLobbies.HISTORICAL_CITRA_API))
        assertEquals(
            listOf(AzaharPublicLobbies.HISTORICAL_CITRA_API),
            AzaharPublicLobbies.candidateApiBases(""),
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
        assertEquals(2, rooms[0].members.size)
        assertEquals("2/8 · Mario Kart 7 · Open", AzaharPublicLobbies.roomSubtitle(rooms[0]))
        assertEquals("0/4 · Smash · Password", AzaharPublicLobbies.roomSubtitle(rooms[1]))
        assertFalse(rooms[0].hasPassword)
        assertTrue(rooms[1].hasPassword)
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
}
