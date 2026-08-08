package com.arcadia.shell.scraper

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyAuthTest {
    @Test
    fun returnUrl_matchesSpotifyHost() {
        assertTrue(SpotifyAuth.isReturnUrl("sora://spotify-auth?code=abc"))
        assertTrue(SpotifyAuth.isReturnUrl("sora://spotify-auth"))
    }

    @Test
    fun returnUrl_rejectsOtherHosts() {
        assertFalse(SpotifyAuth.isReturnUrl("sora://steam-auth?code=abc"))
        assertFalse(SpotifyAuth.isReturnUrl("https://accounts.spotify.com/authorize"))
        assertFalse(SpotifyAuth.isReturnUrl(null))
        assertFalse(SpotifyAuth.isReturnUrl(""))
    }
}
