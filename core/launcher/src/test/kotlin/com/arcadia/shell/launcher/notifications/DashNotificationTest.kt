package com.arcadia.shell.launcher.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DashNotificationTest {

    @Test
    fun everyLineLandsInsideTheStatedBand() {
        val short = dashNotificationDurationMs("Scan finished")
        assertTrue("a two-word line sits just off the floor", short in DASH_MIN_MS..(DASH_MIN_MS + 500))
        // Built from the constants rather than a hand-counted sentence, so raising the ceiling
        // cannot quietly stop this line from reaching it — which is what happened when the
        // maximum went from 5s to 6.75s and a 17-word string no longer clamped.
        val wordsToClamp = ((DASH_MAX_MS - DASH_MIN_MS) / DASH_MS_PER_WORD_FOR_TEST).toInt() + 2
        val long = List(wordsToClamp) { "word" }.joinToString(" ")
        assertEquals(DASH_MAX_MS, dashNotificationDurationMs(long))
    }

    /** Mirrors the production per-word step; kept here so the test reads without opening the file. */
    private companion object {
        const val DASH_MS_PER_WORD_FOR_TEST = 180L
    }

    @Test
    fun durationGrowsWithWordCountInsideTheBand() {
        val short = dashNotificationDurationMs("Fetching artwork")
        val longer = dashNotificationDurationMs("Fetching artwork for the whole library now")
        assertTrue("longer copy should hold longer", longer > short)
        assertTrue(longer in DASH_MIN_MS..DASH_MAX_MS)
    }

    @Test
    fun blankAndWhitespaceCopyStillClampsToTheFloor() {
        assertEquals(DASH_MIN_MS, dashNotificationDurationMs("   "))
    }
}
