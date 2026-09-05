package com.arcadia.shell.retroachievements

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RetroAchievementsClientTest {

    @Test
    fun `looksLikeHtml detects nginx 403 pages`() {
        val html = """
            <html>
            <head><title>403 Forbidden</title></head>
            <body>
            <center><h1>403 Forbidden</h1></center>
            <hr><center>nginx</center>
            <script src="https://static.cloudflareinsights.com/beacon.min.js"></script>
            </body></html>
        """.trimIndent()
        assertTrue(RetroAchievementsClient.looksLikeHtml(html))
        assertTrue(RetroAchievementsClient.looksLikeHtml("\uFEFF$html"))
    }

    @Test
    fun `sanitizeErrorMessage never returns raw html`() {
        val raw = "HTTP 403: <html><head><title>403 Forbidden</title></head>"
        val safe = RetroAchievementsClient.sanitizeErrorMessage(raw)
        assertEquals("HTTP 403 Forbidden", safe)
        assertFalse(safe.contains('<'))
        assertFalse(safe.contains("nginx", ignoreCase = true))
    }

    @Test
    fun `sanitizeErrorMessage keeps plain errors`() {
        assertEquals(
            "Timed out",
            RetroAchievementsClient.sanitizeErrorMessage("Timed out"),
        )
    }

    @Test
    fun `console ids match rcheevos for disc systems`() {
        assertEquals(12, RaConsoleIds.forPlatform("ps1"))
        assertEquals(21, RaConsoleIds.forPlatform("ps2"))
        assertEquals(41, RaConsoleIds.forPlatform("psp"))
        assertEquals(40, RaConsoleIds.forPlatform("dreamcast"))
        assertEquals(13, RaConsoleIds.forPlatform("atarilynx"))
        assertEquals(29, RaConsoleIds.forPlatform("msx"))
        assertEquals(30, RaConsoleIds.forPlatform("c64"))
        assertEquals(35, RaConsoleIds.forPlatform("amiga"))
    }

    @Test
    fun `gameid Success true GameID match`() {
        val parsed = RetroAchievementsClient.parseGameIdResponse(
            """{"Success":true,"GameID":1234}""",
        )
        assertEquals(RetroAchievementsClient.GameIdParse.Matched(1234), parsed)
    }

    @Test
    fun `gameid Success true GameID zero is unknown hash not blocked`() {
        val parsed = RetroAchievementsClient.parseGameIdResponse(
            """{"Success":true,"GameID":0}""",
        )
        assertEquals(RetroAchievementsClient.GameIdParse.NoMatch, parsed)
    }

    @Test
    fun `gameid unsupported client is blocked not no-match`() {
        // RAWeb GetGameIdFromHashAction returns GameID:0 with Success:false when UA is blocked.
        val parsed = RetroAchievementsClient.parseGameIdResponse(
            """
            {
              "Success": false,
              "Status": 403,
              "Code": "unsupported_client",
              "Error": "This client is not supported.",
              "GameID": 0
            }
            """.trimIndent(),
        )
        assertTrue(parsed is RetroAchievementsClient.GameIdParse.Blocked)
        assertEquals(
            "This client is not supported.",
            (parsed as RetroAchievementsClient.GameIdParse.Blocked).message,
        )
    }

    @Test
    fun `gameid accepts string GameID from legacy payloads`() {
        val parsed = RetroAchievementsClient.parseGameIdResponse(
            """{"Success":true,"GameID":"14402"}""",
        )
        assertEquals(RetroAchievementsClient.GameIdParse.Matched(14402), parsed)
    }

    @Test
    fun `user agent identifies XOrA`() {
        assertTrue(RetroAchievementsClient.USER_AGENT.startsWith("XOrA/"))
        assertTrue(RetroAchievementsClient.USER_AGENT.contains("rcheevos/"))
    }

    @Test
    fun `users I follow parses Results envelope`() {
        val parsed = RetroAchievementsClient.parseUsersIFollow(
            """
            {
              "Count": 2,
              "Total": 2,
              "Results": [
                {
                  "User": "Alice",
                  "ULID": "00003ABC",
                  "UserPic": "/UserPic/AliceLogin.png",
                  "Points": 12000,
                  "PointsSoftcore": 40,
                  "IsFollowingMe": true
                },
                {
                  "User": "Bob",
                  "Points": "800",
                  "PointsSoftcore": "0",
                  "IsFollowingMe": 0
                }
              ]
            }
            """.trimIndent(),
        )
        assertEquals(2, parsed.size)
        assertEquals("Alice", parsed[0].username)
        assertEquals("00003ABC", parsed[0].ulid)
        assertEquals("00003ABC", parsed[0].profileLookup)
        assertEquals("/UserPic/AliceLogin.png", parsed[0].userPicPath)
        assertEquals(
            "https://media.retroachievements.org/UserPic/AliceLogin.png",
            parsed[0].userPicUrl,
        )
        assertEquals(12000, parsed[0].points)
        assertEquals(40, parsed[0].pointsSoftcore)
        assertTrue(parsed[0].isFollowingMe)
        assertEquals("Bob", parsed[1].username)
        assertEquals("", parsed[1].ulid)
        assertEquals("Bob", parsed[1].profileLookup)
        assertEquals(800, parsed[1].points)
        assertFalse(parsed[1].isFollowingMe)
    }

    @Test
    fun `user profile parses UserPic login path`() {
        val parsed = RetroAchievementsClient.parseUserProfile(
            """
            {
              "User": "Display Name",
              "ULID": "00003ABC",
              "UserPic": "/UserPic/LoginName.png",
              "TotalPoints": 900,
              "TotalSoftcorePoints": 12
            }
            """.trimIndent(),
            fallbackUsername = "fallback",
        )
        assertEquals("Display Name", parsed.username)
        assertEquals(900, parsed.totalPoints)
        assertEquals(12, parsed.totalSoftcorePoints)
        assertEquals("/UserPic/LoginName.png", parsed.userPicPath)
        assertEquals(
            "https://media.retroachievements.org/UserPic/LoginName.png",
            parsed.userPicUrl,
        )
    }

    @Test
    fun `userPicUrlFrom prefers profile path over display name`() {
        assertEquals(
            "https://media.retroachievements.org/UserPic/LoginName.png",
            RaProfile.userPicUrlFrom("Display Name", "/UserPic/LoginName.png"),
        )
        assertEquals(
            "https://media.retroachievements.org/UserPic/Already%20Renamed.png",
            RaProfile.userPicUrlFrom("Already Renamed"),
        )
        assertEquals("", RaProfile.userPicUrlFrom("  "))
        assertEquals(
            "https://cdn.example/pic.png",
            RaProfile.userPicUrlFrom("x", "https://cdn.example/pic.png"),
        )
    }

    @Test
    fun `users I follow accepts a bare array`() {
        val parsed = RetroAchievementsClient.parseUsersIFollow(
            """[{"User":"Carol","Points":15}]""",
        )
        assertEquals(listOf(RaFollowedUser("Carol", 15)), parsed)
    }

    @Test
    fun `core clause normalizes libretro suffix`() {
        assertEquals("mupen64plus_next_libretro", RaUserAgent.coreClause("mupen64plus_next"))
        assertEquals("mupen64plus_next_libretro", RaUserAgent.coreClause("mupen64plus_next_libretro.so"))
        assertEquals(null, RaUserAgent.coreClause(null))
    }
}
