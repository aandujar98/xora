package com.arcadia.shell.feature.settings

import com.arcadia.shell.input.NavAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OnboardingPadNavTest {

    @Test
    fun aAndRightAdvanceWhenTheStepAllowsIt() {
        assertEquals(
            OnboardingPadCommand.Next,
            onboardingPadCommand(
                NavAction.Confirm,
                canGoBack = true,
                canAdvance = true,
                optional = false,
                pickerOpen = false,
            ),
        )
        assertEquals(
            OnboardingPadCommand.Next,
            onboardingPadCommand(
                NavAction.Right,
                canGoBack = false,
                canAdvance = true,
                optional = false,
                pickerOpen = false,
            ),
        )
        assertEquals(
            OnboardingPadCommand.None,
            onboardingPadCommand(
                NavAction.Confirm,
                canGoBack = false,
                canAdvance = false,
                optional = false,
                pickerOpen = false,
            ),
        )
    }

    @Test
    fun bAndLeftGoBackAndYSkipsOptionalSteps() {
        assertEquals(
            OnboardingPadCommand.Back,
            onboardingPadCommand(
                NavAction.Cancel,
                canGoBack = true,
                canAdvance = true,
                optional = false,
                pickerOpen = false,
            ),
        )
        assertEquals(
            OnboardingPadCommand.None,
            onboardingPadCommand(
                NavAction.Left,
                canGoBack = false,
                canAdvance = true,
                optional = false,
                pickerOpen = false,
            ),
        )
        assertEquals(
            OnboardingPadCommand.Skip,
            onboardingPadCommand(
                NavAction.SwapScreens,
                canGoBack = true,
                canAdvance = true,
                optional = true,
                pickerOpen = false,
            ),
        )
        assertEquals(
            OnboardingPadCommand.None,
            onboardingPadCommand(
                NavAction.SwapScreens,
                canGoBack = true,
                canAdvance = true,
                optional = false,
                pickerOpen = false,
            ),
        )
    }

    @Test
    fun bClosesTheFolderPickerInsteadOfLeavingTheStep() {
        assertEquals(
            OnboardingPadCommand.DismissPicker,
            onboardingPadCommand(
                NavAction.Cancel,
                canGoBack = true,
                canAdvance = true,
                optional = false,
                pickerOpen = true,
            ),
        )
        assertEquals(
            OnboardingPadCommand.None,
            onboardingPadCommand(
                NavAction.Confirm,
                canGoBack = true,
                canAdvance = true,
                optional = false,
                pickerOpen = true,
            ),
        )
    }

    @Test
    fun shouldersAndOptionsMatchFaceButtons() {
        assertEquals(
            OnboardingPadCommand.Next,
            onboardingPadCommand(
                NavAction.NextPlatform,
                canGoBack = true,
                canAdvance = true,
                optional = false,
                pickerOpen = false,
            ),
        )
        assertEquals(
            OnboardingPadCommand.Back,
            onboardingPadCommand(
                NavAction.PreviousPlatform,
                canGoBack = true,
                canAdvance = true,
                optional = false,
                pickerOpen = false,
            ),
        )
        assertEquals(
            OnboardingPadCommand.Skip,
            onboardingPadCommand(
                NavAction.Options,
                canGoBack = true,
                canAdvance = true,
                optional = true,
                pickerOpen = false,
            ),
        )
    }

    @Test
    fun profileFormUsesAToActivateAndShouldersToChangeSteps() {
        assertEquals(
            OnboardingPadCommand.Activate,
            onboardingPadCommand(
                NavAction.Confirm,
                canGoBack = true,
                canAdvance = true,
                optional = false,
                pickerOpen = false,
                intraForm = true,
            ),
        )
        assertEquals(
            OnboardingPadCommand.None,
            onboardingPadCommand(
                NavAction.Right,
                canGoBack = true,
                canAdvance = true,
                optional = false,
                pickerOpen = false,
                intraForm = true,
            ),
        )
        assertEquals(
            OnboardingPadCommand.Next,
            onboardingPadCommand(
                NavAction.NextPlatform,
                canGoBack = true,
                canAdvance = true,
                optional = false,
                pickerOpen = false,
                intraForm = true,
            ),
        )
    }

    @Test
    fun dpadRepeatIsThrottledSoAHeldStickCannotSkipTheWizard() {
        assertTrue(shouldAcceptOnboardingPadRepeat(NavAction.Right, nowMs = 0L, lastDirectionalMs = null))
        assertFalse(
            shouldAcceptOnboardingPadRepeat(
                NavAction.Right,
                nowMs = 70L,
                lastDirectionalMs = 0L,
            ),
        )
        assertTrue(
            shouldAcceptOnboardingPadRepeat(
                NavAction.Right,
                nowMs = ONBOARDING_PAD_DIRECTION_DEBOUNCE_MS,
                lastDirectionalMs = 0L,
            ),
        )
        assertTrue(
            shouldAcceptOnboardingPadRepeat(
                NavAction.Confirm,
                nowMs = 10L,
                lastDirectionalMs = 0L,
            ),
        )
    }
}
