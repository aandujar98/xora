package com.arcadia.shell.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayTopologyTest {

    @Test
    fun `public secondary display is dual-screen and launchable`() {
        val topology = topology(
            primary(),
            publicSecondary(id = 2),
        )
        assertEquals(2, topology.secondary?.displayId)
        assertEquals(2, topology.presentationDisplay?.displayId)
        assertTrue(topology.isDualScreen)
    }

    @Test
    fun `AYN Thor style private presentation display hosts Presentation but not Activity launch`() {
        val topology = topology(
            primary(),
            thorBottomScreen(id = 1),
        )
        assertNull("private presentation is not a launch target", topology.secondary)
        assertEquals(1, topology.presentationDisplay?.displayId)
        assertTrue(topology.isDualScreen)
    }

    @Test
    fun `presentation display is preferred over a later public secondary`() {
        val topology = topology(
            primary(),
            publicSecondary(id = 4),
            thorBottomScreen(id = 1),
        )
        assertEquals(4, topology.secondary?.displayId)
        assertEquals(1, topology.presentationDisplay?.displayId)
    }

    @Test
    fun `private overlay without presentation flag is ignored`() {
        val topology = topology(
            primary(),
            ShellDisplay(
                displayId = 99,
                name = "Overlay",
                widthPx = 100,
                heightPx = 100,
                densityDpi = 160,
                isPrimary = false,
                isPublic = false,
                isPresentation = false,
            ),
        )
        assertNull(topology.secondary)
        assertNull(topology.presentationDisplay)
        assertFalse(topology.isDualScreen)
    }

    @Test
    fun `large private panel without presentation flag is still an expand target`() {
        val topology = topology(
            primary(),
            ShellDisplay(
                displayId = 7,
                name = "Bottom",
                widthPx = 1280,
                heightPx = 720,
                densityDpi = 320,
                isPrimary = false,
                isPublic = false,
                isPresentation = false,
            ),
        )
        assertNull(topology.secondary)
        assertEquals(7, topology.presentationDisplay?.displayId)
        assertTrue(topology.isDualScreen)
    }

    @Test
    fun `empty topology is not dual-screen`() {
        assertFalse(DisplayTopology.Empty.isDualScreen)
        assertNull(DisplayTopology.Empty.presentationDisplay)
    }

    private fun topology(vararg displays: ShellDisplay) = DisplayTopology(
        displays = displays.toList(),
        supportsActivitiesOnSecondaryDisplays = true,
    )

    private fun primary() = ShellDisplay(
        displayId = 0,
        name = "Top",
        widthPx = 1920,
        heightPx = 1080,
        densityDpi = 320,
        isPrimary = true,
        isPublic = true,
        isPresentation = false,
    )

    private fun publicSecondary(id: Int) = ShellDisplay(
        displayId = id,
        name = "HDMI",
        widthPx = 1920,
        heightPx = 1080,
        densityDpi = 320,
        isPrimary = false,
        isPublic = true,
        isPresentation = false,
    )

    /** AYN Thor bottom panel: FLAG_PRESENTATION + FLAG_PRIVATE. */
    private fun thorBottomScreen(id: Int) = ShellDisplay(
        displayId = id,
        name = "Bottom",
        widthPx = 1240,
        heightPx = 1080,
        densityDpi = 320,
        isPrimary = false,
        isPublic = false,
        isPresentation = true,
    )
}
