package com.arcadia.shell.launcher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalStorageUrisTest {

    @Test
    fun `maps primary shared storage paths`() {
        assertEquals(
            "primary:Roms/psp/Game.iso",
            ExternalStorageUris.documentIdForPath("/storage/emulated/0/Roms/psp/Game.iso"),
        )
        assertEquals(
            "primary:Roms/Game.iso",
            ExternalStorageUris.documentIdForPath("/sdcard/Roms/Game.iso"),
        )
        assertEquals(
            "primary:",
            ExternalStorageUris.documentIdForPath("/storage/emulated/0"),
        )
    }

    @Test
    fun `maps adopted volume paths`() {
        assertEquals(
            "ABCD-1234:Roms/n64/Game.z64",
            ExternalStorageUris.documentIdForPath("/storage/ABCD-1234/Roms/n64/Game.z64"),
        )
        assertEquals(
            "ABCD-1234:",
            ExternalStorageUris.documentIdForPath("/storage/ABCD-1234"),
        )
    }

    @Test
    fun `rejects paths that are not shared storage`() {
        assertNull(ExternalStorageUris.documentIdForPath("/data/data/com.sora.shell/files/rom.iso"))
        assertNull(ExternalStorageUris.documentIdForPath("/storage/emulated/legacy/rom.iso"))
    }
}
