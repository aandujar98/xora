package com.arcadia.shell.scanner

import android.os.FileObserver
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DumpFolderWatchPolicyTest {

    @Test
    fun `skips android cache and hidden dump folders`() {
        assertFalse(DumpFolderWatchPolicy.shouldWatchDirectory("Android"))
        assertFalse(DumpFolderWatchPolicy.shouldWatchDirectory("cache"))
        assertFalse(DumpFolderWatchPolicy.shouldWatchDirectory(".thumbnails"))
        assertTrue(DumpFolderWatchPolicy.shouldWatchDirectory("PSP"))
        assertTrue(DumpFolderWatchPolicy.shouldWatchDirectory("My Games"))
        assertTrue(DumpFolderWatchPolicy.shouldWatchDirectory("Emulation"))
    }

    @Test
    fun `ignores hidden children but not a console folder name`() {
        assertTrue(DumpFolderWatchPolicy.shouldIgnoreChildName(".nomedia"))
        assertTrue(DumpFolderWatchPolicy.shouldIgnoreChildName("Android"))
        assertFalse(DumpFolderWatchPolicy.shouldIgnoreChildName("Crisis Core.iso"))
        assertFalse(DumpFolderWatchPolicy.shouldIgnoreChildName("PSP"))
        assertFalse(DumpFolderWatchPolicy.shouldIgnoreChildName(null))
    }

    @Test
    fun `create delete and close write start a scan`() {
        assertTrue(DumpFolderWatchPolicy.isInterestingEvent(FileObserver.CREATE))
        assertTrue(DumpFolderWatchPolicy.isInterestingEvent(FileObserver.DELETE))
        assertTrue(DumpFolderWatchPolicy.isInterestingEvent(FileObserver.CLOSE_WRITE))
        assertTrue(DumpFolderWatchPolicy.isInterestingEvent(FileObserver.MOVED_TO))
        assertTrue(DumpFolderWatchPolicy.isInterestingEvent(FileObserver.MODIFY))
        assertFalse(DumpFolderWatchPolicy.isInterestingEvent(FileObserver.ACCESS))
        assertFalse(DumpFolderWatchPolicy.isInterestingEvent(FileObserver.OPEN))
        assertFalse(DumpFolderWatchPolicy.isInterestingEvent(0))
    }
}
