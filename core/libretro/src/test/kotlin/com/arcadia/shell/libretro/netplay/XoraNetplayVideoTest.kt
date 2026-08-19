package com.arcadia.shell.libretro.netplay

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XoraNetplayVideoTest {

    @Test
    fun nesFrameKeepsNativeWidthOnline() {
        assertEquals(256 to 224, XoraNetplayVideo.targetSize(256, 224, XoraNetplayVideo.ONLINE_MAX_WIDTH))
        assertEquals(256 to 240, XoraNetplayVideo.targetSize(256, 240, XoraNetplayVideo.ONLINE_MAX_WIDTH))
    }

    @Test
    fun gamecubeKeepsLanWidthOnline() {
        val (w, h) = XoraNetplayVideo.targetSize(640, 480, XoraNetplayVideo.ONLINE_MAX_WIDTH)
        assertEquals(400, w)
        assertEquals(300, h)
    }

    @Test
    fun widthStepsDropResolutionBeforeMushingQuality() {
        assertArrayEquals(
            intArrayOf(400, 320, 256, 224, 192, 160, 128),
            XoraNetplayVideo.widthSteps(XoraNetplayVideo.ONLINE_MAX_WIDTH),
        )
        assertArrayEquals(
            intArrayOf(400, 320, 256, 224, 192, 160, 128),
            XoraNetplayVideo.widthSteps(XoraNetplayVideo.MAX_WIDTH),
        )
        assertArrayEquals(
            intArrayOf(256, 224, 192, 160, 128),
            XoraNetplayVideo.widthSteps(256),
        )
    }

    @Test
    fun onlineBudgetFitsNakamaChunkCap() {
        val header = 12
        val maxPayload = XoraNetplayVideo.ONLINE_MAX_BYTES + header
        val chunk = XoraNetplayProtocol.RELAY_CHUNK_BYTES
        val chunks = (maxPayload + chunk - 1) / chunk
        assertTrue(chunks <= XoraNetplayProtocol.RELAY_MAX_CHUNKS)
    }
}
