package com.arcadia.shell.scanner

import com.arcadia.shell.model.LibraryRoot
import com.arcadia.shell.model.RootKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderHintsTest {

    @Test
    fun `seed includes the console folder when that folder is the library root`() {
        val root = LibraryRoot(
            id = "psp",
            location = "/storage/emulated/0/ROMS/PSP",
            kind = RootKind.Filesystem,
            label = "PSP",
        )
        assertEquals(
            listOf("storage", "emulated", "0", "ROMS", "PSP"),
            FolderHints.seedForRoot(root),
        )
    }

    @Test
    fun `path parents of a rom under ROMS PSP still include PSP`() {
        assertEquals(
            listOf("storage", "emulated", "0", "ROMS", "PSP"),
            FolderHints.parentDirectoriesOfFile(
                "/storage/emulated/0/ROMS/PSP/Crisis Core.iso",
            ),
        )
    }

    @Test
    fun `saf tree id still yields ROMS and PSP`() {
        val names = FolderHints.deepestFirst(
            folderChain = emptyList(),
            filePath = null,
            documentUri = "content://com.android.externalstorage.documents/tree/primary%3AROMS%2FPSP/document/primary%3AROMS%2FPSP%2FGame.iso",
            rootLabel = "PSP",
            rootLocation = "content://com.android.externalstorage.documents/tree/primary%3AROMS%2FPSP",
        )
        assertTrue(names.contains("PSP"))
        assertTrue(names.contains("ROMS"))
        assertEquals("PSP", names.first())
    }
}
