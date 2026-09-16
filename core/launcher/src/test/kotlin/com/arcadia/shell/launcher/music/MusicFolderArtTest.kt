package com.arcadia.shell.launcher.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MusicFolderArtTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun `finds cover jpg next to wav dumps`() {
        val album = tmp.newFolder("Persona 3 Reload")
        val cover = java.io.File(album, "cover.jpg").apply { writeBytes(ByteArray(80) { 1 }) }
        java.io.File(album, "01 Battle Hymn of the Soul.wav").writeBytes(ByteArray(16))
        assertEquals(cover.absolutePath, MusicFolderArt.findCover(album))
    }

    @Test
    fun `cover name match is case insensitive`() {
        val album = tmp.newFolder("OST")
        val cover = java.io.File(album, "Folder.PNG").apply { writeBytes(ByteArray(80) { 2 }) }
        assertEquals(cover.absolutePath, MusicFolderArt.findCover(album))
    }

    @Test
    fun `ignores tiny or missing covers`() {
        val album = tmp.newFolder("Empty")
        java.io.File(album, "cover.jpg").writeBytes(ByteArray(8))
        assertNull(MusicFolderArt.findCover(album))
    }

    @Test
    fun `detects png magic`() {
        val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0, 0, 0, 0)
        assertEquals("png", MusicFolderArt.extensionForImage(png))
        assertEquals("jpg", MusicFolderArt.extensionForImage(byteArrayOf(0xFF.toByte(), 0xD8.toByte())))
        assertTrue(MusicFolderArt.extensionForImage(ByteArray(0)) == "jpg")
    }
}
