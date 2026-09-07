package com.arcadia.shell.designsystem

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceVisualBudgetTest {

    @Test
    fun lowRamDeviceAlwaysSuggestsLite() {
        val budget = DeviceVisualBudget(
            totalRamBytes = 12L * 1024 * 1024 * 1024,
            memoryClassMb = 384,
            isLowRamDevice = true,
        )
        assertTrue(budget.suggestsLiteVisuals)
    }

    @Test
    fun fourGigPhoneSuggestsLite() {
        val budget = DeviceVisualBudget(
            totalRamBytes = 3_600_000_000L,
            memoryClassMb = 192,
            isLowRamDevice = false,
        )
        assertTrue(budget.suggestsLiteVisuals)
    }

    @Test
    fun sixGigA15ClassSuggestsLite() {
        val budget = DeviceVisualBudget(
            totalRamBytes = 5_600_000_000L,
            memoryClassMb = 256,
            isLowRamDevice = false,
        )
        assertTrue(budget.suggestsLiteVisuals)
    }

    @Test
    fun eightGigHandheldStaysFull() {
        val budget = DeviceVisualBudget(
            totalRamBytes = 8L * 1024 * 1024 * 1024,
            memoryClassMb = 384,
            isLowRamDevice = false,
        )
        assertFalse(budget.suggestsLiteVisuals)
    }

    @Test
    fun tightMemoryClassSuggestsLiteEvenWithLargeTotal() {
        val budget = DeviceVisualBudget(
            totalRamBytes = 8L * 1024 * 1024 * 1024,
            memoryClassMb = 128,
            isLowRamDevice = false,
        )
        assertTrue(budget.suggestsLiteVisuals)
    }

    @Test
    fun resolveLiteVisualsHonorsPowerSaveAndOverride() {
        assertTrue(resolveLiteVisuals(override = false, deviceSuggestsLite = false, powerSave = true))
        assertFalse(resolveLiteVisuals(override = false, deviceSuggestsLite = true, powerSave = false))
        assertTrue(resolveLiteVisuals(override = true, deviceSuggestsLite = false, powerSave = false))
        assertTrue(resolveLiteVisuals(override = null, deviceSuggestsLite = true, powerSave = false))
        assertFalse(resolveLiteVisuals(override = null, deviceSuggestsLite = false, powerSave = false))
    }
}
