package com.arcadia.shell.feature.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountPillGyroTest {

    @Test
    fun rollTiltsThePillAroundYAndParallaxX() {
        val pose = accountPillGyroFromDelta(deltaRollRad = 0.1f, deltaPitchRad = 0f)
        assertEquals(
            Math.toDegrees(0.1).toFloat() * ACCOUNT_PILL_TILT_GAIN,
            pose.rotationY,
            0.01f,
        )
        assertEquals(0f, pose.rotationX, 0.01f)
        assertTrue(pose.translationX > 0f)
        assertEquals(0f, pose.translationY, 0.01f)
    }

    @Test
    fun pitchRocksTowardTheViewerOnX() {
        val pose = accountPillGyroFromDelta(deltaRollRad = 0f, deltaPitchRad = 0.08f)
        assertEquals(
            (-Math.toDegrees(0.08)).toFloat() * ACCOUNT_PILL_TILT_GAIN,
            pose.rotationX,
            0.01f,
        )
        assertEquals(0f, pose.rotationY, 0.01f)
    }

    @Test
    fun aSmallWristTiltAlreadyReadsAsSeveralDegrees() {
        // 5° of physical roll — the pill has to visibly turn, or it reads as a flat sticker.
        val pose = accountPillGyroFromDelta(
            deltaRollRad = Math.toRadians(5.0).toFloat(),
            deltaPitchRad = 0f,
        )
        assertTrue("rotationY=${pose.rotationY}", pose.rotationY > 10f)
    }

    @Test
    fun tiltIsCappedAtTheMaximum() {
        val pose = accountPillGyroFromDelta(deltaRollRad = 2f, deltaPitchRad = -2f)
        assertEquals(ACCOUNT_PILL_MAX_TILT_DEGREES, pose.rotationY, 0.01f)
        assertEquals(ACCOUNT_PILL_MAX_TILT_DEGREES, pose.rotationX, 0.01f)
    }

    @Test
    fun idleRockSweepsBothAxesWithoutTheSensor() {
        val quarter = accountPillIdleLean(0.25f)
        assertTrue("x=${quarter.x}", quarter.x > 0.9f)
        val start = accountPillIdleLean(0f)
        assertEquals(0f, start.x, 0.01f)
        assertEquals(0f, start.y, 0.01f)
        assertTrue(accountPillIdleLean(0.125f).y > 0.4f)
    }
}
