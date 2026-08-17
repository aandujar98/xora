package com.arcadia.shell.libretro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DualScreenPointerTest {

    @Test
    fun combinedCenterIsOrigin() {
        val ptr = DualScreenPointer.mapViewToPointer(
            viewX = 128f,
            viewY = 192f,
            viewW = 256,
            viewH = 384,
            contentW = 256,
            contentH = 384,
            fill = true,
            target = DualScreenPointerTarget.Combined,
            pressed = true,
        )
        assertNotNull(ptr)
        assertEquals(0, ptr!!.x.toInt())
        assertEquals(0, ptr.y.toInt())
        assertTrue(ptr.pressed)
    }

    @Test
    fun bottomPanelMapsOntoLowerFramebufferHalf() {
        val top = DualScreenPointer.mapViewToPointer(
            viewX = 128f,
            viewY = 0f,
            viewW = 256,
            viewH = 192,
            contentW = 256,
            contentH = 192,
            fill = true,
            target = DualScreenPointerTarget.BottomHalf,
            pressed = true,
        )
        val bottom = DualScreenPointer.mapViewToPointer(
            viewX = 128f,
            viewY = 192f,
            viewW = 256,
            viewH = 192,
            contentW = 256,
            contentH = 192,
            fill = true,
            target = DualScreenPointerTarget.BottomHalf,
            pressed = true,
        )
        assertEquals(0, top!!.y.toInt())
        assertEquals(DualScreenPointer.AXIS_MAX, bottom!!.y.toInt())
        assertTrue(top.pressed)
    }

    @Test
    fun topPanelIsNotATouchScreen() {
        val ptr = DualScreenPointer.mapViewToPointer(
            viewX = 64f,
            viewY = 64f,
            viewW = 256,
            viewH = 192,
            contentW = 256,
            contentH = 192,
            fill = true,
            target = DualScreenPointerTarget.TopHalf,
            pressed = true,
        )
        assertNull(ptr)
    }

    @Test
    fun letterboxIgnoresBars() {
        val rect = DualScreenPointer.contentRect(
            viewW = 400,
            viewH = 192,
            contentW = 256,
            contentH = 192,
            fill = false,
        )
        assertEquals(72, rect[0])
        assertEquals(0, rect[1])
        assertEquals(328, rect[2])
        val outside = DualScreenPointer.mapViewToPointer(
            viewX = 10f,
            viewY = 96f,
            viewW = 400,
            viewH = 192,
            contentW = 256,
            contentH = 192,
            fill = false,
            target = DualScreenPointerTarget.BottomHalf,
            pressed = true,
        )
        assertFalse(outside!!.pressed)
    }
}
