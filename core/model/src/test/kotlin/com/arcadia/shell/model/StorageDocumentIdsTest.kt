package com.arcadia.shell.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StorageDocumentIdsTest {

    @Test
    fun `maps primary shared storage paths`() {
        assertEquals(
            "primary:Roms/psp/Game.iso",
            StorageDocumentIds.documentIdForPath("/storage/emulated/0/Roms/psp/Game.iso"),
        )
        assertEquals(
            "primary:Roms/Game.iso",
            StorageDocumentIds.documentIdForPath("/sdcard/Roms/Game.iso"),
        )
        assertEquals(
            "primary:",
            StorageDocumentIds.documentIdForPath("/storage/emulated/0"),
        )
    }

    @Test
    fun `maps adopted volume paths`() {
        assertEquals(
            "ABCD-1234:Roms/n64/Game.z64",
            StorageDocumentIds.documentIdForPath("/storage/ABCD-1234/Roms/n64/Game.z64"),
        )
        assertEquals(
            "ABCD-1234:",
            StorageDocumentIds.documentIdForPath("/storage/ABCD-1234"),
        )
    }

    @Test
    fun `rejects paths that are not shared storage`() {
        assertNull(StorageDocumentIds.documentIdForPath("/data/data/com.sora.shell/files/rom.iso"))
        assertNull(StorageDocumentIds.documentIdForPath("/storage/emulated/legacy/rom.iso"))
    }

    @Test
    fun `round trips path and document id`() {
        val path = "/storage/emulated/0/Roms/gba/Game.gba"
        val documentId = StorageDocumentIds.documentIdForPath(path)
        assertEquals("primary:Roms/gba/Game.gba", documentId)
        assertEquals(path, StorageDocumentIds.pathForDocumentId(documentId!!))
    }

    @Test
    fun `logical keys match across path forms`() {
        val a = StorageDocumentIds.logicalKeyForPath("/storage/emulated/0/Roms/Game.gba")
        val b = StorageDocumentIds.logicalKeyForPath("/sdcard/Roms/Game.gba")
        val c = StorageDocumentIds.logicalKeyForDocumentId("primary:Roms/Game.gba")
        assertEquals(a, b)
        assertEquals(a, c)
    }
}
