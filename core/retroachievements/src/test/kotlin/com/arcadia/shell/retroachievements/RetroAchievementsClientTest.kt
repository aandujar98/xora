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
    }
}
