package com.arcadia.shell.scraper

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicArtMatcherTest {

    @Test
    fun `persona 3 reload folder matches the published soundtrack title`() {
        val best = MusicArtMatcher.pickBest(
            queryTitle = "Persona 3 Reload",
            queryArtist = "Unknown artist",
            candidates = listOf(
                MusicArtMatcher.Candidate(
                    title = "Persona 3 Reload Original Soundtrack",
                    artist = "ATLUS SOUND TEAM",
                    artUrl = "https://example.com/p3r.jpg",
                ),
                MusicArtMatcher.Candidate(
                    title = "Persona 5 Royal",
                    artist = "ATLUS SOUND TEAM",
                    artUrl = "https://example.com/p5r.jpg",
                ),
            ),
        )
        assertNotNull(best)
        assertEquals("https://example.com/p3r.jpg", best?.artUrl)
    }

    @Test
    fun `strips ost noise so folder dumps still exact-match`() {
        assertEquals(
            "persona 3 reload",
            MusicArtMatcher.normalize("Persona 3 Reload Original Soundtrack"),
        )
        assertEquals(
            "persona 3 reload",
            MusicArtMatcher.normalize("Persona_3_Reload_(OST)"),
        )
    }

    @Test
    fun `rejects unrelated albums`() {
        val best = MusicArtMatcher.pickBest(
            queryTitle = "Persona 3 Reload",
            queryArtist = "Unknown artist",
            candidates = listOf(
                MusicArtMatcher.Candidate(
                    title = "Random Jazz Collection",
                    artist = "Various",
                    artUrl = "https://example.com/jazz.jpg",
                ),
            ),
        )
        assertNull(best)
    }

    @Test
    fun `generic titles are not scraped`() {
        assertFalse(MusicArtMatcher.isScrapableTitle("Unknown album"))
        assertFalse(MusicArtMatcher.isScrapableTitle("Music"))
        assertTrue(MusicArtMatcher.isScrapableTitle("Persona 3 Reload"))
    }

    @Test
    fun `itunes artwork url is upscaled`() {
        val small = "https://is1-ssl.mzstatic.com/image/thumb/Music116/v4/ab/cd/ef/100x100bb.jpg"
        assertEquals(
            "https://is1-ssl.mzstatic.com/image/thumb/Music116/v4/ab/cd/ef/600x600bb.jpg",
            MusicArtMatcher.itunesHiResArtUrl(small),
        )
    }

    @Test
    fun `unknown artist shares a cache key`() {
        assertEquals(
            MusicArtMatcher.cacheKey("Persona 3 Reload", "Unknown artist"),
            MusicArtMatcher.cacheKey("Persona 3 Reload", "Various artists"),
        )
        assertEquals("persona 3 reload|_", MusicArtMatcher.cacheKey("Persona 3 Reload", ""))
    }

    @Test
    fun `search query drops generic artists`() {
        assertEquals(
            "Persona 3 Reload",
            MusicArtMatcher.searchQuery("Persona 3 Reload", "Unknown artist"),
        )
        assertEquals(
            "Persona 3 Reload ATLUS SOUND TEAM",
            MusicArtMatcher.searchQuery("Persona 3 Reload", "ATLUS SOUND TEAM"),
        )
    }
}
