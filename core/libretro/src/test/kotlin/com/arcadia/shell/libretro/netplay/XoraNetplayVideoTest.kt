package com.arcadia.shell.libretro.netplay

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class XoraNetplayVideoTest {

    @Test
    fun nesFrameKeepsNativeWidthOnline() {
        assertEquals(256 to 224, XoraNetplayVideo.targetSize(256, 224, XoraNetplayVideo.ONLINE_MAX_WIDTH))
        assertEquals(256 to 240, XoraNetplayVideo.targetSize(256, 240, XoraNetplayVideo.ONLINE_MAX_WIDTH))
    }

    @Test
    fun gamecubeIsNearestNeighborDownscaledNotStretchedPastCap() {
        val (w, h) = XoraNetplayVideo.targetSize(640, 480, XoraNetplayVideo.ONLINE_MAX_WIDTH)
        assertEquals(256, w)
        assertEquals(192, h)
    }

    @Test
    fun widthStepsDropResolutionBeforeMushingQuality() {
        assertArrayEquals(
            intArrayOf(256, 224, 192, 160, 128),
            XoraNetplayVideo.widthSteps(XoraNetplayVideo.ONLINE_MAX_WIDTH),
        )
        assertArrayEquals(
            intArrayOf(400, 256, 224, 192, 160, 128),
            XoraNetplayVideo.widthSteps(XoraNetplayVideo.MAX_WIDTH),
        )
    }
}
