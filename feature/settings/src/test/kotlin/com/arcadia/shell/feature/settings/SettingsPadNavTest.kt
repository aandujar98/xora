package com.arcadia.shell.feature.settings

import com.arcadia.shell.input.NavAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsPadNavTest {

    @Test
    fun shouldersAndDpadChangeTabsAndLandOnTheFirstCard() {
        val start = SettingsPadNavState(sectionIndex = 1, onTabs = true, cardIndex = 2)
        val left = settingsPadAfterAction(start, NavAction.Left, sectionCount = 7, cardCount = 2)
        assertEquals(0, left.sectionIndex)
        assertFalse(left.onTabs)
        assertEquals(0, left.cardIndex)

        val shoulder = settingsPadAfterAction(
            start,
            NavAction.NextPlatform,
            sectionCount = 7,
            cardCount = 2,
        )
        assertEquals(2, shoulder.sectionIndex)
        assertFalse(shoulder.onTabs)
        assertEquals(0, shoulder.cardIndex)

        val wrap = settingsPadAfterAction(
            SettingsPadNavState(0, onTabs = false, cardIndex = 0),
            NavAction.PreviousPlatform,
            sectionCount = 7,
            cardCount = 2,
        )
        assertEquals(6, wrap.sectionIndex)
    }

    @Test
    fun downLeavesTheWindowAndLandsOnTheFirstOption() {
        val next = settingsPadAfterAction(
            SettingsPadNavState(0, onTabs = true, cardIndex = 0),
            NavAction.Down,
            sectionCount = 7,
            cardCount = 2,
        )
        assertFalse(next.onTabs)
        assertEquals(0, next.cardIndex)
    }

    @Test
    fun upFromTheFirstOptionReachesTheTabsAndScrollsToTheTop() {
        val next = settingsPadAfterAction(
            SettingsPadNavState(0, onTabs = false, cardIndex = 0),
            NavAction.Up,
            sectionCount = 7,
            cardCount = 2,
        )
        assertTrue(next.onTabs)
        assertEquals(0, settingsLazyItemIndex(onTabs = true, cardIndex = 0, hasStatusBanner = true))
    }

    @Test
    fun cardScrollIndexClearsTheHeaderAndOptionalBanner() {
        assertEquals(1, settingsLazyItemIndex(onTabs = false, cardIndex = 0, hasStatusBanner = false))
        assertEquals(2, settingsLazyItemIndex(onTabs = false, cardIndex = 0, hasStatusBanner = true))
        assertEquals(3, settingsLazyItemIndex(onTabs = false, cardIndex = 1, hasStatusBanner = true))
    }
}
