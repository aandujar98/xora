package com.arcadia.shell.scraper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SteamOpenIdTest {

    @Test
    fun `authorizationUrl includes realm and return_to`() {
        val url = SteamOpenId.authorizationUrl()
        assertTrue(url.startsWith("https://steamcommunity.com/openid/login?"))
        assertEquals(SteamOpenId.RETURN_TO, url.queryParam("openid.return_to"))
        assertEquals(SteamOpenId.REALM, url.queryParam("openid.realm"))
        assertEquals("checkid_setup", url.queryParam("openid.mode"))
        assertEquals(
            "http://specs.openid.net/auth/2.0/identifier_select",
            url.queryParam("openid.claimed_id"),
        )
    }

    @Test
    fun `isReturnUri matches sora steam-auth scheme`() {
        assertTrue(SteamOpenId.isReturnUrl("sora://steam-auth?openid.mode=id_res"))
        assertTrue(SteamOpenId.isReturnUrl("SORA://Steam-Auth"))
        assertFalse(SteamOpenId.isReturnUrl("https://example.com/"))
        assertFalse(SteamOpenId.isReturnUrl("sora://other-host?openid.mode=id_res"))
        assertFalse(SteamOpenId.isReturnUrl(null))
    }

    @Test
    fun `steamId64FromReturnUri parses claimed_id`() {
        val url = "sora://steam-auth?openid.claimed_id=" +
            "https://steamcommunity.com/openid/id/76561198000000000"
        assertEquals("76561198000000000", SteamOpenId.steamId64FromReturnUrl(url))
    }

    @Test
    fun `steamId64FromReturnUri falls back to identity`() {
        val url = "sora://steam-auth?openid.identity=" +
            "http://steamcommunity.com/openid/id/76561198123456789"
        assertEquals("76561198123456789", SteamOpenId.steamId64FromReturnUrl(url))
    }

    /** Steam actually percent-encodes the claimed id, which is how it arrives in the real callback. */
    @Test
    fun `steamId64FromReturnUri parses a percent-encoded claimed_id`() {
        val url = "sora://steam-auth?openid.mode=id_res&openid.claimed_id=" +
            "https%3A%2F%2Fsteamcommunity.com%2Fopenid%2Fid%2F76561198111111111"
        assertEquals("76561198111111111", SteamOpenId.steamId64FromReturnUrl(url))
    }

    @Test
    fun `steamId64FromReturnUri returns null when missing`() {
        assertNull(SteamOpenId.steamId64FromReturnUrl("sora://steam-auth"))
        assertNull(SteamOpenId.steamId64FromReturnUrl("sora://steam-auth?openid.mode=id_res"))
    }

    private fun String.queryParam(name: String): String? =
        substringAfter('?')
            .split('&')
            .firstOrNull { it.substringBefore('=') == name }
            ?.substringAfter('=')
            ?.let { java.net.URLDecoder.decode(it, "UTF-8") }
}
