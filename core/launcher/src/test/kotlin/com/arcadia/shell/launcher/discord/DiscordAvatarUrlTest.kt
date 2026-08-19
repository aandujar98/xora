package com.arcadia.shell.launcher.discord

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DiscordAvatarUrlTest {

    @Test
    fun `animated hash png becomes gif`() {
        assertEquals(
            "https://cdn.discordapp.com/avatars/123/a_abc123.gif",
            preferAnimatedDiscordAvatarUrl(
                "https://cdn.discordapp.com/avatars/123/a_abc123.png",
            ),
        )
    }

    @Test
    fun `animated hash webp keeps query`() {
        assertEquals(
            "https://cdn.discordapp.com/avatars/123/a_abc123.gif?size=256",
            preferAnimatedDiscordAvatarUrl(
                "https://cdn.discordapp.com/avatars/123/a_abc123.webp?size=256",
            ),
        )
    }

    @Test
    fun `static hash stays png`() {
        val url = "https://cdn.discordapp.com/avatars/123/deadbeef.png"
        assertEquals(url, preferAnimatedDiscordAvatarUrl(url))
    }

    @Test
    fun `already gif is unchanged`() {
        val url = "https://cdn.discordapp.com/avatars/123/a_abc123.gif"
        assertEquals(url, preferAnimatedDiscordAvatarUrl(url))
    }

    @Test
    fun `blank and null pass through`() {
        assertNull(preferAnimatedDiscordAvatarUrl(null))
        assertEquals("", preferAnimatedDiscordAvatarUrl(""))
        assertEquals("   ", preferAnimatedDiscordAvatarUrl("   "))
    }

    @Test
    fun `friends payload rewrites sdk png for animated hashes`() {
        val payload = "99\tPal\tanimated\thttps://cdn.discordapp.com/avatars/99/a_hash.png\n"
        val friend = DiscordSocialSdkBridge.parseFriendsPayload(payload).single()
        assertEquals("https://cdn.discordapp.com/avatars/99/a_hash.gif", friend.avatarUrl)
    }
}
