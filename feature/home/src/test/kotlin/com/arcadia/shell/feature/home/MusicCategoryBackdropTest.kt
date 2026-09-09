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
            playing = true,
            enabled = true,
            coverPath = "  ",
        )
        assertTrue(backdrop.showCover)
        assertTrue(backdrop.showWaveMask)
        assertNull(backdrop.coverPath)
    }
}
