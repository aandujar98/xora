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
        assertEquals(
            OnboardingPadCommand.Activate,
            onboardingPadCommand(
                NavAction.Confirm,
                canGoBack = true,
                canAdvance = true,
                optional = true,
                pickerOpen = true,
                intraForm = true,
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
    fun shoulderRepeatIsThrottledSoAHeldBumperCannotSkipTheWizard() {
        assertTrue(
            shouldAcceptOnboardingPadRepeat(
                NavAction.NextPlatform,
                nowMs = 0L,
                lastDirectionalMs = null,
            ),
        )
        assertFalse(
            shouldAcceptOnboardingPadRepeat(
                NavAction.NextPlatform,
                nowMs = 70L,
                lastDirectionalMs = 0L,
            ),
        )
        assertTrue(
            shouldAcceptOnboardingPadRepeat(
                NavAction.NextPlatform,
                nowMs = ONBOARDING_PAD_DIRECTION_DEBOUNCE_MS,
                lastDirectionalMs = 0L,
            ),
        )
        assertTrue(
            shouldAcceptOnboardingPadRepeat(
                NavAction.Right,
                nowMs = 10L,
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

    @Test
    fun dpadStaysOnControlsAndWalksRowsLikeAdvancedSettings() {
        val layout = SettingsPadLayout(
            listOf(
                listOf("steam", "discord"),
                listOf("api_key"),
                listOf(OnboardingPadIds.Back, OnboardingPadIds.Skip, OnboardingPadIds.Next),
            ),
        )
        val start = SettingsPadNavState(0, SettingsPadZone.Controls, 0, 0)
        val right = onboardingPadAfterAction(start, NavAction.Right, layout)
        assertEquals("discord", settingsPadFocusId(onboardingPadCoerce(right, layout), layout))

        val down = onboardingPadAfterAction(right, NavAction.Down, layout)
        assertEquals("api_key", settingsPadFocusId(onboardingPadCoerce(down, layout), layout))

        val chrome = onboardingPadAfterAction(down, NavAction.Down, layout)
        assertEquals(OnboardingPadIds.Back, settingsPadFocusId(onboardingPadCoerce(chrome, layout), layout))

        val skip = onboardingPadAfterAction(chrome, NavAction.Right, layout)
        assertEquals(OnboardingPadIds.Skip, settingsPadFocusId(onboardingPadCoerce(skip, layout), layout))

        val next = onboardingPadAfterAction(skip, NavAction.Right, layout)
        assertEquals(OnboardingPadIds.Next, settingsPadFocusId(onboardingPadCoerce(next, layout), layout))

        val still = onboardingPadAfterAction(next, NavAction.Down, layout)
        assertEquals(next.copy(zone = SettingsPadZone.Controls), still)
        assertTrue(settingsPadShouldScrollPage(still, still, NavAction.Down))
    }

    @Test
    fun coerceNeverPromotesTheWizardCursorIntoSetupTabs() {
        val empty = onboardingPadCoerce(
            SettingsPadNavState(0, SettingsPadZone.Tabs, 8, 4),
            SettingsPadLayout(),
        )
        assertEquals(SettingsPadZone.Controls, empty.zone)
        assertEquals(0, empty.rowIndex)
        assertEquals(0, empty.colIndex)
    }
}
