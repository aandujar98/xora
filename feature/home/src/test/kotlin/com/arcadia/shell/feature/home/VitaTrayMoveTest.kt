package com.arcadia.shell.feature.home

import com.arcadia.shell.input.UiOneShot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Slot maths behind picking a bubble up and placing it with the stick / D-pad. */
class VitaTrayMoveTest {

    @Test
    fun horizontalStepsStayInsideTheRow() {
        assertEquals(1, vitaTrayNeighbourSlot(slotCount = 10, from = 0, dx = 1, dy = 0))
        assertNull(vitaTrayNeighbourSlot(slotCount = 10, from = 0, dx = -1, dy = 0))
        assertEquals(2, vitaTrayNeighbourSlot(slotCount = 10, from = 1, dx = 1, dy = 0))
        // Row one holds four bubbles; its right edge does not spill into row two.
        assertNull(vitaTrayNeighbourSlot(slotCount = 10, from = 6, dx = 1, dy = 0))
    }

    @Test
    fun verticalStepsHoldTheHorizontalPositionAcrossUnevenRows() {
        // Rows are 3 / 4 / 3, so the left and right ends map to the ends of the target row.
        assertEquals(3, vitaTrayNeighbourSlot(slotCount = 10, from = 0, dx = 0, dy = 1))
        assertEquals(6, vitaTrayNeighbourSlot(slotCount = 10, from = 2, dx = 0, dy = 1))
        assertEquals(7, vitaTrayNeighbourSlot(slotCount = 10, from = 3, dx = 0, dy = 1))
        assertEquals(0, vitaTrayNeighbourSlot(slotCount = 10, from = 3, dx = 0, dy = -1))
    }

    @Test
    fun verticalStepsCrossPagesAndStopAtTheEnds() {
        assertNull(vitaTrayNeighbourSlot(slotCount = 10, from = 8, dx = 0, dy = 1))
        assertEquals(11, vitaTrayNeighbourSlot(slotCount = 12, from = 8, dx = 0, dy = 1))
        assertNull(vitaTrayNeighbourSlot(slotCount = 10, from = 0, dx = 0, dy = -1))
    }

    @Test
    fun outOfRangeSlotsHaveNoNeighbour() {
        assertNull(vitaTrayNeighbourSlot(slotCount = 0, from = 0, dx = 1, dy = 0))
        assertNull(vitaTrayNeighbourSlot(slotCount = 4, from = 9, dx = 1, dy = 0))
        assertNull(vitaTrayNeighbourSlot(slotCount = 4, from = 1, dx = 0, dy = 0))
    }

    @Test
    fun verticalPageTurnsUseThePageSample() {
        assertEquals(UiOneShot.VitaPageNavigate, vitaTrayVerticalOneShot(crossedPage = true))
        assertEquals(UiOneShot.Cursor, vitaTrayVerticalOneShot(crossedPage = false))
    }

    @Test
    fun openingTheTrayPlaysVitaOpenOnce() {
        assertEquals(UiOneShot.VitaOpen, vitaTrayOpenOneShot(alreadyOpen = false))
        assertNull(vitaTrayOpenOneShot(alreadyOpen = true))
    }

    @Test
    fun peelingAVitaPagePlaysTheZoomStingOnce() {
        assertEquals(UiOneShot.BootVita, vitaPeelZoomOneShot(alreadyStarted = false))
        assertNull(vitaPeelZoomOneShot(alreadyStarted = true))
    }
}
