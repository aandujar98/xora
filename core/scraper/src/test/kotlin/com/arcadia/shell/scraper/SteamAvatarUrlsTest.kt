package com.arcadia.shell.scraper

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SteamAvatarUrlsTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `player summary prefers nested animated gif over jpg`() {
        val obj = json.parseToJsonElement(
            """
            {
              "avatarfull": "https://avatars.steamstatic.com/abc_full.jpg",
              "animated_avatar": {
                "image_large": "https://cdn.akamai.steamstatic.com/steamcommunity/public/images/items/1/a.gif"
              }
            }
            """.trimIndent(),
        ).jsonObject
        assertEquals(
            "https://cdn.akamai.steamstatic.com/steamcommunity/public/images/items/1/a.gif",
            SteamAvatarUrls.fromPlayerSummary(obj),
        )
    }

    @Test
    fun `player summary falls back to avatarfull`() {
        val obj = json.parseToJsonElement(
            """{"avatarfull":"https://avatars.steamstatic.com/abc_full.jpg"}""",
        ).jsonObject
        assertEquals(
            "https://avatars.steamstatic.com/abc_full.jpg",
            SteamAvatarUrls.fromPlayerSummary(obj),
        )
    }

    @Test
    fun `equipped items skip mp4 and keep gif`() {
        val obj = json.parseToJsonElement(
            """
            {
              "response": {
                "animated_avatar": {
                  "image_small": "https://cdn.example/a.mp4",
                  "image_large": "https://cdn.example/a.gif"
                }
              }
            }
            """.trimIndent(),
        ).jsonObject
        assertEquals("https://cdn.example/a.gif", SteamAvatarUrls.fromEquippedProfileItems(obj))
    }

    @Test
    fun `equipped items without animated avatar is null`() {
        val obj = json.parseToJsonElement("""{"response":{}}""").jsonObject
        assertNull(SteamAvatarUrls.fromEquippedProfileItems(obj))
    }
}
