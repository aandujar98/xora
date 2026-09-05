package com.arcadia.shell.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VitaPeelGeometryTest {

    private val w = PEEL_BOUNDARY_W
    private val h = PEEL_BOUNDARY_H

    @Test
    fun `sweep spans both boundary edges so the far corner is the last to go`() {
        assertEquals(w + h, VitaPeelGeometry.sweep(w, h), 0f)
    }

    @Test
    fun `commit depth sits between the rest pose and the full sweep`() {
        val commit = VitaPeelGeometry.commitDepth(w, h)
        assertTrue(commit > PEEL_TIP_REST_DEPTH)
        assertTrue(commit < VitaPeelGeometry.sweep(w, h))
    }

    @Test
    fun `pulling toward the bottom left deepens the fold and pulling back undoes it`() {
        assertEquals(20f, VitaPeelGeometry.depthDelta(dragX = -10f, dragY = 10f), 0f)
        assertEquals(-20f, VitaPeelGeometry.depthDelta(dragX = 10f, dragY = -10f), 0f)
        // A pull straight along the fold neither peels nor restores.
        assertEquals(0f, VitaPeelGeometry.depthDelta(dragX = 10f, dragY = 10f), 0f)
    }

    @Test
    fun `rest face matches the dog-ear asset at the top right corner of the boundary`() {
        val face = VitaPeelGeometry.faceSquare(PEEL_TIP_REST_DEPTH, w)
        assertEquals(w - PEEL_TIP_REST_DEPTH, face.left, 0f)
        assertEquals(0f, face.top, 0f)
        assertEquals(w, face.right, 0f)
        assertEquals(PEEL_TIP_REST_DEPTH, face.bottom, 0f)
    }

    @Test
    fun `face grows with the fold and slides off the boundary at full sweep`() {
        val half = VitaPeelGeometry.faceSquare(h, w)
        assertEquals(h, half.width, 0f)
        assertEquals(w - h, half.left, 0f)

        val gone = VitaPeelGeometry.faceSquare(VitaPeelGeometry.sweep(w, h), w)
        // The square's SW half-diagonal runs from (-h, 0) to (w, w + h): nothing of the face
        // lies inside the 0..w x 0..h sheet any more.
        assertEquals(-h, gone.left, 0f)
        assertTrue(gone.bottom > h)
    }

    @Test
    fun `shadow stays off the untouched asset and ramps in with the pull`() {
        val rest = PEEL_TIP_REST_DEPTH
        assertEquals(0f, VitaPeelGeometry.pullFraction(rest, rest), 0f)
        assertEquals(0f, VitaPeelGeometry.pullFraction(rest - 5f, rest), 0f)
        val mid = VitaPeelGeometry.pullFraction(rest * 1.75f, rest)
        assertTrue(mid > 0f && mid < 1f)
        assertEquals(1f, VitaPeelGeometry.pullFraction(rest * 4f, rest), 0f)
    }

    @Test
    fun `drag speed bands match slow mid and fast peel samples`() {
        assertEquals(VitaPeelDragSpeed.Slow, VitaPeelGeometry.dragSpeed(8f, 32f))
        assertEquals(VitaPeelDragSpeed.Mid, VitaPeelGeometry.dragSpeed(16f, 16f))
        assertEquals(VitaPeelDragSpeed.Fast, VitaPeelGeometry.dragSpeed(80f, 16f))
        assertEquals(VitaPeelDragSpeed.Mid, VitaPeelGeometry.dragSpeed(10f, 0f))
    }
}
