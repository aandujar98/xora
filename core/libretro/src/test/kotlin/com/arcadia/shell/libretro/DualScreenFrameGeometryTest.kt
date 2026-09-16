package com.arcadia.shell.libretro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DualScreenFrameGeometryTest {

    @Test
    fun stackedNdsIsCutAtHalfHeight() {
        val split = DualScreenFrameGeometry.split(256, 384, "nds")!!
        assertEquals(DualScreenSplitKind.Stacked, split.kind)
        assertEquals(DualScreenFrameRect(0, 0, 256, 192), split.top)
        assertEquals(DualScreenFrameRect(0, 192, 256, 192), split.bottom)
        assertEquals(DualScreenPointerTarget.BottomHalf, split.bottomPointerTarget)
    }

    @Test
    fun stacked3dsCropsCenteredBottomLcd() {
        val split = DualScreenFrameGeometry.split(400, 480, "3ds")!!
        assertEquals(DualScreenSplitKind.Stacked, split.kind)
        assertEquals(DualScreenFrameRect(0, 0, 400, 240), split.top)
        assertEquals(DualScreenFrameRect(40, 240, 320, 240), split.bottom)
        assertEquals(400, split.frameWidth)
        assertEquals(480, split.frameHeight)
    }

    @Test
    fun scaledStacked3dsCropsCenteredBottomLcd() {
        val split = DualScreenFrameGeometry.split(800, 960, "3ds")!!
        assertEquals(DualScreenFrameRect(0, 0, 800, 480), split.top)
        assertEquals(DualScreenFrameRect(80, 480, 640, 480), split.bottom)
    }

    @Test
    fun sideBySideNdsIsCutAtHalfWidth() {
        val split = DualScreenFrameGeometry.split(512, 192, "nds")!!
        assertEquals(DualScreenSplitKind.SideBySide, split.kind)
        assertEquals(DualScreenFrameRect(0, 0, 256, 192), split.top)
        assertEquals(DualScreenFrameRect(256, 0, 256, 192), split.bottom)
        assertEquals(DualScreenPointerTarget.BottomRight, split.bottomPointerTarget)
    }

    @Test
    fun sideBySide3dsUsesFourHundredByThreeTwenty() {
        val split = DualScreenFrameGeometry.split(720, 240, "3ds")!!
        assertEquals(DualScreenSplitKind.SideBySide, split.kind)
        assertEquals(DualScreenFrameRect(0, 0, 400, 240), split.top)
        assertEquals(DualScreenFrameRect(400, 0, 320, 240), split.bottom)
    }

    @Test
    fun scaled3dsSideBySideKeepsNativeRatio() {
        val split = DualScreenFrameGeometry.split(1440, 480, "3ds")!!
        assertEquals(800, split.top.width)
        assertEquals(640, split.bottom.width)
    }

    @Test
    fun tinyFramesCannotSplit() {
        assertNull(DualScreenFrameGeometry.split(1, 1, "nds"))
        assertNull(DualScreenFrameGeometry.split(0, 384, "nds"))
    }
}
