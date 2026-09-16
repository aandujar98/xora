package com.arcadia.shell.feature.home

import com.arcadia.shell.launcher.videos.DeviceVideo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Dnu056ReconstructionTest {

    @Test
    fun playerTransportUsesIcons2Bitmaps() {
        assertEquals(R.drawable.player_play, XmbIcon.Play.playerBitmapRes())
        assertEquals(R.drawable.player_pause, XmbIcon.Pause.playerBitmapRes())
        assertEquals(R.drawable.player_prev, XmbIcon.PreviousTrack.playerBitmapRes())
        assertEquals(R.drawable.player_next, XmbIcon.NextTrack.playerBitmapRes())
        assertEquals(R.drawable.player_repeat, XmbIcon.Repeat.playerBitmapRes())
        assertEquals(R.drawable.player_shuf, XmbIcon.Shuffle.playerBitmapRes())
    }

    @Test
    fun favoritesFolderUsesIcons2FolderBitmap() {
        assertEquals(R.drawable.xmb_folder, XmbIcon.FolderFavorites.vectorDrawableRes())
    }

    @Test
    fun favoritesAndRomsAreGameSelect() {
        assertTrue(XoraXmbDepth.Roms.isGameSelect)
        assertTrue(XoraXmbDepth.Favorites.isGameSelect)
        assertFalse(XoraXmbDepth.Systems.isGameSelect)
        assertFalse(XoraXmbDepth.VideoFiles.isGameSelect)
    }

    @Test
    fun videoItemsPlayTheClip() {
        val items = buildXoraVideoItems(
            listOf(
                DeviceVideo(
                    id = "12",
                    title = "clip.mp4",
                    uri = "content://media/external/video/media/12",
                    bucketId = "cam",
                    album = "Camera",
                    durationMs = 4_000,
                    sizeBytes = 1_000,
                ),
            ),
        )
        assertEquals(1, items.size)
        assertEquals("clip", items[0].title)
        assertEquals(XoraXmbAction.PlayVideo("12"), items[0].action)
    }
}
