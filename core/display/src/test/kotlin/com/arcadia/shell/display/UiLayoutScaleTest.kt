package com.arcadia.shell.display

import android.content.pm.ActivityInfo
import com.arcadia.shell.model.ShellDisplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UiLayoutScaleTest {

    @Test
    fun rgRotateIsASquarePanel() {
        assertTrue(isNearSquarePanel(720, 720))
        assertTrue(isNearSquarePanel(1080, 1080))
        assertFalse(isNearSquarePanel(1920, 1080))
        assertFalse(isNearSquarePanel(1280, 720))
    }

    @Test
    fun squarePanelsUseFullSensorSoTheUiFollowsGravity() {
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_FULL_SENSOR,
            xoraScreenOrientation(720, 720),
        )
        assertEquals(
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE,
            xoraScreenOrientation(1920, 1080),
        )
    }

    @Test
    fun squarePanelScaleDoesNotTreatMissingWidthAsATinyStrip() {
        val rotate = ShellDisplay(
            displayId = 0,
            name = "RG Rotate",
            widthPx = 720,
            heightPx = 720,
            densityDpi = 320,
            isPrimary = true,
            isPublic = true,
        )
        val cube1080 = rotate.copy(widthPx = 1080, heightPx = 1080, densityDpi = 320)
        val wide1080 = rotate.copy(widthPx = 1920, heightPx = 1080, densityDpi = 320)
        assertEquals(0.7f, computeUiLayoutScale(rotate), 0.001f)
        assertEquals(1.0f, computeUiLayoutScale(cube1080), 0.001f)
        assertEquals(1.0f, computeUiLayoutScale(wide1080), 0.001f)
    }
}
