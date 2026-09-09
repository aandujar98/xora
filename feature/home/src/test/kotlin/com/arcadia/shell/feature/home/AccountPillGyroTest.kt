package com.arcadia.shell.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountPillGyroTest {

    @Test
    fun rollTiltsThePillAroundYAndParallaxX() {
        val pose = accountPillGyroFromDelta(deltaRollRad = 0.2f, deltaPitchRad = 0f)
        assertEquals(Math.toDegrees(0.2).toFloat(), pose.rotationY, 0.01f)
        assertEquals(0f, pose.rotationX, 0.01f)
        assertTrue(pose.translationX > 0f)
        assertEquals(0f, pose.translationY, 0.01f)
    }

    @Test
    fun pitchRocksTowardTheViewerOnX() {
        val pose = accountPillGyroFromDelta(deltaRollRad = 0f, deltaPitchRad = 0.15f)
        assertEquals((-Math.toDegrees(0.15)).toFloat(), pose.rotationX, 0.01f)
        assertEquals(0f, pose.rotationY, 0.01f)
    }

    @Test
    fun tiltIsCappedAtTwentyTwoDegrees() {
        val pose = accountPillGyroFromDelta(deltaRollRad = 2f, deltaPitchRad = -2f)
        assertEquals(ACCOUNT_PILL_MAX_TILT_DEGREES, pose.rotationY, 0.01f)
        assertEquals(ACCOUNT_PILL_MAX_TILT_DEGREES, pose.rotationX, 0.01f)
    }
}

