package com.arcadia.shell.launcher.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DashNotificationTest {

    @Test
    fun everyLineLandsInsideTheStatedThreeToFiveSeconds() {
        val short = dashNotificationDurationMs("Scan finished")
        assertTrue("a two-word line sits just off the floor", short in DASH_MIN_MS..(DASH_MIN_MS + 500))
        val long = "Logged 12h 30m in a game with a very long name indeed and then some more words"
        assertEquals(DASH_MAX_MS, dashNotificationDurationMs(long))
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
