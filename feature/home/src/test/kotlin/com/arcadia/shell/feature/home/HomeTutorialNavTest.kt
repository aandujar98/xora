package com.arcadia.shell.feature.home

import com.arcadia.shell.input.NavAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeTutorialNavTest {

    @Test
    fun aRightAndRbAdvanceTheCoachMarks() {
        assertEquals(HomeTutorialCommand.Next, homeTutorialPadCommand(NavAction.Confirm))
        assertEquals(HomeTutorialCommand.Next, homeTutorialPadCommand(NavAction.Right))
        assertEquals(HomeTutorialCommand.Next, homeTutorialPadCommand(NavAction.NextPlatform))
    }

    @Test
    fun bLeftAndLbSkipTheRest() {
        assertEquals(HomeTutorialCommand.Skip, homeTutorialPadCommand(NavAction.Cancel))
        assertEquals(HomeTutorialCommand.Skip, homeTutorialPadCommand(NavAction.Left))
        assertEquals(HomeTutorialCommand.Skip, homeTutorialPadCommand(NavAction.PreviousPlatform))
    }

    @Test
    fun chromeButtonsAreSwallowedSoTheyCannotOpenUnderTheTutorial() {
        assertNull(homeTutorialPadCommand(NavAction.ToggleAccountPanel))
        assertNull(homeTutorialPadCommand(NavAction.ToggleSystemPanel))
        assertNull(homeTutorialPadCommand(NavAction.SwapScreens))
        assertNull(homeTutorialPadCommand(NavAction.Up))
        assertNull(homeTutorialPadCommand(NavAction.Down))
    }

    @Test
    fun stepsWalkProfileSocialXmbThenShortcuts() {
        assertEquals(HomeTutorialStep.Social, HomeTutorialStep.Profile.nextOrNull())
        assertEquals(HomeTutorialStep.Xmb, HomeTutorialStep.Social.nextOrNull())
        assertEquals(HomeTutorialStep.Shortcuts, HomeTutorialStep.Xmb.nextOrNull())
        assertNull(HomeTutorialStep.Shortcuts.nextOrNull())
        assertFalse(HomeTutorialStep.Profile.isLast())
        assertTrue(HomeTutorialStep.Shortcuts.isLast())
    }

    @Test
    fun copyNamesTheButtonsPrinceAskedFor() {
        assertTrue(HomeTutorialStep.Profile.body().contains("RT"))
        assertTrue(HomeTutorialStep.Profile.body().contains("profile bubble"))
        assertTrue(HomeTutorialStep.Social.body().contains("LT"))
        assertTrue(HomeTutorialStep.Social.body().contains("capsule"))
        assertTrue(HomeTutorialStep.Xmb.body().contains("joystick"))
        assertTrue(HomeTutorialStep.Xmb.body().contains("swipe"))
        assertTrue(HomeTutorialStep.Shortcuts.body().contains("Y"))
        assertTrue(HomeTutorialStep.Shortcuts.body().contains("two fingers"))
    }

    @Test
    fun profileHoleSitsInTheTopRightAndSocialInTheTopLeft() {
        val profile = profileHoleDesign(viewportW = 1920f)
        assertTrue(profile.oval)
        assertTrue(profile.left > 1920f / 2f)
        assertTrue(profile.top < 40f)

        val social = socialHoleDesign()
        assertFalse(social.oval)
        assertTrue(social.left < 40f)
        assertTrue(social.top < 40f)
        assertTrue(social.width > 120f)
    }

    @Test
    fun xmbHoleCoversTheActiveTabAndRecentsPlate() {
        val hole = xmbTutorialHoleDesign()
        assertTrue(hole.left < 430f)
        assertTrue(hole.top < 282f)
        assertTrue(hole.left + hole.width > 430f)
        assertTrue(hole.top + hole.height > 420.5f + 200f)
        assertTrue(hole.left + hole.width < 1920f / 2f + 80f)
    }

    @Test
    fun finishingOnboardingReopensTheTutorialEvenIfItWasAlreadyDone() {
        assertTrue(
            shouldOfferHomeTutorial(onboardingComplete = true, homeTutorialComplete = false),
        )
        assertFalse(
            shouldOfferHomeTutorial(onboardingComplete = true, homeTutorialComplete = true),
        )
        assertFalse(
            shouldOfferHomeTutorial(onboardingComplete = false, homeTutorialComplete = false),
        )
    }
}
