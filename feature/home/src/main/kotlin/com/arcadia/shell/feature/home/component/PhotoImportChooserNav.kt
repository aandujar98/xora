package com.arcadia.shell.feature.home.component

import com.arcadia.shell.feature.home.PhotoImportSource
import com.arcadia.shell.input.NavAction

/** Left lands on Photos, Right on Files. Other actions leave the focus where it is. */
internal fun photoImportChooserIndex(action: NavAction, current: Int): Int = when (action) {
    NavAction.Left -> 0
    NavAction.Right -> 1
    else -> current.coerceIn(0, 1)
}

internal fun photoImportChooserSource(index: Int): PhotoImportSource =
    if (index <= 0) PhotoImportSource.PhotosApp else PhotoImportSource.FilesApp
