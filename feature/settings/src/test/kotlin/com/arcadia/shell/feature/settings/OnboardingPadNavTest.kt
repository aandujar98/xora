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
            ),
        )
        val chrome = onboardingChromeIds(canGoBack = true, optional = true)
        val start = SettingsPadNavState(0, SettingsPadZone.Controls, 0, 0)
        val right = onboardingPadAfterAction(start, NavAction.Right, layout, chrome)
        assertEquals("discord", onboardingPadFocusId(right, layout, chrome))

        val down = onboardingPadAfterAction(right, NavAction.Down, layout, chrome)
        assertEquals("api_key", onboardingPadFocusId(down, layout, chrome))
    }

    @Test
    fun upFromTheFirstFieldAndDownFromTheLastJumpToNext() {
        val layout = SettingsPadLayout(
            listOf(
                listOf("steam"),
                listOf("api_key"),
            ),
        )
        val chrome = onboardingChromeIds(canGoBack = true, optional = true)
        val first = SettingsPadNavState(0, SettingsPadZone.Controls, 0, 0)
        val up = onboardingPadAfterAction(first, NavAction.Up, layout, chrome)
        assertEquals(SettingsPadZone.Done, up.zone)
        assertEquals(OnboardingPadIds.Next, onboardingPadFocusId(up, layout, chrome))

        val last = SettingsPadNavState(0, SettingsPadZone.Controls, 1, 0)
        val down = onboardingPadAfterAction(last, NavAction.Down, layout, chrome)
        assertEquals(OnboardingPadIds.Next, onboardingPadFocusId(down, layout, chrome))

        val intoForm = onboardingPadAfterAction(down, NavAction.Down, layout, chrome)
        assertEquals("steam", onboardingPadFocusId(intoForm, layout, chrome))

        val back = onboardingPadAfterAction(up, NavAction.Left, layout, chrome)
        assertEquals(OnboardingPadIds.Skip, onboardingPadFocusId(back, layout, chrome))
        val further = onboardingPadAfterAction(back, NavAction.Left, layout, chrome)
        assertEquals(OnboardingPadIds.Back, onboardingPadFocusId(further, layout, chrome))
    }

    @Test
    fun emptyFormLandsOnNextAndStripsChromeFromTheControlGrid() {
        val chrome = onboardingChromeIds(canGoBack = false, optional = false)
        val empty = onboardingPadCoerce(
            SettingsPadNavState(0, SettingsPadZone.Controls, 8, 4),
            SettingsPadLayout(),
            chrome,
        )
        assertEquals(SettingsPadZone.Done, empty.zone)
        assertEquals(OnboardingPadIds.Next, onboardingPadFocusId(empty, SettingsPadLayout(), chrome))

        val mixed = onboardingControlsLayout(
            SettingsPadLayout(
                listOf(
                    listOf("steam"),
                    listOf(OnboardingPadIds.Back, OnboardingPadIds.Skip, OnboardingPadIds.Next),
                ),
            ),
        )
        assertEquals(listOf(listOf("steam")), mixed.rows)
    }

    @Test
    fun coerceNeverPromotesTheWizardCursorIntoSetupTabs() {
        val chrome = onboardingChromeIds(canGoBack = true, optional = false)
        val empty = onboardingPadCoerce(
            SettingsPadNavState(0, SettingsPadZone.Tabs, 8, 4),
            SettingsPadLayout(),
            chrome,
        )
        assertEquals(SettingsPadZone.Done, empty.zone)
        assertEquals(OnboardingPadIds.Next, onboardingPadFocusId(empty, SettingsPadLayout(), chrome))
    }
}
