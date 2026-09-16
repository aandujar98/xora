package com.arcadia.shell.libretro

import com.arcadia.shell.datastore.XoraAspectMode
import com.arcadia.shell.datastore.label
import com.arcadia.shell.datastore.next
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XoraGameRectTest {

    @Test
    fun autoLetterboxesWidePanel() {
        // 256×224 SNES frame in 1920×1080 → height-limited 4:3-ish box.
        val rect = computeXoraGameRect(
            viewW = 1920,
            viewH = 1080,
            contentWidthPx = 256,
            contentHeightPx = 224,
            aspectMode = XoraAspectMode.Core,
        )
        val width = rect[2] - rect[0]
        val height = rect[3] - rect[1]
        assertEquals(1080, height)
        assertEquals(1234, width)
        assertTrue(rect[0] in 342..343)
        assertEquals(0, rect[1])
    }

    @Test
    fun ratio16x9FillsA16x9Panel() {
        val rect = computeXoraGameRect(
            viewW = 1920,
            viewH = 1080,
            contentWidthPx = 256,
            contentHeightPx = 224,
            aspectMode = XoraAspectMode.Ratio16x9,
        )
        assertArrayEquals(intArrayOf(0, 0, 1920, 1080), rect)
    }

    @Test
    fun ratio1x1CentersASquare() {
        val rect = computeXoraGameRect(
            viewW = 1920,
            viewH = 1080,
            contentWidthPx = 320,
            contentHeightPx = 240,
            aspectMode = XoraAspectMode.Ratio1x1,
        )
        assertArrayEquals(intArrayOf(420, 0, 1500, 1080), rect)
    }

    @Test
    fun stretchFillsThePanel() {
        val rect = computeXoraGameRect(
            viewW = 800,
            viewH = 480,
            contentWidthPx = 256,
            contentHeightPx = 224,
            aspectMode = XoraAspectMode.Stretch,
        )
        assertArrayEquals(intArrayOf(0, 0, 800, 480), rect)
    }

    @Test
    fun aspectCycleKeepsAutoAndAddsWidescreen() {
        val labels = XoraAspectMode.entries.map { it.label() }
        assertEquals("Auto", XoraAspectMode.Core.label())
        assertTrue(labels.contains("16:9"))
        assertTrue(labels.contains("1:1"))
        assertTrue(labels.contains("4:3"))
        assertEquals(XoraAspectMode.Ratio4x3, XoraAspectMode.Core.next())
        assertEquals(XoraAspectMode.Core, XoraAspectMode.Stretch.next())
    }

    @Test
    fun launcherAutoFillsThePanel() {
        val rect = computeXoraLauncherRect(1920, 1080, XoraAspectMode.Core)
        assertArrayEquals(intArrayOf(0, 0, 1920, 1080), rect)
    }

    @Test
    fun launcher16x9FillsA16x9Panel() {
        val rect = computeXoraLauncherRect(1920, 1080, XoraAspectMode.Ratio16x9)
        assertArrayEquals(intArrayOf(0, 0, 1920, 1080), rect)
    }

    @Test
    fun launcher1x1FillsAWidePanel() {
        val rect = computeXoraLauncherRect(1920, 1080, XoraAspectMode.Ratio1x1)
        assertArrayEquals(intArrayOf(0, 0, 1920, 1080), rect)
    }

    @Test
    fun launcher1x1FillsASquarePanel() {
        val rect = computeXoraLauncherRect(720, 720, XoraAspectMode.Ratio1x1)
        assertArrayEquals(intArrayOf(0, 0, 720, 720), rect)
    }

    @Test
    fun launcher1x1OnALandscapeLockedSquareWindow() {
        // Some OEMs letterbox sensorLandscape into 16:9 on a 720×720 panel.
        // The XMB still fills that window — aspect ratio is emulator-only.
        val rect = computeXoraLauncherRect(720, 405, XoraAspectMode.Ratio1x1)
        assertArrayEquals(intArrayOf(0, 0, 720, 405), rect)
    }

    @Test
    fun launcher4x3FillsAWidePanel() {
        val rect = computeXoraLauncherRect(1920, 1080, XoraAspectMode.Ratio4x3)
        assertArrayEquals(intArrayOf(0, 0, 1920, 1080), rect)
    }
}
