package com.arcadia.shell.feature.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingStepTest {

    @Test
    fun `android apps sits between library and emulators`() {
        val steps = OnboardingStep.entries
        assertEquals(OnboardingStep.Library, steps[2])
        assertEquals(OnboardingStep.AndroidApps, steps[3])
        assertEquals(OnboardingStep.Emulators, steps[4])
        assertEquals(OnboardingStep.Scrapers, steps[5])
    }

    @Test
    fun `next is blocked only while the emulator scan is running`() {
        val scanning = OnboardingUiState(
            step = OnboardingStep.Emulators,
            scanRunning = true,
        )
        assertFalse(scanning.canAdvance)

        val ready = OnboardingUiState(
            step = OnboardingStep.Emulators,
            scanRunning = false,
            scanCompleted = true,
        )
        assertTrue(ready.canAdvance)

        val library = OnboardingUiState(step = OnboardingStep.Library, scanRunning = true)
        assertTrue(library.canAdvance)
    }
}
