package com.arcadia.shell.feature.home

import com.arcadia.shell.feature.home.component.VitaShortcutTrayEditHints
import com.arcadia.shell.feature.home.component.VitaShortcutTrayHints
import com.arcadia.shell.feature.home.component.VitaShortcutTrayMoveHints
import com.arcadia.shell.feature.home.component.hintsForPage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** A lifted bubble has its own controls, so the legend has to swap with it. */
class VitaTrayMoveHintsTest {

    private fun tray(editMode: Boolean, moveIndex: Int?) = HomeHubUiState(
        vitaShortcutTrayOpen = true,
        shortcutsEditMode = editMode,
        vitaShortcutMoveIndex = moveIndex,
    )

    @Test
    fun `holding a bubble swaps the legend for place and cancel`() {
        val hints = hintsForPage(HomePage.Home, homeHub = tray(editMode = true, moveIndex = 2))
        assertEquals(VitaShortcutTrayMoveHints, hints)
        assertTrue(hints.any { it.first == "A" && it.second == "Drop here" })
        assertTrue(hints.any { it.first == "B" && it.second == "Cancel" })
        assertTrue(hints.any { it.first == "Drag" })
    }

    @Test
    fun `move hints win over edit hints while a bubble is held`() {
        assertEquals(
            VitaShortcutTrayEditHints,
            hintsForPage(HomePage.Home, homeHub = tray(editMode = true, moveIndex = null)),
        )
        assertEquals(
            VitaShortcutTrayMoveHints,
            hintsForPage(HomePage.Home, homeHub = tray(editMode = true, moveIndex = 0)),
        )
    }

    @Test
    fun `browsing the tray advertises hold to move`() {
        val hints = hintsForPage(HomePage.Home, homeHub = tray(editMode = false, moveIndex = null))
        assertEquals(VitaShortcutTrayHints, hints)
        assertTrue(hints.any { it.first == "Hold" && it.second == "Move bubble" })
    }
}
