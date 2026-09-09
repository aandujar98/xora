package com.arcadia.shell.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** The two lean channels that turn the flat bubbles into glass domes. */
class VitaBubbleLeanTest {

    @Test
    fun `profile bubble and tray share the same lean limits`() {
        assertEquals(13f, VITA_BUBBLE_TILT_DEG)
        assertEquals(6f, VITA_BUBBLE_CAMERA_DISTANCE)
        assertEquals(0.03f, VITA_BUBBLE_SHEEN_TRAVEL)
        assertEquals(0.115f, VITA_BUBBLE_TILT_SHIFT_FRACTION)
    }

    @Test
    fun `idle rock leans both ways over a cycle and never overpowers the sway`() {
        val samples = (0..40).map { vitaBubbleIdleLean(index = 0, cycleUnit = it / 40f) }
        assertTrue(samples.any { it > 0.2f })
        assertTrue(samples.any { it < -0.2f })
        assertTrue(samples.all { abs(it) <= 0.35f })
    }

    @Test
    fun `idle rock wraps cleanly so a bubble does not jump at the end of a cycle`() {
        assertEquals(
            vitaBubbleIdleLean(index = 3, cycleUnit = 0f),
            vitaBubbleIdleLean(index = 3, cycleUnit = 1f),
            1e-4f,
        )
    }

    @Test
    fun `neighbouring bubbles rock out of step`() {
        val first = vitaBubbleIdleLean(index = 0, cycleUnit = 0.25f)
        val second = vitaBubbleIdleLean(index = 1, cycleUnit = 0.25f)
        assertTrue(abs(first - second) > 0.1f)
    }

    @Test
    fun `page turn bounce starts hard and is spent by the time the driver stops`() {
        val opening = (0..8).map { vitaBubbleJiggleLean(index = 0, elapsedSeconds = it / 200f) }
        assertTrue(opening.any { abs(it) > 0.5f })

        val settled = vitaBubbleJiggleLean(index = 0, VITA_BUBBLE_JIGGLE_SECONDS)
        assertTrue(abs(settled) < 0.05f)
    }

    @Test
    fun `wobble crosses the slot rather than pushing the bubble one way`() {
        val samples = (0..60).map {
            vitaBubbleJiggleLean(index = 0, elapsedSeconds = it * (VITA_BUBBLE_JIGGLE_SECONDS / 60f))
        }
        assertTrue(samples.any { it > 0.2f })
        assertTrue(samples.any { it < -0.2f })
    }

    @Test
    fun `bubbles later in a page start their wobble later`() {
        assertEquals(0f, vitaBubbleJiggleLean(index = 4, elapsedSeconds = 0.01f), 0f)
        assertTrue(abs(vitaBubbleJiggleLean(index = 0, elapsedSeconds = 0.01f)) > 0f)
    }

    @Test
    fun liftAtReadsTheSameChannelSoAPageTurnHopsOnY() {
        val jiggle = VitaBubbleJiggle(2)
        jiggle.setLean(0, 0.4f)
        assertEquals(0.4f, jiggle.liftAt(0), 0f)
        assertEquals(jiggle.leanAt(0), jiggle.liftAt(0), 0f)
    }

    @Test
    fun `wobble is per page so the same slot on every page behaves the same`() {
        assertEquals(
            vitaBubbleJiggleLean(index = 1, elapsedSeconds = 0.08f),
            vitaBubbleJiggleLean(index = 1 + VITA_TRAY_PAGE_SIZE, elapsedSeconds = 0.08f),
            1e-5f,
        )
    }
}
