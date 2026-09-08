package com.arcadia.shell.feature.home

import com.arcadia.shell.feature.home.component.photoImportChooserIndex
import com.arcadia.shell.feature.home.component.photoImportChooserSource
import com.arcadia.shell.input.NavAction
import org.junit.Assert.assertEquals
import org.junit.Test

class PhotoImportChooserNavTest {

    @Test
    fun leftAndRightPickPhotosOrFiles() {
        assertEquals(0, photoImportChooserIndex(NavAction.Left, current = 1))
        assertEquals(1, photoImportChooserIndex(NavAction.Right, current = 0))
        assertEquals(0, photoImportChooserIndex(NavAction.Up, current = 0))
        assertEquals(PhotoImportSource.PhotosApp, photoImportChooserSource(0))
        assertEquals(PhotoImportSource.FilesApp, photoImportChooserSource(1))
    }
}
