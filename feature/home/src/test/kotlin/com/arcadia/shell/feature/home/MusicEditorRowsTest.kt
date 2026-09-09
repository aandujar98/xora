package com.arcadia.shell.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicEditorRowsTest {

    @Test
    fun railListsDetailsThenArtwork() {
        assertEquals(listOf("Details", "Artwork"), MusicEditorSection.entries.map { it.label })
    }

    @Test
    fun albumArtworkHasCoverAndBackground() {
        val rows = musicEditorRows(
            section = MusicEditorSection.Artwork,
            kind = MusicEditorKind.Album,
            albumCoverPath = "/cover.jpg",
            trackCoverPath = null,
            backgroundPath = null,
            actions = actions(),
        )
        assertEquals(listOf("album_cover", "background"), rows.map { it.key })
        assertEquals("Album cover art", rows[0].label)
        assertEquals("Your file", rows[0].value)
        assertNotNull(rows[0].onActivate)
        assertNotNull(rows[0].onClear)
        assertEquals("Album background media", rows[1].label)
        assertNull(rows[1].onClear)
    }

    @Test
    fun trackArtworkAddsTrackCover() {
        val rows = musicEditorRows(
            section = MusicEditorSection.Artwork,
            kind = MusicEditorKind.Track,
            albumCoverPath = null,
            trackCoverPath = "/track.png",
            backgroundPath = "/bg.mp4",
            actions = actions(),
        )
        assertEquals(listOf("album_cover", "track_cover", "background"), rows.map { it.key })
        assertEquals("Track cover art", rows[1].label)
        assertEquals("Track background media", rows[2].label)
        assertTrue(rows[2].hint.orEmpty().contains("Files"))
        assertNotNull(rows[2].onClear)
    }

    private fun actions() = MusicEditorActions(
        onDismiss = {},
        onPickAlbumCover = {},
        onClearAlbumCover = {},
        onPickTrackCover = {},
        onClearTrackCover = {},
        onPickBackground = {},
        onClearBackground = {},
    )
}
