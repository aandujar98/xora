package com.arcadia.shell.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicCategoryBackdropTest {

    @Test
    fun playingOnMusicShowsCoverAndWaveWhenEnabled() {
        val backdrop = musicCategoryBackdrop(
            category = XoraXmbCategory.Music,
            depth = XoraXmbDepth.Category,
            playing = true,
            enabled = true,
            coverPath = "/sdcard/cover.jpg",
        )
        assertTrue(backdrop.showCover)
        assertTrue(backdrop.showWaveMask)
        assertEquals("/sdcard/cover.jpg", backdrop.coverPath)
    }

    @Test
    fun otherCategoriesKeepTheThemeWallpaper() {
        val backdrop = musicCategoryBackdrop(
            category = XoraXmbCategory.Games,
            depth = XoraXmbDepth.Category,
            playing = true,
            enabled = true,
            coverPath = "/sdcard/cover.jpg",
        )
        assertFalse(backdrop.showCover)
        assertFalse(backdrop.showWaveMask)
        assertNull(backdrop.coverPath)
    }

    @Test
    fun toggleOffLeavesMusicOnTheThemeWallpaper() {
        val backdrop = musicCategoryBackdrop(
            category = XoraXmbCategory.Music,
            depth = XoraXmbDepth.Category,
            playing = true,
            enabled = false,
            coverPath = "/sdcard/cover.jpg",
        )
        assertFalse(backdrop.showCover)
        assertFalse(backdrop.showWaveMask)
    }

    @Test
    fun nothingPlayingDoesNotForceCoverArt() {
        val backdrop = musicCategoryBackdrop(
            category = XoraXmbCategory.Music,
            depth = XoraXmbDepth.Category,
            playing = false,
            enabled = true,
            coverPath = "/sdcard/cover.jpg",
        )
        assertFalse(backdrop.showCover)
        assertFalse(backdrop.showWaveMask)
    }

    @Test
    fun blankCoverStillTurnsTheWaveOn() {
        val backdrop = musicCategoryBackdrop(
            category = XoraXmbCategory.Music,
            depth = XoraXmbDepth.Category,
            playing = true,
            enabled = true,
            coverPath = "  ",
        )
        assertTrue(backdrop.showCover)
        assertTrue(backdrop.showWaveMask)
        assertNull(backdrop.coverPath)
    }

    @Test
    fun nowPlayingKeepsTheCoverButDropsTheWave() {
        val backdrop = musicCategoryBackdrop(
            category = XoraXmbCategory.Music,
            depth = XoraXmbDepth.NowPlaying,
            playing = true,
            enabled = true,
            coverPath = "/sdcard/cover.jpg",
        )
        assertTrue(backdrop.showCover)
        assertFalse(backdrop.showWaveMask)
        assertEquals("/sdcard/cover.jpg", backdrop.coverPath)
    }

    @Test
    fun musicSubPagesOtherThanNowPlayingKeepTheWave() {
        listOf(
            XoraXmbDepth.MusicAlbums,
            XoraXmbDepth.MusicTracks,
            XoraXmbDepth.DspAccounts,
        ).forEach { depth ->
            val backdrop = musicCategoryBackdrop(
                category = XoraXmbCategory.Music,
                depth = depth,
                playing = true,
                enabled = true,
                coverPath = "/sdcard/cover.jpg",
            )
            assertTrue("wave should survive $depth", backdrop.showWaveMask)
        }
    }
}
